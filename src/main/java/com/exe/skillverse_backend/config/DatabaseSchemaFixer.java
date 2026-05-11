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
                "20260510_drop_unused_services_tables",
                "Drop tables related to deprecated parent_service and seminar_service",
                this::patchDropUnusedTables,
                this::verifyDropUnusedTables
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
