package com.exe.skillverse_backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
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

    private static final long SCHEMA_FIXER_LOCK_KEY = 2026031501L;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    @PostConstruct
    public void fixDatabaseConstraints() {
        String productName = getDatabaseProductName();
        if (!isPostgreSql(productName)) {
            log.warn("DatabaseSchemaFixer skipped: only PostgreSQL is supported. Detected: {}", productName);
            return;
        }

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.setName("database-schema-fixer");

        try {
            log.info("Initializing PostgreSQL schema patch system...");
            transactionTemplate.executeWithoutResult(status -> applySchemaPatchesWithLock());
        } catch (Exception ex) {
            log.error("Schema patching failed: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Database schema patching failed", ex);
        }
    }

    // ─── Schema Patch Executor ─────────────────────────────────────────────────

    private void applySchemaPatchesWithLock() {
        ensurePatchHistoryTable();
        acquireAdvisoryLock();
        // All schema changes are now managed by Hibernate ddl-auto.
        // This method only tracks that the fixer's infrastructure is in place.
        log.info("Schema patch infrastructure ready.");
    }

    // ─── Infrastructure ─────────────────────────────────────────────────────

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
        // PostgreSQL advisory lock: ensures only one instance runs patches at a time
        // Auto-released when session disconnects
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + SCHEMA_FIXER_LOCK_KEY + ")");
    }

    // ─── Patch Helpers ────────────────────────────────────────────────────────

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

    // ─── Schema Utilities ────────────────────────────────────────────────────

    /**
     * Check if a column exists in a table.
     */
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

    /**
     * Check if a table exists.
     */
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

    /**
     * Check if an index exists.
     */
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

    /**
     * Check if a foreign key constraint exists.
     */
    protected boolean hasForeignKey(String tableName, String constraintName) {
        try {
            var results = jdbcTemplate.queryForList("""
                SELECT 1 FROM information_schema.table_constraints
                WHERE constraint_type = 'FOREIGN KEY'
                  AND table_name = ?
                  AND constraint_name = ?
            """, tableName, constraintName);
            return !results.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Execute raw SQL with error handling.
     */
    protected void executeSql(String sql) {
        jdbcTemplate.execute(sql);
    }
}
