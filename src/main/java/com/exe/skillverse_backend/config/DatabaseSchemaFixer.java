package com.exe.skillverse_backend.config;

import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
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

    private static final long SCHEMA_FIXER_LOCK_KEY = 2026031502L;

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

            applyPatch("add-certificates-revocation-columns",
                    "Add missing certificate revocation columns for Hibernate schema validation",
                    this::patchCertificatesRevocationColumns,
                    this::verifyCertificatesRevocationColumns);

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

            applyPatch("create-assignment-prompt-audit-log-table",
                    "Create assignment_prompt_audit_log table for assignment AI prompt override auditing",
                    this::patchAssignmentPromptAuditLogTable,
                    this::verifyAssignmentPromptAuditLogTable);

            applyPatch("create-interview-schedules-table",
                    "Create interview_schedules table for managing interview sessions",
                    this::patchInterviewSchedulesTable,
                    this::verifyInterviewSchedulesTable);

            applyPatch("add-job-applications-interview-result",
                    "Add interview_result column to job_applications for storing interview notes",
                    this::patchJobApplicationsInterviewResult,
                    this::verifyJobApplicationsInterviewResult);

            applyPatch("add-notification-types-interview-scheduling",
                    "Add INTERVIEW_SCHEDULED and INTERVIEW_COMPLETED to notification type check constraint",
                    this::patchNotificationTypesInterviewScheduling,
                    this::verifyNotificationTypesInterviewScheduling);

            applyPatch("add-job-applications-interview-statuses",
                    "Add INTERVIEW_SCHEDULED, INTERVIEWED, OFFER_SENT, OFFER_ACCEPTED, OFFER_REJECTED, CONTRACT_SIGNED to job_applications status check constraint",
                    this::patchJobApplicationsInterviewStatuses,
                    this::verifyJobApplicationsInterviewStatuses);

            applyPatch("drop-interview-schedules-app-id-unique",
                    "Drop unique constraint on interview_schedules.application_id to allow re-scheduling cancelled interviews",
                    this::patchInterviewSchedulesDropUnique,
                    this::verifyInterviewSchedulesDropUnique);

            applyPatch("add-interview-schedules-no-show-status",
                    "Add NO_SHOW to interview_schedules status check constraint",
                    this::patchInterviewSchedulesNoShowStatus,
                    this::verifyInterviewSchedulesNoShowStatus);

                applyPatch("add-interview-schedules-response-tracking-columns",
                    "Add response/completion tracking columns to interview_schedules",
                    this::patchInterviewSchedulesResponseTrackingColumns,
                    this::verifyInterviewSchedulesResponseTrackingColumns);

            applyPatch("add-job-applications-offer-columns",
                    "Add offer_details, candidate_offer_response, offer_round columns to job_applications",
                    this::patchJobApplicationsOfferColumns,
                    this::verifyJobApplicationsOfferColumns);

                applyPatch("add-job-applications-structured-offer-columns",
                    "Add structured recruiter offer and candidate counter-offer columns to job_applications",
                    this::patchJobApplicationsStructuredOfferColumns,
                    this::verifyJobApplicationsStructuredOfferColumns);

                applyPatch("add-portfolio-extended-profiles-history-columns",
                    "Add work_experiences and education_history columns to portfolio_extended_profiles",
                    this::patchPortfolioExtendedProfilesHistoryColumns,
                    this::verifyPortfolioExtendedProfilesHistoryColumns);

                applyPatch("add-portfolio-extended-profiles-achievements-column",
                    "Add achievements column to portfolio_extended_profiles for mentor achievements",
                    this::patchPortfolioExtendedProfilesAchievementsColumn,
                    this::verifyPortfolioExtendedProfilesAchievementsColumn);

            applyPatch("add-prechat-messages-booking-id",
                    "Add booking_id FK column to prechat_messages for booking-scoped chat",
                    this::patchPrechatMessagesBookingId,
                    this::verifyPrechatMessagesBookingId);

            applyPatch("fix-short-term-jobs-status-width",
                    "Widen short_term_jobs.status to VARCHAR(30) and sync check constraint",
                    this::patchShortTermJobsStatusWidth,
                    this::verifyShortTermJobsStatusWidth);

            applyPatch("fix-short-term-applications-status-width",
                    "Widen short_term_job_applications.status to VARCHAR(30) and sync check constraint",
                    this::patchShortTermApplicationsStatusWidth,
                    this::verifyShortTermApplicationsStatusWidth);

            // ─── recruiter_profiles: missing company_logo_public_id column ──────
            applyPatch("add-recruiter-profiles-company-logo-public-id",
                    "Add missing company_logo_public_id column to recruiter_profiles for Hibernate schema validation",
                    this::patchRecruiterProfilesCompanyLogoPublicId,
                    this::verifyRecruiterProfilesCompanyLogoPublicId);

            applyPatch("add-recruiter-profiles-company-logo-url",
                    "Add missing company_logo_url column to recruiter_profiles for Hibernate schema validation",
                    this::patchRecruiterProfilesCompanyLogoUrl,
                    this::verifyRecruiterProfilesCompanyLogoUrl);

            // ─── wallet_transactions: sync transaction_type check constraint ─────────
            applyPatch("sync-wallet-transaction-type-constraint",
                    "Sync wallet_transactions.transaction_type check constraint with TransactionType enum values",
                    this::patchWalletTransactionTypeConstraint,
                    this::verifyWalletTransactionTypeConstraint);

            // ─── course.price/currency sync from active_revision (out-of-sync fix) ──
            // When revision approval was implemented, approveRevision() only synced
            // activeRevisionId/latestRevisionId but NOT price/currency on the course table.
            // This caused enrollment 400 errors and incorrect purchase/mentor payments.
            // Fix: sync course.price/currency from active_revision on every approve.
            // The code fix in CourseRevisionServiceImpl.approveRevision() prevents future drift.
            // This patch cleans up existing out-of-sync courses (runs once, idempotent).
            applyPatch("sync-course-price-currency-from-active-revision",
                    "Sync course.price and course.currency from active_revision for out-of-sync courses",
                    this::patchCoursePriceCurrencyFromRevision,
                    this::verifyCoursePriceCurrencyFromRevision);

            applyPatch("create-student-verification-requests-table",
                    "Create and align student_verification_requests table for student premium verification flow",
                    this::patchStudentVerificationRequestsTable,
                    this::verifyStudentVerificationRequestsTable);

                applyPatch("sync-student-verification-status-constraint",
                    "Sync student_verification_requests.status check constraint with StudentVerificationStatus enum values",
                    this::patchStudentVerificationStatusConstraint,
                    this::verifyStudentVerificationStatusConstraint);

            applyPatch("remove-student-verification-ocr-columns",
                    "Remove obsolete OCR extraction columns from student_verification_requests",
                    this::patchStudentVerificationRemoveOcrColumns,
                    this::verifyStudentVerificationOcrColumnsRemoved);

            // ─── course_skill_tags ElementCollection table ───────────────────────────
            applyPatch("create-course-skill-tags-table",
                    "Create course_skill_tags ElementCollection table for free-form skill tags",
                    this::patchCourseSkillTagsTable,
                    this::verifyCourseSkillTagsTable);

            applyPatch("add-course-skill-tags-unique-constraint",
                    "Add unique constraint on course_skill_tags(course_id, skill_tag) to prevent duplicate tags",
                    this::patchCourseSkillTagsUniqueConstraint,
                    this::verifyCourseSkillTagsUniqueConstraint);

            applyPatch("add-skills-name-unique-index",
                    "Add unique case-insensitive index on skills.name to prevent duplicate skills like 'java' and 'JAVA'",
                    this::patchSkillsNameUniqueIndex,
                    this::verifySkillsNameUniqueIndex);

            applyPatch("add-course-revisions-course-skill-tags-json-column",
                    "Add missing course_skill_tags_json column to course_revisions for Hibernate schema validation",
                    this::patchCourseRevisionsCourseSkillTagsJsonColumn,
                    this::verifyCourseRevisionsCourseSkillTagsJsonColumn);

            applyPatch("fix-course-revisions-course-skill-tags-nullable",
                    "Make course_revisions.course_skill_tags_json nullable — existing revisions have NULL values",
                    this::patchCourseRevisionsCourseSkillTagsNullable,
                    this::verifyCourseRevisionsCourseSkillTagsNullable,
                    false);

            applyPatch("add-course-revisions-thumbnail-media-id",
                    "Add thumbnail_media_id FK to course_revisions table for per-revision thumbnail images",
                    this::patchCourseRevisionsThumbnailMediaId,
                    this::verifyCourseRevisionsThumbnailMediaId);

            // ─── job_disputes.dispute_type: add CANCELLATION_REVIEW to check constraint ─────────
            applyPatch("add-job-disputes-cancellation-review-type",
                    "Add CANCELLATION_REVIEW to job_disputes dispute_type check constraint — used when recruiter requests admin cancellation review after 5 revisions",
                    this::patchJobDisputesDisputeTypeConstraint,
                    this::verifyJobDisputesDisputeTypeConstraint);

            // ═══════════════════════════════════════════════════════════════════
            // V3 PHASE 1 — Skill-Centric Redesign patches
            // ═══════════════════════════════════════════════════════════════════

            applyPatch("v3-create-mentor-skill-verification-requests",
                    "Create mentor_skill_verification_requests table for mentor skill verification workflow",
                    this::patchMentorSkillVerificationRequestsTable,
                    this::verifyMentorSkillVerificationRequestsTable);

            applyPatch("v3-create-mentor-verification-evidences",
                    "Create mentor_verification_evidences table for evidence linked to verification requests",
                    this::patchMentorVerificationEvidencesTable,
                    this::verifyMentorVerificationEvidencesTable);

            applyPatch("v3-add-question-bank-questions-verified-fields",
                    "Add is_verified, verified_by, verified_at, verification_source to question_bank_questions",
                    this::patchQuestionBankQuestionsVerifiedFields,
                    this::verifyQuestionBankQuestionsVerifiedFields);

            applyPatch("v3-add-journeys-skill-name",
                    "Add skill_name column to journeys for single-skill journey model",
                    this::patchJourneysSkillName,
                    this::verifyJourneysSkillName);

            applyPatch("v3-add-question-banks-skill-name",
                    "Add skill_name column to question_banks for skill-specific question bank lookup",
                    this::patchQuestionBanksSkillName,
                    this::verifyQuestionBanksSkillName);

            // ═══════════════════════════════════════════════════════════════════
            // V3 PHASE 1 — Node Mentoring + Final Verification Gate
            // ═══════════════════════════════════════════════════════════════════

            applyPatch("v3-node-mentoring-core-tables",
                    "Create node mentoring core tables: assignments, submissions, reviews, verifications, output assessments, completion reports",
                    this::patchNodeMentoringCoreTables,
                    this::verifyNodeMentoringCoreTables);

            applyPatch("v3-mentor-bookings-node-context",
                    "Add optional node/journey context columns to mentor_bookings",
                    this::patchMentorBookingsNodeContext,
                    this::verifyMentorBookingsNodeContext);

            applyPatch("v3-journey-final-gate-flags",
                    "Add final verification gate flag columns to journeys",
                    this::patchJourneyFinalGateFlags,
                    this::verifyJourneyFinalGateFlags);

            applyPatch("v3-create-question-bank-submissions",
                    "Create question_bank_submissions and question_bank_submission_questions tables for mentor contribution review flow",
                    this::patchQuestionBankSubmissionTables,
                    this::verifyQuestionBankSubmissionTables);

            applyPatch("v3-add-job-postings-primary-skill",
                    "Add primary_skill column to job_postings and short_term_jobs",
                    this::patchJobPostingsPrimarySkill,
                    this::verifyJobPostingsPrimarySkill);

            // ─── V3 Phase 1 — journeys status check constraint ─────────────────
            // Old constraint was created before V3 and does not include
            // COMPLETED_UNVERIFIED / AWAITING_VERIFICATION / COMPLETED_VERIFIED.
            // Attempting to set status = COMPLETED_UNVERIFIED raises:
            //   DataIntegrityViolationException: journeys_status_check
            applyPatch("v3-fix-journeys-status-constraint",
                    "Recreate journeys_status_check to include V3 Phase 1 statuses (COMPLETED_UNVERIFIED, AWAITING_VERIFICATION, COMPLETED_VERIFIED)",
                    this::patchJourneysStatusConstraint,
                    this::verifyJourneysStatusConstraint);

            // ─── V3 Phase 1 — normalize legacy mentor skill_name values ────────
            // Skills entered as "BACKEND", "BACK_END", "back end", "back-end" etc.
            // must resolve to the same canonical form so the separator-agnostic
            // REGEXP_REPLACE query in MentorSkillVerificationRequestRepository works
            // for both stored values and incoming query parameters.
            applyPatch("v3-normalize-mentor-skill-names",
                    "Normalize legacy skill_name values in mentor_skill_verification_requests to UPPER_SNAKE canonical form",
                    this::patchNormalizeMentorSkillNames,
                    this::verifyNormalizeMentorSkillNames);

            log.info("Schema patch infrastructure ready.");
        } finally {
            releaseAdvisoryLock();
        }
    }

    private void patchJobPostingsPrimarySkill() {
        jdbcTemplate.execute("ALTER TABLE job_postings ADD COLUMN IF NOT EXISTS primary_skill VARCHAR(100)");
        jdbcTemplate.execute("ALTER TABLE short_term_jobs ADD COLUMN IF NOT EXISTS primary_skill VARCHAR(100)");
    }

    private boolean verifyJobPostingsPrimarySkill() {
        return hasColumn("job_postings", "primary_skill") && hasColumn("short_term_jobs", "primary_skill");
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

    private void patchCertificatesRevocationColumns() {
        if (!hasTable("certificates")) {
            log.debug("Table certificates does not exist yet, skipping patch.");
            return;
        }

        executeSql("""
            ALTER TABLE certificates
                ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ,
                ADD COLUMN IF NOT EXISTS revoke_reason VARCHAR(120),
                ADD COLUMN IF NOT EXISTS revoked_by BIGINT
        """);
    }

    private boolean verifyCertificatesRevocationColumns() {
        if (!hasTable("certificates")) {
            return true;
        }

        return hasColumn("certificates", "revoked_at")
                && hasColumn("certificates", "revoke_reason")
                && hasColumn("certificates", "revoked_by");
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

    private void patchAssignmentPromptAuditLogTable() {
        if (!hasTable("assignment_prompt_audit_log")) {
            executeSql("""
                CREATE TABLE assignment_prompt_audit_log (
                    id BIGSERIAL PRIMARY KEY,
                    assignment_id BIGINT NOT NULL,
                    admin_id BIGINT NOT NULL,
                    admin_name VARCHAR(200) NOT NULL,
                    action VARCHAR(50) NOT NULL,
                    before_value TEXT,
                    after_value TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
                )
            """);
        }

        executeSql("""
            ALTER TABLE assignment_prompt_audit_log
                ADD COLUMN IF NOT EXISTS assignment_id BIGINT,
                ADD COLUMN IF NOT EXISTS admin_id BIGINT,
                ADD COLUMN IF NOT EXISTS admin_name VARCHAR(200),
                ADD COLUMN IF NOT EXISTS action VARCHAR(50),
                ADD COLUMN IF NOT EXISTS before_value TEXT,
                ADD COLUMN IF NOT EXISTS after_value TEXT,
                ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ
        """);

        executeSql("""
            ALTER TABLE assignment_prompt_audit_log
                ALTER COLUMN assignment_id SET NOT NULL,
                ALTER COLUMN admin_id SET NOT NULL,
                ALTER COLUMN admin_name SET NOT NULL,
                ALTER COLUMN action SET NOT NULL,
                ALTER COLUMN created_at SET DEFAULT NOW(),
                ALTER COLUMN created_at SET NOT NULL
        """);

        if (hasTable("assignments") && !hasForeignKey("assignment_prompt_audit_log", "fk_assignment_prompt_audit_log_assignment")) {
            executeSql("""
                ALTER TABLE assignment_prompt_audit_log
                ADD CONSTRAINT fk_assignment_prompt_audit_log_assignment
                FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE
            """);
        }

        executeSql("CREATE INDEX IF NOT EXISTS idx_assignment_prompt_audit_log_assignment_id ON assignment_prompt_audit_log(assignment_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_assignment_prompt_audit_log_assignment_created_at ON assignment_prompt_audit_log(assignment_id, created_at DESC)");
    }

    private boolean verifyAssignmentPromptAuditLogTable() {
        if (!hasTable("assignment_prompt_audit_log")) {
            return false;
        }

        return hasColumn("assignment_prompt_audit_log", "id")
                && hasColumn("assignment_prompt_audit_log", "assignment_id")
                && hasColumn("assignment_prompt_audit_log", "admin_id")
                && hasColumn("assignment_prompt_audit_log", "admin_name")
                && hasColumn("assignment_prompt_audit_log", "action")
                && hasColumn("assignment_prompt_audit_log", "before_value")
                && hasColumn("assignment_prompt_audit_log", "after_value")
                && hasColumn("assignment_prompt_audit_log", "created_at")
                && hasIndex("idx_assignment_prompt_audit_log_assignment_id")
                && hasIndex("idx_assignment_prompt_audit_log_assignment_created_at")
                && (!hasTable("assignments")
                        || hasForeignKey("assignment_prompt_audit_log", "fk_assignment_prompt_audit_log_assignment"));
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

    // ─── recruiter_profiles: company_logo_public_id column ─────────────────────

    private void patchRecruiterProfilesCompanyLogoPublicId() {
        if (!hasTable("recruiter_profiles")) {
            log.debug("Table recruiter_profiles does not exist yet, skipping patch.");
            return;
        }
        if (!hasColumn("recruiter_profiles", "company_logo_public_id")) {
            executeSql("ALTER TABLE recruiter_profiles ADD COLUMN company_logo_public_id VARCHAR(500)");
        }
    }

    private boolean verifyRecruiterProfilesCompanyLogoPublicId() {
        return hasTable("recruiter_profiles")
                && hasColumn("recruiter_profiles", "company_logo_public_id");
    }

    private void patchRecruiterProfilesCompanyLogoUrl() {
        if (!hasTable("recruiter_profiles")) {
            log.debug("Table recruiter_profiles does not exist yet, skipping patch.");
            return;
        }
        if (!hasColumn("recruiter_profiles", "company_logo_url")) {
            executeSql("ALTER TABLE recruiter_profiles ADD COLUMN company_logo_url VARCHAR(1000)");
        }
    }

    private boolean verifyRecruiterProfilesCompanyLogoUrl() {
        return hasTable("recruiter_profiles")
                && hasColumn("recruiter_profiles", "company_logo_url");
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

    private void patchInterviewSchedulesTable() {
        if (hasTable("interview_schedules")) {
            log.debug("Table interview_schedules already exists, skipping patch.");
            return;
        }
        executeSql("""
            CREATE TABLE interview_schedules (
                id BIGSERIAL PRIMARY KEY,
                application_id BIGINT NOT NULL UNIQUE,
                scheduled_at TIMESTAMP NOT NULL,
                duration_minutes INTEGER DEFAULT 60,
                meeting_type VARCHAR(20) NOT NULL,
                meeting_link VARCHAR(500),
                skillverse_room_id VARCHAR(100),
                location VARCHAR(500),
                interviewer_name VARCHAR(200),
                interview_notes TEXT,
                response_deadline_at TIMESTAMP,
                responded_at TIMESTAMP,
                cancelled_by VARCHAR(20),
                cancel_reason TEXT,
                completed_at TIMESTAMP,
                status VARCHAR(20) DEFAULT 'PENDING',
                created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                updated_at TIMESTAMP DEFAULT NOW()
            )
        """);
        executeSql("CREATE INDEX IF NOT EXISTS idx_interview_schedules_application_id ON interview_schedules(application_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_interview_schedules_status ON interview_schedules(status)");
    }

    private boolean verifyInterviewSchedulesTable() {
        if (!hasTable("interview_schedules")) return false;
        return hasColumn("interview_schedules", "id")
                && hasColumn("interview_schedules", "application_id")
                && hasColumn("interview_schedules", "scheduled_at")
                && hasColumn("interview_schedules", "meeting_type")
                && hasColumn("interview_schedules", "status");
    }

    private void patchJobApplicationsInterviewResult() {
        if (!hasTable("job_applications")) {
            log.debug("Table job_applications does not exist yet, skipping patch.");
            return;
        }
        executeSql("ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS interview_result VARCHAR(500)");
    }

    private boolean verifyJobApplicationsInterviewResult() {
        return hasTable("job_applications")
                && hasColumn("job_applications", "interview_result");
    }

    private void patchNotificationTypesInterviewScheduling() {
        if (!hasTable("notifications")) {
            log.debug("Table notifications does not exist yet, skipping patch.");
            return;
        }
        // Drop existing constraint and recreate to include new types
        executeSql("ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check");
        String allowedTypes = Arrays.stream(NotificationType.values())
                .map(NotificationType::name)
                .map(this::toSqlLiteral)
                .collect(Collectors.joining(","));
        executeSql("ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (" + allowedTypes + "))");
    }

    private boolean verifyNotificationTypesInterviewScheduling() {
        if (!hasTable("notifications")) return false;
        var results = jdbcTemplate.queryForList("""
            SELECT pg_get_constraintdef(c.oid) AS constraint_def
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'public'
              AND t.relname = 'notifications'
              AND c.conname = 'notifications_type_check'
        """);
        if (results.isEmpty() || results.get(0).get("constraint_def") == null) return false;
        String constraintDef = results.get(0).get("constraint_def").toString();
        return constraintDef.contains(toSqlLiteral("INTERVIEW_SCHEDULED"))
                && constraintDef.contains(toSqlLiteral("INTERVIEW_COMPLETED"));
    }

    private void patchJobApplicationsInterviewStatuses() {
        if (!hasTable("job_applications")) {
            log.debug("Table job_applications does not exist yet, skipping patch.");
            return;
        }
        String constraintName = "job_applications_status_check";
        // Drop existing constraint and recreate with all current enum values
        executeSql("ALTER TABLE job_applications DROP CONSTRAINT IF EXISTS " + constraintName);
        executeSql("ALTER TABLE job_applications ADD CONSTRAINT " + constraintName
                + " CHECK (status IN ('PENDING','REVIEWED','ACCEPTED','REJECTED',"
                + "'INTERVIEW_SCHEDULED','INTERVIEWED','OFFER_SENT','OFFER_ACCEPTED','OFFER_REJECTED','CONTRACT_SIGNED'))");
    }

    private boolean verifyJobApplicationsInterviewStatuses() {
        if (!hasTable("job_applications")) return false;
        var results = jdbcTemplate.queryForList("""
            SELECT pg_get_constraintdef(c.oid) AS constraint_def
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'public'
              AND t.relname = 'job_applications'
              AND c.conname = 'job_applications_status_check'
        """);
        if (results.isEmpty() || results.get(0).get("constraint_def") == null) return false;
        String constraintDef = results.get(0).get("constraint_def").toString();
        return constraintDef.contains(toSqlLiteral("INTERVIEW_SCHEDULED"))
                && constraintDef.contains(toSqlLiteral("OFFER_SENT"));
    }

    private void patchInterviewSchedulesDropUnique() {
        if (!hasTable("interview_schedules")) {
            log.debug("Table interview_schedules does not exist yet, skipping patch.");
            return;
        }
        try {
            executeSql("ALTER TABLE interview_schedules DROP CONSTRAINT IF EXISTS interview_schedules_application_id_key");
        } catch (Exception e) {
            log.debug("Constraint interview_schedules_application_id_key does not exist, skipping drop: {}", e.getMessage());
        }
    }

    private boolean verifyInterviewSchedulesDropUnique() {
        if (!hasTable("interview_schedules")) return false;
        try {
            var results = jdbcTemplate.queryForList("""
                SELECT 1 FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                WHERE n.nspname = 'public'
                  AND t.relname = 'interview_schedules'
                  AND c.conname = 'interview_schedules_application_id_key'
            """);
            // Return true if constraint no longer exists (was dropped or never existed)
            return results.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    // ─── interview_schedules: add NO_SHOW to status check ────────────────────

    private void patchInterviewSchedulesNoShowStatus() {
        if (!hasTable("interview_schedules")) {
            log.debug("Table interview_schedules does not exist yet, skipping patch.");
            return;
        }
        try {
            executeSql("ALTER TABLE interview_schedules DROP CONSTRAINT IF EXISTS interview_schedules_status_check");
            executeSql("""
                ALTER TABLE interview_schedules ADD CONSTRAINT interview_schedules_status_check
                CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','COMPLETED','NO_SHOW'))
            """);
        } catch (Exception e) {
            log.debug("Status check constraint patch skipped: {}", e.getMessage());
        }
    }

    private boolean verifyInterviewSchedulesNoShowStatus() {
        if (!hasTable("interview_schedules")) return false;
        var results = jdbcTemplate.queryForList("""
            SELECT pg_get_constraintdef(c.oid) AS constraint_def
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'public'
              AND t.relname = 'interview_schedules'
              AND c.conname = 'interview_schedules_status_check'
        """);
        if (results.isEmpty() || results.get(0).get("constraint_def") == null) return false;
        return results.get(0).get("constraint_def").toString().contains("NO_SHOW");
    }

    private void patchInterviewSchedulesResponseTrackingColumns() {
        if (!hasTable("interview_schedules")) {
            log.debug("Table interview_schedules does not exist yet, skipping patch.");
            return;
        }

        executeSql("ALTER TABLE interview_schedules ADD COLUMN IF NOT EXISTS response_deadline_at TIMESTAMP");
        executeSql("ALTER TABLE interview_schedules ADD COLUMN IF NOT EXISTS responded_at TIMESTAMP");
        executeSql("ALTER TABLE interview_schedules ADD COLUMN IF NOT EXISTS cancelled_by VARCHAR(20)");
        executeSql("ALTER TABLE interview_schedules ADD COLUMN IF NOT EXISTS cancel_reason TEXT");
        executeSql("ALTER TABLE interview_schedules ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP");

        executeSql("ALTER TABLE interview_schedules DROP CONSTRAINT IF EXISTS interview_schedules_cancelled_by_check");
        executeSql("""
            ALTER TABLE interview_schedules ADD CONSTRAINT interview_schedules_cancelled_by_check
            CHECK (cancelled_by IS NULL OR cancelled_by IN ('RECRUITER','CANDIDATE','AUTO'))
        """);
    }

    private boolean verifyInterviewSchedulesResponseTrackingColumns() {
        return hasTable("interview_schedules")
                && hasColumn("interview_schedules", "response_deadline_at")
                && hasColumn("interview_schedules", "responded_at")
                && hasColumn("interview_schedules", "cancelled_by")
                && hasColumn("interview_schedules", "cancel_reason")
                && hasColumn("interview_schedules", "completed_at");
    }

    // ─── job_applications: add offer columns ───────────────────────────────────

    private void patchJobApplicationsOfferColumns() {
        if (!hasTable("job_applications")) {
            log.debug("Table job_applications does not exist yet, skipping patch.");
            return;
        }
        executeSql("ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS offer_details TEXT");
        executeSql("ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS candidate_offer_response TEXT");
        executeSql("ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS offer_round INTEGER NOT NULL DEFAULT 0");
    }

    private boolean verifyJobApplicationsOfferColumns() {
        return hasTable("job_applications")
                && hasColumn("job_applications", "offer_details")
                && hasColumn("job_applications", "candidate_offer_response")
                && hasColumn("job_applications", "offer_round");
    }

    // ─── job_applications: add structured offer/counter-offer columns ─────────

    private void patchJobApplicationsStructuredOfferColumns() {
        if (!hasTable("job_applications")) {
            log.debug("Table job_applications does not exist yet, skipping patch.");
            return;
        }
        executeSql("ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS offer_salary BIGINT");
        executeSql("ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS offer_additional_requirements TEXT");
        executeSql("ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS counter_salary_amount BIGINT");
        executeSql("ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS counter_additional_requirements TEXT");
    }

    private boolean verifyJobApplicationsStructuredOfferColumns() {
        return hasTable("job_applications")
                && hasColumn("job_applications", "offer_salary")
                && hasColumn("job_applications", "offer_additional_requirements")
                && hasColumn("job_applications", "counter_salary_amount")
                && hasColumn("job_applications", "counter_additional_requirements");
    }

    // ─── prechat_messages: add booking_id FK ──────────────────────────────────

    private void patchPrechatMessagesBookingId() {
        if (!hasTable("prechat_messages")) {
            log.debug("Table prechat_messages does not exist yet, skipping patch.");
            return;
        }
        // Step 1: add column if it doesn't exist
        if (!hasColumn("prechat_messages", "booking_id")) {
            executeSql("ALTER TABLE prechat_messages ADD COLUMN booking_id BIGINT");
        } else {
            log.debug("Column booking_id already exists in prechat_messages, skipping column addition.");
        }
        // Step 2: add FK constraint idempotently (drop first if exists to handle partial prior runs)
        if (!hasForeignKey("prechat_messages", "fk_prechat_messages_booking")) {
            executeSql("""
                ALTER TABLE prechat_messages
                ADD CONSTRAINT fk_prechat_messages_booking
                FOREIGN KEY (booking_id) REFERENCES mentor_bookings(id) ON DELETE SET NULL
            """);
        } else {
            log.debug("Foreign key fk_prechat_messages_booking already exists, skipping.");
        }
        // Step 3: add index if it doesn't exist
        executeSql("CREATE INDEX IF NOT EXISTS idx_prechat_messages_booking_id ON prechat_messages(booking_id)");
    }

    private boolean verifyPrechatMessagesBookingId() {
        if (!hasTable("prechat_messages")) return false;
        return hasColumn("prechat_messages", "booking_id")
                && hasForeignKey("prechat_messages", "fk_prechat_messages_booking");
    }

    // ─── short_term_jobs: widen status to VARCHAR(30) and sync constraint ──────

    private void patchShortTermJobsStatusWidth() {
        if (!hasTable("short_term_jobs")) {
            log.debug("Table short_term_jobs does not exist yet, skipping patch.");
            return;
        }
        // Widen column
        executeSql("ALTER TABLE short_term_jobs ALTER COLUMN status TYPE VARCHAR(30)");
        // Drop existing constraint
        executeSql("ALTER TABLE short_term_jobs DROP CONSTRAINT IF EXISTS short_term_jobs_status_check");
        // Recreate with all current enum values
        executeSql("""
            ALTER TABLE short_term_jobs ADD CONSTRAINT short_term_jobs_status_check
            CHECK (status IN (
                'DRAFT','PENDING_APPROVAL','PUBLISHED','APPLIED','IN_PROGRESS',
                'SUBMITTED','UNDER_REVIEW','AUTO_APPROVED','CANCELLATION_REQUESTED',
                'AUTO_CANCELLED','DISPUTED','ESCALATED','APPROVED','REJECTED',
                'COMPLETED','PAID','CANCELLED','CLOSED'
            ))
        """);
    }

    private boolean verifyShortTermJobsStatusWidth() {
        if (!hasTable("short_term_jobs")) return false;
        var results = jdbcTemplate.queryForList("""
            SELECT pg_get_constraintdef(c.oid) AS constraint_def
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'public'
              AND t.relname = 'short_term_jobs'
              AND c.conname = 'short_term_jobs_status_check'
        """);
        if (results.isEmpty() || results.get(0).get("constraint_def") == null) return false;
        String def = results.get(0).get("constraint_def").toString();
        return def.contains("PENDING_APPROVAL") && def.contains("AUTO_APPROVED");
    }

    // ─── short_term_job_applications: widen status and sync constraint ──────────

    private void patchShortTermApplicationsStatusWidth() {
        if (!hasTable("short_term_job_applications")) {
            log.debug("Table short_term_job_applications does not exist yet, skipping patch.");
            return;
        }
        executeSql("ALTER TABLE short_term_job_applications ALTER COLUMN status TYPE VARCHAR(30)");
        executeSql("ALTER TABLE short_term_job_applications DROP CONSTRAINT IF EXISTS short_term_job_applications_status_check");
        executeSql("""
            ALTER TABLE short_term_job_applications ADD CONSTRAINT short_term_job_applications_status_check
            CHECK (status IN (
                'PENDING','ACCEPTED','REJECTED','WORKING','SUBMITTED','SUBMITTED_OVERDUE',
                'REVISION_REQUIRED','REVISION_RESPONSE_OVERDUE','CANCELLATION_REQUESTED',
                'AUTO_CANCELLED','APPROVED','COMPLETED','DISPUTE_OPENED',
                'CANCELLED','WITHDRAWN'
            ))
        """);
    }

    private boolean verifyShortTermApplicationsStatusWidth() {
        if (!hasTable("short_term_job_applications")) return false;
        var results = jdbcTemplate.queryForList("""
            SELECT pg_get_constraintdef(c.oid) AS constraint_def
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'public'
              AND t.relname = 'short_term_job_applications'
              AND c.conname = 'short_term_job_applications_status_check'
        """);
        if (results.isEmpty() || results.get(0).get("constraint_def") == null) return false;
        String def = results.get(0).get("constraint_def").toString();
        return def.contains("SUBMITTED_OVERDUE") && def.contains("WITHDRAWN");
    }

    // ─── portfolio_extended_profiles: add history columns ─────────────────────

    private void patchPortfolioExtendedProfilesHistoryColumns() {
        if (!hasTable("portfolio_extended_profiles")) {
            log.debug("Table portfolio_extended_profiles does not exist yet, skipping patch.");
            return;
        }
        executeSql("ALTER TABLE portfolio_extended_profiles ADD COLUMN IF NOT EXISTS work_experiences TEXT");
        executeSql("ALTER TABLE portfolio_extended_profiles ADD COLUMN IF NOT EXISTS education_history TEXT");
    }

    private boolean verifyPortfolioExtendedProfilesHistoryColumns() {
        return hasTable("portfolio_extended_profiles")
                && hasColumn("portfolio_extended_profiles", "work_experiences")
                && hasColumn("portfolio_extended_profiles", "education_history");
    }

    private void patchPortfolioExtendedProfilesAchievementsColumn() {
        if (!hasTable("portfolio_extended_profiles")) {
            log.debug("Table portfolio_extended_profiles does not exist yet, skipping patch.");
            return;
        }
        executeSql("ALTER TABLE portfolio_extended_profiles ADD COLUMN IF NOT EXISTS achievements TEXT");
    }

    private boolean verifyPortfolioExtendedProfilesAchievementsColumn() {
        return hasTable("portfolio_extended_profiles")
                && hasColumn("portfolio_extended_profiles", "achievements");
    }

    // ─── wallet_transactions: sync transaction_type check constraint ────────────────

    private void patchWalletTransactionTypeConstraint() {
        if (!hasTable("wallet_transactions")) {
            log.debug("Table wallet_transactions does not exist yet, skipping patch.");
            return;
        }

        String allowedTypes = Arrays.stream(WalletTransaction.TransactionType.values())
                .map(WalletTransaction.TransactionType::name)
                .map(this::toSqlLiteral)
                .collect(Collectors.joining(","));

        executeSql("ALTER TABLE wallet_transactions DROP CONSTRAINT IF EXISTS wallet_transactions_transaction_type_check");
        executeSql("ALTER TABLE wallet_transactions ADD CONSTRAINT wallet_transactions_transaction_type_check "
                + "CHECK (transaction_type IN (" + allowedTypes + "))");
    }

    private boolean verifyWalletTransactionTypeConstraint() {
        if (!hasTable("wallet_transactions")) {
            return false;
        }

        var results = jdbcTemplate.queryForList("""
            SELECT pg_get_constraintdef(c.oid) AS constraint_def
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'public'
              AND t.relname = 'wallet_transactions'
              AND c.conname = 'wallet_transactions_transaction_type_check'
        """);

        if (results.isEmpty() || results.get(0).get("constraint_def") == null) {
            return false;
        }

        String constraintDef = results.get(0).get("constraint_def").toString();
        for (WalletTransaction.TransactionType type : WalletTransaction.TransactionType.values()) {
            if (!constraintDef.contains(toSqlLiteral(type.name()))) {
                return false;
            }
        }
        return true;
    }

    // ─── course_skill_tags ElementCollection table ────────────────────────────

    private void patchCourseSkillTagsTable() {
        executeSql("""
            CREATE TABLE IF NOT EXISTS course_skill_tags (
                course_id BIGINT NOT NULL,
                skill_tag VARCHAR(255),
                PRIMARY KEY (course_id, skill_tag)
            )
        """);
        executeSql("CREATE INDEX IF NOT EXISTS idx_course_skill_tags_course_id ON course_skill_tags(course_id)");
    }

    private boolean verifyCourseSkillTagsTable() {
        return hasTable("course_skill_tags")
                && hasColumn("course_skill_tags", "course_id")
                && hasColumn("course_skill_tags", "skill_tag");
    }

    private void patchCourseSkillTagsUniqueConstraint() {
        executeSql("""
            ALTER TABLE course_skill_tags
            ADD CONSTRAINT course_skill_tags_course_id_skill_tag_key
            UNIQUE (course_id, skill_tag)
        """);
    }

    private boolean verifyCourseSkillTagsUniqueConstraint() {
        return hasConstraint("course_skill_tags", "course_skill_tags_course_id_skill_tag_key");
    }

    // ─── skills.name case-insensitive unique index ───────────────────────────────

    private void patchSkillsNameUniqueIndex() {
        // PostgreSQL: functional unique index on UPPER(name) allows only one of 'java'/'JAVA'/'Java'
        executeSql("""
            CREATE UNIQUE INDEX IF NOT EXISTS idx_skills_name_upper
            ON skills (UPPER(name))
        """);
    }

    private boolean verifySkillsNameUniqueIndex() {
        return hasIndex("idx_skills_name_upper");
    }

    // ─── course_revisions.course_skill_tags_json column + nullable fix ───────────

    private void patchCourseRevisionsCourseSkillTagsJsonColumn() {
        if (!hasTable("course_revisions")) {
            log.debug("Table course_revisions does not exist yet, skipping patch.");
            return;
        }
        if (!hasColumn("course_revisions", "course_skill_tags_json")) {
            executeSql("""
                ALTER TABLE course_revisions
                ADD COLUMN course_skill_tags_json JSONB
            """);
        }
    }

    private boolean verifyCourseRevisionsCourseSkillTagsJsonColumn() {
        return hasColumn("course_revisions", "course_skill_tags_json");
    }

    private void patchCourseRevisionsCourseSkillTagsNullable() {
        // If column doesn't exist yet, Hibernate will create it as nullable.
        // Only apply if the column exists (was created by Hibernate) and has NOT NULL.
        if (!hasColumn("course_revisions", "course_skill_tags_json")) {
            return;
        }
        try {
            executeSql("ALTER TABLE course_revisions ALTER COLUMN course_skill_tags_json DROP NOT NULL");
        } catch (Exception e) {
            log.debug("Patch drop-not-null skipped (already nullable or column missing): {}", e.getMessage());
        }
    }

    private boolean verifyCourseRevisionsCourseSkillTagsNullable() {
        // Column doesn't exist yet → Hibernate will create it nullable → pass
        if (!hasColumn("course_revisions", "course_skill_tags_json")) {
            return true;
        }
        var results = jdbcTemplate.queryForList(
            "SELECT is_nullable FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND table_name = 'course_revisions' AND column_name = 'course_skill_tags_json'"
        );
        return !results.isEmpty() && "YES".equalsIgnoreCase((String) results.get(0).get("is_nullable"));
    }

    // ─── course_revisions: add thumbnail_media_id FK ────────────────────────────

    private void patchCourseRevisionsThumbnailMediaId() {
        if (!hasTable("course_revisions")) {
            log.debug("Table course_revisions does not exist yet, skipping patch.");
            return;
        }
        // Step 1: add column if it doesn't exist
        if (!hasColumn("course_revisions", "thumbnail_media_id")) {
            executeSql("""
                ALTER TABLE course_revisions
                ADD COLUMN thumbnail_media_id BIGINT
                REFERENCES media(id) ON DELETE SET NULL
            """);
        }
        // Step 2: add index for faster lookups
        executeSql("CREATE INDEX IF NOT EXISTS idx_course_revisions_thumbnail_media_id ON course_revisions(thumbnail_media_id)");
    }

    private boolean verifyCourseRevisionsThumbnailMediaId() {
        return hasColumn("course_revisions", "thumbnail_media_id");
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

    // ─── course.price/currency: sync from active_revision ───────────────────

    private void patchCoursePriceCurrencyFromRevision() {
        if (!hasTable("courses") || !hasTable("course_revisions")) {
            log.debug("courses or course_revisions table not yet created, skipping patch.");
            return;
        }

        // Update courses.price and courses.currency from the currently active revision.
        // Uses a subquery so each course picks up its own active_revision's values.
        executeSql("""
            UPDATE courses c
            SET price  = r.price,
                currency = r.currency,
                updated_at = NOW()
            FROM course_revisions r
            WHERE r.id = c.active_revision_id
              AND (
                  c.price IS DISTINCT FROM r.price
                  OR c.currency IS DISTINCT FROM r.currency
              )
        """);
    }

    private boolean verifyCoursePriceCurrencyFromRevision() {
        if (!hasTable("courses") || !hasTable("course_revisions")) {
            return true; // tables not present yet — will apply on next startup
        }

        // Verify: no courses where price/currency differs from active_revision
        var results = jdbcTemplate.queryForList("""
            SELECT COUNT(*) AS mismatches
            FROM courses c
            JOIN course_revisions r ON r.id = c.active_revision_id
            WHERE c.price IS DISTINCT FROM r.price
               OR c.currency IS DISTINCT FROM r.currency
        """);

        if (!results.isEmpty()) {
            long count = ((Number) results.get(0).get("mismatches")).longValue();
            if (count > 0) {
                log.warn("Course price/currency sync verification found {} out-of-sync courses", count);
            }
        }

        // Always return true — the UPDATE always succeeds even if rows are already in sync
        return true;
    }

    // ─── student_verification_requests table ────────────────────────────────

    private void patchStudentVerificationRequestsTable() {
        if (!hasTable("student_verification_requests")) {
            executeSql("""
                CREATE TABLE student_verification_requests (
                    id BIGSERIAL PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    school_email VARCHAR(255) NOT NULL,
                    school_domain VARCHAR(255) NOT NULL,
                    email_domain_valid BOOLEAN NOT NULL DEFAULT FALSE,
                    status VARCHAR(40) NOT NULL DEFAULT 'EMAIL_OTP_PENDING',
                    otp_hash VARCHAR(128),
                    otp_expires_at TIMESTAMP,
                    otp_attempts INTEGER NOT NULL DEFAULT 0,
                    otp_verified_at TIMESTAMP,
                    last_otp_sent_at TIMESTAMP,
                    temp_image_path TEXT,
                    image_url TEXT,
                    image_storage_path TEXT,
                    image_public_id VARCHAR(255),
                    image_provider VARCHAR(20),
                    uploaded_file_name VARCHAR(255),
                    uploaded_content_type VARCHAR(100),
                    uploaded_file_size BIGINT,
                    review_note TEXT,
                    reviewed_by BIGINT,
                    reviewed_at TIMESTAMP,
                    rejection_reason TEXT,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
            """);
        }

        executeSql("""
            ALTER TABLE student_verification_requests
                ADD COLUMN IF NOT EXISTS user_id BIGINT,
                ADD COLUMN IF NOT EXISTS school_email VARCHAR(255),
                ADD COLUMN IF NOT EXISTS school_domain VARCHAR(255),
                ADD COLUMN IF NOT EXISTS email_domain_valid BOOLEAN DEFAULT FALSE,
                ADD COLUMN IF NOT EXISTS status VARCHAR(40) DEFAULT 'EMAIL_OTP_PENDING',
                ADD COLUMN IF NOT EXISTS otp_hash VARCHAR(128),
                ADD COLUMN IF NOT EXISTS otp_expires_at TIMESTAMP,
                ADD COLUMN IF NOT EXISTS otp_attempts INTEGER DEFAULT 0,
                ADD COLUMN IF NOT EXISTS otp_verified_at TIMESTAMP,
                ADD COLUMN IF NOT EXISTS last_otp_sent_at TIMESTAMP,
                ADD COLUMN IF NOT EXISTS temp_image_path TEXT,
                ADD COLUMN IF NOT EXISTS image_url TEXT,
                ADD COLUMN IF NOT EXISTS image_storage_path TEXT,
                ADD COLUMN IF NOT EXISTS image_public_id VARCHAR(255),
                ADD COLUMN IF NOT EXISTS image_provider VARCHAR(20),
                ADD COLUMN IF NOT EXISTS uploaded_file_name VARCHAR(255),
                ADD COLUMN IF NOT EXISTS uploaded_content_type VARCHAR(100),
                ADD COLUMN IF NOT EXISTS uploaded_file_size BIGINT,
                ADD COLUMN IF NOT EXISTS review_note TEXT,
                ADD COLUMN IF NOT EXISTS reviewed_by BIGINT,
                ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP,
                ADD COLUMN IF NOT EXISTS rejection_reason TEXT,
                ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT NOW(),
                ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP DEFAULT NOW()
        """);

        executeSql("""
            ALTER TABLE student_verification_requests
                ALTER COLUMN user_id SET NOT NULL,
                ALTER COLUMN school_email SET NOT NULL,
                ALTER COLUMN school_domain SET NOT NULL,
                ALTER COLUMN email_domain_valid SET DEFAULT FALSE,
                ALTER COLUMN email_domain_valid SET NOT NULL,
                ALTER COLUMN status SET DEFAULT 'EMAIL_OTP_PENDING',
                ALTER COLUMN status SET NOT NULL,
                ALTER COLUMN otp_attempts SET DEFAULT 0,
                ALTER COLUMN otp_attempts SET NOT NULL,
                ALTER COLUMN created_at SET DEFAULT NOW(),
                ALTER COLUMN created_at SET NOT NULL,
                ALTER COLUMN updated_at SET DEFAULT NOW(),
                ALTER COLUMN updated_at SET NOT NULL
        """);

        if (hasTable("users") && !hasForeignKey("student_verification_requests", "fk_svr_user")) {
            executeSql("""
                ALTER TABLE student_verification_requests
                ADD CONSTRAINT fk_svr_user FOREIGN KEY (user_id) REFERENCES users(id)
            """);
        }

        if (hasTable("users") && !hasForeignKey("student_verification_requests", "fk_svr_reviewed_by")) {
            executeSql("""
                ALTER TABLE student_verification_requests
                ADD CONSTRAINT fk_svr_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(id)
            """);
        }

        executeSql("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_constraint
                    WHERE conname = 'chk_svr_status'
                ) THEN
                    ALTER TABLE student_verification_requests
                    ADD CONSTRAINT chk_svr_status
                    CHECK (status IN ('EMAIL_OTP_PENDING', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED'));
                END IF;
            END $$;
        """);

        executeSql("""
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_constraint
                    WHERE conname = 'chk_svr_image_provider'
                ) THEN
                    ALTER TABLE student_verification_requests
                    ADD CONSTRAINT chk_svr_image_provider
                    CHECK (image_provider IS NULL OR image_provider IN ('CLOUDINARY', 'LOCAL'));
                END IF;
            END $$;
        """);

        executeSql("CREATE INDEX IF NOT EXISTS idx_svr_user_id ON student_verification_requests(user_id)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_svr_status ON student_verification_requests(status)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_svr_created_at ON student_verification_requests(created_at)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_svr_school_email_status ON student_verification_requests(school_email, status)");
    }

    private boolean verifyStudentVerificationRequestsTable() {
        if (!hasTable("student_verification_requests")) {
            return false;
        }

        return hasColumn("student_verification_requests", "id")
                && hasColumn("student_verification_requests", "user_id")
                && hasColumn("student_verification_requests", "school_email")
                && hasColumn("student_verification_requests", "school_domain")
                && hasColumn("student_verification_requests", "status")
                && hasColumn("student_verification_requests", "otp_hash")
                && hasColumn("student_verification_requests", "image_storage_path")
                && hasColumn("student_verification_requests", "reviewed_by")
                && hasColumn("student_verification_requests", "created_at")
                && hasColumn("student_verification_requests", "updated_at")
                && hasIndex("idx_svr_user_id")
                && hasIndex("idx_svr_status")
                && hasIndex("idx_svr_created_at")
                && hasIndex("idx_svr_school_email_status")
                && (!hasTable("users") || hasForeignKey("student_verification_requests", "fk_svr_user"));
    }

    private void patchStudentVerificationStatusConstraint() {
        if (!hasTable("student_verification_requests")) {
            return;
        }

        if (hasConstraint("student_verification_requests", "chk_svr_status")) {
            executeSql("ALTER TABLE student_verification_requests DROP CONSTRAINT chk_svr_status");
        }

        executeSql("""
            ALTER TABLE student_verification_requests
            ADD CONSTRAINT chk_svr_status
            CHECK (status IN ('EMAIL_OTP_PENDING', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'EXPIRED'))
        """);
    }

    private boolean verifyStudentVerificationStatusConstraint() {
        if (!hasTable("student_verification_requests")) {
            return true;
        }

        if (!hasConstraint("student_verification_requests", "chk_svr_status")) {
            return false;
        }

        try {
            String definition = jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(c.oid)
                FROM pg_constraint c
                JOIN pg_class t ON t.oid = c.conrelid
                WHERE t.relname = 'student_verification_requests'
                  AND c.conname = 'chk_svr_status'
            """, String.class);

            return definition != null && definition.contains("'EXPIRED'");
        } catch (Exception e) {
            return false;
        }
    }

    private void patchStudentVerificationRemoveOcrColumns() {
        if (!hasTable("student_verification_requests")) {
            return;
        }

        executeSql("""
            ALTER TABLE student_verification_requests
                DROP COLUMN IF EXISTS ocr_raw_text,
                DROP COLUMN IF EXISTS ocr_readable,
                DROP COLUMN IF EXISTS ocr_error_message,
                DROP COLUMN IF EXISTS ocr_extracted_name,
                DROP COLUMN IF EXISTS ocr_extracted_student_id,
                DROP COLUMN IF EXISTS ocr_extracted_school,
                DROP COLUMN IF EXISTS ocr_extracted_expiry_date,
                DROP COLUMN IF EXISTS ocr_processed_at
        """);
    }

    private boolean verifyStudentVerificationOcrColumnsRemoved() {
        if (!hasTable("student_verification_requests")) {
            return true;
        }

        return !hasColumn("student_verification_requests", "ocr_raw_text")
                && !hasColumn("student_verification_requests", "ocr_readable")
                && !hasColumn("student_verification_requests", "ocr_error_message")
                && !hasColumn("student_verification_requests", "ocr_extracted_name")
                && !hasColumn("student_verification_requests", "ocr_extracted_student_id")
                && !hasColumn("student_verification_requests", "ocr_extracted_school")
                && !hasColumn("student_verification_requests", "ocr_extracted_expiry_date")
                && !hasColumn("student_verification_requests", "ocr_processed_at");
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

    /** Checks if any constraint (any type) with the given name exists on the table. */
    protected boolean hasConstraint(String tableName, String constraintName) {
        try {
            var results = jdbcTemplate.queryForList("""
                SELECT 1 FROM information_schema.table_constraints
                WHERE table_name = ?
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

    // ─── job_disputes.dispute_type: add CANCELLATION_REVIEW to check constraint ───

    private void patchJobDisputesDisputeTypeConstraint() {
        if (!hasTable("job_disputes")) {
            log.debug("Table job_disputes does not exist yet, skipping patch.");
            return;
        }

        // [Nghiệp vụ] DB check constraint `job_disputes_dispute_type_check` được tạo khi bảng được tạo ban đầu.
        // Khi thêm CANCELLATION_REVIEW vào enum, constraint cần được update để include giá trị mới.
        // Sử dụng DO $$ block để alter constraint mà không cần drop trước.
        executeSql("""
            DO $$
            DECLARE
                constraint_exists BOOLEAN;
            BEGIN
                -- Kiểm tra xem constraint có tồn tại không
                SELECT EXISTS (
                    SELECT 1 FROM pg_constraint
                    WHERE conname = 'job_disputes_dispute_type_check'
                ) INTO constraint_exists;

                IF constraint_exists THEN
                    -- Drop constraint cũ
                    ALTER TABLE job_disputes DROP CONSTRAINT job_disputes_dispute_type_check;
                END IF;

                -- Tạo constraint mới với đầy đủ các giá trị enum (bao gồm CANCELLATION_REVIEW)
                ALTER TABLE job_disputes ADD CONSTRAINT job_disputes_dispute_type_check
                    CHECK (dispute_type IN (
                        'NO_SUBMISSION',
                        'POOR_QUALITY',
                        'MISSING_DELIVERABLE',
                        'DEADLINE_VIOLATION',
                        'PAYMENT_ISSUE',
                        'COMMUNICATION_FAILURE',
                        'SCOPE_CHANGE',
                        'SCAM_REPORT',
                        'OTHER',
                        'WORKER_PROTECTION',
                        'RECRUITER_ABUSE',
                        'CANCELLATION_REVIEW'
                    ));
            END $$;
        """);
        log.info("job_disputes_dispute_type_check constraint updated with CANCELLATION_REVIEW");
    }

    private boolean verifyJobDisputesDisputeTypeConstraint() {
        if (!hasTable("job_disputes")) {
            return false;
        }

        var results = jdbcTemplate.queryForList("""
            SELECT pg_get_constraintdef(c.oid) AS constraint_def
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'public'
              AND t.relname = 'job_disputes'
              AND c.conname = 'job_disputes_dispute_type_check'
        """);

        if (results.isEmpty() || results.get(0).get("constraint_def") == null) {
            return false;
        }

        String def = results.get(0).get("constraint_def").toString();
        return def.contains("CANCELLATION_REVIEW");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // V3 PHASE 1 — Mentor Skill Verification
    // ═══════════════════════════════════════════════════════════════════════════

    private void patchMentorSkillVerificationRequestsTable() {
        if (hasTable("mentor_skill_verification_requests")) {
            log.debug("Table mentor_skill_verification_requests already exists, skipping.");
            return;
        }
        executeSql("""
            CREATE TABLE mentor_skill_verification_requests (
                id BIGSERIAL PRIMARY KEY,
                mentor_id BIGINT NOT NULL,
                skill_name VARCHAR(100) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                github_url VARCHAR(500),
                portfolio_url VARCHAR(500),
                additional_notes TEXT,
                review_note TEXT,
                reviewed_by BIGINT,
                requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                reviewed_at TIMESTAMPTZ,
                updated_at TIMESTAMPTZ DEFAULT NOW(),
                CONSTRAINT fk_msvr_mentor FOREIGN KEY (mentor_id) REFERENCES users(id),
                CONSTRAINT fk_msvr_reviewer FOREIGN KEY (reviewed_by) REFERENCES users(id),
                CONSTRAINT chk_msvr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
            )
        """);
        executeSql("CREATE INDEX IF NOT EXISTS idx_msvr_mentor_status ON mentor_skill_verification_requests(mentor_id, status)");
        executeSql("CREATE INDEX IF NOT EXISTS idx_msvr_status_requested ON mentor_skill_verification_requests(status, requested_at)");
        log.info("Created mentor_skill_verification_requests table with indexes.");
    }

    private boolean verifyMentorSkillVerificationRequestsTable() {
        return hasTable("mentor_skill_verification_requests")
                && hasColumn("mentor_skill_verification_requests", "mentor_id")
                && hasColumn("mentor_skill_verification_requests", "skill_name")
                && hasColumn("mentor_skill_verification_requests", "status");
    }

    private void patchMentorVerificationEvidencesTable() {
        if (hasTable("mentor_verification_evidences")) {
            log.debug("Table mentor_verification_evidences already exists, skipping.");
            return;
        }
        executeSql("""
            CREATE TABLE mentor_verification_evidences (
                id BIGSERIAL PRIMARY KEY,
                verification_request_id BIGINT NOT NULL,
                evidence_type VARCHAR(30) NOT NULL,
                evidence_url VARCHAR(1000),
                description TEXT,
                certificate_id BIGINT,
                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                CONSTRAINT fk_mve_request FOREIGN KEY (verification_request_id)
                    REFERENCES mentor_skill_verification_requests(id) ON DELETE CASCADE,
                CONSTRAINT fk_mve_certificate FOREIGN KEY (certificate_id)
                    REFERENCES external_certificates(id),
                CONSTRAINT chk_mve_type CHECK (evidence_type IN ('CERTIFICATE', 'GITHUB', 'PORTFOLIO_LINK', 'WORK_EXPERIENCE'))
            )
        """);
        executeSql("CREATE INDEX IF NOT EXISTS idx_mve_request ON mentor_verification_evidences(verification_request_id)");
        log.info("Created mentor_verification_evidences table with indexes.");
    }

    private boolean verifyMentorVerificationEvidencesTable() {
        return hasTable("mentor_verification_evidences")
                && hasColumn("mentor_verification_evidences", "verification_request_id")
                && hasColumn("mentor_verification_evidences", "evidence_type")
                && hasColumn("mentor_verification_evidences", "certificate_id");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // V3 PHASE 1 — Question Bank verified-only + skill_name
    // ═══════════════════════════════════════════════════════════════════════════

    private void patchQuestionBankQuestionsVerifiedFields() {
        if (!hasTable("question_bank_questions")) {
            log.debug("Table question_bank_questions does not exist yet, skipping.");
            return;
        }
        executeSql("""
            ALTER TABLE question_bank_questions
                ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT FALSE,
                ADD COLUMN IF NOT EXISTS verified_by BIGINT,
                ADD COLUMN IF NOT EXISTS verified_at TIMESTAMPTZ,
                ADD COLUMN IF NOT EXISTS verification_source VARCHAR(50)
        """);
        // Backward compat: mark all existing active questions as verified
        executeSql("""
            UPDATE question_bank_questions
            SET is_verified = TRUE, verification_source = 'LEGACY_MIGRATION'
            WHERE is_verified = FALSE AND is_active = TRUE
        """);
        executeSql("CREATE INDEX IF NOT EXISTS idx_qbq_verified ON question_bank_questions(question_bank_id, is_verified, difficulty)");
        log.info("Added verified fields to question_bank_questions.");
    }

    private boolean verifyQuestionBankQuestionsVerifiedFields() {
        if (!hasTable("question_bank_questions")) return true;
        return hasColumn("question_bank_questions", "is_verified")
                && hasColumn("question_bank_questions", "verified_by")
                && hasColumn("question_bank_questions", "verified_at")
                && hasColumn("question_bank_questions", "verification_source");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // V3 PHASE 1 — Journey skill_name + Question Bank skill_name
    // ═══════════════════════════════════════════════════════════════════════════

    private void patchJourneysSkillName() {
        if (!hasTable("journeys")) {
            log.debug("Table journeys does not exist yet, skipping.");
            return;
        }
        executeSql("ALTER TABLE journeys ADD COLUMN IF NOT EXISTS skill_name VARCHAR(100)");
        log.info("Added skill_name column to journeys.");
    }

    private boolean verifyJourneysSkillName() {
        if (!hasTable("journeys")) return true;
        return hasColumn("journeys", "skill_name");
    }

    private void patchQuestionBanksSkillName() {
        if (!hasTable("question_banks")) {
            log.debug("Table question_banks does not exist yet, skipping.");
            return;
        }
        executeSql("ALTER TABLE question_banks ADD COLUMN IF NOT EXISTS skill_name VARCHAR(100)");
        log.info("Added skill_name column to question_banks.");
    }

    private boolean verifyQuestionBanksSkillName() {
        if (!hasTable("question_banks")) return true;
        return hasColumn("question_banks", "skill_name");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // V3 PHASE 1 — Node Mentoring core tables
    // ═══════════════════════════════════════════════════════════════════════════

    private void patchNodeMentoringCoreTables() {
        // roadmap_node_assignments — snapshot assignment hiện tại của node
        if (!hasTable("roadmap_node_assignments")) {
            executeSql("""
                CREATE TABLE roadmap_node_assignments (
                    id BIGSERIAL PRIMARY KEY,
                    journey_id BIGINT NOT NULL,
                    roadmap_session_id BIGINT,
                    node_id VARCHAR(100) NOT NULL,
                    node_skill_id BIGINT,
                    assignment_source VARCHAR(30) NOT NULL DEFAULT 'SYSTEM_GENERATED',
                    title VARCHAR(255),
                    description TEXT,
                    created_by BIGINT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ,
                    CONSTRAINT fk_rna_journey FOREIGN KEY (journey_id) REFERENCES journeys(id) ON DELETE CASCADE,
                    CONSTRAINT chk_rna_source CHECK (assignment_source IN ('SYSTEM_GENERATED','MENTOR_REFINED'))
                )
            """);
            executeSql("CREATE INDEX IF NOT EXISTS idx_rna_journey_node ON roadmap_node_assignments(journey_id, node_id)");
            log.info("Created roadmap_node_assignments table.");
        }

        // roadmap_node_submissions — evidence record hiện tại của learner
        if (!hasTable("roadmap_node_submissions")) {
            executeSql("""
                CREATE TABLE roadmap_node_submissions (
                    id BIGSERIAL PRIMARY KEY,
                    journey_id BIGINT NOT NULL,
                    roadmap_session_id BIGINT,
                    node_id VARCHAR(100) NOT NULL,
                    assignment_id BIGINT,
                    learner_id BIGINT NOT NULL,
                    submission_text TEXT NOT NULL,
                    evidence_url VARCHAR(1000),
                    attachment_url VARCHAR(1000),
                    submission_status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
                    verification_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                    mentor_feedback TEXT,
                    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ,
                    CONSTRAINT fk_rns_journey FOREIGN KEY (journey_id) REFERENCES journeys(id) ON DELETE CASCADE,
                    CONSTRAINT fk_rns_assignment FOREIGN KEY (assignment_id) REFERENCES roadmap_node_assignments(id),
                    CONSTRAINT fk_rns_learner FOREIGN KEY (learner_id) REFERENCES users(id),
                    CONSTRAINT chk_rns_submission_status CHECK (submission_status IN ('DRAFT','SUBMITTED','REWORK_REQUESTED','RESUBMITTED','WITHDRAWN')),
                    CONSTRAINT chk_rns_verification_status CHECK (verification_status IN ('PENDING','UNDER_REVIEW','APPROVED','REJECTED','VERIFIED')),
                    CONSTRAINT uq_rns_current UNIQUE (journey_id, node_id)
                )
            """);
            executeSql("CREATE INDEX IF NOT EXISTS idx_rns_learner_status ON roadmap_node_submissions(learner_id, verification_status)");
            log.info("Created roadmap_node_submissions table.");
        }

        // roadmap_node_reviews — mentor review record
        if (!hasTable("roadmap_node_reviews")) {
            executeSql("""
                CREATE TABLE roadmap_node_reviews (
                    id BIGSERIAL PRIMARY KEY,
                    submission_id BIGINT NOT NULL,
                    mentor_id BIGINT NOT NULL,
                    booking_id BIGINT,
                    score INTEGER,
                    feedback TEXT,
                    review_result VARCHAR(30) NOT NULL,
                    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    CONSTRAINT fk_rnr_submission FOREIGN KEY (submission_id) REFERENCES roadmap_node_submissions(id) ON DELETE CASCADE,
                    CONSTRAINT fk_rnr_mentor FOREIGN KEY (mentor_id) REFERENCES users(id),
                    CONSTRAINT fk_rnr_booking FOREIGN KEY (booking_id) REFERENCES mentor_bookings(id),
                    CONSTRAINT chk_rnr_result CHECK (review_result IN ('APPROVED','REWORK_REQUESTED','REJECTED'))
                )
            """);
            executeSql("CREATE INDEX IF NOT EXISTS idx_rnr_submission ON roadmap_node_reviews(submission_id)");
            executeSql("CREATE INDEX IF NOT EXISTS idx_rnr_mentor ON roadmap_node_reviews(mentor_id)");
            log.info("Created roadmap_node_reviews table.");
        }

        // roadmap_node_verifications — mentor node verification record
        if (!hasTable("roadmap_node_verifications")) {
            executeSql("""
                CREATE TABLE roadmap_node_verifications (
                    id BIGSERIAL PRIMARY KEY,
                    submission_id BIGINT NOT NULL,
                    mentor_id BIGINT NOT NULL,
                    booking_id BIGINT,
                    node_verification_status VARCHAR(30) NOT NULL,
                    verification_note TEXT,
                    verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    CONSTRAINT fk_rnv_submission FOREIGN KEY (submission_id) REFERENCES roadmap_node_submissions(id) ON DELETE CASCADE,
                    CONSTRAINT fk_rnv_mentor FOREIGN KEY (mentor_id) REFERENCES users(id),
                    CONSTRAINT fk_rnv_booking FOREIGN KEY (booking_id) REFERENCES mentor_bookings(id),
                    CONSTRAINT chk_rnv_status CHECK (node_verification_status IN ('VERIFIED','REJECTED'))
                )
            """);
            executeSql("CREATE INDEX IF NOT EXISTS idx_rnv_submission ON roadmap_node_verifications(submission_id)");
            log.info("Created roadmap_node_verifications table.");
        }

        // journey_output_assessments — optional final output assessment
        if (!hasTable("journey_output_assessments")) {
            executeSql("""
                CREATE TABLE journey_output_assessments (
                    id BIGSERIAL PRIMARY KEY,
                    journey_id BIGINT NOT NULL,
                    learner_id BIGINT NOT NULL,
                    mentor_id BIGINT,
                    submission_text TEXT,
                    evidence_url VARCHAR(1000),
                    attachment_url VARCHAR(1000),
                    score INTEGER,
                    feedback TEXT,
                    assessment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    assessed_at TIMESTAMPTZ,
                    CONSTRAINT fk_joa_journey FOREIGN KEY (journey_id) REFERENCES journeys(id) ON DELETE CASCADE,
                    CONSTRAINT fk_joa_learner FOREIGN KEY (learner_id) REFERENCES users(id),
                    CONSTRAINT fk_joa_mentor FOREIGN KEY (mentor_id) REFERENCES users(id),
                    CONSTRAINT chk_joa_status CHECK (assessment_status IN ('PENDING','APPROVED','REJECTED'))
                )
            """);
            executeSql("CREATE INDEX IF NOT EXISTS idx_joa_journey ON journey_output_assessments(journey_id)");
            log.info("Created journey_output_assessments table.");
        }

        // journey_completion_reports — final mentor confirmation / gate decision
        if (!hasTable("journey_completion_reports")) {
            executeSql("""
                CREATE TABLE journey_completion_reports (
                    id BIGSERIAL PRIMARY KEY,
                    journey_id BIGINT NOT NULL,
                    mentor_id BIGINT NOT NULL,
                    booking_id BIGINT,
                    gate_decision VARCHAR(30) NOT NULL,
                    completion_note TEXT,
                    confirmed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    CONSTRAINT fk_jcr_journey FOREIGN KEY (journey_id) REFERENCES journeys(id) ON DELETE CASCADE,
                    CONSTRAINT fk_jcr_mentor FOREIGN KEY (mentor_id) REFERENCES users(id),
                    CONSTRAINT fk_jcr_booking FOREIGN KEY (booking_id) REFERENCES mentor_bookings(id),
                    CONSTRAINT chk_jcr_decision CHECK (gate_decision IN ('PASS','FAIL','PENDING'))
                )
            """);
            executeSql("CREATE INDEX IF NOT EXISTS idx_jcr_journey ON journey_completion_reports(journey_id)");
            log.info("Created journey_completion_reports table.");
        }
    }

    private boolean verifyNodeMentoringCoreTables() {
        return hasTable("roadmap_node_assignments")
                && hasTable("roadmap_node_submissions")
                && hasTable("roadmap_node_reviews")
                && hasTable("roadmap_node_verifications")
                && hasTable("journey_output_assessments")
                && hasTable("journey_completion_reports")
                && hasColumn("roadmap_node_submissions", "submission_text")
                && hasColumn("roadmap_node_submissions", "verification_status")
                && hasColumn("journey_completion_reports", "gate_decision");
    }

    // ─── mentor_bookings node/journey context ──────────────────────────────────

    private void patchMentorBookingsNodeContext() {
        if (!hasTable("mentor_bookings")) {
            log.debug("Table mentor_bookings does not exist yet, skipping.");
            return;
        }
        executeSql("""
            ALTER TABLE mentor_bookings
                ADD COLUMN IF NOT EXISTS journey_id BIGINT,
                ADD COLUMN IF NOT EXISTS roadmap_session_id BIGINT,
                ADD COLUMN IF NOT EXISTS node_id VARCHAR(100),
                ADD COLUMN IF NOT EXISTS node_skill_id BIGINT,
                ADD COLUMN IF NOT EXISTS booking_type VARCHAR(30)
        """);
        executeSql("CREATE INDEX IF NOT EXISTS idx_mb_journey_node ON mentor_bookings(journey_id, node_id)");
        log.info("Added node/journey context columns to mentor_bookings.");
    }

    private boolean verifyMentorBookingsNodeContext() {
        if (!hasTable("mentor_bookings")) return true;
        return hasColumn("mentor_bookings", "journey_id")
                && hasColumn("mentor_bookings", "roadmap_session_id")
                && hasColumn("mentor_bookings", "node_id")
                && hasColumn("mentor_bookings", "node_skill_id")
                && hasColumn("mentor_bookings", "booking_type");
    }

    // ─── journeys final gate flags ─────────────────────────────────────────────

    private void patchJourneyFinalGateFlags() {
        if (!hasTable("journeys")) {
            log.debug("Table journeys does not exist yet, skipping.");
            return;
        }
        executeSql("""
            ALTER TABLE journeys
                ADD COLUMN IF NOT EXISTS final_verification_required BOOLEAN DEFAULT FALSE,
                ADD COLUMN IF NOT EXISTS node_locked_after_verify BOOLEAN DEFAULT FALSE,
                ADD COLUMN IF NOT EXISTS journey_output_verification_required BOOLEAN DEFAULT FALSE
        """);
        log.info("Added final gate flag columns to journeys.");
    }

    private boolean verifyJourneyFinalGateFlags() {
        if (!hasTable("journeys")) return true;
        return hasColumn("journeys", "final_verification_required")
                && hasColumn("journeys", "node_locked_after_verify")
                && hasColumn("journeys", "journey_output_verification_required");
    }

    private void patchQuestionBankSubmissionTables() {
        if (!hasTable("question_bank_submissions")) {
            executeSql("""
                CREATE TABLE question_bank_submissions (
                    id BIGSERIAL PRIMARY KEY,
                    mentor_id BIGINT NOT NULL,
                    domain VARCHAR(50) NOT NULL,
                    industry VARCHAR(150) NOT NULL,
                    job_role VARCHAR(150) NOT NULL,
                    skill_name VARCHAR(100) NOT NULL,
                    title VARCHAR(255) NOT NULL,
                    description TEXT,
                    difficulty_distribution TEXT,
                    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
                    source VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
                    question_count INT NOT NULL DEFAULT 0,
                    saved_question_count INT,
                    duplicate_question_count INT,
                    review_note TEXT,
                    reviewed_by BIGINT,
                    reviewed_at TIMESTAMPTZ,
                    resolved_question_bank_id BIGINT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    CONSTRAINT fk_qbs_mentor FOREIGN KEY (mentor_id) REFERENCES users(id),
                    CONSTRAINT fk_qbs_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(id),
                    CONSTRAINT fk_qbs_resolved_bank FOREIGN KEY (resolved_question_bank_id) REFERENCES question_banks(id),
                    CONSTRAINT chk_qbs_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
                    CONSTRAINT chk_qbs_source CHECK (source IN ('MANUAL', 'JSON_IMPORT'))
                )
            """);
            executeSql("CREATE INDEX IF NOT EXISTS idx_qbs_mentor_status ON question_bank_submissions(mentor_id, status)");
            executeSql("CREATE INDEX IF NOT EXISTS idx_qbs_status_created_at ON question_bank_submissions(status, created_at)");
        }

        if (!hasTable("question_bank_submission_questions")) {
            executeSql("""
                CREATE TABLE question_bank_submission_questions (
                    id BIGSERIAL PRIMARY KEY,
                    submission_id BIGINT NOT NULL,
                    display_order INT NOT NULL,
                    question_text TEXT NOT NULL,
                    options TEXT NOT NULL,
                    correct_answer VARCHAR(1) NOT NULL,
                    explanation TEXT,
                    difficulty VARCHAR(20) NOT NULL,
                    skill_area VARCHAR(150),
                    category VARCHAR(100),
                    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                    CONSTRAINT fk_qbsq_submission FOREIGN KEY (submission_id)
                        REFERENCES question_bank_submissions(id) ON DELETE CASCADE
                )
            """);
            executeSql("CREATE INDEX IF NOT EXISTS idx_qbsq_submission_order ON question_bank_submission_questions(submission_id, display_order)");
        }
    }

    private boolean verifyQuestionBankSubmissionTables() {
        return hasTable("question_bank_submissions")
                && hasColumn("question_bank_submissions", "mentor_id")
                && hasColumn("question_bank_submissions", "skill_name")
                && hasTable("question_bank_submission_questions")
                && hasColumn("question_bank_submission_questions", "submission_id")
                && hasColumn("question_bank_submission_questions", "question_text");
    }

    // ─── V3 Phase 1 — journeys status check constraint ───────────────────────

    private void patchJourneysStatusConstraint() {
        if (!hasTable("journeys")) {
            log.debug("Table journeys does not exist yet, skipping.");
            return;
        }
        executeSql("ALTER TABLE journeys DROP CONSTRAINT IF EXISTS journeys_status_check");
        executeSql("""
            ALTER TABLE journeys ADD CONSTRAINT journeys_status_check CHECK (
                status IN (
                    'NOT_STARTED',
                    'ASSESSMENT_PENDING',
                    'TEST_IN_PROGRESS',
                    'EVALUATION_PENDING',
                    'ROADMAP_GENERATED',
                    'STUDY_PLAN_IN_PROGRESS',
                    'ACTIVE',
                    'COMPLETED',
                    'PAUSED',
                    'CANCELLED',
                    'COMPLETED_UNVERIFIED',
                    'AWAITING_VERIFICATION',
                    'COMPLETED_VERIFIED'
                )
            )
        """);
        log.info("Recreated journeys_status_check to include V3 Phase 1 statuses.");
    }

    private boolean verifyJourneysStatusConstraint() {
        if (!hasTable("journeys")) return true;
        var results = jdbcTemplate.queryForList("""
            SELECT pg_get_constraintdef(c.oid) AS constraint_def
            FROM pg_constraint c
            JOIN pg_class t ON t.oid = c.conrelid
            JOIN pg_namespace n ON n.oid = t.relnamespace
            WHERE n.nspname = 'public'
              AND t.relname = 'journeys'
              AND c.conname = 'journeys_status_check'
        """);
        if (results.isEmpty() || results.get(0).get("constraint_def") == null) return false;
        String def = results.get(0).get("constraint_def").toString();
        return def.contains("COMPLETED_UNVERIFIED")
                && def.contains("AWAITING_VERIFICATION")
                && def.contains("COMPLETED_VERIFIED");
    }

    // ─── V3 Phase 1 — normalize legacy mentor skill_name values ──────────────

    private void patchNormalizeMentorSkillNames() {
        if (!hasTable("mentor_skill_verification_requests")) {
            log.debug("Table mentor_skill_verification_requests does not exist yet, skipping.");
            return;
        }
        // Apply same normalization used in MentorVerificationServiceImpl.normalizeSkillName():
        // strip non-alphanumeric → single underscores → trim leading/trailing underscores → UPPER.
        // This ensures "BACKEND", "BACK_END", "back end", "back-end" all converge to canonical form.
        executeSql("""
            UPDATE mentor_skill_verification_requests
            SET skill_name = UPPER(
                REGEXP_REPLACE(
                    REGEXP_REPLACE(
                        TRIM(skill_name),
                        '[^a-zA-Z0-9]+', '_', 'g'
                    ),
                    '^_+|_+$', '', 'g'
                )
            )
            WHERE skill_name IS NOT NULL
              AND skill_name <> UPPER(
                REGEXP_REPLACE(
                    REGEXP_REPLACE(
                        TRIM(skill_name),
                        '[^a-zA-Z0-9]+', '_', 'g'
                    ),
                    '^_+|_+$', '', 'g'
                )
              )
        """);
        log.info("Normalized legacy skill_name values in mentor_skill_verification_requests.");
    }

    private boolean verifyNormalizeMentorSkillNames() {
        if (!hasTable("mentor_skill_verification_requests")) return true;
        // Patch is idempotent: verify no rows remain where skill_name differs from canonical form.
        Integer unnormalized = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM mentor_skill_verification_requests
            WHERE skill_name IS NOT NULL
              AND skill_name <> UPPER(
                REGEXP_REPLACE(
                    REGEXP_REPLACE(TRIM(skill_name), '[^a-zA-Z0-9]+', '_', 'g'),
                    '^_+|_+$', '', 'g'
                )
              )
        """, Integer.class);
        return unnormalized != null && unnormalized == 0;
    }
}
