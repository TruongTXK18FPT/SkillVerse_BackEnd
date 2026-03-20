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
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.util.HexFormat;
import java.util.function.BooleanSupplier;

/**
 * DatabaseSchemaFixer — runtime idempotent patches for PostgreSQL.
 * Guardrails:
 * - advisory lock (single-instance execution)
 * - patch history
 * - verify + fail-fast
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSchemaFixer {

    private static final long SCHEMA_FIXER_LOCK_KEY = 2026031501L;
    private static final int SCHEMA_FIXER_LOCK_MAX_ATTEMPTS = 60;
    private static final long SCHEMA_FIXER_LOCK_RETRY_DELAY_MS = 1000L;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    @PostConstruct
    public void fixDatabaseConstraints() {
        String productName = getDatabaseProductName();
        if (!isPostgreSql(productName)) {
            log.warn(
                    "DatabaseSchemaFixer skipped: only PostgreSQL is supported. Detected database: {}",
                    productName
            );
            return;
        }

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.setName("database-schema-fixer");

        try {
            log.info("Applying PostgreSQL schema patches with guardrails...");
            transactionTemplate.executeWithoutResult(status -> applySchemaPatchesWithLock());
        } catch (Exception ex) {
            log.error("Schema patching failed (fail-fast): {}", ex.getMessage(), ex);
            throw new IllegalStateException("Database schema patching failed", ex);
        }
    }

    private void applySchemaPatchesWithLock() {
        ensurePatchHistoryTable();
        acquireAdvisoryLock();

        applyPatch("PATCH-001-course-rejection-columns",
                "Add courses.rejection_reason and courses.rejected_at",
                this::patchCourseRejectionColumns,
                this::verifyCourseRejectionColumns);
        applyPatch("PATCH-002-course-suspension-columns",
                "Add courses.suspension_reason/suspended_at/suspended_by",
                this::patchCourseSuspensionColumns,
                this::verifyCourseSuspensionColumns);
        applyPatch("PATCH-003-notification-type-constraint",
                "Ensure notification type check includes course moderation events",
                this::patchNotificationTypeConstraint,
                this::verifyNotificationTypeConstraint);
        applyPatch("PATCH-004-assignment-criteria-passing-points",
                "Backfill NULL passing_points and enforce NOT NULL DEFAULT 0",
                this::patchAssignmentCriteriaPassingPoints,
                this::verifyAssignmentCriteriaPassingPoints);
        applyPatch("PATCH-005-assignment-submissions-is-passed",
                "Add assignment_submissions.is_passed",
                this::patchAssignmentSubmissionsIsPassed,
                this::verifyAssignmentSubmissionsIsPassed);
        applyPatch("PATCH-006-courses-status-check",
                "Ensure courses.status CHECK includes REJECTED and SUSPENDED",
                this::patchCoursesStatusCheckConstraint,
                this::verifyCoursesStatusCheckConstraint);
        applyPatch("PATCH-007-certificates-active-unique-index",
                "Ensure unique active certificate index or detect duplicates",
                this::patchActiveCertificateUniqueIndex,
                this::verifyActiveCertificateUniqueIndex);
        applyPatch("PATCH-008-certificate-snapshot-columns",
                "Ensure certificate snapshot columns exist",
                this::patchCertificateSnapshotColumns,
                this::verifyCertificateSnapshotColumns);
        applyPatch("PATCH-009-quiz-answer-snapshot-table",
                "Ensure quiz_attempt_answer_snapshots table and indexes exist",
                this::patchQuizAttemptAnswerSnapshotsTable,
                this::verifyQuizAttemptAnswerSnapshotsTable);
        applyPatch("PATCH-017-quiz-attempt-sessions-table",
                "Ensure quiz_attempt_sessions table and indexes exist",
                this::patchQuizAttemptSessionsTable,
                this::verifyQuizAttemptSessionsTable);
        applyPatch("PATCH-018-quiz-assessment-cooldown-hours-8",
                "Normalize assessment quiz cooldown_hours to 8 hours",
                this::patchQuizAssessmentCooldownHours,
                this::verifyQuizAssessmentCooldownHours);
        applyPatch("PATCH-019-recruitment-chat-context-and-notification-type",
                "Add recruitment job context columns and allow recruitment notifications",
                this::patchRecruitmentChatContextAndNotificationType,
                this::verifyRecruitmentChatContextAndNotificationType);
        applyPatch("PATCH-010-course-revisions-schema",
                "Create course_revisions schema and pointer columns on courses",
                this::patchCourseRevisionsSchema,
                this::verifyCourseRevisionsSchema);
        applyPatch("PATCH-015-course-revisions-unique-index",
                "Ensure unique (course_id, revision_number) index exists for course_revisions",
                this::patchCourseRevisionsUniqueIndex,
                this::verifyCourseRevisionsUniqueIndex);
        applyPatch("PATCH-011-course-revisions-backfill-v1",
                "Backfill revision #1 for existing courses and set active/latest pointers",
                this::patchBackfillCourseRevisionsV1,
                this::verifyBackfillCourseRevisionsV1);
        applyPatch("PATCH-019-public-course-revision-v1-backfill",
            "Ensure PUBLIC courses missing revisions get approved revision #1 and enabled revision pointers",
            this::patchPublicCourseRevisionV1Backfill,
            this::verifyPublicCourseRevisionV1Backfill);
        applyPatch("PATCH-016-course-revisions-baseline-and-hash-foundation",
                "Add source baseline/hash columns for phased revision diff enforcement",
                this::patchCourseRevisionBaselineAndHashFoundation,
                this::verifyCourseRevisionBaselineAndHashFoundation);
        applyPatch("PATCH-012-course-revisions-open-unique-index",
                "Ensure only one open revision (DRAFT/PENDING) per course",
                this::patchOpenRevisionUniqueIndex,
                this::verifyOpenRevisionUniqueIndex);
        applyPatch("PATCH-013-enrollment-revision-pin-foundation",
                "Add enrollment pinned revision and course upgrade policy foundation",
                this::patchEnrollmentRevisionPinFoundation,
                this::verifyEnrollmentRevisionPinFoundation);
        applyPatch("PATCH-014-enrollment-upgrade-policy-snapshot-manual-backfill",
                "Backfill legacy enrollment upgrade_policy_snapshot NULL to MANUAL",
                this::patchEnrollmentUpgradePolicySnapshotManualBackfill,
                this::verifyEnrollmentUpgradePolicySnapshotManualBackfill);

        log.info("All PostgreSQL schema patches applied and verified successfully.");
    }

    private void applyPatch(
            String patchKey,
            String description,
            Runnable patchLogic,
            BooleanSupplier verifier
    ) {
        if (isPatchApplied(patchKey)) {
            log.debug("Skipping already-applied patch {}", patchKey);
            return;
        }

        log.info("Applying patch {}: {}", patchKey, description);
        patchLogic.run();

        if (!verifier.getAsBoolean()) {
            throw new IllegalStateException("Verification failed for patch " + patchKey);
        }

        recordPatchSuccess(patchKey, description);
        log.info("Patch {} applied successfully.", patchKey);
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

    private boolean isPatchApplied(String patchKey) {
        Boolean exists = jdbcTemplate.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM schema_patch_history
                WHERE patch_key = ?
                  AND success = TRUE
            )
        """, Boolean.class, patchKey);
        return Boolean.TRUE.equals(exists);
    }

    private void recordPatchSuccess(String patchKey, String description) {
        jdbcTemplate.update("""
            INSERT INTO schema_patch_history (patch_key, patch_description, checksum, success)
            VALUES (?, ?, ?, TRUE)
        """, patchKey, description, sha256(patchKey + "|" + description));
    }

    private void acquireAdvisoryLock() {
        releaseCurrentSessionLockIfPresent();

        for (int attempt = 1; attempt <= SCHEMA_FIXER_LOCK_MAX_ATTEMPTS; attempt++) {
            Boolean locked = jdbcTemplate.queryForObject(
                    "SELECT pg_try_advisory_xact_lock(?)",
                    Boolean.class,
                    SCHEMA_FIXER_LOCK_KEY
            );
            if (Boolean.TRUE.equals(locked)) {
                log.info(
                        "Acquired transaction advisory lock {} (attempt {}/{})",
                        SCHEMA_FIXER_LOCK_KEY,
                        attempt,
                        SCHEMA_FIXER_LOCK_MAX_ATTEMPTS
                );
                return;
            }

            if (attempt < SCHEMA_FIXER_LOCK_MAX_ATTEMPTS) {
                log.warn(
                        "Schema advisory lock {} is busy (attempt {}/{}). Retrying in {} ms.",
                        SCHEMA_FIXER_LOCK_KEY,
                        attempt,
                        SCHEMA_FIXER_LOCK_MAX_ATTEMPTS,
                        SCHEMA_FIXER_LOCK_RETRY_DELAY_MS
                );
                sleepBeforeNextLockAttempt();
            }
        }

        throw new IllegalStateException(
                "Could not acquire schema patch advisory lock: " + SCHEMA_FIXER_LOCK_KEY
                        + " after " + SCHEMA_FIXER_LOCK_MAX_ATTEMPTS + " attempts"
        );
    }

    private void releaseCurrentSessionLockIfPresent() {
        try {
            Boolean unlocked = jdbcTemplate.queryForObject(
                    "SELECT pg_advisory_unlock(?)",
                    Boolean.class,
                    SCHEMA_FIXER_LOCK_KEY
            );
            if (Boolean.TRUE.equals(unlocked)) {
                log.warn(
                        "Released leftover session advisory lock {} in current database session before patching.",
                        SCHEMA_FIXER_LOCK_KEY
                );
            }
        } catch (Exception ex) {
            log.debug(
                    "Ignored advisory unlock pre-check for key {}: {}",
                    SCHEMA_FIXER_LOCK_KEY,
                    ex.getMessage()
            );
        }
    }

    private void sleepBeforeNextLockAttempt() {
        try {
            Thread.sleep(SCHEMA_FIXER_LOCK_RETRY_DELAY_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for schema advisory lock " + SCHEMA_FIXER_LOCK_KEY,
                    ex
            );
        }
    }

    private void patchCourseRejectionColumns() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'courses'
                      AND column_name = 'rejection_reason'
                ) THEN
                    ALTER TABLE courses ADD COLUMN rejection_reason TEXT;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'courses'
                      AND column_name = 'rejected_at'
                ) THEN
                    ALTER TABLE courses ADD COLUMN rejected_at TIMESTAMP;
                END IF;
            END $$;
        """);
    }

    private boolean verifyCourseRejectionColumns() {
        return hasColumn("courses", "rejection_reason")
                && hasColumn("courses", "rejected_at");
    }

    private void patchCourseSuspensionColumns() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'courses'
                      AND column_name = 'suspension_reason'
                ) THEN
                    ALTER TABLE courses ADD COLUMN suspension_reason TEXT;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'courses'
                      AND column_name = 'suspended_at'
                ) THEN
                    ALTER TABLE courses ADD COLUMN suspended_at TIMESTAMP;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'courses'
                      AND column_name = 'suspended_by'
                ) THEN
                    ALTER TABLE courses ADD COLUMN suspended_by BIGINT;
                END IF;
            END $$;
        """);
    }

    private boolean verifyCourseSuspensionColumns() {
        return hasColumn("courses", "suspension_reason")
                && hasColumn("courses", "suspended_at")
                && hasColumn("courses", "suspended_by");
    }

    private void patchNotificationTypeConstraint() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF EXISTS (
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'notifications'
                      AND constraint_name = 'notifications_type_check'
                ) THEN
                    ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
                END IF;

                ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
                CHECK (type IN (
                    'LIKE','COMMENT','PREMIUM_PURCHASE','WALLET_DEPOSIT','COIN_PURCHASE',
                    'WELCOME','PREMIUM_EXPIRATION','PREMIUM_CANCEL','SYSTEM','WARNING',
                    'VIOLATION_REPORT','BOOKING_CREATED','BOOKING_CONFIRMED','BOOKING_REJECTED',
                    'BOOKING_REMINDER','BOOKING_COMPLETED','BOOKING_CANCELLED','BOOKING_REFUND',
                    'PRECHAT_MESSAGE','MENTOR_REVIEW_RECEIVED','WITHDRAWAL_APPROVED','WITHDRAWAL_REJECTED',
                    'MENTOR_LEVEL_UP','MENTOR_BADGE_AWARDED','TASK_DEADLINE','TASK_OVERDUE','TASK_REVIEW',
                    'ASSIGNMENT_SUBMITTED','ASSIGNMENT_GRADED','ASSIGNMENT_LATE',
                    'COURSE_REJECTED','COURSE_SUSPENDED','COURSE_RESTORED'
                ));
            END $$;
        """);
    }

    private boolean verifyNotificationTypeConstraint() {
        String definition = getConstraintDefinition("notifications_type_check");
        return definition != null
                && definition.contains("COURSE_REJECTED")
                && definition.contains("COURSE_SUSPENDED")
                && definition.contains("COURSE_RESTORED");
    }

    private void patchAssignmentCriteriaPassingPoints() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'assignment_criteria'
                      AND column_name = 'passing_points'
                ) THEN
                    UPDATE assignment_criteria
                    SET passing_points = 0
                    WHERE passing_points IS NULL;

                    ALTER TABLE assignment_criteria
                    ALTER COLUMN passing_points SET DEFAULT 0;

                    ALTER TABLE assignment_criteria
                    ALTER COLUMN passing_points SET NOT NULL;
                END IF;
            END $$;
        """);
    }

    private boolean verifyAssignmentCriteriaPassingPoints() {
        Boolean nullable = jdbcTemplate.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'assignment_criteria'
                  AND column_name = 'passing_points'
                  AND is_nullable = 'YES'
            )
        """, Boolean.class);

        return !Boolean.TRUE.equals(nullable);
    }

    private void patchAssignmentSubmissionsIsPassed() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'assignment_submissions'
                      AND column_name = 'is_passed'
                ) THEN
                    ALTER TABLE assignment_submissions
                    ADD COLUMN is_passed BOOLEAN DEFAULT NULL;
                END IF;
            END $$;
        """);
    }

    private boolean verifyAssignmentSubmissionsIsPassed() {
        return hasColumn("assignment_submissions", "is_passed");
    }

    private void patchCoursesStatusCheckConstraint() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF EXISTS (
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'courses'
                      AND constraint_name = 'courses_status_check'
                ) THEN
                    ALTER TABLE courses DROP CONSTRAINT courses_status_check;
                END IF;

                ALTER TABLE courses ADD CONSTRAINT courses_status_check
                CHECK (status IN ('DRAFT', 'PENDING', 'PUBLIC', 'ARCHIVED', 'REJECTED', 'SUSPENDED'));
            END $$;
        """);
    }

    private boolean verifyCoursesStatusCheckConstraint() {
        String definition = getConstraintDefinition("courses_status_check");
        return definition != null
                && definition.contains("DRAFT")
                && definition.contains("PENDING")
                && definition.contains("PUBLIC")
                && definition.contains("ARCHIVED")
                && definition.contains("REJECTED")
                && definition.contains("SUSPENDED");
    }

    private void patchActiveCertificateUniqueIndex() {
        Integer duplicateGroups = countDuplicateActiveCertificateGroups();
        if (duplicateGroups != null && duplicateGroups > 0) {
            log.warn(
                    "Skipped creating active certificate unique index because {} duplicate group(s) already exist.",
                    duplicateGroups
            );
            return;
        }

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS uk_certificates_active_user_course_type
            ON certificates (user_id, course_id, type)
            WHERE revoked_at IS NULL
        """);
    }

    private boolean verifyActiveCertificateUniqueIndex() {
        Integer duplicateGroups = countDuplicateActiveCertificateGroups();
        if (duplicateGroups != null && duplicateGroups > 0) {
            return true;
        }
        return hasIndex("uk_certificates_active_user_course_type");
    }

    private Integer countDuplicateActiveCertificateGroups() {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM (
                SELECT user_id, course_id, type
                FROM certificates
                WHERE revoked_at IS NULL
                GROUP BY user_id, course_id, type
                HAVING COUNT(*) > 1
            ) duplicate_groups
        """, Integer.class);
    }
    private void patchCertificateSnapshotColumns() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'certificates'
                      AND column_name = 'recipient_name_snapshot'
                ) THEN
                    ALTER TABLE certificates ADD COLUMN recipient_name_snapshot VARCHAR(255);
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'certificates'
                      AND column_name = 'course_title_snapshot'
                ) THEN
                    ALTER TABLE certificates ADD COLUMN course_title_snapshot VARCHAR(255);
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'certificates'
                      AND column_name = 'instructor_name_snapshot'
                ) THEN
                    ALTER TABLE certificates ADD COLUMN instructor_name_snapshot VARCHAR(255);
                END IF;
            END $$;
        """);
    }

    private boolean verifyCertificateSnapshotColumns() {
        return hasColumn("certificates", "recipient_name_snapshot")
                && hasColumn("certificates", "course_title_snapshot")
                && hasColumn("certificates", "instructor_name_snapshot");
    }

    private void patchQuizAttemptAnswerSnapshotsTable() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_name = 'quiz_attempt_answer_snapshots'
                ) THEN
                    CREATE TABLE quiz_attempt_answer_snapshots (
                        id BIGSERIAL PRIMARY KEY,
                        attempt_id BIGINT NOT NULL,
                        question_id BIGINT NOT NULL,
                        question_order_index INTEGER NULL,
                        question_text TEXT NOT NULL,
                        question_type VARCHAR(20) NOT NULL,
                        submitted_answer_text TEXT NULL,
                        correct_answer_text TEXT NULL,
                        submitted_answer_json JSONB NULL,
                        options_snapshot_json JSONB NULL,
                        answered BOOLEAN NOT NULL DEFAULT FALSE,
                        is_correct BOOLEAN NOT NULL DEFAULT FALSE,
                        score_earned INTEGER NOT NULL DEFAULT 0,
                        max_score INTEGER NOT NULL DEFAULT 0,
                        CONSTRAINT fk_quiz_attempt_answer_snapshots_attempt
                            FOREIGN KEY (attempt_id) REFERENCES quiz_attempts(id) ON DELETE CASCADE
                    );
                END IF;
            END $$;
        """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_quiz_attempt_answer_snapshots_attempt_id
            ON quiz_attempt_answer_snapshots(attempt_id)
        """);

        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'quiz_attempt_answer_snapshots'
                      AND column_name = 'submitted_answer_json'
                ) THEN
                    ALTER TABLE quiz_attempt_answer_snapshots
                    ADD COLUMN submitted_answer_json JSONB NULL;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'quiz_attempt_answer_snapshots'
                      AND column_name = 'options_snapshot_json'
                ) THEN
                    ALTER TABLE quiz_attempt_answer_snapshots
                    ADD COLUMN options_snapshot_json JSONB NULL;
                END IF;
            END $$;
        """);
    }

    private boolean verifyQuizAttemptAnswerSnapshotsTable() {
        return hasTable("quiz_attempt_answer_snapshots")
                && hasColumn("quiz_attempt_answer_snapshots", "attempt_id")
                && hasColumn("quiz_attempt_answer_snapshots", "submitted_answer_json")
                && hasColumn("quiz_attempt_answer_snapshots", "options_snapshot_json")
                && hasIndex("idx_quiz_attempt_answer_snapshots_attempt_id");
    }

    private void patchQuizAttemptSessionsTable() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_name = 'quiz_attempt_sessions'
                ) THEN
                    CREATE TABLE quiz_attempt_sessions (
                        id BIGSERIAL PRIMARY KEY,
                        quiz_id BIGINT NOT NULL,
                        user_id BIGINT NOT NULL,
                        session_token VARCHAR(64) NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        expires_at TIMESTAMPTZ NOT NULL,
                        submitted_at TIMESTAMPTZ NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        CONSTRAINT fk_quiz_attempt_sessions_quiz
                            FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE,
                        CONSTRAINT uk_quiz_attempt_sessions_token
                            UNIQUE (session_token),
                        CONSTRAINT ck_quiz_attempt_sessions_status
                            CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED', 'ABANDONED'))
                    );
                END IF;
            END $$;
        """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_quiz_attempt_sessions_quiz_user_status_expires
            ON quiz_attempt_sessions(quiz_id, user_id, status, expires_at)
        """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_quiz_attempt_sessions_user_status_expires
            ON quiz_attempt_sessions(user_id, status, expires_at)
        """);
    }

    private boolean verifyQuizAttemptSessionsTable() {
        return hasTable("quiz_attempt_sessions")
                && hasColumn("quiz_attempt_sessions", "quiz_id")
                && hasColumn("quiz_attempt_sessions", "user_id")
                && hasColumn("quiz_attempt_sessions", "session_token")
                && hasColumn("quiz_attempt_sessions", "status")
                && hasColumn("quiz_attempt_sessions", "expires_at")
                && hasIndex("idx_quiz_attempt_sessions_quiz_user_status_expires")
                && hasIndex("idx_quiz_attempt_sessions_user_status_expires");
    }

    private void patchQuizAssessmentCooldownHours() {
        jdbcTemplate.execute("""
            UPDATE quizzes
            SET cooldown_hours = 8
            WHERE COALESCE(is_assessment, FALSE) = TRUE
              AND (cooldown_hours IS NULL OR cooldown_hours <= 0 OR cooldown_hours = 24)
        """);
    }

    private boolean verifyQuizAssessmentCooldownHours() {
        Integer invalidCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM quizzes
            WHERE COALESCE(is_assessment, FALSE) = TRUE
              AND (cooldown_hours IS NULL OR cooldown_hours <= 0 OR cooldown_hours = 24)
        """, Integer.class);

        return invalidCount == null || invalidCount == 0;
    }

    private void patchRecruitmentChatContextAndNotificationType() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'recruitment_sessions'
                      AND column_name = 'job_context_type'
                ) THEN
                    ALTER TABLE recruitment_sessions ADD COLUMN job_context_type VARCHAR(30);
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'recruitment_sessions'
                      AND column_name = 'job_context_id'
                ) THEN
                    ALTER TABLE recruitment_sessions ADD COLUMN job_context_id BIGINT;
                END IF;
            END $$;
        """);

        jdbcTemplate.execute("""
            UPDATE recruitment_sessions
            SET job_context_type = 'JOB_POSTING',
                job_context_id = COALESCE(job_context_id, job_posting_id)
            WHERE job_posting_id IS NOT NULL
              AND (job_context_type IS NULL OR job_context_id IS NULL)
        """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_recruitment_session_context
            ON recruitment_sessions(job_context_type, job_context_id)
        """);

        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF EXISTS (
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'notifications'
                      AND constraint_name = 'notifications_type_check'
                ) THEN
                    ALTER TABLE notifications DROP CONSTRAINT notifications_type_check;
                END IF;

                ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
                CHECK (type IN (
                    'LIKE','COMMENT','PREMIUM_PURCHASE','WALLET_DEPOSIT','COIN_PURCHASE',
                    'WELCOME','PREMIUM_EXPIRATION','PREMIUM_CANCEL','SYSTEM','WARNING',
                    'VIOLATION_REPORT','BOOKING_CREATED','BOOKING_CONFIRMED','BOOKING_REJECTED',
                    'BOOKING_REMINDER','BOOKING_COMPLETED','BOOKING_CANCELLED','BOOKING_REFUND',
                    'PRECHAT_MESSAGE','RECRUITMENT_MESSAGE','MENTOR_REVIEW_RECEIVED',
                    'WITHDRAWAL_APPROVED','WITHDRAWAL_REJECTED','MENTOR_LEVEL_UP',
                    'MENTOR_BADGE_AWARDED','TASK_DEADLINE','TASK_OVERDUE','TASK_REVIEW',
                    'ASSIGNMENT_SUBMITTED','ASSIGNMENT_GRADED','ASSIGNMENT_LATE',
                    'COURSE_REJECTED','COURSE_SUSPENDED','COURSE_RESTORED'
                ));
            END $$;
        """);
    }

    private boolean verifyRecruitmentChatContextAndNotificationType() {
        String definition = getConstraintDefinition("notifications_type_check");
        return hasColumn("recruitment_sessions", "job_context_type")
                && hasColumn("recruitment_sessions", "job_context_id")
                && hasIndex("idx_recruitment_session_context")
                && definition != null
                && definition.contains("RECRUITMENT_MESSAGE");
    }

    private void patchCourseRevisionsSchema() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = current_schema()
                      AND table_name = 'course_revisions'
                ) THEN
                    CREATE TABLE course_revisions (
                        id BIGSERIAL PRIMARY KEY,
                        course_id BIGINT NOT NULL,
                        revision_number INTEGER NOT NULL,
                        status VARCHAR(20) NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        description TEXT NULL,
                        level VARCHAR(50) NULL,
                        category VARCHAR(120) NULL,
                        short_description VARCHAR(300) NULL,
                        estimated_duration_hours INTEGER NULL,
                        language VARCHAR(40) NULL,
                        price NUMERIC(12,2) NULL,
                        currency VARCHAR(10) NULL,
                        learning_objectives_json JSONB NOT NULL DEFAULT '[]'::jsonb,
                        requirements_json JSONB NOT NULL DEFAULT '[]'::jsonb,
                        content_snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb,
                        source_course_status VARCHAR(20) NOT NULL,
                        created_by BIGINT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                        updated_at TIMESTAMPTZ NULL,
                        submitted_at TIMESTAMPTZ NULL,
                        approved_at TIMESTAMPTZ NULL,
                        rejected_at TIMESTAMPTZ NULL,
                        rejection_reason TEXT NULL,
                        archived_at TIMESTAMPTZ NULL,
                        CONSTRAINT fk_course_revisions_course
                            FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
                        CONSTRAINT uk_course_revisions_course_revision
                            UNIQUE (course_id, revision_number),
                        CONSTRAINT ck_course_revisions_status
                            CHECK (status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'ARCHIVED'))
                    );
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'courses'
                      AND column_name = 'active_revision_id'
                ) THEN
                    ALTER TABLE courses ADD COLUMN active_revision_id BIGINT;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'courses'
                      AND column_name = 'latest_revision_id'
                ) THEN
                    ALTER TABLE courses ADD COLUMN latest_revision_id BIGINT;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'courses'
                      AND column_name = 'revisioning_enabled'
                ) THEN
                    ALTER TABLE courses
                    ADD COLUMN revisioning_enabled BOOLEAN NOT NULL DEFAULT FALSE;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'courses'
                      AND constraint_name = 'fk_courses_active_revision'
                ) THEN
                    ALTER TABLE courses
                    ADD CONSTRAINT fk_courses_active_revision
                    FOREIGN KEY (active_revision_id)
                    REFERENCES course_revisions(id)
                    ON DELETE SET NULL;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'courses'
                      AND constraint_name = 'fk_courses_latest_revision'
                ) THEN
                    ALTER TABLE courses
                    ADD CONSTRAINT fk_courses_latest_revision
                    FOREIGN KEY (latest_revision_id)
                    REFERENCES course_revisions(id)
                    ON DELETE SET NULL;
                END IF;
            END $$;
        """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_course_revisions_course_id
            ON course_revisions(course_id)
        """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_course_revisions_course_status
            ON course_revisions(course_id, status)
        """);
        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS uk_course_revisions_course_revision
            ON course_revisions(course_id, revision_number)
        """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_courses_active_revision_id
            ON courses(active_revision_id)
        """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_courses_latest_revision_id
            ON courses(latest_revision_id)
        """);
    }

    private boolean verifyCourseRevisionsSchema() {
        return hasTable("course_revisions")
                && hasColumn("course_revisions", "course_id")
                && hasColumn("course_revisions", "revision_number")
                && hasColumn("courses", "active_revision_id")
                && hasColumn("courses", "latest_revision_id")
                && hasColumn("courses", "revisioning_enabled")
                && hasIndex("uk_course_revisions_course_revision")
                && hasConstraint("courses", "fk_courses_active_revision")
                && hasConstraint("courses", "fk_courses_latest_revision")
                && hasIndex("idx_course_revisions_course_id")
                && hasIndex("idx_course_revisions_course_status");
    }

    private void patchCourseRevisionsUniqueIndex() {
        Integer duplicateRevisionRows = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM (
                SELECT course_id, revision_number
                FROM course_revisions
                GROUP BY course_id, revision_number
                HAVING COUNT(*) > 1
            ) duplicated
        """, Integer.class);

        if (duplicateRevisionRows != null && duplicateRevisionRows > 0) {
            throw new IllegalStateException(
                    "Cannot enforce unique index uk_course_revisions_course_revision: found "
                            + duplicateRevisionRows + " duplicate (course_id, revision_number) groups"
            );
        }

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS uk_course_revisions_course_revision
            ON course_revisions(course_id, revision_number)
        """);
    }

    private boolean verifyCourseRevisionsUniqueIndex() {
        return hasIndex("uk_course_revisions_course_revision");
    }

    private void patchBackfillCourseRevisionsV1() {
        jdbcTemplate.execute("""
            INSERT INTO course_revisions (
                course_id,
                revision_number,
                status,
                title,
                description,
                level,
                category,
                short_description,
                estimated_duration_hours,
                language,
                price,
                currency,
                learning_objectives_json,
                requirements_json,
                content_snapshot_json,
                source_course_status,
                created_by,
                created_at,
                updated_at,
                submitted_at,
                approved_at,
                rejected_at,
                rejection_reason,
                archived_at
            )
            SELECT
                c.id AS course_id,
                1 AS revision_number,
                CASE
                    WHEN c.status IN ('PUBLIC', 'SUSPENDED', 'ARCHIVED') THEN 'APPROVED'
                    WHEN c.status = 'PENDING' THEN 'PENDING'
                    WHEN c.status = 'REJECTED' THEN 'REJECTED'
                    ELSE 'DRAFT'
                END AS status,
                c.title,
                c.description,
                c.level,
                c.category,
                c.short_description,
                c.estimated_duration_hours,
                c.language,
                c.price,
                c.currency,
                COALESCE(
                    (
                        SELECT jsonb_agg(clo.objective ORDER BY clo.objective)
                        FROM course_learning_objectives clo
                        WHERE clo.course_id = c.id
                    ),
                    '[]'::jsonb
                ) AS learning_objectives_json,
                COALESCE(
                    (
                        SELECT jsonb_agg(cr.requirement ORDER BY cr.requirement)
                        FROM course_requirements cr
                        WHERE cr.course_id = c.id
                    ),
                    '[]'::jsonb
                ) AS requirements_json,
                '{}'::jsonb AS content_snapshot_json,
                c.status AS source_course_status,
                c.author_id AS created_by,
                COALESCE(c.created_at, NOW()) AS created_at,
                c.updated_at,
                c.submitted_at,
                CASE
                    WHEN c.published_at IS NOT NULL THEN c.published_at
                    WHEN c.status IN ('PUBLIC', 'SUSPENDED', 'ARCHIVED') THEN COALESCE(c.updated_at, c.created_at, NOW())
                    ELSE NULL
                END AS approved_at,
                c.rejected_at,
                c.rejection_reason,
                CASE
                    WHEN c.status = 'ARCHIVED' THEN COALESCE(c.updated_at, NOW())
                    ELSE NULL
                END AS archived_at
            FROM courses c
            WHERE NOT EXISTS (
                SELECT 1
                FROM course_revisions existing
                WHERE existing.course_id = c.id
                  AND existing.revision_number = 1
            )
        """);

        jdbcTemplate.execute("""
            WITH latest AS (
                SELECT DISTINCT ON (course_id) course_id, id
                FROM course_revisions
                ORDER BY course_id, revision_number DESC, id DESC
            ),
            approved AS (
                SELECT DISTINCT ON (course_id) course_id, id
                FROM course_revisions
                WHERE status = 'APPROVED'
                ORDER BY course_id, revision_number DESC, id DESC
            )
            UPDATE courses c
            SET latest_revision_id = latest.id,
                active_revision_id = COALESCE(approved.id, latest.id)
            FROM latest
            LEFT JOIN approved ON approved.course_id = latest.course_id
            WHERE c.id = latest.course_id
              AND (
                  c.latest_revision_id IS DISTINCT FROM latest.id
                  OR c.active_revision_id IS DISTINCT FROM COALESCE(approved.id, latest.id)
              )
        """);

        jdbcTemplate.execute("""
            UPDATE courses
            SET revisioning_enabled = FALSE
            WHERE revisioning_enabled IS NULL
        """);
    }

    private boolean verifyBackfillCourseRevisionsV1() {
        Integer missingBaseline = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM courses c
            LEFT JOIN course_revisions r
              ON r.course_id = c.id
             AND r.revision_number = 1
            WHERE r.id IS NULL
        """, Integer.class);

        Integer missingPointers = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM courses c
            WHERE EXISTS (
                SELECT 1 FROM course_revisions r WHERE r.course_id = c.id
            )
            AND (c.active_revision_id IS NULL OR c.latest_revision_id IS NULL)
        """, Integer.class);

        return (missingBaseline == null || missingBaseline == 0)
                && (missingPointers == null || missingPointers == 0);
    }

    private void patchPublicCourseRevisionV1Backfill() {
        jdbcTemplate.execute("""
            INSERT INTO course_revisions (
                course_id,
                revision_number,
                status,
                title,
                description,
                level,
                category,
                short_description,
                estimated_duration_hours,
                language,
                price,
                currency,
                learning_objectives_json,
                requirements_json,
                content_snapshot_json,
                source_course_status,
                created_by,
                created_at,
                updated_at,
                submitted_at,
                approved_at,
                rejected_at,
                rejection_reason,
                archived_at
            )
            SELECT
                c.id,
                1,
                'APPROVED',
                c.title,
                c.description,
                c.level,
                c.category,
                c.short_description,
                c.estimated_duration_hours,
                c.language,
                c.price,
                c.currency,
                COALESCE(
                    (
                        SELECT jsonb_agg(clo.objective ORDER BY clo.objective)
                        FROM course_learning_objectives clo
                        WHERE clo.course_id = c.id
                    ),
                    '[]'::jsonb
                ),
                COALESCE(
                    (
                        SELECT jsonb_agg(cr.requirement ORDER BY cr.requirement)
                        FROM course_requirements cr
                        WHERE cr.course_id = c.id
                    ),
                    '[]'::jsonb
                ),
                '{}'::jsonb,
                c.status,
                c.author_id,
                COALESCE(c.created_at, NOW()),
                c.updated_at,
                c.submitted_at,
                COALESCE(c.published_at, c.updated_at, c.created_at, NOW()),
                c.rejected_at,
                c.rejection_reason,
                CASE
                    WHEN c.status = 'ARCHIVED' THEN COALESCE(c.updated_at, NOW())
                    ELSE NULL
                END
            FROM courses c
            WHERE c.status = 'PUBLIC'
              AND NOT EXISTS (
                  SELECT 1
                  FROM course_revisions r
                  WHERE r.course_id = c.id
                    AND r.revision_number = 1
              )
        """);

        jdbcTemplate.execute("""
            UPDATE course_revisions r
            SET status = 'APPROVED',
                approved_at = COALESCE(r.approved_at, NOW()),
                updated_at = COALESCE(r.updated_at, NOW())
            FROM courses c
            WHERE c.id = r.course_id
              AND c.status = 'PUBLIC'
              AND r.revision_number = 1
              AND r.status IS DISTINCT FROM 'APPROVED'
        """);

        jdbcTemplate.execute("""
            WITH latest AS (
                SELECT DISTINCT ON (r.course_id) r.course_id, r.id
                FROM course_revisions r
                JOIN courses c ON c.id = r.course_id
                WHERE c.status = 'PUBLIC'
                ORDER BY r.course_id, r.revision_number DESC, r.id DESC
            ),
            latest_approved AS (
                SELECT DISTINCT ON (r.course_id) r.course_id, r.id
                FROM course_revisions r
                JOIN courses c ON c.id = r.course_id
                WHERE c.status = 'PUBLIC'
                  AND r.status = 'APPROVED'
                ORDER BY r.course_id, r.revision_number DESC, r.id DESC
            )
            UPDATE courses c
            SET active_revision_id = latest_approved.id,
                latest_revision_id = latest.id,
                revisioning_enabled = TRUE
            FROM latest
            JOIN latest_approved ON latest_approved.course_id = latest.course_id
            WHERE c.id = latest.course_id
              AND (
                  c.active_revision_id IS DISTINCT FROM latest_approved.id
                  OR c.latest_revision_id IS DISTINCT FROM latest.id
                  OR c.revisioning_enabled IS DISTINCT FROM TRUE
              )
        """);
    }

    private boolean verifyPublicCourseRevisionV1Backfill() {
        Integer missing = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM courses c
            WHERE c.status = 'PUBLIC'
              AND (
                  NOT EXISTS (
                      SELECT 1
                      FROM course_revisions r
                      WHERE r.course_id = c.id
                  )
                  OR
                  c.active_revision_id IS NULL
                  OR c.latest_revision_id IS NULL
                  OR c.revisioning_enabled IS DISTINCT FROM TRUE
                  OR NOT EXISTS (
                      SELECT 1
                      FROM course_revisions r
                      WHERE r.id = c.active_revision_id
                        AND r.course_id = c.id
                        AND r.status = 'APPROVED'
                  )
                  OR NOT EXISTS (
                      SELECT 1
                      FROM course_revisions r
                      WHERE r.id = c.latest_revision_id
                        AND r.course_id = c.id
                  )
              )
        """, Integer.class);
        return missing == null || missing == 0;
    }

    private void patchCourseRevisionBaselineAndHashFoundation() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'course_revisions'
                      AND column_name = 'source_revision_id'
                ) THEN
                    ALTER TABLE course_revisions ADD COLUMN source_revision_id BIGINT;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'course_revisions'
                      AND column_name = 'baseline_snapshot_hash'
                ) THEN
                    ALTER TABLE course_revisions ADD COLUMN baseline_snapshot_hash VARCHAR(64);
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'course_revisions'
                      AND column_name = 'snapshot_hash'
                ) THEN
                    ALTER TABLE course_revisions ADD COLUMN snapshot_hash VARCHAR(64);
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'course_revisions'
                      AND column_name = 'rejected_snapshot_hash'
                ) THEN
                    ALTER TABLE course_revisions ADD COLUMN rejected_snapshot_hash VARCHAR(64);
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'course_revisions'
                      AND column_name = 'snapshot_version'
                ) THEN
                    ALTER TABLE course_revisions ADD COLUMN snapshot_version INTEGER;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'course_revisions'
                      AND constraint_name = 'fk_course_revisions_source_revision'
                ) THEN
                    ALTER TABLE course_revisions
                    ADD CONSTRAINT fk_course_revisions_source_revision
                    FOREIGN KEY (source_revision_id)
                    REFERENCES course_revisions(id)
                    ON DELETE SET NULL;
                END IF;
            END $$;
        """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_course_revisions_source_revision_id
            ON course_revisions(source_revision_id)
        """);

        jdbcTemplate.execute("""
            UPDATE course_revisions current
            SET source_revision_id = (
                SELECT prev.id
                FROM course_revisions prev
                WHERE prev.course_id = current.course_id
                  AND prev.revision_number < current.revision_number
                  AND prev.status = 'APPROVED'
                ORDER BY prev.revision_number DESC, prev.id DESC
                LIMIT 1
            )
            WHERE current.source_revision_id IS NULL
              AND current.revision_number > 1
        """);

        jdbcTemplate.execute("""
            UPDATE course_revisions
            SET snapshot_version = 1
            WHERE snapshot_version IS NULL
        """);
    }

    private boolean verifyCourseRevisionBaselineAndHashFoundation() {
        return hasColumn("course_revisions", "source_revision_id")
                && hasColumn("course_revisions", "baseline_snapshot_hash")
                && hasColumn("course_revisions", "snapshot_hash")
                && hasColumn("course_revisions", "rejected_snapshot_hash")
                && hasColumn("course_revisions", "snapshot_version")
                && hasConstraint("course_revisions", "fk_course_revisions_source_revision")
                && hasIndex("idx_course_revisions_source_revision_id");
    }

    private void patchOpenRevisionUniqueIndex() {
        Integer duplicateOpenCourses = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM (
                SELECT course_id
                FROM course_revisions
                WHERE status IN ('DRAFT', 'PENDING')
                GROUP BY course_id
                HAVING COUNT(*) > 1
            ) duplicated
        """, Integer.class);

        if (duplicateOpenCourses != null && duplicateOpenCourses > 0) {
            throw new IllegalStateException(
                    "Cannot enforce open revision unique index: found " + duplicateOpenCourses
                            + " courses with multiple DRAFT/PENDING revisions"
            );
        }

        jdbcTemplate.execute("""
            CREATE UNIQUE INDEX IF NOT EXISTS uk_course_revisions_open_per_course
            ON course_revisions(course_id)
            WHERE status IN ('DRAFT', 'PENDING')
        """);
    }

    private boolean verifyOpenRevisionUniqueIndex() {
        return hasIndex("uk_course_revisions_open_per_course");
    }

    private void patchEnrollmentRevisionPinFoundation() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'courses'
                      AND column_name = 'upgrade_policy'
                ) THEN
                    ALTER TABLE courses
                    ADD COLUMN upgrade_policy VARCHAR(32) NOT NULL DEFAULT 'MANUAL';
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'course_enrollment'
                      AND column_name = 'learning_revision_id'
                ) THEN
                    ALTER TABLE course_enrollment ADD COLUMN learning_revision_id BIGINT;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'course_enrollment'
                      AND column_name = 'upgrade_policy_snapshot'
                ) THEN
                    ALTER TABLE course_enrollment ADD COLUMN upgrade_policy_snapshot VARCHAR(32);
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'course_enrollment'
                      AND column_name = 'last_upgraded_at'
                ) THEN
                    ALTER TABLE course_enrollment ADD COLUMN last_upgraded_at TIMESTAMPTZ;
                END IF;

                IF NOT EXISTS (
                    SELECT 1 FROM information_schema.table_constraints
                    WHERE table_schema = current_schema()
                      AND table_name = 'course_enrollment'
                      AND constraint_name = 'fk_course_enrollment_learning_revision'
                ) THEN
                    ALTER TABLE course_enrollment
                    ADD CONSTRAINT fk_course_enrollment_learning_revision
                    FOREIGN KEY (learning_revision_id)
                    REFERENCES course_revisions(id)
                    ON DELETE SET NULL;
                END IF;
            END $$;
        """);

        jdbcTemplate.execute("""
            UPDATE course_enrollment ce
            SET learning_revision_id = COALESCE(ce.learning_revision_id, c.active_revision_id),
                upgrade_policy_snapshot = COALESCE(ce.upgrade_policy_snapshot, c.upgrade_policy)
            FROM courses c
            WHERE ce.course_id = c.id
              AND (ce.learning_revision_id IS NULL OR ce.upgrade_policy_snapshot IS NULL)
        """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_course_enrollment_learning_revision_id
            ON course_enrollment(learning_revision_id)
        """);

        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_courses_upgrade_policy
            ON courses(upgrade_policy)
        """);
    }

    private boolean verifyEnrollmentRevisionPinFoundation() {
        return hasColumn("courses", "upgrade_policy")
                && hasColumn("course_enrollment", "learning_revision_id")
                && hasColumn("course_enrollment", "upgrade_policy_snapshot")
                && hasColumn("course_enrollment", "last_upgraded_at")
                && hasConstraint("course_enrollment", "fk_course_enrollment_learning_revision")
                && hasIndex("idx_course_enrollment_learning_revision_id")
                && hasIndex("idx_courses_upgrade_policy");
    }

    private void patchEnrollmentUpgradePolicySnapshotManualBackfill() {
        jdbcTemplate.execute("""
            UPDATE course_enrollment
            SET upgrade_policy_snapshot = 'MANUAL'
            WHERE upgrade_policy_snapshot IS NULL
        """);
    }

    private boolean verifyEnrollmentUpgradePolicySnapshotManualBackfill() {
        if (!hasColumn("course_enrollment", "upgrade_policy_snapshot")) {
            return false;
        }

        Integer nullCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM course_enrollment
            WHERE upgrade_policy_snapshot IS NULL
        """, Integer.class);

        return nullCount == null || nullCount == 0;
    }

    private boolean hasTable(String tableName) {
        Boolean exists = jdbcTemplate.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.tables
                WHERE table_schema = current_schema()
                  AND table_name = ?
            )
        """, Boolean.class, tableName);
        return Boolean.TRUE.equals(exists);
    }

    private boolean hasColumn(String tableName, String columnName) {
        Boolean exists = jdbcTemplate.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND column_name = ?
            )
        """, Boolean.class, tableName, columnName);
        return Boolean.TRUE.equals(exists);
    }

    private boolean hasConstraint(String tableName, String constraintName) {
        Boolean exists = jdbcTemplate.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM information_schema.table_constraints
                WHERE table_schema = current_schema()
                  AND table_name = ?
                  AND constraint_name = ?
            )
        """, Boolean.class, tableName, constraintName);
        return Boolean.TRUE.equals(exists);
    }

    private boolean hasIndex(String indexName) {
        Boolean exists = jdbcTemplate.queryForObject("""
            SELECT EXISTS (
                SELECT 1
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND indexname = ?
            )
        """, Boolean.class, indexName);
        return Boolean.TRUE.equals(exists);
    }

    private String getConstraintDefinition(String constraintName) {
        return jdbcTemplate.query("""
            SELECT pg_get_constraintdef(c.oid)
            FROM pg_constraint c
            JOIN pg_namespace n ON n.oid = c.connamespace
            WHERE c.conname = ?
              AND n.nspname = current_schema()
            LIMIT 1
        """, ps -> ps.setString(1, constraintName), rs -> rs.next() ? rs.getString(1) : null);
    }

    private String getDatabaseProductName() {
        try {
            return jdbcTemplate.execute((Connection connection) ->
                    connection.getMetaData().getDatabaseProductName());
        } catch (Exception ex) {
            log.warn("Cannot detect database product name: {}", ex.getMessage());
            return "unknown";
        }
    }

    private boolean isPostgreSql(String databaseProductName) {
        return databaseProductName != null
                && databaseProductName.toLowerCase().contains("postgresql");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is not available", ex);
        }
    }

    private void ensureCourseRevisioningColumns() {
        jdbcTemplate.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'courses' AND column_name = 'revisioning_enabled') THEN
                    ALTER TABLE courses ADD COLUMN revisioning_enabled BOOLEAN NOT NULL DEFAULT FALSE;
                END IF;
                IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'courses' AND column_name = 'upgrade_policy') THEN
                    ALTER TABLE courses ADD COLUMN upgrade_policy VARCHAR(32) NOT NULL DEFAULT 'MANUAL';
                END IF;
            END $$;
        """);
    }
}
