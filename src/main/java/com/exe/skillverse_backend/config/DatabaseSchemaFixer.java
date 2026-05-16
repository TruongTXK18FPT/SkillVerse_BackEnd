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
 * All previously applied patches have been removed. All schema changes
 * are now managed by Hibernate ddl-auto.
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
}
