package com.exe.skillverse_backend.ai_service.service.impl;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.service.AiCourseCatalogService;
import com.exe.skillverse_backend.ai_service.service.TaxonomyService;
import com.exe.skillverse_backend.ai_service.service.dto.CourseCatalogEntry;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 2 multi-level course-module matching — Pre-Selection + Post-Matching + Diversity Scoring.
 *
 * <p>Executed AFTER AI generates the roadmap nodes (Phase B), but draws from
 * Phase A pre-selected course catalog.
 *
 * <p>Algorithm overview:
 * <ol>
 *   <li><b>Phase B1 (Course matching):</b> Score each pre-selected course per node
 *       using keyword match + diversity penalty → up to 3 courses per node</li>
 *   <li><b>Phase B2 (Module distribution):</b> Evenly distribute the primary course's
 *       modules across all nodes that reference it, by tree order</li>
 *   <li><b>Phase B3 (Validation):</b> Strip hallucinated module IDs by batch-checking against DB</li>
 * </ol>
 *
 * <h3>Diversity Scoring formula</h3>
 * <pre>
 * finalScore = scoreCourseMatch(course, node) - (timesUsed × DIVERSITY_PENALTY)
 * </pre>
 * <p>Example: "React Complete" used 3 times already → penalty = 9 → a course that
 * hasn't been used gets priority even if the keyword match is slightly lower.
 * Result: No single course dominates 80% of roadmap nodes.
 *
 * <h3>Mode-specific limits</h3>
 * <ul>
 *   <li>SKILL_BASED   → pre-select top-3 courses (1 skill, narrow scope)</li>
 *   <li>CAREER_BASED  → pre-select top-15 courses (many skills, wide scope)</li>
 * </ul>
 *
 * <p>User-facing recommendations are courses. Module titles are searchable signals
 * for matching and primary-course module IDs are kept only as Study Planner context.
 *
 * @see AiCourseCatalogServiceImpl
 * @see #matchNodesToCoursesAndModules
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MultiLevelCourseMatcher {

    /**
     * Penalty applied per each previous assignment of the same course.
     * Formula: diversityPenalty = timesUsed × DIVERSITY_PENALTY
     *
     * <p>With DIVERSITY_PENALTY = 3, a course used 3 times loses 9 points on its
     * next matching attempt — enough to let a lesser-known course win if it's a better
     * fit for the current node.
     */
    public static final int DIVERSITY_PENALTY = 3;

    /** Pre-select limit for SKILL_BASED roadmaps (1 skill, narrow scope) */
    public static final int SKILL_BASED_LIMIT = 3;

    /** Pre-select limit for CAREER_BASED roadmaps (many skills, wide scope) */
    public static final int CAREER_BASED_LIMIT = 15;

    /**
     * Max courses assigned to a single roadmap node — differentiated by roadmap type.
     *
     * <ul>
     *   <li>SKILL_BASED  = 3 courses/node (1 primary + up to 2 alternatives for variety)</li>
     *   <li>CAREER_BASED = 3 courses/node (wide career scope, node may need beginner+intermediate+advanced)</li>
     * </ul>
     *
     * <p>Note: Only the primary course (first in list) receives module distribution for
     * Study Planner integration. Alternatives are for user reference only.
     *
     * <p>Selected dynamically in {@link #matchNodesToCoursesAndModules} via {@code roadmapMode}.
     */
    private static final int MAX_COURSES_PER_SKILL_NODE = 3;
    private static final int MAX_COURSES_PER_CAREER_NODE = 3;

    /** Penalize courses that miss core intent anchors from topic (e.g., java, spring). */
    private static final int ANCHOR_MISS_PENALTY = 4;

    /** Require at least one anchor hit when anchor keywords are present. */
    private static final int MIN_ANCHOR_HITS_FOR_DIRECT_MATCH = 1;

    /** Reject weak lexical matches that only hit noisy description terms. */
    private static final int MIN_STRONG_SIGNAL_SCORE = 4;

    private final AiCourseCatalogService catalogService;
    private final ModuleRepository moduleRepository;
    private final TaxonomyService taxonomyService;

    // ===== Public API =====

    /**
     * Match roadmap nodes to pre-selected courses and distribute their modules.
     *
     * <p>This is the main entry point called from {@link com.exe.skillverse_backend.ai_service.service.AiRoadmapServiceImpl}
     * after AI generates the roadmap (after validateAndParseRoadmapV2).
     *
     * <p>Does NOT mutate AI-generated node titles, descriptions, or structure —
     * only adds suggestedCourseIds and suggestedModuleIds (additive, nullable).
     *
     * @param nodes          Parsed roadmap nodes from AI (in tree order)
     * @param userGoal       The user's original goal/target string
     * @param roadmapMode    "SKILL_BASED" or "CAREER_BASED" (determines pre-selection limit)
     * @param skillName      (optional) skill name for SKILL_BASED mode
     * @param careerGoal     (optional) career goal for CAREER_BASED mode
     */
    public void matchNodesToCoursesAndModules(
            List<RoadmapResponse.RoadmapNode> nodes,
            String userGoal,
            String roadmapMode,
            String skillName,
            String careerGoal) {
        matchNodesToCoursesAndModules(nodes, userGoal, roadmapMode, skillName, careerGoal, null);
    }

    /**
     * User-history-aware variant: pass userId so catalog pre-selection can down-rank
     * already completed/in-progress courses.
     */
    public void matchNodesToCoursesAndModules(
            List<RoadmapResponse.RoadmapNode> nodes,
            String userGoal,
            String roadmapMode,
            String skillName,
            String careerGoal,
            Long userId) {

        if (nodes == null || nodes.isEmpty()) {
            log.info("[Matcher] No nodes to match — skipping");
            return;
        }

        int limit = CAREER_BASED_LIMIT;
        int maxCoursesPerNode = MAX_COURSES_PER_CAREER_NODE;
        if ("SKILL_BASED".equalsIgnoreCase(roadmapMode)) {
            limit = SKILL_BASED_LIMIT;
            maxCoursesPerNode = MAX_COURSES_PER_SKILL_NODE;
        }

        // === PHASE A: Global pre-selection (fallback pool) ===
        // Per-node matching uses this as fallback when a node has no specific course candidates.
        String globalTopic = buildTopic(userGoal, skillName, careerGoal);
        String detectedDomain = taxonomyService.detectDomain(globalTopic, null, null);
        List<String> anchorKeywords = extractKeywords(globalTopic, null, null, null);
        List<CourseCatalogEntry> globalCandidates = catalogService.preSelectCourses(globalTopic, limit, userId);

        if (globalCandidates.isEmpty()) {
            log.info("[Matcher] No global courses pre-selected for topic '{}' — skipping matching", globalTopic);
            return;
        }

        log.info("[Matcher] Phase A global pool: {} courses for topic '{}' (mode={}, domain={})",
                globalCandidates.size(), globalTopic, roadmapMode, detectedDomain);

        // === PHASE B1: Per-node course matching with Diversity Scoring ===
        Map<Long, Integer> usedCourseCount = new HashMap<>();
        Map<Long, List<RoadmapResponse.RoadmapNode>> courseToNodes = new LinkedHashMap<>();
        int skippedNoKeywords = 0;
        int skippedNoPositiveScore = 0;
        int rejectedNoAnchor = 0;
        int rejectedWeakSignal = 0;
        int assignedNodes = 0;
        int usedGlobalFallback = 0;
        int usedNodeSpecific = 0;

        for (int nodeIdx = 0; nodeIdx < nodes.size(); nodeIdx++) {
            RoadmapResponse.RoadmapNode node = nodes.get(nodeIdx);

            // Build topic from THIS node's content — not the goal level
            String nodeTopic = buildNodeTopic(node);
            List<String> nodeAnchorKeywords = extractKeywords(nodeTopic, null, null, null);

            // Try per-node course pre-selection first
            List<CourseCatalogEntry> candidates = catalogService.preSelectCourses(nodeTopic, limit, userId);

            // Fall back to global pool if no node-specific courses found (BM25 threshold or no match)
            if (candidates.isEmpty()) {
                candidates = globalCandidates;
                usedGlobalFallback++;
            } else {
                usedNodeSpecific++;
            }

            // Strict intent-first: always re-score candidates instead of preserving AI IDs.
            List<String> existingIds = node.getSuggestedCourseIds();
            boolean hasAiIds = existingIds != null && !existingIds.isEmpty();
            if (hasAiIds) {
                log.debug("Node '{}' has {} AI-provided course IDs — re-scoring under strict intent-first mode",
                        node.getId(), existingIds.size());
            }

            List<String> keywords = extractKeywords(
                    node.getTitle(),
                    node.getKeyConcepts(),
                    node.getPracticalExercises(),
                    node.getLearningObjectives()
            );

            if (keywords.isEmpty()) {
                log.debug("Node '{}' has no extractable keywords — skipping", node.getId());
                skippedNoKeywords++;
                continue;
            }

            // Score courses with diversity penalty
            List<ScoredCourse> scored = new ArrayList<>();
            List<String> rejectReasons = new ArrayList<>();
            for (CourseCatalogEntry course : candidates) {
                MatchScore matchScore = scoreCourseMatch(course, keywords, node.getDifficulty(), nodeAnchorKeywords);
                int baseScore = matchScore.totalScore();
                int timesUsed = usedCourseCount.getOrDefault(course.getId(), 0);
                int finalScore = baseScore - (timesUsed * DIVERSITY_PENALTY);
                boolean hasAnchorSignal = nodeAnchorKeywords.isEmpty()
                        || matchScore.anchorHits() >= MIN_ANCHOR_HITS_FOR_DIRECT_MATCH;
                boolean hasStrongSignal = matchScore.structuralHits() > 0 || baseScore >= MIN_STRONG_SIGNAL_SCORE;

                if (!hasAnchorSignal) {
                    rejectedNoAnchor++;
                    if (rejectReasons.size() < 3) {
                        rejectReasons.add(course.getId() + ":anchor-miss");
                    }
                    continue;
                }

                if (!hasStrongSignal) {
                    rejectedWeakSignal++;
                    if (rejectReasons.size() < 3) {
                        rejectReasons.add(course.getId() + ":weak-signal");
                    }
                    continue;
                }

                if (finalScore > 0) {
                    scored.add(new ScoredCourse(course, baseScore, finalScore, timesUsed, matchScore.anchorHits()));
                } else if (rejectReasons.size() < 3) {
                    rejectReasons.add(course.getId() + ":non-positive");
                }
            }

            scored.sort((a, b) -> Integer.compare(b.finalScore, a.finalScore));
            List<String> selectedCourseIds = scored.stream()
                    .limit(maxCoursesPerNode)
                    .map(sc -> String.valueOf(sc.course.getId()))
                    .collect(Collectors.toList());

            if (!selectedCourseIds.isEmpty()) {
                node.setSuggestedCourseIds(selectedCourseIds);
                // Track all displayed recommendations so diversity applies to primary and alternatives.
                for (String idStr : selectedCourseIds) {
                    Long cid = Long.parseLong(idStr);
                    usedCourseCount.merge(cid, 1, Integer::sum);
                }
                // Only the primary course drives suggestedModuleIds for Study Planner context.
                Long primaryCourseId = Long.parseLong(selectedCourseIds.get(0));
                courseToNodes.computeIfAbsent(primaryCourseId, k -> new ArrayList<>()).add(node);
                assignedNodes++;
                log.info("[Matcher] Node '{}' topic='{}' | candidates={} | selected={} courses | primary={}",
                        node.getId(), nodeTopic, candidates.size(), selectedCourseIds.size(), primaryCourseId);
            } else {
                skippedNoPositiveScore++;
                log.debug("Node '{}' skipped: no valid candidate after gating (sampleRejects={})",
                    node.getId(), rejectReasons);
            }
        }

        log.info("[Matcher] B1 summary: assignedNodes={}, skippedNoKeywords={}, skippedNoPositiveScore={}, " +
                "rejectedNoAnchor={}, rejectedWeakSignal={}, usedGlobalFallback={}, usedNodeSpecific={}",
                assignedNodes, skippedNoKeywords, skippedNoPositiveScore,
                rejectedNoAnchor, rejectedWeakSignal, usedGlobalFallback, usedNodeSpecific);

        // === PHASE B2: Module distribution — divide each course's modules evenly across its nodes ===
        distributeModulesEvenly(courseToNodes);

        // === PHASE B3: Anti-hallucination — strip invalid module IDs with batch DB validation ===
        validateAndStripFakeModuleIds(nodes);
    }

    // ===== Phase B1 helpers =====

    /**
     * Build a search topic string from the available inputs.
     */
    private String buildTopic(String userGoal, String skillName, String careerGoal) {
        StringBuilder sb = new StringBuilder();
        if (userGoal != null && !userGoal.isBlank()) sb.append(userGoal).append(" ");
        if (skillName != null && !skillName.isBlank()) sb.append(skillName).append(" ");
        if (careerGoal != null && !careerGoal.isBlank()) sb.append(careerGoal);
        return sb.toString().trim();
    }

    /**
     * Build a per-node search topic from the node's own content.
     * Used for per-node course pre-selection (vs goal-level global pool).
     */
    private String buildNodeTopic(RoadmapResponse.RoadmapNode node) {
        StringBuilder sb = new StringBuilder();
        if (node.getTitle() != null && !node.getTitle().isBlank()) sb.append(node.getTitle()).append(" ");
        if (node.getKeyConcepts() != null && !node.getKeyConcepts().isEmpty())
            sb.append(String.join(" ", node.getKeyConcepts())).append(" ");
        if (node.getLearningObjectives() != null && !node.getLearningObjectives().isEmpty())
            sb.append(String.join(" ", node.getLearningObjectives())).append(" ");
        if (node.getPracticalExercises() != null && !node.getPracticalExercises().isEmpty())
            sb.append(String.join(" ", node.getPracticalExercises())).append(" ");
        return sb.toString().trim();
    }

    /**
     * Score how well a course matches a node's keywords.
     *
     * <p>Scoring:
     * <ul>
     *   <li>skill tag match  → +5 per matched keyword</li>
     *   <li>title match      → +4 per matched keyword</li>
     *   <li>category match   → +2 per matched keyword</li>
     *   <li>level match      → +2 per matched keyword (basic/intermediate/advanced alignment)</li>
     *   <li>learning signal  → +2 per matched keyword</li>
     *   <li>description/shortDescription match → +1 per matched keyword</li>
     * </ul>
     *
     * <p>Additionally, difficulty alignment bonus:
     * <ul>
     *   <li>node difficulty = "BEGINNER" + course level contains "beginner" → +2</li>
     *   <li>node difficulty = "INTERMEDIATE" + course level matches → +2</li>
     *   <li>node difficulty = "ADVANCED" + course level matches → +2</li>
     * </ul>
     */
    MatchScore scoreCourseMatch(
            CourseCatalogEntry course,
            List<String> keywords,
            String difficulty,
            List<String> anchorKeywords) {
        int score = 0;
        int structuralHits = 0;
        int anchorHits = 0;
        String title = safeLower(course.getTitle());
        String category = safeLower(course.getCategory());
        String level = safeLower(course.getLevel());
        String desc = safeLower(course.getDescription()) + " " + safeLower(course.getShortDescription());
        String skillTags = safeLower(course.getSkillTagText());
        String learningSignals = safeLower(course.getLearningSignalText());

        for (String kw : keywords) {
            if (skillTags.contains(kw)) {
                score += 5;
                structuralHits++;
            }
            if (title.contains(kw)) {
                score += 4;
                structuralHits++;
            }
            if (category.contains(kw)) {
                score += 2;
                structuralHits++;
            }
            if (level.contains(kw)) {
                score += 2;
                structuralHits++;
            }
            if (learningSignals.contains(kw)) {
                score += 2;
                structuralHits++;
            }
            if (desc.contains(kw)) score += 1;
        }

        if (anchorKeywords != null && !anchorKeywords.isEmpty()) {
            for (String anchor : anchorKeywords) {
                if (title.contains(anchor) || category.contains(anchor) || skillTags.contains(anchor)) {
                    anchorHits++;
                }
            }

            if (anchorHits == 0) {
                score -= ANCHOR_MISS_PENALTY;
            } else {
                score += Math.min(3, anchorHits);
            }
        }

        // Difficulty alignment bonus
        if (difficulty != null && level.contains(difficulty.toLowerCase(Locale.ROOT))) {
            score += 2;
        }

        // Efficiency bonus: short courses get a small boost without overpowering relevance.
        int moduleCount = course.getModuleCount();
        if (moduleCount > 0 && moduleCount <= 5) {
            score += 2;
        } else if (moduleCount > 5 && moduleCount <= 10) {
            score += 1;
        }

        return new MatchScore(score, anchorHits, structuralHits);
    }

    /**
     * Extract keyword tokens from node content for matching.
     *
     * <p>Combines: title + keyConcepts + practicalExercises + learningObjectives.
     * Removes stopwords, keeps tokens 2+ chars, converts to lowercase.
     */
    List<String> extractKeywords(
            String title,
            List<String> keyConcepts,
            List<String> practicalExercises,
            List<String> learningObjectives) {

        Set<String> keywords = new HashSet<>();
        addTokens(keywords, title);
        if (keyConcepts != null) for (String s : keyConcepts) addTokens(keywords, s);
        if (practicalExercises != null) for (String s : practicalExercises) addTokens(keywords, s);
        if (learningObjectives != null) for (String s : learningObjectives) addTokens(keywords, s);

        return new ArrayList<>(keywords);
    }

    private void addTokens(Set<String> out, String text) {
        if (text == null || text.isBlank()) return;
        String[] tokens = text.toLowerCase()
                .replaceAll("[\\[\\]{}()\"'.,;:!?\\-/\\\\|\\n\\r\\t]", " ")
                .split("\\s+");
        for (String t : tokens) {
            t = t.trim();
            if (t.length() >= 2 && !STOP_WORDS.contains(t)) {
                out.add(t);
            }
        }
    }

    // ===== Phase B2: Module distribution =====

    /**
     * Distribute each course's modules across its nodes, respecting prerequisite order.
     *
     * <p>Algorithm: Topological sort of module prerequisites across each course.
     * Modules without prerequisites are distributed first; modules whose prerequisites
     * are already assigned to earlier nodes come next.
     *
     * <p>Distribution:
     * <pre>
     * modulesPerNode = Math.max(1, totalModules / nodeCount)
     * remainder      = totalModules % nodeCount
     * First `remainder` nodes get (modulesPerNode + 1) modules
     * </pre>
     *
     * <p>For courses without prerequisite data, falls back to round-robin even distribution.
     *
     * @param courseToNodes Map: courseId → nodes referencing this course (in tree order)
     */
    private void distributeModulesEvenly(Map<Long, List<RoadmapResponse.RoadmapNode>> courseToNodes) {
        for (Map.Entry<Long, List<RoadmapResponse.RoadmapNode>> entry : courseToNodes.entrySet()) {
            Long courseId = entry.getKey();
            List<RoadmapResponse.RoadmapNode> nodesForCourse = entry.getValue();

            if (nodesForCourse.isEmpty()) continue;

            // Fetch modules and their prerequisites from the catalog
            List<Long> allModuleIds = catalogService.getModuleIds(courseId);
            if (allModuleIds.isEmpty()) {
                log.debug("Course {} has no modules — skipping distribution", courseId);
                continue;
            }

            // Build prerequisite graph for this course's modules
            Map<Long, List<Long>> prereqGraph = buildPrereqGraph(courseId, allModuleIds);

            // Topological sort: modules ordered by dependency
            List<Long> sortedModuleIds = topologicalSort(allModuleIds, prereqGraph);

            // Even distribution across nodes
            int totalModules = sortedModuleIds.size();
            int nodeCount = nodesForCourse.size();
            int modulesPerNode = Math.max(1, totalModules / nodeCount);
            int remainder = totalModules % nodeCount;

            int moduleIndex = 0;
            for (int i = 0; i < nodesForCourse.size(); i++) {
                int count = modulesPerNode + (i < remainder ? 1 : 0);
                if (moduleIndex + count > totalModules) {
                    count = totalModules - moduleIndex;
                }
                if (count <= 0) break;

                List<Long> slice = sortedModuleIds.subList(moduleIndex, moduleIndex + count);
                List<String> idStrings = slice.stream()
                        .map(String::valueOf)
                        .collect(Collectors.toList());

                nodesForCourse.get(i).setSuggestedModuleIds(idStrings);
                log.debug("Course {} → Node '{}': {} modules (index {}..{})",
                        courseId, nodesForCourse.get(i).getId(), count, moduleIndex, moduleIndex + count - 1);
                moduleIndex += count;
            }
        }
    }

    /**
     * Build prerequisite graph for a set of module IDs.
     * Returns: moduleId → list of prerequisite module IDs.
     */
    private Map<Long, List<Long>> buildPrereqGraph(Long courseId, List<Long> moduleIds) {
        Map<Long, List<Long>> graph = new HashMap<>();
        Set<Long> moduleSet = new HashSet<>(moduleIds);
        for (Long moduleId : moduleIds) {
            List<Long> prereqs = catalogService.getModulePrerequisiteIds(courseId, moduleId);
            // Filter to only include prerequisites that belong to this course's modules
            List<Long> filtered = prereqs.stream()
                    .filter(moduleSet::contains)
                    .collect(Collectors.toList());
            graph.put(moduleId, filtered);
        }
        return graph;
    }

    /**
     * Topological sort of module IDs based on their prerequisite graph.
     * Uses Kahn's algorithm for cycle detection.
     *
     * @return Module IDs in dependency order (prerequisites first)
     */
    private List<Long> topologicalSort(List<Long> moduleIds, Map<Long, List<Long>> prereqGraph) {
        if (prereqGraph.values().stream().allMatch(List::isEmpty)) {
            // No prerequisites — return natural order
            return new ArrayList<>(moduleIds);
        }

        Set<Long> moduleSet = new HashSet<>(moduleIds);
        Map<Long, Integer> inDegree = new HashMap<>();
        Map<Long, List<Long>> dependents = new HashMap<>();

        for (Long id : moduleIds) {
            inDegree.put(id, 0);
        }

        for (Map.Entry<Long, List<Long>> e : prereqGraph.entrySet()) {
            Long moduleId = e.getKey();
            for (Long prereq : e.getValue()) {
                if (moduleSet.contains(prereq)) {
                    inDegree.merge(moduleId, 1, Integer::sum);
                    dependents.computeIfAbsent(prereq, k -> new ArrayList<>()).add(moduleId);
                }
            }
        }

        Queue<Long> queue = new LinkedList<>();
        for (Long id : moduleIds) {
            if (inDegree.getOrDefault(id, 0) == 0) {
                queue.offer(id);
            }
        }

        List<Long> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            Long current = queue.poll();
            result.add(current);

            for (Long dependent : dependents.getOrDefault(current, List.of())) {
                int newDeg = inDegree.getOrDefault(dependent, 0) - 1;
                inDegree.put(dependent, newDeg);
                if (newDeg == 0) {
                    queue.offer(dependent);
                }
            }
        }

        // If result is smaller than input → cycle detected. Append remaining (cycle) at end.
        if (result.size() < moduleIds.size()) {
            log.warn("[Matcher] Cycle detected in prerequisites for course — appending remaining modules");
            for (Long id : moduleIds) {
                if (!result.contains(id)) result.add(id);
            }
        }

        return result;
    }

    // ===== Phase B3: Validation =====

    /**
     * Strip hallucinated module IDs by batch-checking against the DB.
     *
     * <p>Steps:
     * <ol>
     *   <li>Collect all module IDs from all nodes</li>
    *   <li>Batch query: {@code ModuleRepository.findModuleCoursePairsByIds(collectedIds)}</li>
    *   <li>Build moduleId → courseId lookup map</li>
     *   <li>Verify: each module's courseId must be in the node's suggestedCourseIds</li>
     *   <li>Replace node's suggestedModuleIds with validated list (or null if empty)</li>
     * </ol>
     *
    * <p>Side effect: If module doesn't exist OR its course is not in the node's suggestedCourseIds,
    * that module is stripped to prevent cross-course leakage.
     */
    private void validateAndStripFakeModuleIds(List<RoadmapResponse.RoadmapNode> nodes) {
        // 1. Collect all module IDs across all nodes
        Set<Long> allModuleIds = new LinkedHashSet<>();
        for (RoadmapResponse.RoadmapNode node : nodes) {
            List<String> moduleIdStrs = node.getSuggestedModuleIds();
            if (moduleIdStrs != null) {
                for (String idStr : moduleIdStrs) {
                    try {
                        allModuleIds.add(Long.parseLong(idStr.trim()));
                    } catch (NumberFormatException ignored) {
                        // Invalid string — treat as non-existent
                    }
                }
            }
        }

        if (allModuleIds.isEmpty()) return;

        // 2. Batch query DB — single round-trip for all IDs
        Map<Long, Long> moduleCourseMap = new HashMap<>();
        try {
            List<Object[]> moduleCoursePairs = moduleRepository.findModuleCoursePairsByIds(allModuleIds);
            for (Object[] pair : moduleCoursePairs) {
                Long moduleId = ((Number) pair[0]).longValue();
                Long courseId = ((Number) pair[1]).longValue();
                moduleCourseMap.put(moduleId, courseId);
            }
        } catch (Exception e) {
            log.warn("[Matcher] ModuleRepository batch validation failed — clearing all module IDs", e);
            for (RoadmapResponse.RoadmapNode node : nodes) {
                node.setSuggestedModuleIds(null);
            }
            return;
        }

        // 3 & 4. Validate each node's module IDs
        for (RoadmapResponse.RoadmapNode node : nodes) {
            List<String> idStrs = node.getSuggestedModuleIds();
            if (idStrs == null) continue;

            // Build valid course ID set for this node
            Set<Long> nodeCourseIds = new HashSet<>();
            List<String> courseIdStrs = node.getSuggestedCourseIds();
            if (courseIdStrs != null) {
                for (String cs : courseIdStrs) {
                    try {
                        nodeCourseIds.add(Long.parseLong(cs.trim()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            // Filter: only keep IDs that are (a) in validModuleIds AND (b) belong to a course in nodeCourseIds
            List<String> validated = new ArrayList<>();
            for (String idStr : idStrs) {
                try {
                    Long mid = Long.parseLong(idStr.trim());
                    Long moduleCourseId = moduleCourseMap.get(mid);
                    if (moduleCourseId == null) continue;
                    if (nodeCourseIds.isEmpty() || !nodeCourseIds.contains(moduleCourseId)) continue;
                    validated.add(idStr);
                } catch (NumberFormatException ignored) {
                    // Invalid string — skip
                }
            }

            // 5. Set null if empty
            node.setSuggestedModuleIds(validated.isEmpty() ? null : validated);
        }

        log.info("[Matcher] Phase B3 validation done — {} total module IDs validated", moduleCourseMap.size());
    }

    // ===== Inner class for scoring =====

    /** Temporary scored course holder used during matching */
    private record ScoredCourse(CourseCatalogEntry course, int baseScore, int finalScore, int timesUsed, int anchorHits) {}

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record MatchScore(int totalScore, int anchorHits, int structuralHits) {}

    // Vietnamese + English stopwords for keyword extraction
    // Vietnamese + English stopwords for keyword extraction.
    // Built from a sorted deduplicated list to avoid Set.of() duplicate-element crashes.
    // Compound phrases (e.g. "bài tập") are kept as single tokens so the matcher
    // treats them as one semantic unit rather than splitting into individual stopwords.
    private static final Set<String> STOP_WORDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            // Vietnamese single-word stopwords
            "và", "của", "là", "có", "được", "trong", "cho", "với", "không", "để",
            "theo", "về", "từ", "ra", "vào", "hay", "vẫn", "còn", "sẽ", "này", "khi",
            "đã", "một", "các", "những", "bạn", "hành", "lộ", "trình",
            // Vietnamese compound stopwords (treated as single token)
            "bài tập", "khóa học", "học viên",
            // English stopwords (standard NLTK list)
            "and", "or", "the", "a", "an", "to", "in", "for", "of", "is", "it", "on",
            "with", "as", "by", "at", "from", "this", "that", "be", "are", "was",
            "will", "can", "you", "your", "how", "what", "when", "where", "why",
            "so", "do", "but", "if", "or", "because", "until", "while",
            "all", "both", "each", "few", "more", "most", "other", "some", "such",
            "no", "not", "only", "same", "than", "too", "very"
    )));
}
