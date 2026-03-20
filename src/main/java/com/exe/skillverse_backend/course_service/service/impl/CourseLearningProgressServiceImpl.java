package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.certificatedto.CertificateDTO;
import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningRevisionInfoDTO;
import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningStatusDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.entity.enums.QuizAttemptSessionStatus;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.repository.LessonProgressRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptSessionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;
import com.exe.skillverse_backend.course_service.service.CertificateService;
import com.exe.skillverse_backend.course_service.service.CourseLearningProgressService;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourseLearningProgressServiceImpl implements CourseLearningProgressService {

    private static final String ENROLLMENT_NOT_FOUND = "ENROLLMENT_NOT_FOUND";
    private static final String LEARNING_UPGRADE_METRIC = "course_revision_learning_upgrade_total";
    private static final String PROGRESS_REVISION_METRIC = "course_learning_progress_revision_total";
    private static final int CONTENT_SNAPSHOT_VERSION_V1 = 1;

    private final CourseEnrollmentRepository enrollmentRepository;
    private final CourseRepository courseRepository;
    private final CourseRevisionRepository courseRevisionRepository;
    private final LessonRepository lessonRepository;
    private final LessonProgressRepository lessonProgressRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptSessionRepository quizAttemptSessionRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final CertificateService certificateService;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional(readOnly = true)
    public CourseLearningStatusDTO getCourseLearningStatus(Long courseId, Long userId) {
        return calculateCourseLearningStatus(courseId, userId, null);
    }

    private CourseLearningStatusDTO calculateCourseLearningStatus(
            Long courseId,
            Long userId,
            CourseEnrollment enrollmentHint
    ) {
        List<Long> completedLessonIds = lessonProgressRepository.findCompletedLessonIdsByCourseAndUser(courseId, userId);
        List<Long> completedQuizIds = quizAttemptRepository.findPassedQuizIdsByCourseAndUser(courseId, userId);
        List<Long> completedAssignmentIds = assignmentSubmissionRepository
                .findPassedAssignmentIdsByCourseAndUser(courseId, userId);
        List<Long> completedRequiredAssignmentIds = assignmentSubmissionRepository
                .findPassedRequiredAssignmentIdsByCourseAndUser(courseId, userId);

        ProgressComputationScope progressScope = resolveProgressComputationScope(courseId, userId, enrollmentHint);

        long totalLessonCount;
        long totalQuizCount;
        long totalRequiredAssignmentCount;
        long completedLessonCount;
        long completedQuizCount;
        long completedRequiredAssignmentCount;

        if (progressScope.isRevisionScoped()) {
            completedLessonIds = filterByAllowedIds(completedLessonIds, progressScope.getLessonIds());
            completedQuizIds = filterByAllowedIds(completedQuizIds, progressScope.getQuizIds());
            completedAssignmentIds = filterByAllowedIds(completedAssignmentIds, progressScope.getAssignmentIds());
            completedRequiredAssignmentIds = filterByAllowedIds(
                    completedRequiredAssignmentIds,
                    progressScope.getRequiredAssignmentIds()
            );

            totalLessonCount = progressScope.getLessonIds().size();
            totalQuizCount = progressScope.getQuizIds().size();
            totalRequiredAssignmentCount = progressScope.getRequiredAssignmentIds().size();
            completedLessonCount = completedLessonIds.size();
            completedQuizCount = completedQuizIds.size();
            completedRequiredAssignmentCount = completedRequiredAssignmentIds.size();
        } else {
            totalLessonCount = lessonRepository.countByCourseId(courseId);
            totalQuizCount = quizRepository.countByCourseId(courseId);
            totalRequiredAssignmentCount = assignmentRepository.countRequiredByCourseId(courseId);
            completedLessonCount = completedLessonIds.size();
            completedQuizCount = completedQuizIds.size();
            completedRequiredAssignmentCount = completedRequiredAssignmentIds.size();
        }

        long totalItemCount = totalLessonCount + totalQuizCount + totalRequiredAssignmentCount;
        long completedItemCount = completedLessonCount + completedQuizCount + completedRequiredAssignmentCount;
        int percent = totalItemCount == 0
                ? 0
                : Math.min(100, (int) Math.round((completedItemCount * 100.0) / totalItemCount));
        CertificateDTO activeCertificate = certificateService
                .findActiveUserCourseCertificate(courseId, userId)
                .orElse(null);
        CertificateDTO latestCertificate = certificateService.findUserCourseCertificate(courseId, userId).orElse(null);
        boolean certificateRevoked = activeCertificate == null
                && latestCertificate != null
                && latestCertificate.getRevokedAt() != null;
        Instant certificateRevokedAt = certificateRevoked ? latestCertificate.getRevokedAt() : null;

        return CourseLearningStatusDTO.builder()
                .courseId(courseId)
                .userId(userId)
                .completedLessonIds(completedLessonIds)
                .completedQuizIds(completedQuizIds)
                .completedAssignmentIds(completedAssignmentIds)
                .completedLessonCount(completedLessonCount)
                .totalLessonCount(totalLessonCount)
                .completedQuizCount(completedQuizCount)
                .totalQuizCount(totalQuizCount)
                .completedRequiredAssignmentCount(completedRequiredAssignmentCount)
                .totalRequiredAssignmentCount(totalRequiredAssignmentCount)
                .completedItemCount(completedItemCount)
                .totalItemCount(totalItemCount)
                .percent(percent)
                .certificateId(activeCertificate != null ? activeCertificate.getId() : null)
                .certificateSerial(activeCertificate != null ? activeCertificate.getSerial() : null)
                .certificateRevoked(certificateRevoked)
                .certificateRevokedAt(certificateRevokedAt)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CourseLearningRevisionInfoDTO getLearningRevisionInfo(Long courseId, Long userId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("COURSE_NOT_FOUND"));

        CourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndUserId(courseId, userId)
                .orElseThrow(() -> new NotFoundException(ENROLLMENT_NOT_FOUND));
        return buildRevisionInfo(course, enrollment);
    }

    @Override
    @Transactional
    public CourseLearningRevisionInfoDTO upgradeToActiveRevision(Long courseId, Long userId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("COURSE_NOT_FOUND"));

        CourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndUserIdForUpdate(courseId, userId)
                .orElseThrow(() -> new NotFoundException(ENROLLMENT_NOT_FOUND));

        Long targetRevisionId = resolveTargetRevisionId(course);
        Long sourceRevisionId = enrollment.getLearningRevisionId();
        if (targetRevisionId == null) {
            recordLearningUpgradeEvent(
                    "BLOCKED",
                    "COURSE_ACTIVE_REVISION_NOT_SET",
                    course,
                    userId,
                    sourceRevisionId,
                    null
            );
            throw new ConflictException("COURSE_ACTIVE_REVISION_NOT_SET");
        }

        if (sourceRevisionId == null) {
            enrollment.setLearningRevisionId(targetRevisionId);
            enrollment.setUpgradePolicySnapshot(course.getUpgradePolicy() != null ? course.getUpgradePolicy().name() : null);
            enrollment.setLastUpgradedAt(Instant.now());
            enrollmentRepository.save(enrollment);
            recordLearningUpgradeEvent(
                    "UPGRADED",
                    "INITIAL_PIN_ASSIGNED",
                    course,
                    userId,
                    null,
                    targetRevisionId
            );
            return buildRevisionInfo(course, enrollment);
        }

        if (!targetRevisionId.equals(sourceRevisionId)) {
            if (certificateService.findActiveUserCourseCertificate(courseId, userId).isPresent()) {
                recordLearningUpgradeEvent(
                        "BLOCKED",
                        "COURSE_UPGRADE_BLOCKED_BY_ACTIVE_CERTIFICATE",
                        course,
                        userId,
                        sourceRevisionId,
                        targetRevisionId
                );
                throw new ConflictException("COURSE_UPGRADE_BLOCKED_BY_ACTIVE_CERTIFICATE");
            }
            if (assignmentSubmissionRepository.existsNewestPendingGradeByCourseAndUser(courseId, userId)) {
                recordLearningUpgradeEvent(
                        "BLOCKED",
                        "COURSE_UPGRADE_BLOCKED_BY_PENDING_ASSIGNMENT_GRADE",
                        course,
                        userId,
                        sourceRevisionId,
                        targetRevisionId
                );
                throw new ConflictException("COURSE_UPGRADE_BLOCKED_BY_PENDING_ASSIGNMENT_GRADE");
            }
            if (quizAttemptSessionRepository.existsActiveSessionByCourseAndUser(
                    courseId,
                    userId,
                    QuizAttemptSessionStatus.IN_PROGRESS
            )) {
                recordLearningUpgradeEvent(
                        "BLOCKED",
                        "COURSE_UPGRADE_BLOCKED_BY_QUIZ_IN_PROGRESS",
                        course,
                        userId,
                        sourceRevisionId,
                        targetRevisionId
                );
                throw new ConflictException("COURSE_UPGRADE_BLOCKED_BY_QUIZ_IN_PROGRESS");
            }
            enrollment.setLearningRevisionId(targetRevisionId);
            enrollment.setUpgradePolicySnapshot(course.getUpgradePolicy() != null ? course.getUpgradePolicy().name() : null);
            enrollment.setLastUpgradedAt(Instant.now());
            enrollmentRepository.save(enrollment);
            recordLearningUpgradeEvent("UPGRADED", "NONE", course, userId, sourceRevisionId, targetRevisionId);
        } else {
            recordLearningUpgradeEvent("SKIPPED", "NO_REVISION_CHANGE", course, userId, sourceRevisionId, targetRevisionId);
        }

        return buildRevisionInfo(course, enrollment);
    }

    @Override
    @Transactional
    public int recalculateCourseProgress(Long courseId, Long userId) {
        CourseEnrollment enrollment = enrollmentRepository.findByCourseIdAndUserIdForUpdate(courseId, userId)
                .orElseThrow(() -> new NotFoundException(ENROLLMENT_NOT_FOUND));

        CourseLearningStatusDTO status = calculateCourseLearningStatus(courseId, userId, enrollment);
        enrollment.setProgressPercent(status.getPercent());

        if (enrollment.getStatus() == null) {
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
        }

        if (status.getPercent() >= 100 && enrollment.getStatus() != EnrollmentStatus.DROPPED) {
            enrollment.setStatus(EnrollmentStatus.COMPLETED);
            certificateService.issueCourseCertificate(courseId, userId, status);
        }

        enrollmentRepository.save(enrollment);

        log.debug(
                "Recalculated course {} progress for user {} to {}%",
                courseId,
                userId,
                status.getPercent()
        );
        return status.getPercent();
    }

    private ProgressComputationScope resolveProgressComputationScope(
            Long courseId,
            Long userId,
            CourseEnrollment enrollmentHint
    ) {
        Optional<CourseEnrollment> enrollmentOpt = enrollmentHint != null
                ? Optional.of(enrollmentHint)
                : safeOptional(enrollmentRepository.findByCourseIdAndUserId(courseId, userId));
        if (enrollmentOpt.isEmpty()) {
            recordProgressScopeEvent("fallback", "enrollment_not_found", courseId, userId, null);
            return ProgressComputationScope.fallback("enrollment_not_found");
        }

        CourseEnrollment enrollment = enrollmentOpt.get();
        Long learningRevisionId = enrollment.getLearningRevisionId();
        String revisionScopeReasonCode = "using_learning_revision_snapshot";

        if (learningRevisionId == null) {
            Optional<Course> courseOpt = safeOptional(courseRepository.findById(courseId));
            Long fallbackRevisionId = courseOpt.map(this::resolveTargetRevisionId).orElse(null);
            if (fallbackRevisionId == null) {
                recordProgressScopeEvent("fallback", "learning_revision_missing", courseId, userId, null);
                return ProgressComputationScope.fallback("learning_revision_missing");
            }

            learningRevisionId = fallbackRevisionId;
            revisionScopeReasonCode = "learning_revision_missing_using_course_revision";
        }

        Optional<CourseRevision> revisionOpt = safeOptional(
                courseRevisionRepository.findByIdAndCourse_Id(learningRevisionId, courseId)
        );
        if (revisionOpt.isEmpty()) {
            recordProgressScopeEvent("fallback", "revision_not_found", courseId, userId, learningRevisionId);
            return ProgressComputationScope.fallback("revision_not_found");
        }

        RevisionProgressSnapshot snapshot = extractRevisionProgressSnapshot(revisionOpt.get().getContentSnapshotJson());
        if (snapshot.getSnapshotVersion() == null) {
            recordProgressScopeEvent("fallback", "snapshot_version_missing", courseId, userId, learningRevisionId);
            return ProgressComputationScope.fallback("snapshot_version_missing");
        }
        if (snapshot.getSnapshotVersion() != CONTENT_SNAPSHOT_VERSION_V1) {
            recordProgressScopeEvent("fallback", "unsupported_snapshot_version", courseId, userId, learningRevisionId);
            return ProgressComputationScope.fallback("unsupported_snapshot_version");
        }
        if (!snapshot.hasAnyTrackedItems()) {
            recordProgressScopeEvent(
                    "fallback",
                    "revision_snapshot_missing_tracked_item_ids",
                    courseId,
                    userId,
                    learningRevisionId
            );
            return ProgressComputationScope.fallback("revision_snapshot_missing_tracked_item_ids");
        }

        recordProgressScopeEvent("revision", revisionScopeReasonCode, courseId, userId, learningRevisionId);
        return ProgressComputationScope.revision(learningRevisionId, snapshot);
    }

    private List<Long> filterByAllowedIds(List<Long> completedIds, Set<Long> allowedIds) {
        if (completedIds == null || completedIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (allowedIds == null || allowedIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> filtered = new ArrayList<>(completedIds.size());
        for (Long completedId : completedIds) {
            if (completedId != null && allowedIds.contains(completedId)) {
                filtered.add(completedId);
            }
        }
        return filtered;
    }

    private RevisionProgressSnapshot extractRevisionProgressSnapshot(JsonNode snapshot) {
        if (snapshot == null || snapshot.isNull()) {
            return RevisionProgressSnapshot.empty();
        }

        Integer snapshotVersion = parseSnapshotVersion(snapshot.path("snapshotVersion"));

        boolean hasStructuredContent = snapshot.has("modules")
                || snapshot.has("lessons")
                || snapshot.has("quizzes")
                || snapshot.has("assignments");

        Set<Long> lessonIds = new LinkedHashSet<>();
        Set<Long> quizIds = new LinkedHashSet<>();
        Set<Long> assignmentIds = new LinkedHashSet<>();
        Set<Long> requiredAssignmentIds = new LinkedHashSet<>();

        collectLessonLikeContentItems(
                snapshot.path("lessons"),
                lessonIds,
                quizIds,
                assignmentIds,
                requiredAssignmentIds
        );
        collectContentItems(snapshot.path("quizzes"), quizIds, null);
        collectContentItems(snapshot.path("assignments"), assignmentIds, requiredAssignmentIds);

        JsonNode modules = snapshot.path("modules");
        if (modules.isArray()) {
            for (JsonNode moduleNode : modules) {
                collectLessonLikeContentItems(
                        moduleNode.path("lessons"),
                        lessonIds,
                        quizIds,
                        assignmentIds,
                        requiredAssignmentIds
                );
                collectContentItems(moduleNode.path("quizzes"), quizIds, null);
                collectContentItems(moduleNode.path("assignments"), assignmentIds, requiredAssignmentIds);
            }
        }

        return new RevisionProgressSnapshot(
                lessonIds,
                quizIds,
                assignmentIds,
                requiredAssignmentIds,
                hasStructuredContent,
                snapshotVersion
        );
    }

    private Integer parseSnapshotVersion(JsonNode versionNode) {
        if (versionNode == null || versionNode.isNull() || versionNode.isMissingNode()) {
            return null;
        }
        if (versionNode.canConvertToInt()) {
            return versionNode.asInt();
        }
        if (versionNode.isTextual()) {
            String text = versionNode.asText("");
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void collectContentItems(
            JsonNode itemArrayNode,
            Set<Long> itemIds,
            Set<Long> requiredAssignmentIds
    ) {
        if (itemArrayNode == null || !itemArrayNode.isArray()) {
            return;
        }

        for (JsonNode itemNode : itemArrayNode) {
            Long itemId = parseId(itemNode.path("id"));
            if (itemId == null) {
                continue;
            }
            itemIds.add(itemId);
            if (requiredAssignmentIds != null && isRequiredAssignmentNode(itemNode)) {
                requiredAssignmentIds.add(itemId);
            }
        }
    }

    /**
     * Backward-compatible parser for legacy snapshots where quizzes/assignments were stored
     * under the "lessons" array with a "type" discriminator.
     */
    private void collectLessonLikeContentItems(
            JsonNode itemArrayNode,
            Set<Long> lessonIds,
            Set<Long> quizIds,
            Set<Long> assignmentIds,
            Set<Long> requiredAssignmentIds
    ) {
        if (itemArrayNode == null || !itemArrayNode.isArray()) {
            return;
        }

        for (JsonNode itemNode : itemArrayNode) {
            Long itemId = parseId(itemNode.path("id"));
            if (itemId == null) {
                continue;
            }

            String normalizedType = resolveLessonLikeType(itemNode);
            if (Objects.equals(normalizedType, "quiz")) {
                quizIds.add(itemId);
                continue;
            }
            if (Objects.equals(normalizedType, "assignment")) {
                assignmentIds.add(itemId);
                if (requiredAssignmentIds != null && isRequiredAssignmentNode(itemNode)) {
                    requiredAssignmentIds.add(itemId);
                }
                continue;
            }
            lessonIds.add(itemId);
        }
    }

    private String resolveLessonLikeType(JsonNode itemNode) {
        if (itemNode == null || itemNode.isNull()) {
            return "lesson";
        }
        JsonNode typeNode = itemNode.path("type");
        if (typeNode.isMissingNode() || typeNode.isNull()) {
            typeNode = itemNode.path("lessonType");
        }
        if (typeNode.isMissingNode() || typeNode.isNull()) {
            typeNode = itemNode.path("itemType");
        }
        if (!typeNode.isTextual()) {
            return "lesson";
        }
        String normalized = typeNode.asText("").trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "lesson";
        }
        if (Objects.equals(normalized, "quiz")
                || Objects.equals(normalized, "assignment")
                || Objects.equals(normalized, "lesson")
                || Objects.equals(normalized, "reading")
                || Objects.equals(normalized, "video")) {
            return normalized;
        }
        return "lesson";
    }

    private Long parseId(JsonNode idNode) {
        if (idNode == null || idNode.isNull()) {
            return null;
        }
        if (idNode.canConvertToLong()) {
            return idNode.asLong();
        }
        if (!idNode.isTextual()) {
            return null;
        }

        String raw = idNode.asText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean isRequiredAssignmentNode(JsonNode assignmentNode) {
        JsonNode isRequiredNode = assignmentNode.path("isRequired");
        if (isRequiredNode.isMissingNode() || isRequiredNode.isNull()) {
            isRequiredNode = assignmentNode.path("required");
        }

        if (isRequiredNode.isMissingNode() || isRequiredNode.isNull()) {
            return true;
        }
        if (isRequiredNode.isBoolean()) {
            return isRequiredNode.asBoolean();
        }
        if (isRequiredNode.isNumber()) {
            return isRequiredNode.asInt() != 0;
        }
        if (isRequiredNode.isTextual()) {
            String normalized = isRequiredNode.asText("").trim().toLowerCase();
            if (normalized.isEmpty()) {
                return true;
            }
            if (Objects.equals(normalized, "true")
                    || Objects.equals(normalized, "1")
                    || Objects.equals(normalized, "yes")) {
                return true;
            }
            if (Objects.equals(normalized, "false")
                    || Objects.equals(normalized, "0")
                    || Objects.equals(normalized, "no")) {
                return false;
            }
        }
        return true;
    }

    private void recordProgressScopeEvent(
            String scope,
            String reasonCode,
            Long courseId,
            Long userId,
            Long revisionId
    ) {
        incrementCounter(
                PROGRESS_REVISION_METRIC,
                1,
                "scope",
                scope,
                "reason_code",
                reasonCode
        );
        log.info(
                "course_learning_progress_scope_event scope={} reasonCode={} courseId={} userId={} revisionId={}",
                scope,
                reasonCode,
                courseId,
                userId,
                revisionId
        );
    }

    private <T> Optional<T> safeOptional(Optional<T> candidate) {
        return candidate == null ? Optional.empty() : candidate;
    }

    private Long resolveTargetRevisionId(Course course) {
        if (course == null) {
            return null;
        }
        return course.getActiveRevisionId();
    }

    private CourseLearningRevisionInfoDTO buildRevisionInfo(Course course, CourseEnrollment enrollment) {
        Long learningRevisionId = resolveEffectiveLearningRevisionId(course, enrollment);
        Long activeRevisionId = course.getActiveRevisionId();
        return CourseLearningRevisionInfoDTO.builder()
                .courseId(course.getId())
                .userId(enrollment.getUser().getId())
                .learningRevisionId(learningRevisionId)
                .activeRevisionId(activeRevisionId)
                .latestRevisionId(course.getLatestRevisionId())
                .upgradePolicy(course.getUpgradePolicy() != null ? course.getUpgradePolicy().name() : null)
                .hasNewerRevision(activeRevisionId != null
                        && learningRevisionId != null
                        && !learningRevisionId.equals(activeRevisionId))
                .build();
    }

    private Long resolveEffectiveLearningRevisionId(Course course, CourseEnrollment enrollment) {
        if (enrollment != null && enrollment.getLearningRevisionId() != null) {
            return enrollment.getLearningRevisionId();
        }
        return resolveTargetRevisionId(course);
    }

    private void recordLearningUpgradeEvent(
            String result,
            String reasonCode,
            Course course,
            Long userId,
            Long sourceRevisionId,
            Long targetRevisionId
    ) {
        String policy = course != null && course.getUpgradePolicy() != null
                ? course.getUpgradePolicy().name()
                : "UNKNOWN";
        incrementCounter(
                LEARNING_UPGRADE_METRIC,
                1,
                "result",
                result,
                "reason_code",
                reasonCode,
                "policy",
                policy
        );
        log.info(
                "course_learning_revision_upgrade_event result={} reasonCode={} courseId={} userId={} sourceRevisionId={} targetRevisionId={} policy={}",
                result,
                reasonCode,
                course != null ? course.getId() : null,
                userId,
                sourceRevisionId,
                targetRevisionId,
                policy
        );
    }

    private void incrementCounter(String metricName, double amount, String... tags) {
        if (amount <= 0 || meterRegistry == null) {
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

    private static final class RevisionProgressSnapshot {
        private final Set<Long> lessonIds;
        private final Set<Long> quizIds;
        private final Set<Long> assignmentIds;
        private final Set<Long> requiredAssignmentIds;
        private final boolean structuredContent;
        private final Integer snapshotVersion;

        private RevisionProgressSnapshot(
                Set<Long> lessonIds,
                Set<Long> quizIds,
                Set<Long> assignmentIds,
                Set<Long> requiredAssignmentIds,
                boolean structuredContent,
                Integer snapshotVersion
        ) {
            this.lessonIds = lessonIds == null ? Collections.emptySet() : lessonIds;
            this.quizIds = quizIds == null ? Collections.emptySet() : quizIds;
            this.assignmentIds = assignmentIds == null ? Collections.emptySet() : assignmentIds;
            this.requiredAssignmentIds = requiredAssignmentIds == null ? Collections.emptySet() : requiredAssignmentIds;
            this.structuredContent = structuredContent;
            this.snapshotVersion = snapshotVersion;
        }

        private static RevisionProgressSnapshot empty() {
            return new RevisionProgressSnapshot(
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    false,
                    null
            );
        }

        private boolean hasAnyTrackedItems() {
            return !(lessonIds.isEmpty() && quizIds.isEmpty() && requiredAssignmentIds.isEmpty());
        }

        private boolean hasStructuredContent() {
            return structuredContent;
        }

        private Integer getSnapshotVersion() {
            return snapshotVersion;
        }
    }

    private static final class ProgressComputationScope {
        private final boolean revisionScoped;
        private final Long revisionId;
        private final Set<Long> lessonIds;
        private final Set<Long> quizIds;
        private final Set<Long> assignmentIds;
        private final Set<Long> requiredAssignmentIds;
        private final String reasonCode;

        private ProgressComputationScope(
                boolean revisionScoped,
                Long revisionId,
                Set<Long> lessonIds,
                Set<Long> quizIds,
                Set<Long> assignmentIds,
                Set<Long> requiredAssignmentIds,
                String reasonCode
        ) {
            this.revisionScoped = revisionScoped;
            this.revisionId = revisionId;
            this.lessonIds = lessonIds == null ? Collections.emptySet() : lessonIds;
            this.quizIds = quizIds == null ? Collections.emptySet() : quizIds;
            this.assignmentIds = assignmentIds == null ? Collections.emptySet() : assignmentIds;
            this.requiredAssignmentIds = requiredAssignmentIds == null ? Collections.emptySet() : requiredAssignmentIds;
            this.reasonCode = reasonCode;
        }

        private static ProgressComputationScope revision(Long revisionId, RevisionProgressSnapshot snapshot) {
            return new ProgressComputationScope(
                    true,
                    revisionId,
                    snapshot.lessonIds,
                    snapshot.quizIds,
                    snapshot.assignmentIds,
                    snapshot.requiredAssignmentIds,
                    "using_learning_revision_snapshot"
            );
        }

        private static ProgressComputationScope fallback(String reasonCode) {
            return new ProgressComputationScope(
                    false,
                    null,
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    Collections.emptySet(),
                    reasonCode
            );
        }

        private boolean isRevisionScoped() {
            return revisionScoped;
        }

        private Long getRevisionId() {
            return revisionId;
        }

        private Set<Long> getLessonIds() {
            return lessonIds;
        }

        private Set<Long> getQuizIds() {
            return quizIds;
        }

        private Set<Long> getAssignmentIds() {
            return assignmentIds;
        }

        private Set<Long> getRequiredAssignmentIds() {
            return requiredAssignmentIds;
        }

        @SuppressWarnings("unused")
        private String getReasonCode() {
            return reasonCode;
        }
    }
}
