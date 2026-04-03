package com.exe.skillverse_backend.admin_service.service.impl;

import com.exe.skillverse_backend.admin_service.dto.response.AdminJourneyDashboardResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminJourneyListItemResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminQuestionAnalyticsItemResponse;
import com.exe.skillverse_backend.admin_service.service.AdminJourneyAnalyticsService;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminJourneyAnalyticsServiceImpl implements AdminJourneyAnalyticsService {

    private static final int DEFAULT_TOP_BANK_LIMIT = 5;
    private static final int DEFAULT_TOP_QUESTION_LIMIT = 10;
    private static final int MIN_READY_QUESTION_POOL = 25;
    private static final String LEGACY_BANK_PROMPT_PREFIX = "Generated from question bank id=%";

    private static final String JOURNEY_LIST_SELECT = """
            SELECT
                j.id AS journey_id,
                u.id AS user_id,
                COALESCE(NULLIF(TRIM(CONCAT(COALESCE(u.first_name, ''), ' ', COALESCE(u.last_name, ''))), ''), u.email) AS user_name,
                u.email AS user_email,
                j.type,
                j.domain,
                j.industry,
                j.job_role,
                j.goal,
                j.status,
                j.progress_percentage,
                lt.id AS assessment_test_id,
                lt.status AS assessment_test_status,
                lt.question_bank_id AS question_bank_id,
                qb.title AS question_bank_title,
                CASE
                    WHEN lt.id IS NULL THEN 'NONE'
                    WHEN lt.question_bank_id IS NOT NULL THEN 'BANK'
                    WHEN lt.generation_prompt LIKE 'Generated from question bank id=%%' THEN 'LEGACY_BANK'
                    ELSE 'AI'
                END AS question_source,
                lr.score_percentage AS latest_score,
                lr.evaluated_level AS evaluated_level,
                j.roadmap_session_id AS roadmap_session_id,
                j.created_at AS created_at,
                j.last_activity_at AS last_activity_at,
                lr.evaluated_at AS evaluated_at
            """;

    private static final String JOURNEY_LIST_FROM = """
            FROM journeys j
            JOIN users u ON u.id = j.user_id
            LEFT JOIN LATERAL (
                SELECT at.id, at.status, at.question_bank_id, at.generation_prompt, at.created_at
                FROM assessment_tests at
                WHERE at.journey_id = j.id
                ORDER BY at.created_at DESC, at.id DESC
                LIMIT 1
            ) lt ON TRUE
            LEFT JOIN question_banks qb ON qb.id = lt.question_bank_id
            LEFT JOIN LATERAL (
                SELECT tr.score_percentage, tr.evaluated_level, tr.evaluated_at, tr.created_at
                FROM test_results tr
                WHERE tr.journey_id = j.id
                ORDER BY tr.created_at DESC, tr.id DESC
                LIMIT 1
            ) lr ON TRUE
            """;

    private static final String QUESTION_ANALYTICS_SELECT = """
            SELECT
                q.id AS question_id,
                q.question_bank_id AS question_bank_id,
                qb.title AS question_bank_title,
                qb.domain AS domain,
                qb.industry AS industry,
                qb.job_role AS job_role,
                q.question_text AS question_text,
                q.difficulty AS difficulty,
                q.skill_area AS skill_area,
                q.category AS category,
                q.source AS source,
                q.used_count AS used_count,
                q.is_active AS is_active,
                q.created_at AS created_at,
                q.updated_at AS updated_at
            """;

    private static final String QUESTION_ANALYTICS_FROM = """
            FROM question_bank_questions q
            JOIN question_banks qb ON qb.id = q.question_bank_id
            """;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public AdminJourneyDashboardResponse getDashboard() {
        long totalJourneys = queryForLong("SELECT COUNT(*) FROM journeys");
        long totalAssessmentTests = queryForLong("SELECT COUNT(*) FROM assessment_tests");
        long totalEvaluations = queryForLong("SELECT COUNT(*) FROM test_results");
        long totalQuestionBanks = queryForLong("SELECT COUNT(*) FROM question_banks");
        long totalActiveQuestions = queryForLong("SELECT COUNT(*) FROM question_bank_questions WHERE is_active = TRUE");
        long bankLinkedTests = queryForLong("SELECT COUNT(*) FROM assessment_tests WHERE question_bank_id IS NOT NULL");
        long legacyUnlinkedTests = queryForLong("""
                SELECT COUNT(*)
                FROM assessment_tests
                WHERE question_bank_id IS NULL
                  AND generation_prompt LIKE ?
                """, LEGACY_BANK_PROMPT_PREFIX);
        long aiGeneratedTests = Math.max(0L, totalAssessmentTests - bankLinkedTests - legacyUnlinkedTests);
        long roadmapReadyJourneys = queryForLong("SELECT COUNT(*) FROM journeys WHERE roadmap_session_id IS NOT NULL");
        long questionSelectionsServed = queryForLong("SELECT COALESCE(SUM(used_count), 0) FROM question_bank_questions");
        long recoveryReadyTests = bankLinkedTests + legacyUnlinkedTests;

        return AdminJourneyDashboardResponse.builder()
                .totalJourneys(totalJourneys)
                .totalAssessmentTests(totalAssessmentTests)
                .totalEvaluations(totalEvaluations)
                .totalQuestionBanks(totalQuestionBanks)
                .totalActiveQuestions(totalActiveQuestions)
                .bankLinkedTests(bankLinkedTests)
                .aiGeneratedTests(aiGeneratedTests)
                .legacyUnlinkedTests(legacyUnlinkedTests)
                .recoveryReadyTests(recoveryReadyTests)
                .roadmapReadyJourneys(roadmapReadyJourneys)
                .questionSelectionsServed(questionSelectionsServed)
                .bankLinkedCoverageRate(toRate(bankLinkedTests, totalAssessmentTests))
                .recoveryCoverageRate(toRate(recoveryReadyTests, totalAssessmentTests))
                .assessmentFunnel(buildAssessmentFunnel(totalJourneys, roadmapReadyJourneys))
                .journeyStatusBreakdown(queryMetricBreakdown("""
                        SELECT COALESCE(status, 'UNKNOWN') AS label, COUNT(*) AS value
                        FROM journeys
                        GROUP BY status
                        ORDER BY value DESC, label ASC
                        """))
                .journeyTypeBreakdown(queryMetricBreakdown("""
                        SELECT COALESCE(type, 'UNKNOWN') AS label, COUNT(*) AS value
                        FROM journeys
                        GROUP BY type
                        ORDER BY value DESC, label ASC
                        """))
                .testSourceBreakdown(queryMetricBreakdown("""
                        SELECT
                            CASE
                                WHEN question_bank_id IS NOT NULL THEN 'BANK'
                                WHEN generation_prompt LIKE 'Generated from question bank id=%%' THEN 'LEGACY_BANK'
                                ELSE 'AI'
                            END AS label,
                            COUNT(*) AS value
                        FROM assessment_tests
                        GROUP BY label
                        ORDER BY value DESC, label ASC
                        """))
                .difficultyBreakdown(queryMetricBreakdown("""
                        SELECT COALESCE(NULLIF(TRIM(difficulty), ''), 'UNSPECIFIED') AS label, COUNT(*) AS value
                        FROM question_bank_questions
                        WHERE is_active = TRUE
                        GROUP BY label
                        ORDER BY value DESC, label ASC
                        """))
                .questionAuthoringSourceBreakdown(queryMetricBreakdown("""
                        SELECT COALESCE(NULLIF(TRIM(source), ''), 'MANUAL') AS label, COUNT(*) AS value
                        FROM question_bank_questions
                        WHERE is_active = TRUE
                        GROUP BY label
                        ORDER BY value DESC, label ASC
                        """))
                .skillAreaBreakdown(queryMetricBreakdown("""
                        SELECT COALESCE(NULLIF(TRIM(skill_area), ''), 'UNSPECIFIED') AS label, COUNT(*) AS value
                        FROM question_bank_questions
                        WHERE is_active = TRUE
                        GROUP BY label
                        ORDER BY value DESC, label ASC
                        """))
                .topBanks(queryTopBanks())
                .topQuestions(queryTopQuestions())
                .build();
    }

    @Override
    public Page<AdminJourneyListItemResponse> listJourneys(
            String status,
            String type,
            String domain,
            String questionSource,
            Long questionBankId,
            Boolean hasRoadmap,
            Instant createdFrom,
            Instant createdTo,
            String keyword,
            Pageable pageable) {
        List<Object> params = new ArrayList<>();
        String whereClause = buildJourneyWhereClause(
                normalizeText(status),
                normalizeText(type),
                normalizeText(domain),
                normalizeText(questionSource),
                questionBankId,
                hasRoadmap,
                createdFrom,
                createdTo,
                normalizeText(keyword),
                params);

        long total = queryForLong("SELECT COUNT(*) " + JOURNEY_LIST_FROM + whereClause, params.toArray());
        if (total == 0L) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        Map<String, String> sortMapping = new LinkedHashMap<>();
        sortMapping.put("journeyId", "j.id");
        sortMapping.put("createdAt", "j.created_at");
        sortMapping.put("lastActivityAt", "j.last_activity_at");
        sortMapping.put("latestScore", "lr.score_percentage");
        sortMapping.put("status", "j.status");
        sortMapping.put("questionSource", "question_source");

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageable.getPageSize());
        dataParams.add(pageable.getOffset());

        List<AdminJourneyListItemResponse> content = jdbcTemplate.query(
                JOURNEY_LIST_SELECT
                        + JOURNEY_LIST_FROM
                        + whereClause
                        + " ORDER BY "
                        + resolveSortClause(pageable.getSort(), sortMapping, "j.created_at DESC NULLS LAST, j.id DESC")
                        + " LIMIT ? OFFSET ?",
                this::mapJourneyRow,
                dataParams.toArray());

        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public Page<AdminQuestionAnalyticsItemResponse> listQuestionAnalytics(
            Long questionBankId,
            String difficulty,
            String skillArea,
            String source,
            Boolean isActive,
            String keyword,
            Pageable pageable) {
        List<Object> params = new ArrayList<>();
        String whereClause = buildQuestionAnalyticsWhereClause(
                questionBankId,
                normalizeText(difficulty),
                normalizeText(skillArea),
                normalizeText(source),
                isActive,
                normalizeText(keyword),
                params);

        long total = queryForLong("SELECT COUNT(*) " + QUESTION_ANALYTICS_FROM + whereClause, params.toArray());
        if (total == 0L) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        Map<String, String> sortMapping = new LinkedHashMap<>();
        sortMapping.put("questionId", "q.id");
        sortMapping.put("usedCount", "q.used_count");
        sortMapping.put("createdAt", "q.created_at");
        sortMapping.put("updatedAt", "q.updated_at");
        sortMapping.put("difficulty", "q.difficulty");

        List<Object> dataParams = new ArrayList<>(params);
        dataParams.add(pageable.getPageSize());
        dataParams.add(pageable.getOffset());

        List<AdminQuestionAnalyticsItemResponse> content = jdbcTemplate.query(
                QUESTION_ANALYTICS_SELECT
                        + QUESTION_ANALYTICS_FROM
                        + whereClause
                        + " ORDER BY "
                        + resolveSortClause(pageable.getSort(), sortMapping, "q.used_count DESC NULLS LAST, q.updated_at DESC NULLS LAST, q.id DESC")
                        + " LIMIT ? OFFSET ?",
                this::mapQuestionAnalyticsRow,
                dataParams.toArray());

        return new PageImpl<>(content, pageable, total);
    }

    private List<AdminJourneyDashboardResponse.MetricBreakdownItem> buildAssessmentFunnel(
            long totalJourneys,
            long roadmapReadyJourneys) {
        long testsGenerated = queryForLong("SELECT COUNT(DISTINCT journey_id) FROM assessment_tests");
        long testsSubmitted = queryForLong("SELECT COUNT(DISTINCT journey_id) FROM assessment_tests WHERE status = 'COMPLETED'");
        long evaluationsCompleted = queryForLong("SELECT COUNT(DISTINCT journey_id) FROM test_results");

        List<AdminJourneyDashboardResponse.MetricBreakdownItem> items = new ArrayList<>();
        items.add(metric("CREATED", totalJourneys));
        items.add(metric("TEST_GENERATED", testsGenerated));
        items.add(metric("TEST_SUBMITTED", testsSubmitted));
        items.add(metric("EVALUATED", evaluationsCompleted));
        items.add(metric("ROADMAP_READY", roadmapReadyJourneys));
        return items;
    }

    private List<AdminJourneyDashboardResponse.MetricBreakdownItem> queryMetricBreakdown(String sql, Object... args) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> metric(rs.getString("label"), rs.getLong("value")), args);
    }

    private List<AdminJourneyDashboardResponse.TopBankItem> queryTopBanks() {
        return jdbcTemplate.query("""
                SELECT
                    qb.id AS question_bank_id,
                    qb.title AS title,
                    qb.domain AS domain,
                    qb.industry AS industry,
                    qb.job_role AS job_role,
                    COALESCE(qstats.active_question_count, 0) AS active_question_count,
                    COALESCE(qstats.total_question_usage, 0) AS total_question_usage,
                    COALESCE(tstats.linked_assessment_test_count, 0) AS linked_assessment_test_count,
                    COALESCE(tstats.linked_journey_count, 0) AS linked_journey_count,
                    COALESCE(tstats.question_volume_served, 0) AS question_volume_served,
                    tstats.average_score AS average_score,
                    tstats.last_used_at AS last_used_at
                FROM question_banks qb
                LEFT JOIN (
                    SELECT
                        question_bank_id,
                        COUNT(*) FILTER (WHERE is_active = TRUE) AS active_question_count,
                        COALESCE(SUM(used_count), 0) AS total_question_usage
                    FROM question_bank_questions
                    GROUP BY question_bank_id
                ) qstats ON qstats.question_bank_id = qb.id
                LEFT JOIN (
                    SELECT
                        at.question_bank_id,
                        COUNT(*) AS linked_assessment_test_count,
                        COUNT(DISTINCT at.journey_id) AS linked_journey_count,
                        COALESCE(SUM(at.question_count), 0) AS question_volume_served,
                        AVG(tr.score_percentage) AS average_score,
                        MAX(at.created_at) AS last_used_at
                    FROM assessment_tests at
                    LEFT JOIN test_results tr ON tr.assessment_test_id = at.id
                    WHERE at.question_bank_id IS NOT NULL
                    GROUP BY at.question_bank_id
                ) tstats ON tstats.question_bank_id = qb.id
                ORDER BY
                    COALESCE(tstats.linked_assessment_test_count, 0) DESC,
                    COALESCE(qstats.total_question_usage, 0) DESC,
                    COALESCE(qstats.active_question_count, 0) DESC,
                    qb.updated_at DESC NULLS LAST,
                    qb.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    long bankId = rs.getLong("question_bank_id");
                    long activeQuestionCount = rs.getLong("active_question_count");
                    String readinessStatus = activeQuestionCount >= MIN_READY_QUESTION_POOL ? "READY" : "LOW_POOL";
                    String readinessReason = activeQuestionCount >= MIN_READY_QUESTION_POOL
                            ? "Question pool is healthy for bank-based assessments."
                            : "Question pool is below the recommended minimum of " + MIN_READY_QUESTION_POOL + " questions.";

                    return AdminJourneyDashboardResponse.TopBankItem.builder()
                            .questionBankId(bankId)
                            .title(rs.getString("title"))
                            .domain(rs.getString("domain"))
                            .industry(rs.getString("industry"))
                            .jobRole(rs.getString("job_role"))
                            .activeQuestionCount(activeQuestionCount)
                            .totalQuestionUsage(rs.getLong("total_question_usage"))
                            .linkedAssessmentTestCount(rs.getLong("linked_assessment_test_count"))
                            .linkedJourneyCount(rs.getLong("linked_journey_count"))
                            .questionVolumeServed(rs.getLong("question_volume_served"))
                            .averageScore(roundNullableDouble(rs.getObject("average_score")))
                            .lastUsedAt(toInstant(rs.getTimestamp("last_used_at")))
                            .readinessStatus(readinessStatus)
                            .readinessReason(readinessReason)
                            .difficultyBreakdown(queryBankDifficultyBreakdown(bankId))
                            .build();
                },
                DEFAULT_TOP_BANK_LIMIT);
    }

    private List<AdminJourneyDashboardResponse.TopQuestionItem> queryTopQuestions() {
        return jdbcTemplate.query("""
                SELECT
                    q.id AS question_id,
                    q.question_bank_id AS question_bank_id,
                    qb.title AS question_bank_title,
                    qb.domain AS domain,
                    qb.industry AS industry,
                    qb.job_role AS job_role,
                    q.question_text AS question_text,
                    q.difficulty AS difficulty,
                    q.skill_area AS skill_area,
                    q.category AS category,
                    q.source AS source,
                    q.used_count AS used_count,
                    q.is_active AS is_active,
                    q.created_at AS created_at,
                    q.updated_at AS updated_at
                FROM question_bank_questions q
                JOIN question_banks qb ON qb.id = q.question_bank_id
                ORDER BY q.used_count DESC NULLS LAST, q.updated_at DESC NULLS LAST, q.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> AdminJourneyDashboardResponse.TopQuestionItem.builder()
                        .questionId(rs.getLong("question_id"))
                        .questionBankId(rs.getLong("question_bank_id"))
                        .questionBankTitle(rs.getString("question_bank_title"))
                        .domain(rs.getString("domain"))
                        .industry(rs.getString("industry"))
                        .jobRole(rs.getString("job_role"))
                        .questionText(rs.getString("question_text"))
                        .difficulty(rs.getString("difficulty"))
                        .skillArea(rs.getString("skill_area"))
                        .category(rs.getString("category"))
                        .source(rs.getString("source"))
                        .usedCount(getInteger(rs, "used_count"))
                        .isActive(getBoolean(rs, "is_active"))
                        .createdAt(toInstant(rs.getTimestamp("created_at")))
                        .updatedAt(toInstant(rs.getTimestamp("updated_at")))
                        .build(),
                DEFAULT_TOP_QUESTION_LIMIT);
    }

    private Map<String, Long> queryBankDifficultyBreakdown(Long bankId) {
        LinkedHashMap<String, Long> breakdown = new LinkedHashMap<>();
        jdbcTemplate.query("""
                SELECT COALESCE(NULLIF(TRIM(difficulty), ''), 'UNSPECIFIED') AS label, COUNT(*) AS value
                FROM question_bank_questions
                WHERE question_bank_id = ?
                  AND is_active = TRUE
                GROUP BY label
                ORDER BY value DESC, label ASC
                """,
                (rs, rowNum) -> {
                    breakdown.put(rs.getString("label"), rs.getLong("value"));
                    return null;
                },
                bankId);
        return breakdown;
    }

    private AdminJourneyListItemResponse mapJourneyRow(ResultSet rs, int rowNum) throws SQLException {
        Long roadmapSessionId = getLong(rs, "roadmap_session_id");
        return AdminJourneyListItemResponse.builder()
                .journeyId(rs.getLong("journey_id"))
                .userId(rs.getLong("user_id"))
                .userName(rs.getString("user_name"))
                .userEmail(rs.getString("user_email"))
                .type(rs.getString("type"))
                .domain(rs.getString("domain"))
                .industry(rs.getString("industry"))
                .jobRole(rs.getString("job_role"))
                .goal(rs.getString("goal"))
                .status(rs.getString("status"))
                .progressPercentage(getInteger(rs, "progress_percentage"))
                .assessmentTestId(getLong(rs, "assessment_test_id"))
                .assessmentTestStatus(rs.getString("assessment_test_status"))
                .questionBankId(getLong(rs, "question_bank_id"))
                .questionBankTitle(rs.getString("question_bank_title"))
                .questionSource(rs.getString("question_source"))
                .latestScore(getInteger(rs, "latest_score"))
                .evaluatedLevel(rs.getString("evaluated_level"))
                .roadmapReady(roadmapSessionId != null)
                .roadmapSessionId(roadmapSessionId)
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .lastActivityAt(toInstant(rs.getTimestamp("last_activity_at")))
                .evaluatedAt(toInstant(rs.getTimestamp("evaluated_at")))
                .build();
    }

    private AdminQuestionAnalyticsItemResponse mapQuestionAnalyticsRow(ResultSet rs, int rowNum) throws SQLException {
        return AdminQuestionAnalyticsItemResponse.builder()
                .questionId(rs.getLong("question_id"))
                .questionBankId(rs.getLong("question_bank_id"))
                .questionBankTitle(rs.getString("question_bank_title"))
                .domain(rs.getString("domain"))
                .industry(rs.getString("industry"))
                .jobRole(rs.getString("job_role"))
                .questionText(rs.getString("question_text"))
                .difficulty(rs.getString("difficulty"))
                .skillArea(rs.getString("skill_area"))
                .category(rs.getString("category"))
                .source(rs.getString("source"))
                .usedCount(getInteger(rs, "used_count"))
                .isActive(getBoolean(rs, "is_active"))
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .updatedAt(toInstant(rs.getTimestamp("updated_at")))
                .build();
    }

    private String buildJourneyWhereClause(
            String status,
            String type,
            String domain,
            String questionSource,
            Long questionBankId,
            Boolean hasRoadmap,
            Instant createdFrom,
            Instant createdTo,
            String keyword,
            List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");

        if (status != null) {
            where.append(" AND j.status = ?");
            params.add(status);
        }
        if (type != null) {
            where.append(" AND j.type = ?");
            params.add(type);
        }
        if (domain != null) {
            where.append(" AND j.domain = ?");
            params.add(domain);
        }
        if (questionBankId != null) {
            where.append(" AND lt.question_bank_id = ?");
            params.add(questionBankId);
        }
        if (hasRoadmap != null) {
            where.append(hasRoadmap ? " AND j.roadmap_session_id IS NOT NULL" : " AND j.roadmap_session_id IS NULL");
        }
        if (createdFrom != null) {
            where.append(" AND j.created_at >= ?");
            params.add(Timestamp.from(createdFrom));
        }
        if (createdTo != null) {
            where.append(" AND j.created_at <= ?");
            params.add(Timestamp.from(createdTo));
        }
        if (questionSource != null) {
            switch (questionSource.toUpperCase(Locale.ROOT)) {
                case "BANK" -> where.append(" AND lt.question_bank_id IS NOT NULL");
                case "AI" -> where.append(" AND lt.id IS NOT NULL AND lt.question_bank_id IS NULL AND (lt.generation_prompt IS NULL OR lt.generation_prompt NOT LIKE 'Generated from question bank id=%%')");
                case "LEGACY_BANK" -> where.append(" AND lt.id IS NOT NULL AND lt.question_bank_id IS NULL AND lt.generation_prompt LIKE 'Generated from question bank id=%%'");
                case "NONE" -> where.append(" AND lt.id IS NULL");
                default -> {
                }
            }
        }
        if (keyword != null) {
            where.append("""
                     AND LOWER(
                        CONCAT(
                            COALESCE(u.email, ''), ' ',
                            COALESCE(u.first_name, ''), ' ',
                            COALESCE(u.last_name, ''), ' ',
                            COALESCE(j.domain, ''), ' ',
                            COALESCE(j.industry, ''), ' ',
                            COALESCE(j.job_role, ''), ' ',
                            COALESCE(j.goal, '')
                        )
                    ) LIKE ?
                    """);
            params.add("%" + keyword.toLowerCase(Locale.ROOT) + "%");
        }

        return where.toString();
    }

    private String buildQuestionAnalyticsWhereClause(
            Long questionBankId,
            String difficulty,
            String skillArea,
            String source,
            Boolean isActive,
            String keyword,
            List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");

        if (questionBankId != null) {
            where.append(" AND q.question_bank_id = ?");
            params.add(questionBankId);
        }
        if (difficulty != null) {
            where.append(" AND q.difficulty = ?");
            params.add(difficulty);
        }
        if (skillArea != null) {
            where.append(" AND q.skill_area = ?");
            params.add(skillArea);
        }
        if (source != null) {
            where.append(" AND q.source = ?");
            params.add(source);
        }
        if (isActive != null) {
            where.append(" AND q.is_active = ?");
            params.add(isActive);
        }
        if (keyword != null) {
            where.append("""
                     AND LOWER(
                        CONCAT(
                            COALESCE(q.question_text, ''), ' ',
                            COALESCE(qb.title, ''), ' ',
                            COALESCE(qb.domain, ''), ' ',
                            COALESCE(qb.job_role, '')
                        )
                    ) LIKE ?
                    """);
            params.add("%" + keyword.toLowerCase(Locale.ROOT) + "%");
        }

        return where.toString();
    }

    private String resolveSortClause(Sort sort, Map<String, String> mapping, String defaultSortClause) {
        if (sort == null || sort.isUnsorted()) {
            return defaultSortClause;
        }

        Sort.Order order = sort.iterator().next();
        String mappedColumn = mapping.get(order.getProperty());
        if (mappedColumn == null) {
            return defaultSortClause;
        }

        return mappedColumn + (order.isAscending() ? " ASC" : " DESC") + " NULLS LAST";
    }

    private AdminJourneyDashboardResponse.MetricBreakdownItem metric(String label, long value) {
        return AdminJourneyDashboardResponse.MetricBreakdownItem.builder()
                .label(label)
                .value(value)
                .build();
    }

    private long queryForLong(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Double toRate(long numerator, long denominator) {
        if (denominator <= 0L) {
            return 0.0;
        }
        return Math.round((numerator * 10000.0d / denominator)) / 100.0d;
    }

    private Double roundNullableDouble(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        return Math.round(number.doubleValue() * 100.0d) / 100.0d;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Integer getInteger(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        return value instanceof Number number ? number.intValue() : null;
    }

    private Long getLong(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        return value instanceof Number number ? number.longValue() : null;
    }

    private Boolean getBoolean(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        return value instanceof Boolean bool ? bool : null;
    }
}
