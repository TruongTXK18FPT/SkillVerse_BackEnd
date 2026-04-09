package com.exe.skillverse_backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private static final long SCHEMA_FIXER_LOCK_KEY = 2026031501L;

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void fixDatabaseConstraints() {
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
            applyPatch("fix-quizzes-description-oid", "Cast quizzes.description from TEXT to TEXT to resolve Hibernate oid cast failure",
                    this::patchQuizzesDescriptionOid,
                    this::verifyQuizzesDescriptionOid);

            applyPatch("add-student-learning-report-snapshots",
                    "Add missing snapshot columns to student_learning_reports for Hibernate schema validation",
                    this::patchStudentLearningReportSnapshotColumns,
                    this::verifyStudentLearningReportSnapshotColumns);

            applyPatch("create-contract-signatures-table",
                    "Create contract_signatures table for digital signature tracking",
                    this::patchContractSignaturesTable,
                    this::verifyContractSignaturesTable);

            applyPatch("add-violation-reports-reported-user-name",
                    "Add reported_user_name column to violation_reports for storing reported user's display name",
                    this::patchViolationReportsReportedUserName,
                    this::verifyViolationReportsReportedUserName);

            log.info("Schema patch infrastructure ready.");
        } finally {
            releaseAdvisoryLock();
        }
    }

    private void patchQuizzesDescriptionOid() {
        if (!hasTable("quizzes")) {
            log.debug("Table quizzes does not exist yet, skipping patch.");
            return;
        }
        // Hibernate @Lob on String maps to oid in PostgreSQL.
        // The column is already TEXT so we cast it explicitly to satisfy Hibernate's DDL.
        executeSql("ALTER TABLE quizzes ALTER COLUMN description TYPE TEXT USING description::text");
    }

    private boolean verifyQuizzesDescriptionOid() {
        if (!hasTable("quizzes") || !hasColumn("quizzes", "description")) {
            return false;
        }
        var results = jdbcTemplate.queryForList(
            "SELECT data_type FROM information_schema.columns WHERE table_name = 'quizzes' AND column_name = 'description'"
        );
        return !results.isEmpty() && "text".equalsIgnoreCase((String) results.get(0).get("data_type"));
    }

    private void patchStudentLearningReportSnapshotColumns() {
        if (!hasTable("student_learning_reports")) {
            log.debug("Table student_learning_reports does not exist yet, skipping patch.");
            return;
        }

        executeSql("""
            ALTER TABLE student_learning_reports
                ADD COLUMN IF NOT EXISTS average_progress_snapshot INTEGER,
                ADD COLUMN IF NOT EXISTS learning_trend VARCHAR(20),
                ADD COLUMN IF NOT EXISTS recommended_focus TEXT,
                ADD COLUMN IF NOT EXISTS total_study_hours_snapshot INTEGER,
                ADD COLUMN IF NOT EXISTS streak_days_snapshot INTEGER,
                ADD COLUMN IF NOT EXISTS tasks_completed_snapshot INTEGER
        """);
    }

    private boolean verifyStudentLearningReportSnapshotColumns() {
        return hasTable("student_learning_reports")
                && hasColumn("student_learning_reports", "average_progress_snapshot")
                && hasColumn("student_learning_reports", "learning_trend")
                && hasColumn("student_learning_reports", "recommended_focus")
                && hasColumn("student_learning_reports", "total_study_hours_snapshot")
                && hasColumn("student_learning_reports", "streak_days_snapshot")
                && hasColumn("student_learning_reports", "tasks_completed_snapshot");
    }

    private void patchContractSignaturesTable() {
        if (hasTable("contract_signatures")) {
            log.debug("Table contract_signatures already exists, skipping patch.");
            return;
        }
        executeSql("""
            CREATE TABLE contract_signatures (
                id BIGSERIAL PRIMARY KEY,
                contract_id BIGINT NOT NULL,
                signed_by BIGINT NOT NULL,
                signed_by_name VARCHAR(200),
                signed_by_role VARCHAR(20) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'NOT_SIGNED',
                signature_image_url VARCHAR(500),
                signed_at TIMESTAMP,
                ip_address VARCHAR(50),
                user_agent VARCHAR(500)
            )
        """);
        executeSql("CREATE INDEX IF NOT EXISTS idx_contract_signatures_contract_id ON contract_signatures(contract_id)");
    }

    private boolean verifyContractSignaturesTable() {
        if (!hasTable("contract_signatures")) return false;
        return hasColumn("contract_signatures", "id")
                && hasColumn("contract_signatures", "contract_id")
                && hasColumn("contract_signatures", "signed_by")
                && hasColumn("contract_signatures", "signed_by_role")
                && hasColumn("contract_signatures", "status");
    }

    private void patchViolationReportsReportedUserName() {
        if (!hasTable("violation_reports")) {
            log.debug("Table violation_reports does not exist yet, skipping patch.");
            return;
        }
        executeSql("ALTER TABLE violation_reports ADD COLUMN IF NOT EXISTS reported_user_name VARCHAR(100)");
    }

    private boolean verifyViolationReportsReportedUserName() {
        return hasTable("violation_reports")
                && hasColumn("violation_reports", "reported_user_name");
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
        // Session-level advisory lock: ensures only one instance runs patches at a time.
        jdbcTemplate.execute("SELECT pg_advisory_lock(" + SCHEMA_FIXER_LOCK_KEY + ")");
    }

    private void releaseAdvisoryLock() {
        jdbcTemplate.execute("SELECT pg_advisory_unlock(" + SCHEMA_FIXER_LOCK_KEY + ")");
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
