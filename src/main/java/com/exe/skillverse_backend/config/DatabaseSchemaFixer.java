package com.exe.skillverse_backend.config;

import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.stream.Collectors;

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
            // ─── quizzes.description oid → TEXT ─────────────────────────────────
            applyPatch("fix-quizzes-description-oid",
                    "Cast quizzes.description from TEXT to TEXT to resolve Hibernate oid cast failure",
                    this::patchQuizzesDescriptionOid,
                    this::verifyQuizzesDescriptionOid);

            // ─── student_learning_reports snapshot columns ──────────────────────
            applyPatch("add-student-learning-report-snapshots",
                    "Add missing snapshot columns to student_learning_reports for Hibernate schema validation",
                    this::patchStudentLearningReportSnapshotColumns,
                    this::verifyStudentLearningReportSnapshotColumns);

            applyPatch("add-course-enrollment-learning-columns",
                    "Add missing learning-tracking columns to course_enrollment for Hibernate schema validation",
                    this::patchCourseEnrollmentLearningColumns,
                    this::verifyCourseEnrollmentLearningColumns);

                applyPatch("create-module-prerequisites-table",
                    "Create module_prerequisites table for Hibernate schema validation",
                    this::patchModulePrerequisitesTable,
                    this::verifyModulePrerequisitesTable);

            applyPatch("create-contract-signatures-table",
                    "Create contract_signatures table for digital signature tracking",
                    this::patchContractSignaturesTable,
                    this::verifyContractSignaturesTable);

            applyPatch("add-violation-reports-reported-user-name",
                    "Add reported_user_name column to violation_reports for storing reported user's display name",
                    this::patchViolationReportsReportedUserName,
                    this::verifyViolationReportsReportedUserName);

            applyPatch("create-job-contracts-table",
                    "Create job_contracts table for managing employment contracts with digital signatures",
                    this::patchJobContractsTable,
                    this::verifyJobContractsTable);

            applyPatch("add-job-postings-posting-fee-charged",
                    "Add posting_fee_charged column to job_postings for Hibernate schema validation",
                    this::patchJobPostingsPostingFeeCharged,
                    this::verifyJobPostingsPostingFeeCharged);

            applyPatch("sync-notifications-type-check-constraint",
                    "Sync notifications.type check constraint with NotificationType enum values",
                    this::patchNotificationsTypeConstraint,
                    this::verifyNotificationsTypeConstraint);

            // ─── Course-related entity oid → TEXT patches ────────────────────────
            // Hibernate @Lob on String maps to oid in PostgreSQL.
            // These patches convert oid columns to TEXT so Hibernate reads them correctly.
            applyPatch("fix-courses-oid",
                    "Convert course-related oid columns to TEXT",
                    this::patchCoursesOid, this::verifyCoursesOid);

            applyPatch("fix-coding-exercises-oid",
                    "Convert coding_exercises oid columns to TEXT",
                    this::patchCodingExercisesOid, this::verifyCodingExercisesOid);

            applyPatch("fix-coding-test-cases-oid",
                    "Convert coding_test_cases oid columns to TEXT",
                    this::patchCodingTestCasesOid, this::verifyCodingTestCasesOid);

            applyPatch("fix-coding-submissions-oid",
                    "Convert coding_submissions oid columns to TEXT",
                    this::patchCodingSubmissionsOid, this::verifyCodingSubmissionsOid);

            applyPatch("fix-lessons-oid",
                    "Convert lessons oid columns to TEXT",
                    this::patchLessonsOid, this::verifyLessonsOid);

            applyPatch("fix-quiz-questions-oid",
                    "Convert quiz_questions oid columns to TEXT",
                    this::patchQuizQuestionsOid, this::verifyQuizQuestionsOid);

            applyPatch("fix-quiz-options-oid",
                    "Convert quiz_options oid columns to TEXT",
                    this::patchQuizOptionsOid, this::verifyQuizOptionsOid);

            applyPatch("fix-assignments-oid",
                    "Convert assignments oid columns to TEXT",
                    this::patchAssignmentsOid, this::verifyAssignmentsOid);

            applyPatch("fix-assignment-submissions-oid",
                    "Convert assignment_submissions oid columns to TEXT",
                    this::patchAssignmentSubmissionsOid, this::verifyAssignmentSubmissionsOid);

            applyPatch("fix-certificates-oid",
                    "Convert certificates oid columns to TEXT",
                    this::patchCertificatesOid, this::verifyCertificatesOid);

            applyPatch("fix-quiz-attempt-answer-snapshots-oid",
                    "Convert quiz_attempt_answer_snapshots oid columns to TEXT",
                    this::patchQuizAttemptAnswerSnapshotsOid, this::verifyQuizAttemptAnswerSnapshotsOid);

            // ─── AI Grading fields — assignments table ────────────────────────────
            applyPatch("add-assignments-ai-grading-fields",
                    "Add AI grading columns to assignments table",
                    this::patchAssignmentsAiGradingFields,
                    this::verifyAssignmentsAiGradingFields);

            // ─── AI Grading fields — assignment_submissions table ───────────────
            applyPatch("add-assignment-submissions-ai-grading-fields",
                    "Add AI grading columns to assignment_submissions table",
                    this::patchAssignmentSubmissionsAiGradingFields,
                    this::verifyAssignmentSubmissionsAiGradingFields);

            applyPatch("create-contract-signatures-table",
                    "Create contract_signatures table for digital signature tracking",
                    this::patchContractSignaturesTable,
                    this::verifyContractSignaturesTable);

            applyPatch("add-violation-reports-reported-user-name",
                    "Add reported_user_name column to violation_reports for storing reported user's display name",
                    this::patchViolationReportsReportedUserName,
                    this::verifyViolationReportsReportedUserName);

            applyPatch("create-job-contracts-table",
                    "Create job_contracts table for managing employment contracts with digital signatures",
                    this::patchJobContractsTable,
                    this::verifyJobContractsTable);

            applyPatch("add-job-postings-posting-fee-charged",
                    "Add posting_fee_charged column to job_postings for Hibernate schema validation",
                    this::patchJobPostingsPostingFeeCharged,
                    this::verifyJobPostingsPostingFeeCharged);

            applyPatch("sync-notifications-type-check-constraint",
                    "Sync notifications.type check constraint with NotificationType enum values",
                    this::patchNotificationsTypeConstraint,
                    this::verifyNotificationsTypeConstraint);

            log.info("Schema patch infrastructure ready.");
        } finally {
            releaseAdvisoryLock();
        }
    }

    // ─── quizzes.description oid ────────────────────────────────────────────────

    private void patchQuizzesDescriptionOid() {
        if (!hasTable("quizzes")) {
            log.debug("Table quizzes does not exist yet, skipping patch.");
            return;
        }
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

    // ─── student_learning_reports snapshot columns ─────────────────────────────

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

    // ─── course_enrollment learning tracking columns ─────────────────────────

    private void patchCourseEnrollmentLearningColumns() {
        if (!hasTable("course_enrollment")) {
            log.debug("Table course_enrollment does not exist yet, skipping patch.");
            return;
        }

        executeSql("""
            ALTER TABLE course_enrollment
                ADD COLUMN IF NOT EXISTS entitlement_source VARCHAR(20),
                ADD COLUMN IF NOT EXISTS entitlement_ref VARCHAR(64),
                ADD COLUMN IF NOT EXISTS learning_revision_id BIGINT,
                ADD COLUMN IF NOT EXISTS upgrade_policy_snapshot VARCHAR(32),
                ADD COLUMN IF NOT EXISTS last_upgraded_at TIMESTAMPTZ,
                ADD COLUMN IF NOT EXISTS completed_at TIMESTAMPTZ
        """);

        executeSql("""
            UPDATE course_enrollment
            SET entitlement_source = 'PURCHASE'
            WHERE entitlement_source IS NULL
        """);

        executeSql("""
            ALTER TABLE course_enrollment
                ALTER COLUMN entitlement_source SET DEFAULT 'PURCHASE'
        """);

        executeSql("""
            ALTER TABLE course_enrollment
                ALTER COLUMN entitlement_source SET NOT NULL
        """);
    }

    private boolean verifyCourseEnrollmentLearningColumns() {
        if (!hasTable("course_enrollment")) {
            return true;
        }

        return hasColumn("course_enrollment", "entitlement_source")
                && hasColumn("course_enrollment", "entitlement_ref")
                && hasColumn("course_enrollment", "learning_revision_id")
                && hasColumn("course_enrollment", "upgrade_policy_snapshot")
                && hasColumn("course_enrollment", "last_upgraded_at")
                && hasColumn("course_enrollment", "completed_at");
    }

    // ─── module_prerequisites table ─────────────────────────────────────────

    private void patchModulePrerequisitesTable() {
        if (hasTable("module_prerequisites")) {
            return;
        }

        executeSql("""
            CREATE TABLE IF NOT EXISTS module_prerequisites (
                module_id BIGINT NOT NULL,
                prerequisite_module_id BIGINT
            )
        """);
    }

    private boolean verifyModulePrerequisitesTable() {
        if (!hasTable("module_prerequisites")) {
            return false;
        }
        return hasColumn("module_prerequisites", "module_id")
                && hasColumn("module_prerequisites", "prerequisite_module_id");
    }

    // ─── Course-related oid → TEXT patches ────────────────────────────────────

    private void patchCoursesOid() {
        if (!hasTable("courses")) return;
        executeSql("ALTER TABLE courses ALTER COLUMN description TYPE TEXT USING description::text");
        if (hasColumn("courses", "rejection_reason"))
            executeSql("ALTER TABLE courses ALTER COLUMN rejection_reason TYPE TEXT USING rejection_reason::text");
        if (hasColumn("courses", "suspension_reason"))
            executeSql("ALTER TABLE courses ALTER COLUMN suspension_reason TYPE TEXT USING suspension_reason::text");
    }

    private boolean verifyCoursesOid() {
        if (!hasTable("courses")) return true;
        var cols = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'courses' AND column_name IN ('description','rejection_reason','suspension_reason')"
        );
        if (cols.isEmpty()) return true;
        for (var row : cols) {
            if (!"text".equalsIgnoreCase((String) row.get("data_type"))) return false;
        }
        return true;
    }

    private void patchCodingExercisesOid() {
        if (!hasTable("coding_exercises")) return;
        executeSql("ALTER TABLE coding_exercises ALTER COLUMN prompt TYPE TEXT USING prompt::text");
        if (hasColumn("coding_exercises", "starter_code"))
            executeSql("ALTER TABLE coding_exercises ALTER COLUMN starter_code TYPE TEXT USING starter_code::text");
    }

    private boolean verifyCodingExercisesOid() {
        if (!hasTable("coding_exercises")) return true;
        var cols = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'coding_exercises' AND column_name IN ('prompt','starter_code')"
        );
        if (cols.isEmpty()) return true;
        for (var row : cols) {
            if (!"text".equalsIgnoreCase((String) row.get("data_type"))) return false;
        }
        return true;
    }

    private void patchCodingTestCasesOid() {
        if (!hasTable("coding_test_cases")) return;
        executeSql("ALTER TABLE coding_test_cases ALTER COLUMN input TYPE TEXT USING input::text");
        executeSql("ALTER TABLE coding_test_cases ALTER COLUMN expected_output TYPE TEXT USING expected_output::text");
    }

    private boolean verifyCodingTestCasesOid() {
        if (!hasTable("coding_test_cases")) return true;
        var cols = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'coding_test_cases' AND column_name IN ('input','expected_output')"
        );
        if (cols.isEmpty()) return true;
        for (var row : cols) {
            if (!"text".equalsIgnoreCase((String) row.get("data_type"))) return false;
        }
        return true;
    }

    private void patchCodingSubmissionsOid() {
        if (!hasTable("coding_submissions")) return;
        executeSql("ALTER TABLE coding_submissions ALTER COLUMN submitted_code TYPE TEXT USING submitted_code::text");
        if (hasColumn("coding_submissions", "feedback"))
            executeSql("ALTER TABLE coding_submissions ALTER COLUMN feedback TYPE TEXT USING feedback::text");
    }

    private boolean verifyCodingSubmissionsOid() {
        if (!hasTable("coding_submissions")) return true;
        var cols = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'coding_submissions' AND column_name IN ('submitted_code','feedback')"
        );
        if (cols.isEmpty()) return true;
        for (var row : cols) {
            if (!"text".equalsIgnoreCase((String) row.get("data_type"))) return false;
        }
        return true;
    }

    private void patchLessonsOid() {
        if (!hasTable("lessons")) return;
        if (hasColumn("lessons", "content_text"))
            executeSql("ALTER TABLE lessons ALTER COLUMN content_text TYPE TEXT USING content_text::text");
    }

    private boolean verifyLessonsOid() {
        if (!hasTable("lessons")) return true;
        var cols = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'lessons' AND column_name = 'content_text'"
        );
        if (cols.isEmpty()) return true;
        return "text".equalsIgnoreCase((String) cols.get(0).get("data_type"));
    }

    private void patchQuizQuestionsOid() {
        if (!hasTable("quiz_questions")) return;
        executeSql("ALTER TABLE quiz_questions ALTER COLUMN question_text TYPE TEXT USING question_text::text");
    }

    private boolean verifyQuizQuestionsOid() {
        if (!hasTable("quiz_questions")) return true;
        var cols = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'quiz_questions' AND column_name = 'question_text'"
        );
        if (cols.isEmpty()) return true;
        return "text".equalsIgnoreCase((String) cols.get(0).get("data_type"));
    }

    private void patchQuizOptionsOid() {
        if (!hasTable("quiz_options")) return;
        executeSql("ALTER TABLE quiz_options ALTER COLUMN option_text TYPE TEXT USING option_text::text");
    }

    private boolean verifyQuizOptionsOid() {
        if (!hasTable("quiz_options")) return true;
        var cols = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'quiz_options' AND column_name = 'option_text'"
        );
        if (cols.isEmpty()) return true;
        return "text".equalsIgnoreCase((String) cols.get(0).get("data_type"));
    }

    private void patchAssignmentsOid() {
        if (!hasTable("assignments")) return;
        executeSql("ALTER TABLE assignments ALTER COLUMN description TYPE TEXT USING description::text");
        if (hasColumn("assignments", "grading_criteria"))
            executeSql("ALTER TABLE assignments ALTER COLUMN grading_criteria TYPE TEXT USING grading_criteria::text");
        if (hasColumn("assignments", "learning_outcome"))
            executeSql("ALTER TABLE assignments ALTER COLUMN learning_outcome TYPE TEXT USING learning_outcome::text");
    }

    private boolean verifyAssignmentsOid() {
        if (!hasTable("assignments")) return true;
        var cols = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'assignments' AND column_name IN ('description','grading_criteria','learning_outcome')"
        );
        if (cols.isEmpty()) return true;
        for (var row : cols) {
            if (!"text".equalsIgnoreCase((String) row.get("data_type"))) return false;
        }
        return true;
    }

    private void patchAssignmentSubmissionsOid() {
        if (!hasTable("assignment_submissions")) return;
        executeSql("ALTER TABLE assignment_submissions ALTER COLUMN submission_text TYPE TEXT USING submission_text::text");
        if (hasColumn("assignment_submissions", "feedback"))
            executeSql("ALTER TABLE assignment_submissions ALTER COLUMN feedback TYPE TEXT USING feedback::text");
    }

    private boolean verifyAssignmentSubmissionsOid() {
        if (!hasTable("assignment_submissions")) return true;
        var cols = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'assignment_submissions' AND column_name IN ('submission_text','feedback')"
        );
        if (cols.isEmpty()) return true;
        for (var row : cols) {
            if (!"text".equalsIgnoreCase((String) row.get("data_type"))) return false;
        }
        return true;
    }

    private void patchCertificatesOid() {
        if (!hasTable("certificates")) return;
        if (hasColumn("certificates", "criteria"))
            executeSql("ALTER TABLE certificates ALTER COLUMN criteria TYPE TEXT USING criteria::text");
    }

    private boolean verifyCertificatesOid() {
        if (!hasTable("certificates")) return true;
        var cols = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'certificates' AND column_name = 'criteria'"
        );
        if (cols.isEmpty()) return true;
        return "text".equalsIgnoreCase((String) cols.get(0).get("data_type"));
    }

    private void patchQuizAttemptAnswerSnapshotsOid() {
        if (!hasTable("quiz_attempt_answer_snapshots")) return;
        executeSql("ALTER TABLE quiz_attempt_answer_snapshots ALTER COLUMN question_text TYPE TEXT USING question_text::text");
        if (hasColumn("quiz_attempt_answer_snapshots", "submitted_answer_text"))
            executeSql("ALTER TABLE quiz_attempt_answer_snapshots ALTER COLUMN submitted_answer_text TYPE TEXT USING submitted_answer_text::text");
        if (hasColumn("quiz_attempt_answer_snapshots", "correct_answer_text"))
            executeSql("ALTER TABLE quiz_attempt_answer_snapshots ALTER COLUMN correct_answer_text TYPE TEXT USING correct_answer_text::text");
    }

    private boolean verifyQuizAttemptAnswerSnapshotsOid() {
        if (!hasTable("quiz_attempt_answer_snapshots")) return true;
        var cols = jdbcTemplate.queryForList(
            "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'quiz_attempt_answer_snapshots' AND column_name IN ('question_text','submitted_answer_text','correct_answer_text')"
        );
        if (cols.isEmpty()) return true;
        for (var row : cols) {
            if (!"text".equalsIgnoreCase((String) row.get("data_type"))) return false;
        }
        return true;
    }

    // ─── AI Grading fields — assignments ─────────────────────────────────────

    private void patchAssignmentsAiGradingFields() {
        if (!hasTable("assignments")) return;

        if (!hasColumn("assignments", "ai_grading_enabled")) {
            executeSql("ALTER TABLE assignments ADD COLUMN ai_grading_enabled BOOLEAN NOT NULL DEFAULT FALSE");
        }
        if (!hasColumn("assignments", "ai_grading_prompt")) {
            executeSql("ALTER TABLE assignments ADD COLUMN ai_grading_prompt TEXT");
        }
        if (!hasColumn("assignments", "grading_style")) {
            executeSql("ALTER TABLE assignments ADD COLUMN grading_style VARCHAR(20) DEFAULT 'STANDARD'");
        }
        if (!hasColumn("assignments", "trust_ai_enabled")) {
            executeSql("ALTER TABLE assignments ADD COLUMN trust_ai_enabled BOOLEAN NOT NULL DEFAULT FALSE");
        }
    }

    private boolean verifyAssignmentsAiGradingFields() {
        if (!hasTable("assignments")) return true;
        return hasColumn("assignments", "ai_grading_enabled")
                && hasColumn("assignments", "ai_grading_prompt")
                && hasColumn("assignments", "grading_style")
                && hasColumn("assignments", "trust_ai_enabled");
    }

    // ─── AI Grading fields — assignment_submissions ──────────────────────────

    private void patchAssignmentSubmissionsAiGradingFields() {
        if (!hasTable("assignment_submissions")) return;

        if (!hasColumn("assignment_submissions", "is_ai_graded")) {
            executeSql("ALTER TABLE assignment_submissions ADD COLUMN is_ai_graded BOOLEAN NOT NULL DEFAULT FALSE");
        }
        if (!hasColumn("assignment_submissions", "ai_graded_at")) {
            executeSql("ALTER TABLE assignment_submissions ADD COLUMN ai_graded_at TIMESTAMPTZ");
        }
        if (!hasColumn("assignment_submissions", "ai_score")) {
            executeSql("ALTER TABLE assignment_submissions ADD COLUMN ai_score NUMERIC(10, 2)");
        }
        if (!hasColumn("assignment_submissions", "ai_feedback")) {
            executeSql("ALTER TABLE assignment_submissions ADD COLUMN ai_feedback TEXT");
        }
        if (!hasColumn("assignment_submissions", "ai_confidence")) {
            executeSql("ALTER TABLE assignment_submissions ADD COLUMN ai_confidence DOUBLE PRECISION");
        }
        if (!hasColumn("assignment_submissions", "mentor_confirmed")) {
            executeSql("ALTER TABLE assignment_submissions ADD COLUMN mentor_confirmed BOOLEAN");
        }
        if (!hasColumn("assignment_submissions", "ai_grade_attempt_count")) {
            executeSql("ALTER TABLE assignment_submissions ADD COLUMN ai_grade_attempt_count INTEGER NOT NULL DEFAULT 0");
        }
        if (!hasColumn("assignment_submissions", "dispute_flag")) {
            executeSql("ALTER TABLE assignment_submissions ADD COLUMN dispute_flag BOOLEAN NOT NULL DEFAULT FALSE");
        }
        if (!hasColumn("assignment_submissions", "dispute_at")) {
            executeSql("ALTER TABLE assignment_submissions ADD COLUMN dispute_at TIMESTAMPTZ");
        }
        if (!hasColumn("assignment_submissions", "dispute_reason")) {
            executeSql("ALTER TABLE assignment_submissions ADD COLUMN dispute_reason TEXT");
        }
        if (!hasColumn("assignment_submissions", "grading_mode")) {
            executeSql("ALTER TABLE assignment_submissions ADD COLUMN grading_mode VARCHAR(10) DEFAULT 'AI'");
        }
    }

    private boolean verifyAssignmentSubmissionsAiGradingFields() {
        if (!hasTable("assignment_submissions")) return true;
        return hasColumn("assignment_submissions", "is_ai_graded")
                && hasColumn("assignment_submissions", "ai_graded_at")
                && hasColumn("assignment_submissions", "ai_score")
                && hasColumn("assignment_submissions", "ai_feedback")
                && hasColumn("assignment_submissions", "ai_confidence")
                && hasColumn("assignment_submissions", "mentor_confirmed")
                && hasColumn("assignment_submissions", "ai_grade_attempt_count")
                && hasColumn("assignment_submissions", "dispute_flag")
                && hasColumn("assignment_submissions", "dispute_at")
                && hasColumn("assignment_submissions", "dispute_reason")
                && hasColumn("assignment_submissions", "grading_mode");
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

    private void patchJobContractsTable() {
        if (hasTable("job_contracts")) {
            log.debug("Table job_contracts already exists, skipping patch.");
            return;
        }
        executeSql("""
            CREATE TABLE job_contracts (
                id BIGSERIAL PRIMARY KEY,
                application_id BIGINT NOT NULL UNIQUE,
                status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
                contract_type VARCHAR(20) NOT NULL,
                contract_number VARCHAR(50),
                job_title VARCHAR(300),
                working_location VARCHAR(500),
                candidate_position VARCHAR(200),
                job_description TEXT,
                probation_months INTEGER,
                probation_salary NUMERIC(15, 2),
                probation_salary_text VARCHAR(500),
                probation_evaluation_criteria TEXT,
                probation_objectives TEXT,
                salary NUMERIC(15, 2),
                salary_text VARCHAR(500),
                salary_payment_date INTEGER,
                payment_method VARCHAR(100),
                meal_allowance NUMERIC(15, 2),
                transport_allowance NUMERIC(15, 2),
                housing_allowance NUMERIC(15, 2),
                other_allowances TEXT,
                bonus_policy TEXT,
                working_hours_per_day INTEGER,
                working_hours_per_week INTEGER,
                working_schedule VARCHAR(300),
                remote_work_policy TEXT,
                annual_leave_days INTEGER,
                leave_policy TEXT,
                insurance_policy TEXT,
                health_checkup_annual BOOLEAN,
                training_policy TEXT,
                other_benefits TEXT,
                legal_text TEXT,
                confidentiality_clause TEXT,
                ip_clause TEXT,
                non_compete_clause TEXT,
                non_compete_duration_months INTEGER,
                termination_notice_days INTEGER,
                termination_clause TEXT,
                employer_id BIGINT NOT NULL,
                employer_name VARCHAR(200),
                employer_company_name VARCHAR(300),
                employer_address VARCHAR(500),
                employer_tax_id VARCHAR(50),
                employer_email VARCHAR(200),
                candidate_id BIGINT NOT NULL,
                candidate_name VARCHAR(200),
                candidate_email VARCHAR(200),
                candidate_phone VARCHAR(30),
                candidate_address VARCHAR(500),
                candidate_date_of_birth DATE,
                candidate_id_card_number VARCHAR(50),
                candidate_id_card_place VARCHAR(200),
                start_date DATE NOT NULL,
                end_date DATE,
                signed_pdf_url VARCHAR(500),
                signed_at TIMESTAMP,
                version BIGINT DEFAULT 0,
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP DEFAULT NOW()
            )
        """);
        executeSql("CREATE INDEX IF NOT EXISTS idx_job_contracts_application_id ON job_contracts(application_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_job_contracts_employer_id ON job_contracts(employer_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_job_contracts_candidate_id ON job_contracts(candidate_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_job_contracts_status ON job_contracts(status)");
    }

    private boolean verifyJobContractsTable() {
        if (!hasTable("job_contracts")) return false;
        return hasColumn("job_contracts", "id")
                && hasColumn("job_contracts", "application_id")
                && hasColumn("job_contracts", "status")
                && hasColumn("job_contracts", "contract_type")
                && hasColumn("job_contracts", "employer_id")
                && hasColumn("job_contracts", "candidate_id")
                && hasColumn("job_contracts", "start_date");
    }

    // ─── Infrastructure ─────────────────────────────────────────────────────

    private void patchJobPostingsPostingFeeCharged() {
        if (!hasTable("job_postings")) {
            log.debug("Table job_postings does not exist yet, skipping patch.");
            return;
        }

        executeSql("""
            ALTER TABLE job_postings
                ADD COLUMN IF NOT EXISTS posting_fee_charged BOOLEAN DEFAULT FALSE
        """);
        executeSql("""
            UPDATE job_postings
            SET posting_fee_charged = FALSE
            WHERE posting_fee_charged IS NULL
        """);
        executeSql("""
            ALTER TABLE job_postings
                ALTER COLUMN posting_fee_charged SET DEFAULT FALSE
        """);
    }

    private boolean verifyJobPostingsPostingFeeCharged() {
        return hasTable("job_postings")
                && hasColumn("job_postings", "posting_fee_charged");
    }

    private void patchNotificationsTypeConstraint() {
        if (!hasTable("notifications")) {
            log.debug("Table notifications does not exist yet, skipping patch.");
            return;
        }

        String allowedTypes = Arrays.stream(NotificationType.values())
                .map(NotificationType::name)
                .map(this::toSqlLiteral)
                .collect(Collectors.joining(","));

        executeSql("ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check");
        executeSql("ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (" + allowedTypes + "))");
    }

    private boolean verifyNotificationsTypeConstraint() {
        if (!hasTable("notifications")) {
            return false;
        }

        var results = jdbcTemplate.queryForList("""
            SELECT pg_get_constraintdef(c.oid) AS constraint_def
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'public'
              AND t.relname = 'notifications'
              AND c.conname = 'notifications_type_check'
        """);

        if (results.isEmpty() || results.get(0).get("constraint_def") == null) {
            return false;
        }

        String constraintDef = results.get(0).get("constraint_def").toString();
        for (NotificationType notificationType : NotificationType.values()) {
            if (!constraintDef.contains(toSqlLiteral(notificationType.name()))) {
                return false;
            }
        }
        return true;
    }

    private String toSqlLiteral(String rawValue) {
        return "'" + rawValue.replace("'", "''") + "'";
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

    protected void executeSql(String sql) {
        jdbcTemplate.execute(sql);
    }
}
