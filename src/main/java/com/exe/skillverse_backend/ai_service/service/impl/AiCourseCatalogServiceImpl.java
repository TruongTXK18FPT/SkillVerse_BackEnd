package com.exe.skillverse_backend.ai_service.service.impl;

import com.exe.skillverse_backend.ai_service.service.AiCourseCatalogService;
import com.exe.skillverse_backend.ai_service.service.TaxonomyService;
import com.exe.skillverse_backend.ai_service.service.dto.CourseCatalogEntry;
import com.exe.skillverse_backend.ai_service.service.dto.CourseCatalogEntry.ModuleEntry;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.event.CourseRevisionApprovedEvent;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory course catalog index for Phase 2 multi-level matching.
 *
 * <p>Pre-selects courses using BM25 scoring (industry-standard, e.g. Elasticsearch/Solr)
 * instead of simple LIKE queries. Features:
 * <ul>
 *   <li>BM25 scoring with k1=1.2, b=0.75</li>
 *   <li>IDF pre-computed per term (rare terms score higher)</li>
 *   <li>Term saturation — diminishing returns for repeated terms</li>
 *   <li>Document-length normalization</li>
 *   <li>Inverted index for O(1) candidate lookup</li>
 * </ul>
 *
 * <p>Built once at startup via {@link #buildIndex()}, refreshed every 5 minutes via
 * {@link #scheduledRefresh()}. Thread-safe via ConcurrentHashMap.
 *
 * <h3>BM25 Formula</h3>
 * <pre>
 * score = IDF(t) × (tf × (k1 + 1)) / (tf + k1 × (1 - b + b × |D|/avgDl))
 *
 * where:
 *   IDF(t)     = log((N - df + 0.5) / (df + 0.5) + 1)
 *   tf        = term frequency in document
 *   |D|       = document length (word count)
 *   avgDl     = average document length across corpus
 *   k1 = 1.2  = term saturation parameter (higher → less saturation)
 *   b  = 0.75 = length normalization parameter (higher → longer docs penalized more)
 * </pre>
 *
 * @see AiCourseCatalogService
 * @see MultiLevelCourseMatcher
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiCourseCatalogServiceImpl implements AiCourseCatalogService {

    private static final double BM25_K1 = 1.2;
    private static final double BM25_B = 0.75;
    private static final int TAXONOMY_MAX_EXTRA_TERMS = 12;
    private static final double BM25_FALLBACK_THRESHOLD = 1.25;
    private static final double TF_IDF_SCORE_SCALE = 6.0;
    /** Minimum BM25 score to consider a result relevant. Below this → return empty (fallback to global). */
    private static final double BM25_MIN_RELEVANCE_THRESHOLD = 1.0;

    private static final double QUALITY_BOOST_MAX_RATIO = 0.25;
    private static final double QUALITY_ENROLLMENT_WEIGHT = 0.60;
    private static final double QUALITY_FRESHNESS_WEIGHT = 0.25;
    private static final double QUALITY_RATING_WEIGHT = 0.15;

    private static final double COMPLETED_COURSE_FACTOR = 0.25;
    private static final double ENROLLED_BASE_FACTOR = 0.80;
    private static final double ENROLLED_PROGRESS_FACTOR_DELTA = 0.25;
    private static final double DROPPED_COURSE_FACTOR = 0.90;

    private final CourseRepository courseRepository;
    private final ModuleRepository moduleRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final TaxonomyService taxonomyService;
    private final TfIdfVectorizer tfIdfVectorizer;

    // ===== Index structures =====

    /** term → courses containing that term (inverted index) */
    private final Map<String, List<Long>> invertedIndex = new ConcurrentHashMap<>(256);

    /** courseId → pre-computed term frequency map for this course */
    private final Map<Long, Map<String, Long>> courseTermFreqs = new ConcurrentHashMap<>(256);

    /** term → document frequency (how many courses contain this term) */
    private final Map<String, Long> documentFreqs = new ConcurrentHashMap<>(256);

    /** courseId → CourseCatalogEntry (main index) */
    private final Map<Long, CourseCatalogEntry> courseIndex = new ConcurrentHashMap<>(256);

    /** courseId → list of module IDs (ordered by orderIndex) */
    private final Map<Long, List<Long>> moduleIndex = new ConcurrentHashMap<>(256);

    /** courseId → ModuleEntry list (id + title + orderIndex) */
    private final Map<Long, List<ModuleEntry>> moduleEntryIndex = new ConcurrentHashMap<>(256);

    /** moduleId → list of prerequisite module IDs for that module */
    private final Map<Long, List<Long>> prerequisiteIndex = new ConcurrentHashMap<>(256);

    /** courseId → document length (word count in searchable fields) */
    private final Map<Long, Integer> docLengths = new ConcurrentHashMap<>(256);

    /** Average document length across all indexed courses */
    private volatile int avgDocLength = 1;

    /** Total number of indexed courses */
    private volatile int totalCourses = 0;

    /** Max enrollment count for quality normalization. */
    private volatile long maxEnrollmentCount = 1;

    /** Min and max createdAt for recency normalization. */
    private volatile long minCreatedAtEpochMillis = 0;
    private volatile long maxCreatedAtEpochMillis = 0;

    private volatile boolean loaded = false;

    // ===== Lifecycle =====

    @PostConstruct
    public void init() {
        log.info("[Catalog] @PostConstruct — building index on startup...");
        if (safeBuildIndex("startup")) {
            log.info("[Catalog] Startup index built. {} courses indexed.", totalCourses);
        } else {
            log.warn("[Catalog] Startup index build skipped; catalog will remain empty until the next successful refresh.");
        }
    }

    @Transactional(readOnly = true)
    public void scheduledRefresh() {
        log.info("[Catalog] Scheduled refresh starting...");
        if (safeBuildIndex("scheduled refresh")) {
            log.info("[Catalog] Scheduled refresh done. {} courses indexed.", totalCourses);
        }
    }

    @Override
    public void refresh() {
        safeBuildIndex("manual refresh");
    }

    /**
     * Incrementally refresh a single course in the BM25 index.
     * Used for event-driven updates when a course revision is approved,
     * replacing the need to rebuild the entire index.
     */
    @Transactional(readOnly = true)
    public void refreshCourse(Long courseId) {
        if (courseId == null) {
            return;
        }

        // Remove old entry from all index structures
        courseIndex.remove(courseId);
        courseTermFreqs.remove(courseId);
        docLengths.remove(courseId);
        moduleIndex.remove(courseId);
        moduleEntryIndex.remove(courseId);
        prerequisiteIndex.remove(courseId);

        // Remove from inverted index and document frequencies
        for (Map.Entry<String, List<Long>> entry : new ConcurrentHashMap<>(invertedIndex).entrySet()) {
            if (entry.getValue().contains(courseId)) {
                entry.getValue().remove(courseId);
                if (entry.getValue().isEmpty()) {
                    invertedIndex.remove(entry.getKey());
                    documentFreqs.remove(entry.getKey());
                }
            }
        }

        // Re-load and index the course if it's PUBLIC with an active revision
        List<Object[]> rows = courseRepository.findPublicCourseById(courseId);
        if (rows == null || rows.isEmpty()) {
            log.info("[Catalog] Course {} is no longer public or has no active revision — removed from index.", courseId);
            recalculateAvgDocLength();
            return;
        }

        Object[] row = rows.get(0);
        Long id = ((Number) row[0]).longValue();
        String title = (String) row[1];
        String description = (String) row[2];
        String shortDesc = (String) row[3];
        String category = (String) row[4];
        String level = (String) row[5];
        Instant createdAt = toInstant(row.length > 6 ? row[6] : null);
        long enrollmentCount = toLong(row.length > 7 ? row[7] : null);
        String learningObjectives = listToString(row.length > 8 ? row[8] : null);
        String requirements = listToString(row.length > 9 ? row[9] : null);
        String courseSkillTags = listToString(row.length > 10 ? row[10] : null);

        // Build course entry
        CourseCatalogEntry entry = CourseCatalogEntry.builder()
                .id(id)
                .title(safe(title))
                .description(safe(description))
                .shortDescription(safe(shortDesc))
                .category(safe(category))
                .level(safe(level))
                .createdAt(createdAt)
                .enrollmentCount(enrollmentCount)
                .averageRating(0.0)
                .moduleIds(new ArrayList<>())
                .modules(new ArrayList<>())
                .build();
        courseIndex.put(id, entry);

        // Build searchable text
        String searchable = (safe(title) + " " +
                safe(description) + " " +
                safe(shortDesc) + " " +
                safe(category) + " " +
                safe(learningObjectives) + " " +
                safe(requirements) + " " +
                safe(courseSkillTags)).toLowerCase();

        // Add module titles
        List<Object[]> moduleRows = moduleRepository.findAllModulesWithCourseId(List.of(courseId));
        for (Object[] mr : moduleRows) {
            Long mid = ((Number) mr[1]).longValue();
            String modTitle = (String) mr[2];
            int orderIdx = mr[3] != null ? ((Number) mr[3]).intValue() : 0;
            entry.getModuleIds().add(mid);
            entry.getModules().add(ModuleEntry.builder()
                    .id(mid)
                    .title(safe(modTitle))
                    .orderIndex(orderIdx)
                    .build());
            searchable += " " + safe(modTitle);
        }

        // Tokenize and update index structures
        List<String> terms = tokenizeWithBigrams(searchable);
        Map<String, Long> termFreq = new HashMap<>();
        for (String term : terms) {
            termFreq.merge(term, 1L, Long::sum);
        }

        docLengths.put(id, terms.size());
        courseTermFreqs.put(id, termFreq);

        for (String term : termFreq.keySet()) {
            invertedIndex.computeIfAbsent(term, k -> new ArrayList<>()).add(id);
            documentFreqs.merge(term, 1L, Long::sum);
        }

        // Prerequisite index
        List<Object[]> prereqRows = moduleRepository.findPrerequisitesByCourseIds(List.of(courseId));
        for (Object[] pr : prereqRows) {
            Long moduleId = (Long) pr[0];
            Long prereqId = (Long) pr[1];
            prerequisiteIndex.computeIfAbsent(moduleId, k -> new ArrayList<>()).add(prereqId);
        }

        // Update global stats
        maxEnrollmentCount = Math.max(maxEnrollmentCount, enrollmentCount);
        if (createdAt != null) {
            long epoch = createdAt.toEpochMilli();
            if (minCreatedAtEpochMillis == 0 || epoch < minCreatedAtEpochMillis) {
                minCreatedAtEpochMillis = epoch;
            }
            if (epoch > maxCreatedAtEpochMillis) {
                maxCreatedAtEpochMillis = epoch;
            }
        }
        recalculateAvgDocLength();

        log.info("[Catalog] Course {} refreshed in index with {} modules, {} searchable terms.",
                id, entry.getModuleIds().size(), termFreq.size());
    }

    private void recalculateAvgDocLength() {
        if (docLengths.isEmpty()) {
            avgDocLength = 1;
            totalCourses = 0;
            return;
        }
        totalCourses = docLengths.size();
        avgDocLength = (int) (docLengths.values().stream().mapToInt(Integer::intValue).sum() / totalCourses);
    }

    /**
     * Event-driven: trigger immediate index update when a course revision is approved.
     */
    @EventListener
    public void onCourseRevisionApproved(CourseRevisionApprovedEvent event) {
        log.info("[Catalog] Received CourseRevisionApprovedEvent — refreshing course {} (revision {})",
                event.getCourseId(), event.getRevisionId());
        refreshCourse(event.getCourseId());
    }

    @Override
    public boolean isLoaded() {
        return loaded;
    }

    // ===== Public API =====

    @Override
    @Transactional(readOnly = true)
    public List<CourseCatalogEntry> preSelectCourses(String topic, int limit) {
        return preSelectCourses(topic, limit, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CourseCatalogEntry> preSelectCourses(String topic, int limit, Long userId) {
        if (!loaded) {
            synchronized (this) {
                if (!loaded) {
                    if (!safeBuildIndex("lazy pre-select")) {
                        return List.of();
                    }
                }
            }
        }

        String rawTopic = topic != null ? topic : "";
        String normalizedTopic = normalizeText(rawTopic);
        List<String> baseTerms = extractTerms(normalizedTopic, false);
        List<String> queryTerms = expandQueryTerms(rawTopic, baseTerms);

        if (queryTerms.isEmpty()) {
            log.debug("[Catalog] Skip preSelect: no query terms after normalization for topic='{}'", rawTopic);
            return List.of();
        }

        Set<Long> candidateIds = collectCandidateIds(queryTerms);
        Map<Long, Double> bm25Scores = scoreCandidatesWithBm25(candidateIds, queryTerms);

        Map<Long, Double> rankingScores = new LinkedHashMap<>(bm25Scores);
        double maxBm25 = bm25Scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        boolean shouldFallback = bm25Scores.isEmpty() || maxBm25 < BM25_FALLBACK_THRESHOLD;

        if (log.isDebugEnabled()) {
            log.debug(
                "[Catalog] topic='{}' terms={} candidateIds={} bm25Hits={} bm25Max={} fallback={} limit={}",
                rawTopic,
                queryTerms.size(),
                candidateIds.size(),
                bm25Scores.size(),
                String.format("%.4f", maxBm25),
                shouldFallback,
                limit);
        }

        if (shouldFallback) {
            Set<Long> fallbackPool = candidateIds.isEmpty()
                    ? new LinkedHashSet<>(courseIndex.keySet())
                    : candidateIds;

            Map<Long, Double> tfIdfScores = tfIdfVectorizer.computeCosineScores(
                    queryTerms,
                    fallbackPool,
                    courseTermFreqs,
                    documentFreqs,
                    totalCourses);

            for (Map.Entry<Long, Double> entry : tfIdfScores.entrySet()) {
                rankingScores.merge(entry.getKey(), entry.getValue() * TF_IDF_SCORE_SCALE, Double::sum);
            }

            if (!tfIdfScores.isEmpty()) {
                log.debug("[Catalog] Applied TF-IDF fallback for topic '{}' (bm25Max={}, tfidfHits={})",
                        rawTopic, String.format("%.4f", maxBm25), tfIdfScores.size());
            }
        }

        if (rankingScores.isEmpty()) {
            log.debug("[Catalog] Empty ranking scores for topic='{}'", rawTopic);
            return List.of();
        }

        // BM25_MIN_RELEVANCE_THRESHOLD: if top score is below threshold, return empty
        // so the caller falls back to global pre-selection (per-node matching in MultiLevelCourseMatcher).
        double maxFinalScore = Collections.max(rankingScores.values());
        if (maxFinalScore < BM25_MIN_RELEVANCE_THRESHOLD) {
            log.info("[Catalog] BM25 max score {} < threshold {} for topic='{}' — returning empty (fallback to global)",
                    String.format("%.4f", maxFinalScore), BM25_MIN_RELEVANCE_THRESHOLD, rawTopic);
            return List.of();
        }

        Map<Long, Double> qualityAdjustedScores = applyQualityBonus(rankingScores);
        Map<Long, Double> historyAdjustedScores = applyUserHistoryAdjustments(qualityAdjustedScores, userId);

        List<Map.Entry<Long, Double>> rankedEntries = historyAdjustedScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());

        if (log.isDebugEnabled()) {
            String topSummary = rankedEntries.stream()
                    .limit(3)
                    .map(entry -> entry.getKey() + "#" + String.format("%.4f", entry.getValue()))
                    .collect(Collectors.joining(" | "));
            log.debug("[Catalog] Final top courses for topic='{}': {}", rawTopic, topSummary);
        }

        // 3. Sort descending, clone with score, return top-N
        return rankedEntries.stream()
                .map(entry -> cloneWithScore(entry.getKey(), entry.getValue()))
                .filter(c -> c != null)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getModuleIds(Long courseId) {
        List<Long> ids = moduleIndex.get(courseId);
        return ids != null ? List.copyOf(ids) : List.of();
    }

    @Override
    public List<ModuleEntry> getModuleEntries(Long courseId) {
        List<ModuleEntry> entries = moduleEntryIndex.get(courseId);
        return entries != null ? List.copyOf(entries) : List.of();
    }

    @Override
    public List<Long> getModulePrerequisiteIds(Long courseId, Long moduleId) {
        // The prerequisite index is keyed by moduleId (globally unique, not per-course)
        List<Long> prereqs = prerequisiteIndex.get(moduleId);
        return prereqs != null ? List.copyOf(prereqs) : List.of();
    }

    // ===== BM25 scoring =====

    /**
     * Compute BM25 score for a single course given query terms.
     *
     * <pre>
     * score = Σ IDF(t) × (tf × (k1 + 1)) / (tf + k1 × (1 - b + b × |D|/avgDl))
     * </pre>
     */
    private double computeBm25Score(Long courseId, List<String> queryTerms, int n) {
        Map<String, Long> termFreqs = courseTermFreqs.get(courseId);
        if (termFreqs == null) return 0;

        int docLen = docLengths.getOrDefault(courseId, 1);
        double score = 0;

        for (String term : queryTerms) {
            Long df = documentFreqs.get(term); // document frequency
            if (df == null || df == 0) continue;

            long tf = termFreqs.getOrDefault(term, 0L);
            if (tf == 0) continue;

            // IDF with smoothing
            double idf = Math.log(((n - df + 0.5) / (df + 0.5)) + 1.0);
            if (idf < 0) idf = 0; // clamp negative IDF

            // BM25 term saturation
            double numerator = tf * (BM25_K1 + 1);
            double denominator = tf + BM25_K1 * (1 - BM25_B + BM25_B * ((double) docLen / avgDocLength));

            score += idf * (numerator / denominator);
        }

        return score;
    }

    // ===== Index building =====

    private void buildIndex() {
        Map<Long, CourseCatalogEntry> newIndex = new ConcurrentHashMap<>(256);
        Map<Long, Map<String, Long>> newTermFreqs = new ConcurrentHashMap<>(256);
        Map<String, Long> newDocFreqs = new ConcurrentHashMap<>(256);
        Map<String, List<Long>> newInverted = new ConcurrentHashMap<>(256);
        Map<Long, Integer> newDocLengths = new ConcurrentHashMap<>(256);
        Map<Long, List<Long>> newModuleIndex = new ConcurrentHashMap<>(256);
        Map<Long, List<ModuleEntry>> newModuleEntryIndex = new ConcurrentHashMap<>(256);
        Map<Long, List<Long>> newPrereqIndex = new ConcurrentHashMap<>(256);
        Map<Long, StringBuilder> moduleTitlesBuilder = new ConcurrentHashMap<>(256);
        Map<Long, String> courseSearchableText = new ConcurrentHashMap<>(256);
        long newMaxEnrollment = 1;
        long newMinCreatedAt = Long.MAX_VALUE;
        long newMaxCreatedAt = Long.MIN_VALUE;

        // === Phase 1: Load all PUBLIC courses ===
        List<Object[]> courseRows = courseRepository.findAllPublicCourseProjectionsV2();
        for (Object[] row : courseRows) {
            Long id = ((Number) row[0]).longValue();
            String title = (String) row[1];
            String description = (String) row[2];
            String shortDesc = (String) row[3];
            String category = (String) row[4];
            String level = (String) row[5];
            Instant createdAt = toInstant(row.length > 6 ? row[6] : null);
            long enrollmentCount = toLong(row.length > 7 ? row[7] : null);
            String learningObjectives = listToString(row.length > 8 ? row[8] : null);
            String requirements = listToString(row.length > 9 ? row[9] : null);
            String courseSkillTags = listToString(row.length > 10 ? row[10] : null);

            if (createdAt != null) {
                long epoch = createdAt.toEpochMilli();
                newMinCreatedAt = Math.min(newMinCreatedAt, epoch);
                newMaxCreatedAt = Math.max(newMaxCreatedAt, epoch);
            }
            newMaxEnrollment = Math.max(newMaxEnrollment, enrollmentCount);

            newIndex.put(id, CourseCatalogEntry.builder()
                    .id(id)
                    .title(safe(title))
                    .description(safe(description))
                    .shortDescription(safe(shortDesc))
                    .category(safe(category))
                    .level(safe(level))
                    .createdAt(createdAt)
                    .enrollmentCount(enrollmentCount)
                    .averageRating(0.0)
                    .moduleIds(new ArrayList<>())
                    .modules(new ArrayList<>())
                    .build());
            // Build searchable text from metadata fields for BM25 indexing
            courseSearchableText.put(id, (safe(title) + " " +
                    safe(description) + " " +
                    safe(shortDesc) + " " +
                    safe(category) + " " +
                    safe(learningObjectives) + " " +
                    safe(requirements) + " " +
                    safe(courseSkillTags)));
        }

        if (newIndex.isEmpty()) {
            loaded = true;
            return;
        }

        List<Long> courseIds = new ArrayList<>(newIndex.keySet());

        // === Phase 2: Batch load module counts ===
        for (Object[] row : moduleRepository.countByCourseIds(courseIds)) {
            Long cid = ((Number) row[0]).longValue();
            int count = ((Number) row[1]).intValue();
            CourseCatalogEntry entry = newIndex.get(cid);
            if (entry != null) entry.setModuleCount(count);
        }

        // === Phase 3: Batch load module IDs and titles ===
        for (Object[] row : moduleRepository.findAllModulesWithCourseId(courseIds)) {
            Long cid = ((Number) row[0]).longValue();
            Long mid = ((Number) row[1]).longValue();
            String modTitle = (String) row[2];
            int orderIdx = row[3] != null ? ((Number) row[3]).intValue() : 0;

            CourseCatalogEntry entry = newIndex.get(cid);
            if (entry != null) {
                entry.getModuleIds().add(mid);
                entry.getModules().add(ModuleEntry.builder()
                        .id(mid)
                        .title(safe(modTitle))
                        .orderIndex(orderIdx)
                        .build());
            }

            // Also build searchable text from module titles for this course
            // (stored temporarily in a map keyed by courseId, used in Phase 4)
            moduleTitlesBuilder.computeIfAbsent(cid, k -> new StringBuilder())
                    .append(" ")
                    .append(safe(modTitle));
        }

        // === Phase 3b: Load prerequisite relationships ===
        for (Object[] row : moduleRepository.findPrerequisitesByCourseIds(courseIds)) {
            Long moduleId = ((Number) row[0]).longValue();
            Long prereqId = (Long) row[1];
            newPrereqIndex.computeIfAbsent(moduleId, k -> new ArrayList<>()).add(prereqId);
        }

        // === Phase 4: Build term frequencies, document lengths, inverted index ===
        for (CourseCatalogEntry course : newIndex.values()) {
            Long cid = course.getId();
            // Include module titles in searchable text so BM25 can match courses by module topic
            StringBuilder moduleTitles = moduleTitlesBuilder.get(cid);
            String searchable = (courseSearchableText.getOrDefault(cid, "") + " " +
                    (moduleTitles != null ? moduleTitles.toString() : "")).toLowerCase();

            // Tokenize with bigrams for compound phrases
            List<String> terms = tokenizeWithBigrams(searchable);
            Map<String, Long> termFreq = new HashMap<>();
            for (String term : terms) {
                termFreq.merge(term, 1L, Long::sum);
            }

            int docLen = terms.size();
            newDocLengths.put(cid, docLen);

            // Update document frequency for each unique term
            for (String term : termFreq.keySet()) {
                newDocFreqs.merge(term, 1L, Long::sum);
                newInverted.computeIfAbsent(term, k -> new ArrayList<>()).add(cid);
            }

            newTermFreqs.put(cid, termFreq);
            newModuleIndex.put(cid, List.copyOf(course.getModuleIds()));
            newModuleEntryIndex.put(cid, List.copyOf(course.getModules()));
        }

        // === Phase 5: Compute average document length ===
        int totalLen = newDocLengths.values().stream().mapToInt(Integer::intValue).sum();
        int n = newIndex.size();

        // === Atomically replace all indexes ===
        synchronized (this) {
            courseIndex.clear();
            courseIndex.putAll(newIndex);
            courseTermFreqs.clear();
            courseTermFreqs.putAll(newTermFreqs);
            documentFreqs.clear();
            documentFreqs.putAll(newDocFreqs);
            invertedIndex.clear();
            invertedIndex.putAll(newInverted);
            docLengths.clear();
            docLengths.putAll(newDocLengths);
            moduleIndex.clear();
            moduleIndex.putAll(newModuleIndex);
            moduleEntryIndex.clear();
            moduleEntryIndex.putAll(newModuleEntryIndex);
            prerequisiteIndex.clear();
            prerequisiteIndex.putAll(newPrereqIndex);
            avgDocLength = n > 0 ? totalLen / n : 1;
            totalCourses = n;
            maxEnrollmentCount = Math.max(1, newMaxEnrollment);
            minCreatedAtEpochMillis = newMinCreatedAt == Long.MAX_VALUE ? 0 : newMinCreatedAt;
            maxCreatedAtEpochMillis = newMaxCreatedAt == Long.MIN_VALUE ? 0 : newMaxCreatedAt;
            loaded = true;
        }
    }

    private boolean safeBuildIndex(String reason) {
        try {
            buildIndex();
            return true;
        } catch (RuntimeException ex) {
            log.warn("[Catalog] {} index build skipped: {}", reason, ex.getMessage(), ex);
            return false;
        }
    }

    private CourseCatalogEntry cloneWithScore(Long courseId, double score) {
        CourseCatalogEntry src = courseIndex.get(courseId);
        if (src == null) {
            return null;
        }

        CourseCatalogEntry clone = CourseCatalogEntry.builder()
                .id(src.getId())
                .title(src.getTitle())
                .description(src.getDescription())
                .shortDescription(src.getShortDescription())
                .category(src.getCategory())
                .level(src.getLevel())
                .moduleCount(src.getModuleCount())
                .enrollmentCount(src.getEnrollmentCount())
                .averageRating(src.getAverageRating())
                .createdAt(src.getCreatedAt())
                .moduleIds(List.copyOf(src.getModuleIds()))
                .modules(List.copyOf(src.getModules()))
                .build();
        clone.setScore((int) Math.round(score));
        return clone;
    }

    private Map<Long, Double> scoreCandidatesWithBm25(Set<Long> candidateIds, List<String> queryTerms) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Double> scores = new LinkedHashMap<>();
        int n = totalCourses;
        for (Long courseId : candidateIds) {
            double score = computeBm25Score(courseId, queryTerms, n);
            if (score > 0) {
                scores.put(courseId, score);
            }
        }
        return scores;
    }

    private Set<Long> collectCandidateIds(List<String> queryTerms) {
        Set<Long> candidateIds = new LinkedHashSet<>();
        for (String term : queryTerms) {
            List<Long> ids = invertedIndex.get(term);
            if (ids != null) {
                candidateIds.addAll(ids);
            }
        }
        return candidateIds;
    }

    private List<String> expandQueryTerms(String rawTopic, List<String> baseTerms) {
        LinkedHashSet<String> baseSet = new LinkedHashSet<>();
        for (String term : baseTerms) {
            if (term != null && !term.isBlank()) {
                baseSet.add(term.trim());
            }
        }
        if (baseSet.isEmpty()) {
            return List.of();
        }

        Set<String> expanded = taxonomyService.expandQueryWithTaxonomy(rawTopic, baseSet, TAXONOMY_MAX_EXTRA_TERMS);
        LinkedHashSet<String> merged = new LinkedHashSet<>(baseSet);
        if (expanded != null) {
            for (String term : expanded) {
                if (term != null && !term.isBlank()) {
                    merged.add(term.trim());
                }
            }
        }

        if (merged.size() > baseSet.size()) {
            log.debug("[Catalog] Query expanded with taxonomy from {} to {} terms", baseSet.size(), merged.size());
        }

        return new ArrayList<>(merged);
    }

    private Map<Long, Double> applyQualityBonus(Map<Long, Double> baseScores) {
        Map<Long, Double> adjusted = new LinkedHashMap<>(baseScores.size());
        for (Map.Entry<Long, Double> entry : baseScores.entrySet()) {
            CourseCatalogEntry course = courseIndex.get(entry.getKey());
            if (course == null) {
                continue;
            }
            double qualitySignal = computeQualitySignal(course);
            double boosted = entry.getValue() * (1 + QUALITY_BOOST_MAX_RATIO * qualitySignal);
            adjusted.put(entry.getKey(), boosted);
        }
        return adjusted;
    }

    private Map<Long, Double> applyUserHistoryAdjustments(Map<Long, Double> baseScores, Long userId) {
        if (userId == null || userId <= 0 || baseScores.isEmpty()) {
            return baseScores;
        }

        List<Long> courseIds = new ArrayList<>(baseScores.keySet());
        List<CourseEnrollment> enrollments;
        try {
            enrollments = courseEnrollmentRepository.findByUserIdAndCourseIdIn(userId, courseIds);
        } catch (Exception ex) {
            log.warn("[Catalog] Failed to apply user-history ranking for user {} — using base scores", userId, ex);
            return baseScores;
        }

        if (enrollments == null || enrollments.isEmpty()) {
            return baseScores;
        }

        Map<Long, CourseEnrollment> enrollmentByCourseId = new HashMap<>();
        for (CourseEnrollment enrollment : enrollments) {
            if (enrollment != null && enrollment.getCourse() != null && enrollment.getCourse().getId() != null) {
                enrollmentByCourseId.put(enrollment.getCourse().getId(), enrollment);
            }
        }

        if (enrollmentByCourseId.isEmpty()) {
            return baseScores;
        }

        Map<Long, Double> adjusted = new LinkedHashMap<>(baseScores.size());
        int penalized = 0;
        for (Map.Entry<Long, Double> entry : baseScores.entrySet()) {
            CourseEnrollment enrollment = enrollmentByCourseId.get(entry.getKey());
            double factor = computeUserHistoryFactor(enrollment);
            if (factor < 0.999) {
                penalized++;
            }
            adjusted.put(entry.getKey(), entry.getValue() * factor);
        }

        if (penalized > 0) {
            log.debug("[Catalog] Applied user-history weighting for user {}: {}/{} courses adjusted",
                    userId, penalized, baseScores.size());
        }

        return adjusted;
    }

    private double computeUserHistoryFactor(CourseEnrollment enrollment) {
        if (enrollment == null || enrollment.getStatus() == null) {
            return 1.0;
        }

        EnrollmentStatus status = enrollment.getStatus();
        if (status == EnrollmentStatus.COMPLETED) {
            return COMPLETED_COURSE_FACTOR;
        }

        if (status == EnrollmentStatus.ENROLLED) {
            int progress = enrollment.getProgressPercent() != null
                    ? Math.max(0, Math.min(100, enrollment.getProgressPercent()))
                    : 0;
            double dynamicPenalty = (progress / 100.0) * ENROLLED_PROGRESS_FACTOR_DELTA;
            return Math.max(0.40, ENROLLED_BASE_FACTOR - dynamicPenalty);
        }

        if (status == EnrollmentStatus.DROPPED) {
            return DROPPED_COURSE_FACTOR;
        }

        return 1.0;
    }

    private double computeQualitySignal(CourseCatalogEntry course) {
        double enrollmentSignal = maxEnrollmentCount > 0
                ? Math.min(1.0, (double) course.getEnrollmentCount() / maxEnrollmentCount)
                : 0.0;

        double freshnessSignal = computeFreshnessSignal(course.getCreatedAt());
        double ratingSignal = Math.min(1.0, Math.max(0.0, course.getAverageRating()) / 5.0);

        double quality = (enrollmentSignal * QUALITY_ENROLLMENT_WEIGHT)
                + (freshnessSignal * QUALITY_FRESHNESS_WEIGHT)
                + (ratingSignal * QUALITY_RATING_WEIGHT);
        return Math.min(1.0, Math.max(0.0, quality));
    }

    private double computeFreshnessSignal(Instant createdAt) {
        if (createdAt == null || minCreatedAtEpochMillis <= 0 || maxCreatedAtEpochMillis <= 0) {
            return 0.5;
        }

        if (maxCreatedAtEpochMillis == minCreatedAtEpochMillis) {
            return 0.5;
        }

        long epoch = createdAt.toEpochMilli();
        double normalized = (double) (epoch - minCreatedAtEpochMillis)
                / (double) (maxCreatedAtEpochMillis - minCreatedAtEpochMillis);
        return Math.min(1.0, Math.max(0.0, normalized));
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0;
    }

    private Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        return null;
    }

    // ===== Tokenization =====

    /**
     * Tokenize text and extract bigrams for compound phrases.
     * Returns deduplicated list of terms.
     */
    private List<String> tokenizeWithBigrams(String text) {
        if (text == null || text.isBlank()) return List.of();

        String[] raw = text.toLowerCase().split("[\\s,.!?;:/\\-_()\\[\\]{}\"']+");
        List<String> tokens = new ArrayList<>();
        for (String t : raw) {
            t = t.trim();
            if (t.length() >= 2) {
                tokens.add(t);
            }
        }

        List<String> terms = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (!isStopWord(t)) terms.add(t);

            // Bigram: pair current + next token
            if (i < tokens.size() - 1) {
                String bg = t + " " + tokens.get(i + 1);
                if (!isStopWord(bg)) terms.add(bg);
            }
        }
        return terms;
    }

    /**
     * Extract query terms from text (stopwords excluded, bigrams included).
     */
    private List<String> extractTerms(String text, boolean includeStopwords) {
        if (text == null || text.isBlank()) return List.of();

        String[] raw = text.toLowerCase().split("[\\s,.!?;:/\\-_()\\[\\]{}\"']+");
        List<String> terms = new ArrayList<>();
        for (int i = 0; i < raw.length; i++) {
            String t = raw[i].trim();
            if (t.isEmpty()) continue;

            if (includeStopwords || !isStopWord(t)) {
                terms.add(t);
            }
            if (i < raw.length - 1) {
                String bg = t + " " + raw[i + 1].trim();
                if (!isStopWord(bg)) terms.add(bg);
            }
        }
        return terms;
    }

    private String normalizeText(String text) {
        if (text == null) return "";
        return text.replaceAll("[^a-zA-Z0-9\\s]", " ").toLowerCase().trim();
    }

    private String safe(String s) {
        return s != null ? s : "";
    }

    private String listToString(Object field) {
        if (field == null) return "";
        if (field instanceof List<?> list) {
            return list.stream()
                    .filter(v -> v != null)
                    .map(v -> v.toString())
                    .collect(Collectors.joining(" "));
        }
        // Handle JSON string from PostgreSQL JSONB::TEXT (e.g. "[\"Java\",\"Spring\"]")
        String str = field.toString().trim();
        if (str.startsWith("[")) {
            return parseJsonArray(str);
        }
        return str;
    }

    private String parseJsonArray(String json) {
        if (json == null || json.isBlank()) return "";
        String inner = json.trim();
        if (inner.startsWith("[") && inner.endsWith("]")) {
            inner = inner.substring(1, inner.length() - 1).trim();
        }
        if (inner.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        boolean inQuote = false;
        boolean escape = false;
        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (ch == '\\') {
                escape = true;
                continue;
            }
            if (ch == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (!inQuote && ch == ',') {
                result.append(' ');
                continue;
            }
            if (!inQuote && ch != '"') {
                result.append(ch);
            }
        }
        return result.toString().replaceAll("\\s+", " ").trim();
    }

    // Stopword list — built from a sorted deduplicated set to avoid Set.of() crashes.
    // Compound phrases kept as single tokens so the matcher treats them as one unit.
    private static final Set<String> STOP_WORDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            // Vietnamese single-word stopwords
            "và", "của", "là", "có", "được", "trong", "cho", "với", "không", "để",
            "theo", "về", "từ", "ra", "vào", "hay", "vẫn", "còn", "sẽ", "này", "khi",
            "đã", "một", "các", "những", "bạn", "hành", "lộ", "trình",
            // Vietnamese compound stopwords (single semantic token)
            "bài tập", "khóa học", "học viên",
            // English stopwords (standard NLTK list)
            "and", "or", "the", "a", "an", "to", "in", "for", "of", "is", "it", "on",
            "with", "as", "by", "at", "from", "this", "that", "be", "are", "was",
            "will", "can", "you", "your", "how", "what", "when", "where", "why",
            "so", "do", "but", "if", "because", "until", "while",
            "all", "both", "each", "few", "more", "most", "other", "some", "such",
            "no", "not", "only", "same", "than", "too", "very"
    )));

    private static boolean isStopWord(String term) {
        return STOP_WORDS.contains(term);
    }
}