package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseUpgradePolicy;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.policy.CourseRevisionCompatibilityChecker;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseAutoCompatibleUpgradeExecutor {

    private static final String EXECUTION_METRIC = "course_revision_auto_upgrade_executions_total";
    private static final String UPGRADED_ENROLLMENTS_METRIC = "course_revision_auto_upgrade_enrollments_total";
    private static final String SKIPPED_BREAKING_ENROLLMENTS_METRIC =
            "course_revision_auto_upgrade_skipped_breaking_enrollments_total";
    private static final String SKIPPED_EXECUTIONS_METRIC = "course_revision_auto_upgrade_skipped_total";
    private static final String EXECUTOR_ERRORS_METRIC = "course_revision_auto_upgrade_errors_total";
    private static final String POST_COMMIT_RECONCILIATION_METRIC =
            "course_revision_auto_upgrade_post_commit_reconciliation_total";

    private final CourseRepository courseRepository;
    private final CourseRevisionRepository courseRevisionRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final CourseRevisionCompatibilityChecker compatibilityChecker;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Transactional
    public AutoUpgradeExecutionResult executeAfterRevisionApproval(
            Course course,
            Long previousActiveRevisionId,
            CourseRevision approvedRevision
    ) {
        Long courseId = course != null ? course.getId() : null;
        Long targetRevisionId = approvedRevision != null ? approvedRevision.getId() : null;
        String policyTag = resolvePolicyTag(course);

        try {
            if (course == null || approvedRevision == null || previousActiveRevisionId == null) {
                recordExecution("skipped_invalid_input", course, previousActiveRevisionId, targetRevisionId);
                log.warn(
                        "Skip auto-upgrade: invalid input (courseId={}, sourceRevisionId={}, targetRevisionId={})",
                        courseId,
                        previousActiveRevisionId,
                        targetRevisionId
                );
                return AutoUpgradeExecutionResult.skipped("INVALID_INPUT", "source_or_target_revision_missing");
            }

            if (approvedRevision.getStatus() != CourseRevisionStatus.APPROVED) {
                recordExecution("skipped_target_not_approved", course, previousActiveRevisionId, targetRevisionId);
                log.warn(
                        "Skip auto-upgrade for course {} because target revision {} is not APPROVED (actual={})",
                        courseId,
                        targetRevisionId,
                        approvedRevision.getStatus()
                );
                return AutoUpgradeExecutionResult.skipped(
                        "TARGET_REVISION_NOT_APPROVED",
                        String.valueOf(approvedRevision.getStatus())
                );
            }

            if (course.getUpgradePolicy() != CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY) {
                recordExecution("skipped_policy", course, previousActiveRevisionId, targetRevisionId);
                log.debug(
                        "Skip auto-upgrade for course {} because policy is {}",
                        courseId,
                        policyTag
                );
                return AutoUpgradeExecutionResult.skipped("POLICY_NOT_AUTO_COMPATIBLE_ONLY", policyTag);
            }

            if (approvedRevision.getId() == null || approvedRevision.getId().equals(previousActiveRevisionId)) {
                recordExecution("skipped_no_revision_change", course, previousActiveRevisionId, targetRevisionId);
                log.info(
                        "Skip auto-upgrade for course {} because revision did not change (source={}, target={})",
                        courseId,
                        previousActiveRevisionId,
                        targetRevisionId
                );
                return AutoUpgradeExecutionResult.skipped("NO_REVISION_CHANGE", "source_equals_target");
            }

            CourseRevision sourceRevision = courseRevisionRepository.findById(previousActiveRevisionId).orElse(null);
            if (sourceRevision == null) {
                recordExecution("skipped_missing_source_revision", course, previousActiveRevisionId, targetRevisionId);
                log.warn(
                        "Skip auto-upgrade for course {} because source revision {} was not found",
                        courseId,
                        previousActiveRevisionId
                );
                return AutoUpgradeExecutionResult.skipped("SOURCE_REVISION_NOT_FOUND", String.valueOf(previousActiveRevisionId));
            }
            CourseRevisionCompatibilityChecker.CompatibilityResult compatibilityResult =
                    compatibilityChecker.evaluateCompatibility(sourceRevision, approvedRevision);
            if (!compatibilityResult.isNonBreaking()) {
                long skippedBreakingCount = courseEnrollmentRepository.countEligibleEnrollmentsForAutoUpgrade(
                        courseId,
                        previousActiveRevisionId,
                        CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY.name(),
                        EnrollmentStatus.ENROLLED
                );
                recordExecution("skipped_breaking", course, previousActiveRevisionId, targetRevisionId);
                incrementCounter(
                        SKIPPED_BREAKING_ENROLLMENTS_METRIC,
                        skippedBreakingCount,
                        "policy",
                        policyTag,
                        "course_id",
                        String.valueOf(courseId),
                        "source_revision_id",
                        String.valueOf(previousActiveRevisionId),
                        "target_revision_id",
                        String.valueOf(targetRevisionId),
                        "reason_code",
                        compatibilityResult.getReasonCode()
                );
                log.info(
                        "Skip auto-upgrade for course {} because revision {} -> {} is breaking (eligibleSkipped={}, reasonCode={}, reasonDetail={})",
                        courseId,
                        previousActiveRevisionId,
                        targetRevisionId,
                        skippedBreakingCount,
                        compatibilityResult.getReasonCode(),
                        compatibilityResult.getReasonDetail()
                );
                return AutoUpgradeExecutionResult.skipped(
                        compatibilityResult.getReasonCode(),
                        compatibilityResult.getReasonDetail()
                );
            }

            Instant upgradedAt = Instant.now(clock);
            int upgradedCount = courseEnrollmentRepository.autoUpgradePinnedRevisionForEligibleEnrollments(
                    course.getId(),
                    previousActiveRevisionId,
                    approvedRevision.getId(),
                    CourseUpgradePolicy.AUTO_COMPATIBLE_ONLY.name(),
                    upgradedAt,
                    EnrollmentStatus.ENROLLED
            );

            recordExecution("upgraded", course, previousActiveRevisionId, targetRevisionId);
            incrementCounter(
                    UPGRADED_ENROLLMENTS_METRIC,
                    upgradedCount,
                    "policy",
                    policyTag,
                    "course_id",
                    String.valueOf(courseId),
                    "source_revision_id",
                    String.valueOf(previousActiveRevisionId),
                    "target_revision_id",
                    String.valueOf(targetRevisionId)
            );
            log.info(
                    "Auto-compatible executor upgraded {} enrollments for course {} from revision {} to {}",
                    upgradedCount,
                    courseId,
                    previousActiveRevisionId,
                    targetRevisionId
            );
            return AutoUpgradeExecutionResult.upgraded(upgradedCount);
        } catch (Exception ex) {
            recordExecution("error", course, previousActiveRevisionId, targetRevisionId);
            incrementCounter(
                    EXECUTOR_ERRORS_METRIC,
                    1,
                    "policy",
                    policyTag,
                    "course_id",
                    String.valueOf(courseId),
                    "source_revision_id",
                    String.valueOf(previousActiveRevisionId),
                    "target_revision_id",
                    String.valueOf(targetRevisionId)
            );
            log.error(
                    "AUTO_COMPATIBLE_ONLY executor failed for course {} (from revision {} to {})",
                    courseId,
                    previousActiveRevisionId,
                    targetRevisionId,
                    ex
            );
            return AutoUpgradeExecutionResult.error("EXECUTOR_ERROR", ex.getClass().getSimpleName());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AutoUpgradeExecutionResult executePostCommitReconciliation(Long courseId, Long sourceRevisionId, Long targetRevisionId) {
        if (courseId == null || sourceRevisionId == null || targetRevisionId == null) {
            return AutoUpgradeExecutionResult.skipped("INVALID_INPUT", "post_commit_missing_identifier");
        }

        Course course = courseRepository.findById(courseId).orElse(null);
        CourseRevision targetRevision = courseRevisionRepository.findById(targetRevisionId).orElse(null);
        AutoUpgradeExecutionResult result = executeAfterRevisionApproval(course, sourceRevisionId, targetRevision);
        String resultTag = result.getOutcome() == null ? "unknown" : result.getOutcome().toLowerCase();
        incrementCounter(
                POST_COMMIT_RECONCILIATION_METRIC,
                1,
                "policy",
                resolvePolicyTag(course),
                "result",
                resultTag,
                "reason_code",
                result.getReasonCode() == null ? "UNKNOWN" : result.getReasonCode(),
                "course_id",
                String.valueOf(courseId),
                "source_revision_id",
                String.valueOf(sourceRevisionId),
                "target_revision_id",
                String.valueOf(targetRevisionId)
        );
        return result;
    }

    private void recordExecution(String result, Course course, Long sourceRevisionId, Long targetRevisionId) {
        incrementCounter(
                EXECUTION_METRIC,
                1,
                "result",
                result,
                "policy",
                resolvePolicyTag(course),
                "course_id",
                String.valueOf(course != null ? course.getId() : null),
                "source_revision_id",
                String.valueOf(sourceRevisionId),
                "target_revision_id",
                String.valueOf(targetRevisionId)
        );
        if (result.startsWith("skipped")) {
            incrementCounter(
                    SKIPPED_EXECUTIONS_METRIC,
                    1,
                    "result",
                    result,
                    "policy",
                    resolvePolicyTag(course),
                    "course_id",
                    String.valueOf(course != null ? course.getId() : null),
                    "source_revision_id",
                    String.valueOf(sourceRevisionId),
                    "target_revision_id",
                    String.valueOf(targetRevisionId)
            );
        }
    }

    public static final class AutoUpgradeExecutionResult {
        private final int upgradedCount;
        private final String outcome;
        private final String reasonCode;
        private final String reasonDetail;

        private AutoUpgradeExecutionResult(int upgradedCount, String outcome, String reasonCode, String reasonDetail) {
            this.upgradedCount = upgradedCount;
            this.outcome = outcome;
            this.reasonCode = reasonCode;
            this.reasonDetail = reasonDetail;
        }

        public static AutoUpgradeExecutionResult upgraded(int upgradedCount) {
            return new AutoUpgradeExecutionResult(Math.max(upgradedCount, 0), "UPGRADED", "NONE", "compatible");
        }

        public static AutoUpgradeExecutionResult skipped(String reasonCode, String reasonDetail) {
            return new AutoUpgradeExecutionResult(0, "SKIPPED", reasonCode, reasonDetail);
        }

        public static AutoUpgradeExecutionResult error(String reasonCode, String reasonDetail) {
            return new AutoUpgradeExecutionResult(0, "ERROR", reasonCode, reasonDetail);
        }

        public int getUpgradedCount() {
            return upgradedCount;
        }

        public String getOutcome() {
            return outcome;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public String getReasonDetail() {
            return reasonDetail;
        }

        public boolean isUpgraded() {
            return upgradedCount > 0;
        }
    }

    private String resolvePolicyTag(Course course) {
        return course != null && course.getUpgradePolicy() != null
                ? course.getUpgradePolicy().name()
                : "UNKNOWN";
    }

    private void incrementCounter(String metricName, double amount, String... tags) {
        if (amount <= 0) {
            return;
        }
        try {
            Counter counter = meterRegistry.counter(metricName, tags);
            if (counter != null) {
                counter.increment(amount);
            }
        } catch (RuntimeException metricEx) {
            log.debug("Cannot publish metric {} with tags {}", metricName, String.join(",", tags), metricEx);
        }
    }
}
