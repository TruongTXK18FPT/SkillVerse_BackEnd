package com.exe.skillverse_backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;

/**
 * DatabaseSchemaFixer — runtime idempotent patches for PostgreSQL.
 *
 * GUARDRAILS:
 * - Advisory lock (single-instance execution)
 * - Patch history tracking
 * - Verify + fail-fast
 *
 * HOW TO ADD A NEW PATCH:
 * 1. Add applyPatch() call in applySchemaPatchesWithLock()
 * 2. Implement patchXxx() method with the SQL
 * 3. Implement verifyXxx() method to check it worked
 *
 * Keep this file for production-safe schema gaps that cannot rely on
 * Hibernate ddl-auto, especially new feature rollouts on existing databases.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSchemaFixer {

    private static final long SCHEMA_FIXER_LOCK_KEY = 2026031502L;

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.database-schema-fixer.enabled:true}")
    private boolean enabled;

    @Value("${skillverse.ai.rag.java-enabled:false}")
    private boolean javaRagEnabled;

    @PostConstruct
    public void fixDatabaseConstraints() {
        if (!enabled) {
            log.info("DatabaseSchemaFixer skipped: app.database-schema-fixer.enabled=false");
            return;
        }

        String productName = getDatabaseProductName();
        if (!isPostgreSql(productName)) {
            log.warn("DatabaseSchemaFixer skipped: only PostgreSQL is supported. Detected: {}", productName);
            return;
        }

        try {
            log.info("Initializing PostgreSQL schema patch system...");
            applySchemaPatchesWithLock();
        } catch (Exception ex) {
            log.error("Schema patching failed: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Database schema patching failed", ex);
        }
    }

    // ─── Schema Patch Executor ─────────────────────────────────────────────────

    private void applySchemaPatchesWithLock() {
        ensurePatchHistoryTable();
        acquireAdvisoryLock();
        try {
            applyPatch(
                "20260509_backfill_skill_canonical_key",
                "Backfill canonical_key and status for existing skills",
                this::patchBackfillSkillCanonicalKey,
                this::verifyBackfillSkillCanonicalKey
            );

            applyPatch(
                "20260514_add_skills_status_column",
                "Add 'status' column to skills table (enum string)",
                this::patchAddSkillStatusColumn,
                this::verifyAddSkillStatusColumn
            );

            applyPatch(
                "20260514_create_ai_token_usage_logs",
                "Create ai_token_usage_logs table if missing",
                this::patchCreateAiTokenUsageLogs,
                this::verifyCreateAiTokenUsageLogs
            );

            applyPatch(
                "20260515_roadmap_template_admin_v2",
                "Create and update roadmap template admin V2 tables, columns, indexes, and constraints",
                this::patchRoadmapTemplateAdminV2,
                this::verifyRoadmapTemplateAdminV2
            );

            applyPatch(
                "20260516_roadmap_template_activity_level_band",
                "Add min/max level band to roadmap template activities",
                this::patchRoadmapTemplateActivityLevelBand,
                this::verifyRoadmapTemplateActivityLevelBand
            );

            applyPatch(
                "20260516_roadmap_templates_drop_mentor_not_null",
                "Allow admin-owned roadmap templates without legacy mentor_id",
                this::patchRoadmapTemplatesDropMentorNotNull,
                this::verifyRoadmapTemplatesDropMentorNotNull
            );

            applyPatch(
                "20260510_drop_unused_services_tables",
                "Drop tables related to deprecated parent_service and seminar_service",
                this::patchDropUnusedTables,
                this::verifyDropUnusedTables
            );
            if (javaRagEnabled) {
                applyPatch(
                    "20260516_create_rag_chunks_and_vector",
                    "Create pgvector extension and rag_chunks table",
                    this::patchCreateRagChunksAndVector,
                    this::verifyCreateRagChunksAndVector
                );
            } else {
                log.info("Skipping rag_chunks and vector schema patch because java-rag is disabled");
            }

            applyPatch(
                "20260516_create_app_runtime_settings",
                "Create app_runtime_settings table for admin runtime toggles",
                this::patchCreateAppRuntimeSettings,
                this::verifyCreateAppRuntimeSettings
            );

            applyPatch(
                "20260517_question_bank_taxonomy_scope",
                "Add taxonomy scope columns to question banks",
                this::patchQuestionBankTaxonomyScope,
                this::verifyQuestionBankTaxonomyScope
            );

            applyPatch(
                "20260517_drop_track_skill_importance_level",
                "Drop importance_level from job_position_track_skills and enforce weight 1-10",
                this::patchDropTrackSkillImportanceLevel,
                this::verifyDropTrackSkillImportanceLevel
            );

            applyPatch(
                "20260518_drop_track_skill_requirement_type_check",
                "Drop check constraint on requirement_type in job_position_track_skills",
                this::patchDropTrackSkillRequirementTypeCheck,
                this::verifyDropTrackSkillRequirementTypeCheck
            );
            // New patch: create batch verification tables & columns
            applyPatch(
                "20260520_create_mentor_batch_verification",
                "Create mentor_batch_verification_requests table and batch_id columns",
                this::patchCreateMentorBatchVerification,
                this::verifyCreateMentorBatchVerification
            );
            applyPatch(
                "20260521_allow_revoked_mentor_verification_status",
                "Allow REVOKED status for mentor skill verification requests and batches",
                this::patchAllowRevokedMentorVerificationStatus,
                this::verifyAllowRevokedMentorVerificationStatus
            );
            applyPatch(
                "20260521_allow_cv_verification_evidence_type",
                "Allow CV evidence type for mentor and student verification evidences",
                this::patchAllowCvVerificationEvidenceType,
                this::verifyAllowCvVerificationEvidenceType
            );
            applyPatch(
                "20260521_verified_skill_featured_order",
                "Add featured ordering fields to user verified skills",
                this::patchVerifiedSkillFeaturedOrder,
                this::verifyVerifiedSkillFeaturedOrder
            );
            
            applyPatch(
                "20260522_roadmap_evidence_ai_review",
                "Add tables and columns for roadmap evidence AI review",
                this::patchRoadmapEvidenceAiReview,
                this::verifyRoadmapEvidenceAiReview
            );

            log.info("No active schema patches to run. Infrastructure ready.");
        } finally {
            releaseAdvisoryLock();
        }
    }

    private void patchDropUnusedTables() {
        log.info("Dropping tables for parent_service and seminar_service...");
        // Drop in correct order to avoid FK constraint issues
        executeSql("DROP TABLE IF EXISTS seminar_tickets CASCADE");
        executeSql("DROP TABLE IF EXISTS seminars CASCADE");
        executeSql("DROP TABLE IF EXISTS learning_reports CASCADE");
        executeSql("DROP TABLE IF EXISTS parent_student_links CASCADE");
    }

    private boolean verifyDropUnusedTables() {
        return !hasTable("seminar_tickets") && 
               !hasTable("seminars") && 
               !hasTable("learning_reports") && 
               !hasTable("parent_student_links");
    }

    private void patchBackfillSkillCanonicalKey() {
        // Only run if column exists (hibernate ddl-auto might have created it)
        if (!hasColumn("skills", "canonical_key")) {
            log.info("Column canonical_key does not exist yet. Assuming first run or hibernate hasn't run.");
            return;
        }
        boolean hasStatusColumn = hasColumn("skills", "status");
        var skills = jdbcTemplate.queryForList("SELECT id, name FROM skills WHERE canonical_key IS NULL");
        for (var skill : skills) {
            Long id = ((Number) skill.get("id")).longValue();
            String name = (String) skill.get("name");
            String canonicalKey = com.exe.skillverse_backend.shared.util.SkillNameUtils.normalize(name);
            if (hasStatusColumn) {
                jdbcTemplate.update("UPDATE skills SET canonical_key = ?, status = 'ACTIVE' WHERE id = ?", canonicalKey, id);
            } else {
                jdbcTemplate.update("UPDATE skills SET canonical_key = ? WHERE id = ?", canonicalKey, id);
            }
        }
    }

    private boolean verifyBackfillSkillCanonicalKey() {
        if (!hasColumn("skills", "canonical_key")) {
            return true; // Wait for hibernate
        }
        var results = jdbcTemplate.queryForList("SELECT 1 FROM skills WHERE canonical_key IS NULL");
        return results.isEmpty();
    }

    private void patchAddSkillStatusColumn() {
        if (hasColumn("skills", "status")) {
            log.debug("Column 'status' already exists on skills table; skipping");
            return;
        }
        log.info("Adding 'status' column to skills table...");
        // Use a VARCHAR that matches the enum string values; set default to ACTIVE for safety
        executeSql("ALTER TABLE skills ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'");
    }

    private boolean verifyAddSkillStatusColumn() {
        return hasColumn("skills", "status");
    }

    private void patchCreateAiTokenUsageLogs() {
        if (hasTable("ai_token_usage_logs")) {
            log.debug("Table ai_token_usage_logs already exists; skipping");
            return;
        }
        log.info("Creating table ai_token_usage_logs...");
        executeSql("CREATE TABLE IF NOT EXISTS ai_token_usage_logs (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "flow_type VARCHAR(30) NOT NULL, " +
                "provider_type VARCHAR(20) NOT NULL, " +
                "model_name VARCHAR(50), " +
                "user_id BIGINT, " +
                "related_entity_type VARCHAR(30), " +
                "related_entity_id BIGINT, " +
                "prompt_tokens BIGINT, " +
                "completion_tokens BIGINT, " +
                "total_tokens BIGINT, " +
                "estimated BOOLEAN NOT NULL, " +
                "status VARCHAR(10) NOT NULL, " +
                "latency_ms BIGINT, " +
                "error_code VARCHAR(50), " +
                "created_at TIMESTAMP NOT NULL" +
                ")");
        executeSql("CREATE INDEX IF NOT EXISTS idx_ai_token_usage_logs_created_at ON ai_token_usage_logs(created_at)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_ai_token_usage_logs_flow_type_created ON ai_token_usage_logs(flow_type, created_at)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_ai_token_usage_logs_provider_type_created ON ai_token_usage_logs(provider_type, created_at)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_ai_token_usage_logs_user_id_created ON ai_token_usage_logs(user_id, created_at)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_ai_token_usage_logs_status_created ON ai_token_usage_logs(status, created_at)");
    }

    private boolean verifyCreateAiTokenUsageLogs() {
        return hasTable("ai_token_usage_logs");
    }

    private void patchCreateRagChunksAndVector() {
        if (hasTable("rag_chunks")) {
            log.debug("Table rag_chunks already exists; skipping");
            return;
        }
        log.info("Creating pgvector extension and table rag_chunks...");
        executeSql("CREATE EXTENSION IF NOT EXISTS vector");

        executeSql("CREATE TABLE IF NOT EXISTS rag_chunks (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "course_id BIGINT, " +
                "module_id BIGINT, " +
                "doc_id VARCHAR(100) NOT NULL, " +
                "doc_type VARCHAR(20) NOT NULL, " +
                "title VARCHAR(500), " +
                "content TEXT NOT NULL, " +
                "embedding vector(1024), " +
                "metadata JSONB DEFAULT '{}', " +
                "created_at TIMESTAMPTZ DEFAULT NOW()" +
                ")");

        executeSql("CREATE INDEX IF NOT EXISTS idx_rag_course ON rag_chunks (course_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_rag_module ON rag_chunks (course_id, module_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_rag_metadata ON rag_chunks USING GIN (metadata)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_rag_embedding ON rag_chunks USING hnsw (embedding vector_cosine_ops)");
    }

    private boolean verifyCreateRagChunksAndVector() {
        return hasTable("rag_chunks");
    }

    private void patchCreateAppRuntimeSettings() {
        if (hasTable("app_runtime_settings")) {
            log.debug("Table app_runtime_settings already exists; skipping");
            return;
        }
        log.info("Creating table app_runtime_settings...");
        executeSql("CREATE TABLE IF NOT EXISTS app_runtime_settings (" +
                "setting_key VARCHAR(100) PRIMARY KEY, " +
                "setting_value TEXT NOT NULL, " +
                "updated_at TIMESTAMPTZ DEFAULT NOW(), " +
                "updated_by BIGINT" +
                ")");
    }

    private boolean verifyCreateAppRuntimeSettings() {
        return hasTable("app_runtime_settings");
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private void patchQuestionBankTaxonomyScope() {
        if (!hasTable("question_banks")) {
            log.info("Table question_banks does not exist yet; skipping taxonomy scope patch");
            return;
        }

        executeSql("ALTER TABLE question_banks ADD COLUMN IF NOT EXISTS domain_id BIGINT");
        executeSql("ALTER TABLE question_banks ADD COLUMN IF NOT EXISTS job_position_id BIGINT");
        executeSql("ALTER TABLE question_banks ADD COLUMN IF NOT EXISTS skill_id BIGINT");
        executeSql("CREATE INDEX IF NOT EXISTS idx_question_banks_domain_id ON question_banks(domain_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_question_banks_job_position_id ON question_banks(job_position_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_question_banks_skill_id ON question_banks(skill_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_question_banks_taxonomy_scope ON question_banks(domain_id, job_position_id, skill_id)");
        executeSql("""
            UPDATE question_banks qb
            SET domain_id = d.id
            FROM domains d
            WHERE qb.domain_id IS NULL
              AND qb.domain IS NOT NULL
              AND lower(qb.domain) = lower(d.code)
        """);
        executeSql("""
            UPDATE question_banks qb
            SET job_position_id = jp.id
            FROM job_positions jp
            WHERE qb.job_position_id IS NULL
              AND qb.domain_id = jp.domain_id
              AND (
                lower(coalesce(qb.job_role, '')) = lower(jp.name)
                OR lower(coalesce(qb.job_role, '')) = lower(jp.code)
                OR lower(coalesce(qb.industry, '')) = lower(jp.name)
                OR lower(coalesce(qb.industry, '')) = lower(jp.code)
              )
        """);
        executeSql("""
            UPDATE question_banks qb
            SET skill_id = s.id
            FROM skills s
            WHERE qb.skill_id IS NULL
              AND qb.skill_name IS NOT NULL
              AND (
                lower(qb.skill_name) = lower(s.name)
                OR lower(qb.skill_name) = lower(s.canonical_key)
              )
        """);

        if (hasTable("question_bank_submissions")) {
            executeSql("ALTER TABLE question_bank_submissions ADD COLUMN IF NOT EXISTS domain_id BIGINT");
            executeSql("ALTER TABLE question_bank_submissions ADD COLUMN IF NOT EXISTS job_position_id BIGINT");
            executeSql("ALTER TABLE question_bank_submissions ADD COLUMN IF NOT EXISTS skill_id BIGINT");
            executeSql("CREATE INDEX IF NOT EXISTS idx_qbs_domain_id ON question_bank_submissions(domain_id)");
            executeSql("CREATE INDEX IF NOT EXISTS idx_qbs_job_position_id ON question_bank_submissions(job_position_id)");
            executeSql("CREATE INDEX IF NOT EXISTS idx_qbs_skill_id ON question_bank_submissions(skill_id)");
            executeSql("""
                UPDATE question_bank_submissions qbs
                SET domain_id = d.id
                FROM domains d
                WHERE qbs.domain_id IS NULL
                  AND qbs.domain IS NOT NULL
                  AND lower(qbs.domain) = lower(d.code)
            """);
            executeSql("""
                UPDATE question_bank_submissions qbs
                SET job_position_id = jp.id
                FROM job_positions jp
                WHERE qbs.job_position_id IS NULL
                  AND qbs.domain_id = jp.domain_id
                  AND (
                    lower(coalesce(qbs.job_role, '')) = lower(jp.name)
                    OR lower(coalesce(qbs.job_role, '')) = lower(jp.code)
                    OR lower(coalesce(qbs.industry, '')) = lower(jp.name)
                    OR lower(coalesce(qbs.industry, '')) = lower(jp.code)
                  )
            """);
            executeSql("""
                UPDATE question_bank_submissions qbs
                SET skill_id = s.id
                FROM skills s
                WHERE qbs.skill_id IS NULL
                  AND qbs.skill_name IS NOT NULL
                  AND (
                    lower(qbs.skill_name) = lower(s.name)
                    OR lower(qbs.skill_name) = lower(s.canonical_key)
                  )
            """);
        }
    }

    private boolean verifyQuestionBankTaxonomyScope() {
        boolean bankColumnsOk = !hasTable("question_banks")
                || (hasColumn("question_banks", "domain_id")
                && hasColumn("question_banks", "job_position_id")
                && hasColumn("question_banks", "skill_id"));
        boolean submissionColumnsOk = !hasTable("question_bank_submissions")
                || (hasColumn("question_bank_submissions", "domain_id")
                && hasColumn("question_bank_submissions", "job_position_id")
                && hasColumn("question_bank_submissions", "skill_id"));
        return bankColumnsOk && submissionColumnsOk;
    }

    private void patchRoadmapTemplateAdminV2() {
        log.info("Creating/updating roadmap template admin V2 schema...");

        executeSql("""
            CREATE TABLE IF NOT EXISTS roadmap_templates (
                id BIGSERIAL PRIMARY KEY,
                created_by_admin_id BIGINT,
                updated_by_admin_id BIGINT,
                domain_id BIGINT NOT NULL,
                job_position_id BIGINT NOT NULL,
                job_position_track_id BIGINT NOT NULL,
                title VARCHAR(255) NOT NULL,
                description TEXT,
                target_role VARCHAR(255),
                target_level VARCHAR(50),
                target_role_snapshot VARCHAR(255),
                target_level_snapshot VARCHAR(50),
                total_node_count INTEGER,
                generation_mode VARCHAR(30) NOT NULL DEFAULT 'LEGACY_STATIC',
                knowledge_policy VARCHAR(40) NOT NULL DEFAULT 'TEMPLATE_ONLY',
                global_learning_goal TEXT,
                audience_level VARCHAR(80),
                output_standard TEXT,
                assessment_policy TEXT,
                template_instructions TEXT,
                constraints_json TEXT,
                status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ
            )
        """);

        executeSql("""
            CREATE TABLE IF NOT EXISTS roadmap_template_nodes (
                id BIGSERIAL PRIMARY KEY,
                template_id BIGINT NOT NULL,
                parent_node_id BIGINT,
                node_key VARCHAR(100),
                title VARCHAR(255) NOT NULL,
                description TEXT,
                order_index INTEGER NOT NULL,
                skill_id BIGINT,
                skill_name_snapshot VARCHAR(255),
                skill_canonical_key_snapshot VARCHAR(255),
                requirement_type VARCHAR(30),
                importance_level VARCHAR(30),
                difficulty VARCHAR(30),
                estimated_hours DOUBLE PRECISION,
                expected_output TEXT,
                rubric TEXT,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ
            )
        """);

        executeSql("""
            CREATE TABLE IF NOT EXISTS roadmap_template_courses (
                id BIGSERIAL PRIMARY KEY,
                template_id BIGINT NOT NULL,
                template_node_id BIGINT,
                course_id BIGINT NOT NULL,
                skill_id BIGINT,
                display_order INTEGER,
                required BOOLEAN DEFAULT FALSE,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
        """);

        executeSql("""
            CREATE TABLE IF NOT EXISTS roadmap_template_skill_blocks (
                id BIGSERIAL PRIMARY KEY,
                template_id BIGINT NOT NULL,
                skill_id BIGINT NOT NULL,
                skill_name_snapshot VARCHAR(255),
                skill_canonical_key_snapshot VARCHAR(255),
                weight_percent DOUBLE PRECISION NOT NULL,
                min_nodes INTEGER,
                max_nodes INTEGER,
                node_count_override INTEGER,
                learning_goals TEXT,
                required_topics TEXT,
                activity_instructions TEXT,
                exercise_types TEXT,
                success_criteria TEXT,
                rag_query_hint TEXT,
                course_link_policy VARCHAR(30) NOT NULL DEFAULT 'AUTO_HYBRID',
                auto_course_limit INTEGER NOT NULL DEFAULT 2,
                rag_enabled BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ
            )
        """);

        executeSql("""
            CREATE TABLE IF NOT EXISTS roadmap_template_activities (
                id BIGSERIAL PRIMARY KEY,
                template_id BIGINT NOT NULL,
                skill_block_id BIGINT NOT NULL,
                title VARCHAR(255) NOT NULL,
                description TEXT,
                exercise_type VARCHAR(80),
                expected_output TEXT,
                rubric TEXT,
                difficulty VARCHAR(30),
                min_level VARCHAR(20),
                max_level VARCHAR(20),
                estimated_hours DOUBLE PRECISION,
                prerequisite_hint TEXT,
                ai_prompt_hint TEXT,
                order_index INTEGER NOT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ
            )
        """);

        executeSql("""
            CREATE TABLE IF NOT EXISTS roadmap_template_node_groups (
                id BIGSERIAL PRIMARY KEY,
                template_id BIGINT NOT NULL,
                node_key VARCHAR(120),
                title VARCHAR(255) NOT NULL,
                description TEXT,
                learning_objectives TEXT,
                lessons_json TEXT,
                exercises_json TEXT,
                completion_criteria TEXT,
                expected_output TEXT,
                rubric TEXT,
                difficulty VARCHAR(30),
                estimated_hours DOUBLE PRECISION,
                ai_prompt_hint TEXT,
                order_index INTEGER NOT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ
            )
        """);

        executeSql("""
            CREATE TABLE IF NOT EXISTS roadmap_template_node_group_skills (
                id BIGSERIAL PRIMARY KEY,
                node_group_id BIGINT NOT NULL,
                skill_id BIGINT NOT NULL,
                skill_name_snapshot VARCHAR(255),
                skill_canonical_key_snapshot VARCHAR(255),
                requirement_type VARCHAR(30) NOT NULL DEFAULT 'REQUIRED',
                weight_in_node DOUBLE PRECISION,
                order_index INTEGER NOT NULL,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ
            )
        """);

        patchRoadmapTemplateV2Columns();
        patchRoadmapTemplateV2Indexes();
        patchRoadmapTemplateV2Constraints();
    }

    private void patchRoadmapTemplateV2Columns() {
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS total_node_count INTEGER");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS generation_mode VARCHAR(30) NOT NULL DEFAULT 'LEGACY_STATIC'");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS knowledge_policy VARCHAR(40) NOT NULL DEFAULT 'TEMPLATE_ONLY'");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS global_learning_goal TEXT");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS audience_level VARCHAR(80)");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS output_standard TEXT");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS assessment_policy TEXT");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS template_instructions TEXT");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS constraints_json TEXT");
        executeSql("UPDATE roadmap_templates SET generation_mode = 'LEGACY_STATIC' WHERE generation_mode IS NULL");
        executeSql("UPDATE roadmap_templates SET knowledge_policy = 'TEMPLATE_ONLY' WHERE knowledge_policy IS NULL");
        executeSql("ALTER TABLE roadmap_templates ALTER COLUMN generation_mode SET DEFAULT 'LEGACY_STATIC'");
        executeSql("ALTER TABLE roadmap_templates ALTER COLUMN knowledge_policy SET DEFAULT 'TEMPLATE_ONLY'");
        executeSql("ALTER TABLE roadmap_templates ALTER COLUMN generation_mode SET NOT NULL");
        executeSql("ALTER TABLE roadmap_templates ALTER COLUMN knowledge_policy SET NOT NULL");
        patchRoadmapTemplatesDropMentorNotNull();

        executeSql("ALTER TABLE roadmap_template_courses ADD COLUMN IF NOT EXISTS skill_id BIGINT");

        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS skill_name_snapshot VARCHAR(255)");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS skill_canonical_key_snapshot VARCHAR(255)");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS min_nodes INTEGER");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS max_nodes INTEGER");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS node_count_override INTEGER");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS learning_goals TEXT");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS required_topics TEXT");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS activity_instructions TEXT");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS exercise_types TEXT");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS success_criteria TEXT");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS rag_query_hint TEXT");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS course_link_policy VARCHAR(30) NOT NULL DEFAULT 'AUTO_HYBRID'");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS auto_course_limit INTEGER NOT NULL DEFAULT 2");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ADD COLUMN IF NOT EXISTS rag_enabled BOOLEAN NOT NULL DEFAULT TRUE");
        executeSql("UPDATE roadmap_template_skill_blocks SET course_link_policy = 'AUTO_HYBRID' WHERE course_link_policy IS NULL");
        executeSql("UPDATE roadmap_template_skill_blocks SET auto_course_limit = 2 WHERE auto_course_limit IS NULL");
        executeSql("UPDATE roadmap_template_skill_blocks SET rag_enabled = TRUE WHERE rag_enabled IS NULL");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ALTER COLUMN course_link_policy SET DEFAULT 'AUTO_HYBRID'");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ALTER COLUMN auto_course_limit SET DEFAULT 2");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ALTER COLUMN rag_enabled SET DEFAULT TRUE");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ALTER COLUMN course_link_policy SET NOT NULL");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ALTER COLUMN auto_course_limit SET NOT NULL");
        executeSql("ALTER TABLE roadmap_template_skill_blocks ALTER COLUMN rag_enabled SET NOT NULL");
    }

    private void patchRoadmapTemplateV2Indexes() {
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_templates_status ON roadmap_templates(status)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_templates_job_position_track_id ON roadmap_templates(job_position_track_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_templates_created_by_admin_id ON roadmap_templates(created_by_admin_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_nodes_template_id ON roadmap_template_nodes(template_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_nodes_skill_id ON roadmap_template_nodes(skill_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_courses_template_id ON roadmap_template_courses(template_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_courses_template_node_id ON roadmap_template_courses(template_node_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_courses_course_id ON roadmap_template_courses(course_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_courses_skill_id ON roadmap_template_courses(skill_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_skill_blocks_template_id ON roadmap_template_skill_blocks(template_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_skill_blocks_skill_id ON roadmap_template_skill_blocks(skill_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_activities_skill_block_id ON roadmap_template_activities(skill_block_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_activities_template_id ON roadmap_template_activities(template_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_node_groups_template_id ON roadmap_template_node_groups(template_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_node_groups_template_order ON roadmap_template_node_groups(template_id, order_index)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_node_group_skills_node_group_id ON roadmap_template_node_group_skills(node_group_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_roadmap_template_node_group_skills_skill_id ON roadmap_template_node_group_skills(skill_id)");
    }

    private void patchRoadmapTemplateV2Constraints() {
        addConstraintIfMissing(
                "roadmap_template_nodes",
                "fk_roadmap_template_nodes_template",
                "ALTER TABLE roadmap_template_nodes ADD CONSTRAINT fk_roadmap_template_nodes_template " +
                        "FOREIGN KEY (template_id) REFERENCES roadmap_templates(id) ON DELETE CASCADE"
        );
        addConstraintIfMissing(
                "roadmap_template_courses",
                "fk_roadmap_template_courses_template",
                "ALTER TABLE roadmap_template_courses ADD CONSTRAINT fk_roadmap_template_courses_template " +
                        "FOREIGN KEY (template_id) REFERENCES roadmap_templates(id) ON DELETE CASCADE"
        );
        addConstraintIfMissing(
                "roadmap_template_courses",
                "fk_roadmap_template_courses_node",
                "ALTER TABLE roadmap_template_courses ADD CONSTRAINT fk_roadmap_template_courses_node " +
                        "FOREIGN KEY (template_node_id) REFERENCES roadmap_template_nodes(id) ON DELETE CASCADE"
        );
        addConstraintIfMissing(
                "roadmap_template_skill_blocks",
                "fk_roadmap_template_skill_blocks_template",
                "ALTER TABLE roadmap_template_skill_blocks ADD CONSTRAINT fk_roadmap_template_skill_blocks_template " +
                        "FOREIGN KEY (template_id) REFERENCES roadmap_templates(id) ON DELETE CASCADE"
        );
        addConstraintIfMissing(
                "roadmap_template_activities",
                "fk_roadmap_template_activities_template",
                "ALTER TABLE roadmap_template_activities ADD CONSTRAINT fk_roadmap_template_activities_template " +
                        "FOREIGN KEY (template_id) REFERENCES roadmap_templates(id) ON DELETE CASCADE"
        );
        addConstraintIfMissing(
                "roadmap_template_activities",
                "fk_roadmap_template_activities_skill_block",
                "ALTER TABLE roadmap_template_activities ADD CONSTRAINT fk_roadmap_template_activities_skill_block " +
                        "FOREIGN KEY (skill_block_id) REFERENCES roadmap_template_skill_blocks(id) ON DELETE CASCADE"
        );
        addConstraintIfMissing(
                "roadmap_template_node_groups",
                "fk_roadmap_template_node_groups_template",
                "ALTER TABLE roadmap_template_node_groups ADD CONSTRAINT fk_roadmap_template_node_groups_template " +
                        "FOREIGN KEY (template_id) REFERENCES roadmap_templates(id) ON DELETE CASCADE"
        );
        addConstraintIfMissing(
                "roadmap_template_node_group_skills",
                "fk_roadmap_template_node_group_skills_group",
                "ALTER TABLE roadmap_template_node_group_skills ADD CONSTRAINT fk_roadmap_template_node_group_skills_group " +
                        "FOREIGN KEY (node_group_id) REFERENCES roadmap_template_node_groups(id) ON DELETE CASCADE"
        );
    }

    private boolean verifyRoadmapTemplateAdminV2() {
        return hasTable("roadmap_templates")
                && hasTable("roadmap_template_nodes")
                && hasTable("roadmap_template_courses")
                && hasTable("roadmap_template_skill_blocks")
                && hasTable("roadmap_template_activities")
                && hasTable("roadmap_template_node_groups")
                && hasTable("roadmap_template_node_group_skills")
                && hasColumn("roadmap_templates", "total_node_count")
                && hasColumn("roadmap_templates", "generation_mode")
                && hasColumn("roadmap_templates", "knowledge_policy")
                && hasColumn("roadmap_templates", "global_learning_goal")
                && hasColumn("roadmap_templates", "audience_level")
                && hasColumn("roadmap_templates", "output_standard")
                && hasColumn("roadmap_templates", "assessment_policy")
                && hasColumn("roadmap_templates", "template_instructions")
                && hasColumn("roadmap_templates", "constraints_json")
                && hasColumn("roadmap_template_courses", "skill_id")
                && hasColumn("roadmap_template_skill_blocks", "weight_percent")
                && hasColumn("roadmap_template_skill_blocks", "course_link_policy")
                && hasColumn("roadmap_template_skill_blocks", "auto_course_limit")
                && hasColumn("roadmap_template_skill_blocks", "rag_enabled")
                && hasColumn("roadmap_template_activities", "expected_output")
                && hasColumn("roadmap_template_activities", "rubric")
                && hasColumn("roadmap_template_activities", "ai_prompt_hint")
                && hasColumn("roadmap_template_activities", "min_level")
                && hasColumn("roadmap_template_activities", "max_level")
                && hasColumn("roadmap_template_node_groups", "completion_criteria")
                && hasColumn("roadmap_template_node_group_skills", "requirement_type")
                && hasIndex("idx_roadmap_template_skill_blocks_template_id")
                && hasIndex("idx_roadmap_template_activities_skill_block_id")
                && hasIndex("idx_roadmap_template_node_groups_template_id")
                && hasIndex("idx_roadmap_template_node_group_skills_node_group_id");
    }

    private void patchRoadmapTemplateActivityLevelBand() {
        if (!hasTable("roadmap_template_activities")) {
            log.info("Table roadmap_template_activities does not exist yet. Creating V2 schema first.");
            patchRoadmapTemplateAdminV2();
        }
        executeSql("ALTER TABLE roadmap_template_activities ADD COLUMN IF NOT EXISTS min_level VARCHAR(20)");
        executeSql("ALTER TABLE roadmap_template_activities ADD COLUMN IF NOT EXISTS max_level VARCHAR(20)");
    }

    private boolean verifyRoadmapTemplateActivityLevelBand() {
        return hasTable("roadmap_template_activities")
                && hasColumn("roadmap_template_activities", "min_level")
                && hasColumn("roadmap_template_activities", "max_level");
    }

    private void patchRoadmapTemplatesDropMentorNotNull() {
        if (!hasTable("roadmap_templates") || !hasColumn("roadmap_templates", "mentor_id")) {
            return;
        }
        log.info("Relaxing legacy roadmap_templates.mentor_id NOT NULL constraint for admin templates...");
        executeSql("ALTER TABLE roadmap_templates ALTER COLUMN mentor_id DROP NOT NULL");
        executeSql("ALTER TABLE roadmap_templates ALTER COLUMN mentor_id DROP DEFAULT");
    }

    private boolean verifyRoadmapTemplatesDropMentorNotNull() {
        return !hasTable("roadmap_templates")
                || !hasColumn("roadmap_templates", "mentor_id")
                || !isColumnNotNull("roadmap_templates", "mentor_id");
    }

    private void patchDropTrackSkillImportanceLevel() {
        if (!hasTable("job_position_track_skills")) {
            log.info("Table job_position_track_skills does not exist yet; skipping");
            return;
        }

        boolean hasWeightColumn = hasColumn("job_position_track_skills", "weight");
        boolean hasImportanceLevelColumn = hasColumn("job_position_track_skills", "importance_level");

        if (!hasWeightColumn) {
            log.info("Adding missing weight column to job_position_track_skills...");
            executeSql("ALTER TABLE job_position_track_skills ADD COLUMN IF NOT EXISTS weight INTEGER");
            hasWeightColumn = true;
        }

        if (hasImportanceLevelColumn) {
            log.info("Backfilling weight from legacy importance_level values...");
            executeSql("""
                UPDATE job_position_track_skills
                SET weight = CASE
                    WHEN importance_level IS NULL THEN 1
                    WHEN importance_level BETWEEN 0 AND 5 THEN GREATEST(1, importance_level * 2)
                    ELSE LEAST(10, importance_level)
                END
                WHERE weight IS NULL
            """);
        }

        // 1. Clamp existing weight values to 1-10
        if (hasWeightColumn) {
            executeSql("UPDATE job_position_track_skills SET weight = 1 WHERE weight IS NULL OR weight < 1");
            executeSql("UPDATE job_position_track_skills SET weight = 10 WHERE weight > 10");
        }

        // 2. Drop the importance_level column
        if (hasImportanceLevelColumn) {
            log.info("Dropping importance_level column from job_position_track_skills...");
            executeSql("ALTER TABLE job_position_track_skills DROP COLUMN importance_level");
        }

        // 3. Ensure weight is NOT NULL with a default
        if (hasWeightColumn) {
            executeSql("ALTER TABLE job_position_track_skills ALTER COLUMN weight SET NOT NULL");
            executeSql("ALTER TABLE job_position_track_skills ALTER COLUMN weight SET DEFAULT 1");
        }

        // 4. Add CHECK constraint for weight range
        addConstraintIfMissing(
                "job_position_track_skills",
                "chk_track_skill_weight",
                "ALTER TABLE job_position_track_skills ADD CONSTRAINT chk_track_skill_weight CHECK (weight >= 1 AND weight <= 10)"
        );
    }

    private boolean verifyDropTrackSkillImportanceLevel() {
        if (!hasTable("job_position_track_skills")) {
            return true;
        }
        return hasColumn("job_position_track_skills", "weight")
                && !hasColumn("job_position_track_skills", "importance_level")
                && hasConstraint("job_position_track_skills", "chk_track_skill_weight");
    }

    private void patchDropTrackSkillRequirementTypeCheck() {
        if (!hasTable("job_position_track_skills")) {
            log.info("Table job_position_track_skills does not exist yet; skipping");
            return;
        }

        if (hasConstraint("job_position_track_skills", "job_position_track_skills_requirement_type_check")) {
            log.info("Dropping requirement_type check constraint from job_position_track_skills...");
            executeSql("ALTER TABLE job_position_track_skills DROP CONSTRAINT job_position_track_skills_requirement_type_check");
        }
    }

    private boolean verifyDropTrackSkillRequirementTypeCheck() {
        if (!hasTable("job_position_track_skills")) {
            return true;
        }
        return !hasConstraint("job_position_track_skills", "job_position_track_skills_requirement_type_check");
    }

    private String getDatabaseProductName() {
        try {
            return jdbcTemplate.getDataSource().getConnection().getMetaData().getDatabaseProductName();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private boolean isPostgreSql(String productName) {
        return "PostgreSQL".equalsIgnoreCase(productName);
    }

    private void ensurePatchHistoryTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS schema_patch_history (
                patch_key VARCHAR(120) PRIMARY KEY,
                patch_description TEXT NOT NULL,
                checksum VARCHAR(64) NOT NULL,
                applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                success BOOLEAN NOT NULL DEFAULT TRUE
            )
        """);
    }

    private void acquireAdvisoryLock() {
        jdbcTemplate.execute("SELECT pg_advisory_lock(" + SCHEMA_FIXER_LOCK_KEY + ")");
    }

    private void releaseAdvisoryLock() {
        jdbcTemplate.execute("SELECT pg_advisory_unlock(" + SCHEMA_FIXER_LOCK_KEY + ")");
    }

    private void applyPatch(
            String patchKey,
            String description,
            Runnable patchLogic,
            java.util.function.BooleanSupplier verifier
    ) {
        applyPatch(patchKey, description, patchLogic, verifier, true);
    }

    private void applyPatch(
            String patchKey,
            String description,
            Runnable patchLogic,
            java.util.function.BooleanSupplier verifier,
            boolean useHistoryCheck
    ) {
        if (useHistoryCheck && isPatchApplied(patchKey)) {
            log.debug("Skipping already-applied patch {}", patchKey);
            return;
        }
        log.info("Applying patch {}: {}", patchKey, description);
        patchLogic.run();
        if (!verifier.getAsBoolean()) {
            throw new IllegalStateException("Verification failed for patch " + patchKey);
        }
        if (useHistoryCheck) {
            recordPatchSuccess(patchKey, description);
        }
        log.info("Patch {} applied successfully.", patchKey);
    }

    private boolean isPatchApplied(String patchKey) {
        try {
            var results = jdbcTemplate.queryForList(
                "SELECT checksum FROM schema_patch_history WHERE patch_key = ? AND success = TRUE",
                patchKey
            );
            return !results.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private void recordPatchSuccess(String patchKey, String description) {
        String checksum = buildChecksum(patchKey, description);
        jdbcTemplate.update("""
            INSERT INTO schema_patch_history (patch_key, patch_description, checksum, success)
            VALUES (?, ?, ?, TRUE)
            ON CONFLICT (patch_key) DO UPDATE SET
                patch_description = EXCLUDED.patch_description,
                checksum = EXCLUDED.checksum,
                applied_at = NOW(),
                success = TRUE
        """, patchKey, description, checksum);
    }

    private String buildChecksum(String key, String description) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest((key + description).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    // ─── SQL Execution Helpers ────────────────────────────────────────────────

    private void addConstraintIfMissing(String tableName, String constraintName, String sql) {
        if (!hasTable(tableName)) {
            log.debug("Table {} does not exist; skipping constraint {}", tableName, constraintName);
            return;
        }
        if (hasConstraint(tableName, constraintName)) {
            log.debug("Constraint {} already exists on {}; skipping", constraintName, tableName);
            return;
        }
        executeSql(sql);
    }

    protected void executeSql(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("SQL execution failed (might be ignorable): {}", e.getMessage());
        }
    }

    protected boolean hasColumn(String tableName, String columnName) {
        try {
            var results = jdbcTemplate.queryForList("""
                SELECT 1 FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
            """, tableName, columnName);
            return !results.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean isColumnNotNull(String tableName, String columnName) {
        try {
            var results = jdbcTemplate.queryForList("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
            """, tableName, columnName);
            if (results.isEmpty()) {
                return false;
            }
            Object value = results.get(0).get("is_nullable");
            return "NO".equalsIgnoreCase(String.valueOf(value));
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean hasTable(String tableName) {
        try {
            var results = jdbcTemplate.queryForList("""
                SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ?
            """, tableName);
            return !results.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean hasConstraint(String tableName, String constraintName) {
        try {
            var results = jdbcTemplate.queryForList("""
                SELECT 1 FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND constraint_name = ?
            """, tableName, constraintName);
            return !results.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean hasIndex(String indexName) {
        try {
            var results = jdbcTemplate.queryForList("""
                SELECT 1 FROM pg_indexes WHERE indexname = ?
            """, indexName);
            return !results.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    protected boolean hasIndex(String tableName, String indexName) {
        if (!hasTable(tableName)) {
            return false;
        }
        return hasIndex(indexName);
    }

    // ---------------------------------------------------------------------
    // Batch Verification Schema Patch
    // ---------------------------------------------------------------------
    private void patchCreateMentorBatchVerification() {
        log.info("Creating mentor_batch_verification_requests table and batch_id columns...");
        // Table for batch verification requests
        executeSql("CREATE TABLE IF NOT EXISTS mentor_batch_verification_requests (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "mentor_id BIGINT NOT NULL, " +
                "status VARCHAR(30) NOT NULL DEFAULT 'PENDING', " +
                "github_url VARCHAR(500), " +
                "portfolio_url VARCHAR(500), " +
                "additional_notes TEXT, " +
                "general_review_note TEXT, " +
                "submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), " +
                "reviewed_by BIGINT, " +
                "reviewed_at TIMESTAMPTZ, " +
                "updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()" +
                ")");
        // Indexes for fast lookup
        executeSql("CREATE INDEX IF NOT EXISTS idx_mbvr_mentor_status ON mentor_batch_verification_requests(mentor_id, status)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_mbvr_status_submitted ON mentor_batch_verification_requests(status, submitted_at)");

        // Add batch_request_id columns to existing tables
        executeSql("ALTER TABLE mentor_verification_evidences ADD COLUMN IF NOT EXISTS batch_request_id BIGINT");
        executeSql("ALTER TABLE mentor_verification_evidences ALTER COLUMN verification_request_id DROP NOT NULL");
        executeSql("CREATE INDEX IF NOT EXISTS idx_mve_batch ON mentor_verification_evidences(batch_request_id)");
        executeSql("ALTER TABLE mentor_skill_verification_requests ADD COLUMN IF NOT EXISTS batch_request_id BIGINT");
        executeSql("CREATE INDEX IF NOT EXISTS idx_msvr_batch ON mentor_skill_verification_requests(batch_request_id)");

        // Foreign key constraints (cascade delete when a batch is removed)
        addConstraintIfMissing(
                "mentor_verification_evidences",
                "fk_evidence_batch",
                "ALTER TABLE mentor_verification_evidences ADD CONSTRAINT fk_evidence_batch FOREIGN KEY (batch_request_id) REFERENCES mentor_batch_verification_requests(id) ON DELETE CASCADE"
        );
        addConstraintIfMissing(
                "mentor_skill_verification_requests",
                "fk_skill_batch",
                "ALTER TABLE mentor_skill_verification_requests ADD CONSTRAINT fk_skill_batch FOREIGN KEY (batch_request_id) REFERENCES mentor_batch_verification_requests(id) ON DELETE CASCADE"
        );
        addConstraintIfMissing(
                "mentor_verification_evidences",
                "chk_mve_owner_request",
                "ALTER TABLE mentor_verification_evidences ADD CONSTRAINT chk_mve_owner_request CHECK (verification_request_id IS NOT NULL OR batch_request_id IS NOT NULL)"
        );
    }

    private boolean verifyCreateMentorBatchVerification() {
        boolean tableOk = hasTable("mentor_batch_verification_requests");
        boolean evColOk = hasColumn("mentor_verification_evidences", "batch_request_id");
        boolean skillColOk = hasColumn("mentor_skill_verification_requests", "batch_request_id");
        boolean evSingleNullable = !isColumnNotNull("mentor_verification_evidences", "verification_request_id");
        return tableOk && evColOk && skillColOk && evSingleNullable;
    }

    private void patchAllowRevokedMentorVerificationStatus() {
        if (hasConstraint("mentor_skill_verification_requests", "chk_msvr_status")) {
            executeSql("ALTER TABLE mentor_skill_verification_requests DROP CONSTRAINT chk_msvr_status");
        }
        addConstraintIfMissing(
                "mentor_skill_verification_requests",
                "chk_msvr_status",
                "ALTER TABLE mentor_skill_verification_requests ADD CONSTRAINT chk_msvr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PARTIAL_APPROVED', 'COMPLETED', 'REVOKED'))"
        );

        if (hasConstraint("mentor_batch_verification_requests", "chk_mbvr_status")) {
            executeSql("ALTER TABLE mentor_batch_verification_requests DROP CONSTRAINT chk_mbvr_status");
        }
        addConstraintIfMissing(
                "mentor_batch_verification_requests",
                "chk_mbvr_status",
                "ALTER TABLE mentor_batch_verification_requests ADD CONSTRAINT chk_mbvr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'PARTIAL_APPROVED', 'COMPLETED', 'REVOKED'))"
        );
    }

    private boolean verifyAllowRevokedMentorVerificationStatus() {
        return hasConstraint("mentor_skill_verification_requests", "chk_msvr_status")
                && hasConstraint("mentor_batch_verification_requests", "chk_mbvr_status");
    }

    private void patchAllowCvVerificationEvidenceType() {
        if (hasConstraint("mentor_verification_evidences", "chk_mve_type")) {
            executeSql("ALTER TABLE mentor_verification_evidences DROP CONSTRAINT chk_mve_type");
        }
        addConstraintIfMissing(
                "mentor_verification_evidences",
                "chk_mve_type",
                "ALTER TABLE mentor_verification_evidences ADD CONSTRAINT chk_mve_type CHECK (evidence_type IN ('CERTIFICATE', 'GITHUB', 'PORTFOLIO_LINK', 'WORK_EXPERIENCE', 'CV'))"
        );

        if (hasTable("student_verification_evidences")) {
            if (hasConstraint("student_verification_evidences", "chk_sve_type")) {
                executeSql("ALTER TABLE student_verification_evidences DROP CONSTRAINT chk_sve_type");
            }
            addConstraintIfMissing(
                    "student_verification_evidences",
                    "chk_sve_type",
                    "ALTER TABLE student_verification_evidences ADD CONSTRAINT chk_sve_type CHECK (evidence_type IN ('CERTIFICATE', 'GITHUB', 'PORTFOLIO_LINK', 'WORK_EXPERIENCE', 'CV'))"
            );
        }
    }

    private boolean verifyAllowCvVerificationEvidenceType() {
        return hasConstraint("mentor_verification_evidences", "chk_mve_type")
                && (!hasTable("student_verification_evidences")
                || hasConstraint("student_verification_evidences", "chk_sve_type"));
    }

    private void patchVerifiedSkillFeaturedOrder() {
        executeSql("CREATE TABLE IF NOT EXISTS user_verified_skills (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE, " +
                "skill_name VARCHAR(100) NOT NULL, " +
                "verified_by_mentor_id BIGINT NOT NULL REFERENCES users(id), " +
                "journey_id BIGINT, " +
                "booking_id BIGINT, " +
                "skill_level VARCHAR(20), " +
                "verification_note TEXT, " +
                "featured_order INTEGER, " +
                "verified_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                "CONSTRAINT uk_user_verified_skill UNIQUE (user_id, skill_name)" +
                ")");
        executeSql("ALTER TABLE user_verified_skills ADD COLUMN IF NOT EXISTS featured_order INTEGER");
        executeSql("CREATE INDEX IF NOT EXISTS idx_uvs_user_verified_at ON user_verified_skills(user_id, verified_at DESC)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_uvs_user_featured_order ON user_verified_skills(user_id, featured_order, verified_at DESC)");
    }

    private boolean verifyVerifiedSkillFeaturedOrder() {
        return hasTable("user_verified_skills") && hasColumn("user_verified_skills", "featured_order");
    }
    private void patchRoadmapEvidenceAiReview() {
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS ai_evidence_review_enabled BOOLEAN DEFAULT FALSE");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS ai_auto_pass_enabled BOOLEAN DEFAULT FALSE");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS ai_auto_pass_min_score_percent INTEGER DEFAULT 70");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS ai_auto_pass_min_confidence DOUBLE PRECISION DEFAULT 0.85");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS ai_manual_review_below_confidence DOUBLE PRECISION DEFAULT 0.75");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS ai_evidence_prompt TEXT");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS final_assignment_instructions TEXT");
        executeSql("ALTER TABLE roadmap_templates ADD COLUMN IF NOT EXISTS final_assignment_rubric TEXT");

        executeSql("""
            CREATE TABLE IF NOT EXISTS roadmap_evidence_ai_reviews (
                id BIGSERIAL PRIMARY KEY,
                node_submission_id BIGINT,
                journey_output_assessment_id BIGINT,
                journey_id BIGINT NOT NULL,
                roadmap_session_id BIGINT NOT NULL,
                node_id VARCHAR(100),
                learner_id BIGINT NOT NULL,
                attempt_number INTEGER NOT NULL,
                status VARCHAR(50) NOT NULL,
                ai_score_percent INTEGER,
                ai_confidence DOUBLE PRECISION,
                ai_feedback TEXT,
                ai_rubric_breakdown_json TEXT,
                ai_model_name VARCHAR(100),
                ai_provider VARCHAR(50),
                error_message TEXT,
                admin_decision VARCHAR(50),
                admin_review_reason TEXT,
                admin_reviewed_by BIGINT,
                admin_reviewed_at TIMESTAMPTZ,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMPTZ
            )
        """);
        executeSql("""
            DO $$
            BEGIN
                IF EXISTS (
                    SELECT 1 
                    FROM information_schema.columns 
                    WHERE table_name = 'roadmap_evidence_ai_reviews' 
                      AND column_name = 'node_id' 
                      AND data_type = 'bigint'
                ) THEN
                    ALTER TABLE roadmap_evidence_ai_reviews ALTER COLUMN node_id TYPE VARCHAR(100) USING node_id::VARCHAR(100);
                END IF;
            END $$;
        """);

        executeSql("ALTER TABLE roadmap_node_submissions ADD COLUMN IF NOT EXISTS latest_ai_review_id BIGINT");
        executeSql("ALTER TABLE roadmap_node_submissions ADD COLUMN IF NOT EXISTS latest_ai_review_status VARCHAR(50)");
        
        executeSql("ALTER TABLE journey_output_assessments ADD COLUMN IF NOT EXISTS latest_ai_review_id BIGINT");
        executeSql("ALTER TABLE journey_output_assessments ADD COLUMN IF NOT EXISTS latest_ai_review_status VARCHAR(50)");
    }

    private boolean verifyRoadmapEvidenceAiReview() {
        return hasColumn("roadmap_templates", "ai_evidence_review_enabled")
                && hasTable("roadmap_evidence_ai_reviews")
                && hasColumn("roadmap_node_submissions", "latest_ai_review_id")
                && hasColumn("journey_output_assessments", "latest_ai_review_id");
    }
}
