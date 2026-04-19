package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentCriteria;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.SubmissionCriteriaScore;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.mapper.AssignmentMapper;
import com.exe.skillverse_backend.course_service.mapper.AssignmentSubmissionMapper;
import com.exe.skillverse_backend.course_service.repository.AssignmentCriteriaRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.repository.SubmissionCriteriaScoreRepository;
import com.exe.skillverse_backend.course_service.service.AssignmentService;
import com.exe.skillverse_backend.course_service.service.CourseLearningProgressService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.dto.NotificationPayload;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.shared.service.MediaService;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentCreateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentCriteriaDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentDetailDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentGradeDTO;
import com.exe.skillverse_backend.course_service.service.dto.AssignmentUpdateResultDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionCreateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionDetailDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentUpdateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.CriteriaScoreDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.MentorSubmissionItemDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.MentorSubmissionStatsDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.PageResponse;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.PendingSubmissionItemDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleDetailDTO;
import com.exe.skillverse_backend.assignment_ai_service.event.SubmissionCreatedEvent;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentCriteriaRepository criteriaRepository;
    private final SubmissionCriteriaScoreRepository criteriaScoreRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final ModuleRepository moduleRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentSubmissionMapper submissionMapper;
    private final UserProfileRepository userProfileRepository;
    private final Clock clock;
    private final CourseLearningProgressService courseLearningProgressService;
    private final RevisionPinnedContentResolver revisionPinnedContentResolver;
    private final CloudinaryService cloudinaryService;
    private final MediaService mediaService;

    @Override
    @Transactional
    public AssignmentDetailDTO createAssignment(Long moduleId, AssignmentCreateDTO dto, Long actorId) {
        log.info("Creating assignment '{}' for module {} by actor {}", dto.getTitle(), moduleId, actorId);
        
        Module module = getModuleOrThrow(moduleId);
        ensureAuthorOrAdmin(actorId, module.getCourse().getAuthor().getId());
        
        validateCreateAssignmentRequest(dto);
        
        Assignment assignment = assignmentMapper.toEntity(dto, module);
        if (assignment.getIsRequired() == null) {
            assignment.setIsRequired(true);
        }
        if (assignment.getAiGradingEnabled() == null) {
            assignment.setAiGradingEnabled(false);
        }
        if (assignment.getTrustAiEnabled() == null) {
            assignment.setTrustAiEnabled(false);
        }
        if (assignment.getGradingStyle() == null) {
            assignment.setGradingStyle("STANDARD");
        }
        // Auto-enable trustAi when AI grading is enabled — AI chấm xong tự confirm
        if (Boolean.TRUE.equals(assignment.getAiGradingEnabled())) {
            assignment.setTrustAiEnabled(true);
        }
        assignment.setCreatedAt(now());
        assignment.setUpdatedAt(now());
        if (dto.getCriteria() != null) {
            assignment.setCriteria(buildCriteriaEntities(dto.getCriteria(), assignment));
        }
        
        Assignment saved = assignmentRepository.save(assignment);
        log.info("Assignment {} created for module {} by actor {}", saved.getId(), moduleId, actorId);
        
        return assignmentMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public AssignmentUpdateResultDTO updateAssignment(Long assignmentId, AssignmentUpdateDTO dto, Long actorId) {
        log.info("Updating assignment {} by actor {}", assignmentId, actorId);

        Assignment assignment = getAssignmentOrThrow(assignmentId);
        ensureAuthorOrAdmin(actorId, assignment.getModule().getCourse().getAuthor().getId());

        validateUpdateAssignmentRequest(dto, assignment);

        assignmentMapper.updateEntity(assignment, dto);
        // Auto-enable trustAi when AI grading is enabled — AI chấm xong tự confirm
        if (Boolean.TRUE.equals(assignment.getAiGradingEnabled())) {
            assignment.setTrustAiEnabled(true);
        }
        assignment.setUpdatedAt(now());

        boolean criteriaChanged = dto.getCriteria() != null;
        if (criteriaChanged) {
            // Cascade-delete SubmissionCriteriaScore rows that reference old criteria
            // to prevent FK orphans when criteria are replaced
            List<AssignmentCriteria> oldCriteria = criteriaRepository.findByAssignmentIdOrderByOrderIndexAsc(assignmentId);
            for (AssignmentCriteria old : oldCriteria) {
                criteriaScoreRepository.deleteByCriteriaId(old.getId());
            }
            // Must clear + addAll on the SAME collection reference —
            // replacing via setCriteria() breaks Hibernate orphanRemoval tracking
            assignment.getCriteria().clear();
            assignment.getCriteria().addAll(buildCriteriaEntities(dto.getCriteria(), assignment));
        }

        Assignment saved = assignmentRepository.save(assignment);
        log.info("Assignment {} updated by actor {}", assignmentId, actorId);

        // Count submissions for FE warning modal
        long gradedCount = submissionRepository.countByAssignmentIdAndScoreIsNotNull(assignmentId);
        long aiPendingCount = submissionRepository.countByAssignmentIdAndIsAiGradedTrueAndMentorConfirmedNull(assignmentId);
        long pendingCount = submissionRepository.countByAssignmentIdAndScoreIsNull(assignmentId);

        if (criteriaChanged && (gradedCount > 0 || aiPendingCount > 0)) {
            log.warn("Assignment {} criteria updated with {} graded submissions "
                    + "and {} AI-pending submissions. AI suggestions may be invalidated.",
                assignmentId, gradedCount, aiPendingCount);
        }

        return new AssignmentUpdateResultDTO(
                assignmentMapper.toDetailDto(saved),
                gradedCount,
                aiPendingCount,
                pendingCount
        );
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentDetailDTO getAssignmentById(Long assignmentId, Long actorId) {
        log.debug("Getting assignment details for ID {}", assignmentId);
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
        Course course = assignment.getModule().getCourse();
        ensureCanReadCourseStructure(course.getId(), course.getAuthor().getId(), course.getStatus(), actorId);
        ensurePinnedAssignmentAccessibleForLearner(course, actorId, assignmentId);
        return assignmentMapper.toDetailDto(assignment);
    }

    @Override
    @Transactional
    public void deleteAssignment(Long assignmentId, Long actorId) {
        log.info("Deleting assignment {} by actor {}", assignmentId, actorId);
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
        ensureAuthorOrAdmin(actorId, assignment.getModule().getCourse().getAuthor().getId());
        
        long submissionCount = submissionRepository.countByAssignmentId(assignmentId);
        if (submissionCount > 0) {
            log.info("Assignment {} has {} submissions — cleaning up criteria scores before deletion",
                    assignmentId, submissionCount);
            // SubmissionCriteriaScore has no cascade from AssignmentSubmission,
            // so delete them first or FK violation will occur.
            criteriaScoreRepository.deleteByAssignmentId(assignmentId);
        }
        
        assignmentRepository.delete(assignment);
        log.info("Assignment {} deleted by actor {}", assignmentId, actorId);
    }

    @Override
    @Transactional
    public AssignmentSubmissionDetailDTO submit(Long assignmentId, Long userId, AssignmentSubmissionCreateDTO dto) {
        log.info("Submitting assignment {} by user {}", assignmentId, userId);
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
        Course course = assignment.getModule().getCourse();
        Long courseId = course.getId();
        
        // Allow submissions from active learners and already-completed learners.
        var enrollmentOpt = enrollmentRepository.findByCourseIdAndUserId(courseId, userId);
        
        if (enrollmentOpt.isEmpty()) {
            log.warn("No enrollment found for user {} in course {}", userId, courseId);
            throw new AccessDeniedException("USER_NOT_ENROLLED");
        }
        
        var enrollment = enrollmentOpt.get();
        if (!hasLearningAccess(enrollment.getStatus())) {
            log.warn("User {} has enrollment status {} (no learning access) for course {}",
                    userId, enrollment.getStatus(), courseId);
            throw new AccessDeniedException("USER_NOT_ENROLLED");
        }

        ensurePinnedAssignmentAccessibleForLearner(course, userId, assignmentId);

        // DEADCODE: isLate never set — no deadline input in mentor form (as of 2026-04-15).
        // LessonEditorAssignment.tsx has no dueAt field → assignment.getDueAt() always null
        // → isLate always false → LATE_PENDING never triggered. Left for future deadline feature.
        // boolean isLate = false;
        // if (assignment.getDueAt() != null && now().isAfter(assignment.getDueAt())) {
        //     isLate = true;
        // }
        
        // Load user entity properly
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND"));
        
        // ===== Submission Gate + Audit Trail (Coursera pattern) =====
        // DB retains ALL submissions for audit. No hard deletes.
        List<AssignmentSubmission> existing = submissionRepository
                .findByAssignmentIdAndUserIdOrderByAttemptNumberDesc(assignmentId, userId);
        
        int nextAttemptNumber = 1;
        
        if (!existing.isEmpty()) {
            AssignmentSubmission newest = existing.get(0);
            nextAttemptNumber = newest.getAttemptNumber() + 1;

            // Gate: block if pending grading
            if (newest.getScore() == null) {
                throw new BadRequestException("SUBMISSION_PENDING_GRADING");
            }
            // Gate: block if already passed — no resubmission needed
            if (Boolean.TRUE.equals(newest.getIsPassed())) {
                throw new BadRequestException("ASSIGNMENT_ALREADY_PASSED");
            }
            // isPassed == false (FAIL) → allow reattempt, fall through

            // Mark current newest as previous (keep all history, just swap flags)
            newest.setIsNewest(false);
            newest.setIsPrevious(true);
            submissionRepository.save(newest);

            // Also clear isPrevious flag on any earlier submissions
            for (int i = 1; i < existing.size(); i++) {
                AssignmentSubmission older = existing.get(i);
                if (Boolean.TRUE.equals(older.getIsPrevious())) {
                    older.setIsPrevious(false);
                    submissionRepository.save(older);
                }
            }
        }
        
        // Load file media if provided, then validate
        Media fileMedia = null;
        if (dto.getFileMediaId() != null) {
            fileMedia = mediaRepository.findById(dto.getFileMediaId())
                    .orElseThrow(() -> new NotFoundException("MEDIA_NOT_FOUND"));

            // Verify: file must belong to the user submitting
            if (!fileMedia.getUploadedByUser().getId().equals(userId)) {
                throw new AccessDeniedException("File không thuộc về bạn");
            }
        }

        validateSubmissionRequest(dto, assignment, fileMedia);

        // Validate file against AI grading rules when applicable
        if (fileMedia != null) {
            mediaService.validateAssignmentFile(
                    fileMedia.getType(),
                    fileMedia.getFileSize(),
                    Boolean.TRUE.equals(assignment.getAiGradingEnabled())
            );
        }

        AssignmentSubmission submission = submissionMapper.toEntity(dto, assignment, user, fileMedia);
        submission.setSubmittedAt(now());
        submission.setAttemptNumber(nextAttemptNumber);
        submission.setIsNewest(true);
        submission.setIsPrevious(false);
        // DEADCODE: isLate always false — no deadline input in mentor form
        submission.setIsLate(false);
        // gradingMode: null/AI = AI chấm, MENTOR = skip AI, vào mentor queue ngay
        String gradingMode = dto.getGradingMode() != null
                ? dto.getGradingMode().name() : "AI";
        submission.setGradingMode(gradingMode);
        boolean skipAi = "MENTOR".equals(gradingMode);

        AssignmentSubmission saved = submissionRepository.save(submission);
        log.info("Assignment {} submitted by user {}, submission id {}, attempt #{}, gradingMode={}",
                assignmentId, userId, saved.getId(), nextAttemptNumber, gradingMode);

        // DEADCODE: isLate always false → late notification never sent. No deadline input in mentor form.
        // if (isLate) { notificationService.createNotification(...) }
        // Publish SubmissionCreatedEvent — only if NOT MENTOR mode
        if (!skipAi) {
            eventPublisher.publishEvent(new SubmissionCreatedEvent(
                    this,
                    saved.getId(),
                    assignmentId,
                    userId
            ));
        } else {
            notificationService.createNotification(
                    userId,
                    "Bài đã được gửi cho mentor chấm thủ công",
                    "Bài tập '" + assignment.getTitle() + "' sẽ được mentor xem xét và chấm điểm.",
                    NotificationType.ASSIGNMENT_GRADED,
                    saved.getId().toString(),
                    NotificationPayload.forAssignmentGraded(assignmentId, saved.getId()),
                    null
            );
        }

        return toDetailWithCriteria(saved);
    }

    @Override
    @Transactional
    public AssignmentSubmissionDetailDTO grade(Long submissionId, Long graderId,
                                                AssignmentGradeDTO grading,
                                                BigDecimal legacyScore, String legacyFeedback) {
        // Merge legacy query-param grading into DTO
        AssignmentGradeDTO payload = grading != null
                ? grading
                : new AssignmentGradeDTO(legacyScore, legacyFeedback, null, null);

        log.info("Grading submission {} by grader {}", submissionId, graderId);
        
        AssignmentSubmission submission = getSubmissionOrThrow(submissionId);
        
        // Guard: only grade the newest version — grading a previous version is a logic error
        if (!Boolean.TRUE.equals(submission.getIsNewest())) {
            throw new BadRequestException("Cannot grade a previous submission version. Only the newest submission can be graded.");
        }
        
        Assignment assignment = submission.getAssignment();
        
        // Check if grader has permission (course author/mentor/admin)
        ensureAuthorOrAdmin(graderId, assignment.getModule().getCourse().getAuthor().getId());

        BigDecimal totalScore = payload.getScore();
        if (payload.getCriteriaScores() != null && !payload.getCriteriaScores().isEmpty()) {
            totalScore = applyCriteriaScores(submission, assignment, payload.getCriteriaScores());
        }

        validateGradingRequest(totalScore, assignment.getMaxScore());
        
        // Load grader user properly
        User grader = userRepository.findById(graderId)
                .orElseThrow(() -> new NotFoundException("GRADER_NOT_FOUND"));
        
        submission.setScore(totalScore);
        submission.setFeedback(payload.getFeedback());
        submission.setGradedBy(grader);
        submission.setGradedAt(now());

        // Mark as AI-graded + mentor-confirmed when mentor confirms an AI pre-grade result
        if (Boolean.TRUE.equals(payload.getIsAiGrade())) {
            submission.setIsAiGraded(true);
            submission.setMentorConfirmed(true);
        }

        // Persist isPassed once at grading time (immune to later criteria edits)
        List<CriteriaScoreDTO> gradedCriteriaScores = loadCriteriaScores(submission.getId());
        boolean passed = computeIsPassed(assignment, gradedCriteriaScores, totalScore);
        submission.setIsPassed(passed);
        
        // Handle dispute: if this submission was flagged for re-grade
        if (Boolean.TRUE.equals(submission.getDisputeFlag())) {
            submission.setDisputeFlag(false);
            submission.setDisputeAt(null);
            submission.setDisputeReason(null);
            log.info("Mentor re-graded disputed submission {}. isPassed recalculated.", submissionId);

            // Notify student about re-grade result
            String reGradeStatus = passed ? "PASSED ✓" : "Cần cải thiện";
            notificationService.createNotification(
                    submission.getUser().getId(),
                    "Mentor đã xem xét lại bài của bạn",
                    "Mentor đã xem xét lại bài '" + assignment.getTitle()
                            + "': " + totalScore + "/" + assignment.getMaxScore() + " - " + reGradeStatus,
                    NotificationType.ASSIGNMENT_GRADED,
                    submission.getId().toString(),
                    NotificationPayload.forAssignmentGraded(assignment.getId(), submission.getId()),
                    graderId
            );
        }

        AssignmentSubmission saved = submissionRepository.save(submission);
        log.info("Submission {} graded by grader {} with score {}, passed={}", submissionId, graderId, totalScore, passed);

        courseLearningProgressService.recalculateCourseProgress(
                assignment.getModule().getCourse().getId(),
                submission.getUser().getId()
        );
        
        // Build result DTO (reads isPassed from entity, no recomputation)
        AssignmentSubmissionDetailDTO detail = toDetailWithCriteria(saved);
        
        // Send grading notification to student using persisted pass/fail
        String passStatus = passed ? "PASSED ✓" : "Cần cải thiện";
        notificationService.createNotification(
                submission.getUser().getId(),
                "Bài tập đã được chấm điểm",
                "Bài tập '" + assignment.getTitle() + "' đã được chấm: " + totalScore + "/" + assignment.getMaxScore() + " - " + passStatus,
                NotificationType.ASSIGNMENT_GRADED,
                saved.getId().toString(),
                NotificationPayload.forAssignmentGraded(assignment.getId(), saved.getId()),
                graderId
        );
        
        return detail;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AssignmentSubmissionDetailDTO> listSubmissions(Long assignmentId, Pageable pageable) {
        log.debug("Listing submissions for assignment {} with page {}", assignmentId, pageable.getPageNumber());

        Assignment assignment = getAssignmentOrThrow(assignmentId);

        // Ensure only the course author or admin can list all submissions
        Long actorId = getCurrentUserId();
        ensureAuthorOrAdmin(actorId, assignment.getModule().getCourse().getAuthor().getId());

        // Only show newest version per student (avoid duplicates from previous versions)
        Page<AssignmentSubmission> submissions = submissionRepository
                .findLatestSubmissionsByAssignmentId(assignmentId, pageable);

        return PageResponse.<AssignmentSubmissionDetailDTO>builder()
                .content(submissions.map(this::toDetailWithCriteria).getContent())
                .page(submissions.getNumber())
                .size(submissions.getSize())
                .totalElements(submissions.getTotalElements())
                .totalPages(submissions.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentSummaryDTO> listAssignmentsByModule(Long moduleId, Long actorId) {
        log.debug("Listing assignments for module {}", moduleId);
        
        Module module = getModuleOrThrow(moduleId);
        Course course = module.getCourse();
        ensureCanReadCourseStructure(course.getId(), course.getAuthor().getId(), course.getStatus(), actorId);

        if (shouldUsePinnedSnapshotForLearner(course, actorId)) {
            ModuleDetailDTO pinnedModule = revisionPinnedContentResolver
                    .resolvePinnedModule(course, actorId, moduleId)
                    .orElseThrow(() -> new NotFoundException("MODULE_NOT_FOUND"));
            return pinnedModule.getAssignments() != null ? pinnedModule.getAssignments() : List.of();
        }
        
        List<Assignment> assignments = assignmentRepository.findByModuleIdOrderByOrderIndexAsc(moduleId);
        
        return assignments.stream()
                .map(assignmentMapper::toSummaryDto)
                .toList();
    }

    // ===== Helper Methods =====
    
    private Module getModuleOrThrow(Long moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("MODULE_NOT_FOUND"));
    }
    
    private Assignment getAssignmentOrThrow(Long assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND"));
    }
    
    private AssignmentSubmission getSubmissionOrThrow(Long submissionId) {
        return submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("SUBMISSION_NOT_FOUND"));
    }

    /**
     * Extracts the current authenticated user's ID from the SecurityContext.
     * Used in service methods that need the caller's identity without it being passed as a parameter.
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("UNAUTHORIZED");
        }
        // The JWT principal is a Jwt object; its "userId" claim was set during token issuance
        if (auth.getPrincipal() instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString("userId");
            if (userId != null) return Long.parseLong(userId);
            return Long.parseLong(jwt.getSubject());
        }
        throw new AccessDeniedException("UNAUTHORIZED");
    }

    private void ensureAuthorOrAdmin(Long actorId, Long authorId) {
        if (!isAuthorOrAdmin(actorId, authorId)) {
            throw new AccessDeniedException("FORBIDDEN");
        }
    }

    private void validateCreateAssignmentRequest(AssignmentCreateDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BadRequestException("Assignment title is required");
        }
        if (dto.getMaxScore() != null && dto.getMaxScore().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Max score must be positive");
        }
        if (dto.getPassingScore() != null && dto.getMaxScore() != null
                && dto.getPassingScore().compareTo(dto.getMaxScore()) > 0) {
            throw new BadRequestException("Passing score cannot exceed max score");
        }
        validateCriteriaRequired(dto.getCriteria());
        validateCriteria(dto.getCriteria());
        validateCriteriaSumMatchesMaxScore(dto.getCriteria(), dto.getMaxScore());
    }

    private void validateUpdateAssignmentRequest(AssignmentUpdateDTO dto, Assignment assignment) {
        if (dto.getTitle() != null && dto.getTitle().isBlank()) {
            throw new BadRequestException("Assignment title cannot be blank");
        }
        if (dto.getMaxScore() != null && dto.getMaxScore().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Max score must be positive");
        }
        if (dto.getPassingScore() != null) {
            BigDecimal maxScore = dto.getMaxScore() != null ? dto.getMaxScore() : assignment.getMaxScore();
            if (maxScore != null && dto.getPassingScore().compareTo(maxScore) > 0) {
                throw new BadRequestException("Passing score cannot exceed max score");
            }
        }
        if (dto.getCriteria() != null) {
            validateCriteriaRequired(dto.getCriteria());
        }
        validateCriteria(dto.getCriteria());

        // Fail-fast: if maxScore changes, validate against existing criteria from DB when none provided
        BigDecimal effectiveMaxScore = dto.getMaxScore() != null ? dto.getMaxScore() : assignment.getMaxScore();
        List<AssignmentCriteriaDTO> criteriaToValidate = dto.getCriteria();
        if (criteriaToValidate == null && dto.getMaxScore() != null
                && assignment.getCriteria() != null && !assignment.getCriteria().isEmpty()) {
            // Mentor changed maxScore but didn't send new criteria — validate against existing
            criteriaToValidate = assignment.getCriteria().stream()
                    .map(c -> AssignmentCriteriaDTO.builder()
                            .maxPoints(c.getMaxPoints())
                            .build())
                    .toList();
        }
        validateCriteriaSumMatchesMaxScore(criteriaToValidate, effectiveMaxScore);
    }

    private void validateCriteriaRequired(List<AssignmentCriteriaDTO> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            throw new BadRequestException("Assignment must have at least one rubric criterion");
        }
    }

    private void validateCriteria(List<AssignmentCriteriaDTO> criteria) {
        if (criteria == null) return;
        for (AssignmentCriteriaDTO c : criteria) {
            if (c.getName() == null || c.getName().isBlank()) {
                throw new BadRequestException("Criteria name is required");
            }
            if (c.getMaxPoints() == null || c.getMaxPoints().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BadRequestException("Criteria max points must be positive");
            }
            // Validate passingPoints: must be between 0 and maxPoints
            if (c.getPassingPoints() != null) {
                if (c.getPassingPoints().compareTo(BigDecimal.ZERO) < 0) {
                    throw new BadRequestException("Criteria passing points cannot be negative: " + c.getName());
                }
                if (c.getPassingPoints().compareTo(c.getMaxPoints()) > 0) {
                    throw new BadRequestException("Criteria passing points cannot exceed max points: " + c.getName());
                }
            }
            // Note: if passingPoints is null in DTO, buildCriteriaEntities defaults it to BigDecimal.ZERO
        }
    }

    /**
     * Validates that the sum of criteria maxPoints equals the assignment maxScore.
     * This prevents inconsistent rubrics where criteria don't add up correctly.
     */
    private void validateCriteriaSumMatchesMaxScore(List<AssignmentCriteriaDTO> criteria, BigDecimal maxScore) {
        if (criteria == null || criteria.isEmpty() || maxScore == null) return;
        BigDecimal sum = criteria.stream()
                .map(c -> c.getMaxPoints() != null ? c.getMaxPoints() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(maxScore) != 0) {
            throw new BadRequestException(
                    "Tổng điểm các tiêu chí (" + sum + ") phải bằng điểm tối đa của bài tập (" + maxScore + ")"
            );
        }
    }

    private List<AssignmentCriteria> buildCriteriaEntities(List<AssignmentCriteriaDTO> criteriaDtos, Assignment assignment) {
        if (criteriaDtos == null) return new ArrayList<>();
        return criteriaDtos.stream()
                .filter(c -> c.getName() != null && !c.getName().isBlank())
                .map(c -> AssignmentCriteria.builder()
                        .assignment(assignment)
                        .name(c.getName().trim())
                        .description(c.getDescription())
                        .maxPoints(c.getMaxPoints() != null ? c.getMaxPoints() : BigDecimal.ZERO)
                        .passingPoints(c.getPassingPoints() != null ? c.getPassingPoints() : BigDecimal.ZERO)
                        .orderIndex(c.getOrderIndex())
                        .isRequired(c.isRequired())
                        .build())
                .collect(Collectors.toList());
    }

    private void validateSubmissionRequest(AssignmentSubmissionCreateDTO dto, Assignment assignment, Media fileMedia) {
        // Type-specific validation based on assignment submission type
        switch (assignment.getSubmissionType()) {
            case TEXT:
                if (dto.getSubmissionText() == null || dto.getSubmissionText().isBlank()) {
                    throw new BadRequestException("Submission text is required for TEXT type assignments");
                }
                break;
            case FILE:
                if (dto.getFileMediaId() == null) {
                    throw new BadRequestException("File upload is required for FILE type assignments");
                }
                if (fileMedia == null) {
                    throw new NotFoundException("MEDIA_NOT_FOUND");
                }
                break;
            case LINK:
                if (dto.getLinkUrl() == null || dto.getLinkUrl().isBlank()) {
                    throw new BadRequestException("Link URL is required for LINK type assignments");
                }
                // Basic URL validation
                if (!dto.getLinkUrl().startsWith("http://") && !dto.getLinkUrl().startsWith("https://")) {
                    throw new BadRequestException("Link URL must start with http:// or https://");
                }
                break;
            default:
                // Allow any submission type if not specified
                break;
        }
    }

    private void validateGradingRequest(BigDecimal score, BigDecimal maxScore) {
        if (score == null) {
            throw new BadRequestException("Score is required");
        }
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Score cannot be negative");
        }
        if (maxScore != null && score.compareTo(maxScore) > 0) {
            throw new BadRequestException("Score cannot exceed max score");
        }
    }

    private AssignmentSubmissionDetailDTO toDetailWithCriteria(AssignmentSubmission submission) {
        AssignmentSubmissionDetailDTO detail = submissionMapper.toDetailDto(submission);
        detail.setUserName(resolveSubmissionUserName(submission.getUser(), detail.getUserName()));
        List<CriteriaScoreDTO> criteriaScores = loadCriteriaScores(submission.getId());
        detail.setCriteriaScores(criteriaScores);

        // Read isPassed from entity (persisted at grading time) — no recomputation
        if (submission.getScore() != null) {
            detail.setIsPassed(submission.getIsPassed());
            detail.setPassingScore(computePassingScore(submission.getAssignment()));
        }
        
        return detail;
    }

    private String resolveSubmissionUserName(User user, String currentValue) {
        if (user == null) {
            return currentValue;
        }

        if (user.getPrimaryRole() == PrimaryRole.USER) {
            String profileName = userProfileRepository.findByUserId(user.getId())
                    .map(profile -> profile.getFullName())
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .map(String::trim)
                    .orElse(null);
            if (profileName != null) {
                return profileName;
            }
        }

        if (currentValue != null && !currentValue.trim().isEmpty()
                && !currentValue.trim().equalsIgnoreCase("null null")) {
            return currentValue.trim();
        }

        String fullName = user.getFullName();
        if (fullName != null && !fullName.trim().isEmpty()) {
            return fullName.trim();
        }

        String email = user.getEmail();
        if (email != null && !email.trim().isEmpty()) {
            return email.trim();
        }

        return user.getId() != null ? "Học viên #" + user.getId() : "Học viên";
    }

    /**
     * Coursera-style pass/fail: if criteria exist, ALL required criteria must individually
     * meet their passingPoints. If no criteria, use assignment.passingScore (or sum of
     * criteria passingPoints as the effective threshold).
     */
    private boolean computeIsPassed(Assignment assignment, List<CriteriaScoreDTO> criteriaScores, BigDecimal totalScore) {
        if (criteriaScores != null && !criteriaScores.isEmpty() && hasRequiredCriteriaThresholds(assignment)) {
            // Criteria-based: every required criterion must be passed individually
            for (CriteriaScoreDTO cs : criteriaScores) {
                if (Boolean.FALSE.equals(cs.getPassed())) {
                    // Check if this criterion is required
                    if (isRequiredCriterion(assignment, cs.getCriteriaId())) {
                        return false;
                    }
                }
            }
            return true;
        }
        // Flat score: use assignment passingScore
        BigDecimal threshold = computePassingScore(assignment);
        return totalScore.compareTo(threshold) >= 0;
    }

    /**
     * Compute the effective passing score for an assignment.
     * If criteria exist with passingPoints → sum of passingPoints for required criteria.
     * Otherwise → assignment.passingScore or 70% of maxScore.
     */
    private BigDecimal computePassingScore(Assignment assignment) {
        List<AssignmentCriteria> criteria = assignment.getCriteria();
        if (criteria != null && !criteria.isEmpty() && hasRequiredCriteriaThresholds(assignment)) {
            // Sum of configured passingPoints for required criteria
            return criteria.stream()
                    .filter(AssignmentCriteria::isRequired)
                    .map(AssignmentCriteria::getPassingPoints)
                    .filter(this::hasMeaningfulPassingPoints)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        return assignment.getPassingScore() != null
                ? assignment.getPassingScore()
                : assignment.getMaxScore().multiply(new BigDecimal("0.7"));
    }

    private boolean hasRequiredCriteriaThresholds(Assignment assignment) {
        return assignment != null
                && assignment.getCriteria() != null
                && assignment.getCriteria().stream()
                .filter(AssignmentCriteria::isRequired)
                    .map(AssignmentCriteria::getPassingPoints)
                    .anyMatch(this::hasMeaningfulPassingPoints);
    }

    private boolean hasMeaningfulPassingPoints(BigDecimal passingPoints) {
        return passingPoints != null && passingPoints.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal resolveCriteriaPassingPoints(AssignmentCriteria criteria) {
        if (criteria == null || !hasMeaningfulPassingPoints(criteria.getPassingPoints())) {
            return null;
        }
        return criteria.getPassingPoints();
    }

    private Boolean isCriterionPassed(BigDecimal score, BigDecimal passingPoints) {
        return passingPoints != null && score != null ? score.compareTo(passingPoints) >= 0 : null;
    }

    private boolean isRequiredCriterion(Assignment assignment, Long criteriaId) {
        if (criteriaId == null || assignment.getCriteria() == null) return true;
        return assignment.getCriteria().stream()
                .filter(c -> criteriaId.equals(c.getId()))
                .findFirst()
                .map(AssignmentCriteria::isRequired)
                .orElse(true); // Default to required if not found
    }

    private List<CriteriaScoreDTO> loadCriteriaScores(Long submissionId) {
        List<SubmissionCriteriaScore> scores = criteriaScoreRepository.findBySubmissionId(submissionId);
        if (scores == null || scores.isEmpty()) {
            return List.of();
        }
        return scores.stream()
                .map(score -> {
                    AssignmentCriteria c = score.getCriteria();
                    BigDecimal passingPts = resolveCriteriaPassingPoints(c);
                    BigDecimal maxPts = (c != null) ? c.getMaxPoints() : null;
                    Boolean passed = isCriterionPassed(score.getScore(), passingPts);
                    return CriteriaScoreDTO.builder()
                            .id(score.getId())
                            .criteriaId(c != null ? c.getId() : null)
                            .criteriaName(c != null ? c.getName() : null)
                            .maxPoints(maxPts)
                            .passingPoints(passingPts)
                            .score(score.getScore())
                            .passed(passed)
                            .feedback(score.getFeedback())
                            .build();
                })
                .toList();
    }

    private BigDecimal applyCriteriaScores(
            AssignmentSubmission submission,
            Assignment assignment,
            List<CriteriaScoreDTO> criteriaScores
    ) {
        List<AssignmentCriteria> criteriaList = criteriaRepository
                .findByAssignmentIdOrderByOrderIndexAsc(assignment.getId());
        if (criteriaList == null || criteriaList.isEmpty()) {
            throw new BadRequestException("Assignment does not have criteria configured");
        }

        Map<Long, AssignmentCriteria> criteriaMap = criteriaList.stream()
                .filter(c -> c.getId() != null)
                .collect(Collectors.toMap(AssignmentCriteria::getId, c -> c));

        criteriaScoreRepository.deleteBySubmissionId(submission.getId());

        // Validate that ALL criteria are scored — mentor must grade every criterion
        if (criteriaScores.size() != criteriaList.size()) {
            throw new BadRequestException(
                    "Phải chấm điểm tất cả " + criteriaList.size() + " tiêu chí (nhận được " + criteriaScores.size() + ")"
            );
        }

        BigDecimal total = BigDecimal.ZERO;
        for (CriteriaScoreDTO scoreDto : criteriaScores) {
            if (scoreDto.getCriteriaId() == null) {
                throw new BadRequestException("Criteria id is required");
            }
            AssignmentCriteria criteria = criteriaMap.get(scoreDto.getCriteriaId());
            if (criteria == null) {
                throw new BadRequestException("Criteria not found: " + scoreDto.getCriteriaId());
            }
            BigDecimal score = scoreDto.getScore() != null ? scoreDto.getScore() : BigDecimal.ZERO;
            if (score.compareTo(BigDecimal.ZERO) < 0) {
                throw new BadRequestException("Criteria score cannot be negative");
            }
            if (criteria.getMaxPoints() != null && score.compareTo(criteria.getMaxPoints()) > 0) {
                throw new BadRequestException("Criteria score cannot exceed max points");
            }
            total = total.add(score);

            SubmissionCriteriaScore entity = SubmissionCriteriaScore.builder()
                    .submission(submission)
                    .criteria(criteria)
                    .score(score)
                    .feedback(scoreDto.getFeedback())
                    .build();
            criteriaScoreRepository.save(entity);
        }

        return total;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentSubmissionDetailDTO> getUserSubmissions(Long assignmentId, Long userId) {
        log.debug("Getting submissions for user {} on assignment {}", userId, assignmentId);
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
        Course course = assignment.getModule().getCourse();
        ensureCanAccessLearningContent(course.getId(), course.getAuthor().getId(), userId);
        ensurePinnedAssignmentAccessibleForLearner(course, userId, assignmentId);
        
        List<AssignmentSubmission> submissions = submissionRepository
                .findByAssignmentIdAndUserIdOrderByAttemptNumberDesc(assignmentId, userId);
        
        return submissions.stream()
                .map(this::toDetailWithCriteria)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentSubmissionDetailDTO> getPendingSubmissions(Long assignmentId, Long actorId) {
        log.debug("Getting pending submissions for assignment {} by actor {}", assignmentId, actorId);
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
        
        // Check actor has permission (course author/mentor/admin)
        ensureAuthorOrAdmin(actorId, assignment.getModule().getCourse().getAuthor().getId());
        
        List<AssignmentSubmission> submissions = submissionRepository
                .findPendingSubmissionsByAssignmentId(assignmentId);
        
        return submissions.stream()
                .map(this::toDetailWithCriteria)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Long countPendingSubmissions(Long assignmentId, Long actorId) {
        log.debug("Counting pending submissions for assignment {} by actor {}", assignmentId, actorId);
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
        
        // Check actor has permission (course author/mentor/admin)
        ensureAuthorOrAdmin(actorId, assignment.getModule().getCourse().getAuthor().getId());
        
        return submissionRepository.countPendingByAssignmentId(assignmentId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingSubmissionItemDTO> getAllPendingForMentor(Long mentorId) {
        log.debug("Batch-loading all pending submissions for mentor {}", mentorId);
        
        List<AssignmentSubmission> submissions = submissionRepository.findAllPendingByAuthorId(mentorId);
        
        return submissions.stream()
                .map(this::toPendingSubmissionItem)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MentorSubmissionItemDTO> getAllMentorSubmissions(Long mentorId) {
        log.debug("Batch-loading newest submissions for mentor {}", mentorId);

        List<AssignmentSubmission> submissions = submissionRepository.findAllLatestByAuthorId(mentorId);

        return submissions.stream()
                .sorted(Comparator
                        .comparing((AssignmentSubmission submission) -> submission.getScore() != null)
                        .thenComparing(AssignmentSubmission::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toMentorSubmissionItem)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MentorSubmissionItemDTO> getMentorSubmissionsPage(
            Long mentorId,
            String filter,
            String search,
            Pageable pageable
    ) {
        String normalizedFilter = (filter == null || filter.isBlank()) ? "ALL" : filter.trim().toUpperCase();
        if (!List.of("ALL", "PENDING", "GRADED", "LATE").contains(normalizedFilter)) {
            normalizedFilter = "ALL";
        }
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        Page<AssignmentSubmission> submissionsPage = submissionRepository.findMentorLatestByAuthorIdWithFilters(
                mentorId,
                normalizedFilter,
                normalizedSearch,
                pageable
        );

        return submissionsPage.map(this::toMentorSubmissionItem);
    }

    @Override
    @Transactional(readOnly = true)
    public MentorSubmissionStatsDTO getMentorSubmissionStats(Long mentorId) {
        long totalCount = submissionRepository.countAllLatestByAuthorId(mentorId);
        long pendingCount = submissionRepository.countPendingLatestByAuthorId(mentorId);
        long gradedCount = submissionRepository.countGradedLatestByAuthorId(mentorId);
        long lateCount = submissionRepository.countLateLatestByAuthorId(mentorId);

        return MentorSubmissionStatsDTO.builder()
                .totalCount(totalCount)
                .pendingCount(pendingCount)
                .gradedCount(gradedCount)
                .lateCount(lateCount)
                .build();
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private boolean hasLearningAccess(EnrollmentStatus status) {
        return status == EnrollmentStatus.ENROLLED || status == EnrollmentStatus.COMPLETED;
    }

    private boolean shouldUsePinnedSnapshotForLearner(Course course, Long actorId) {
        return course != null
                && Boolean.TRUE.equals(course.getRevisioningEnabled())
                && actorId != null
                && !isAuthorOrAdmin(actorId, course.getAuthor().getId())
                && revisionPinnedContentResolver.hasLearningAccessEnrollment(course, actorId);
    }

    private void ensurePinnedAssignmentAccessibleForLearner(Course course, Long actorId, Long assignmentId) {
        if (!shouldUsePinnedSnapshotForLearner(course, actorId)) {
            return;
        }
        if (!revisionPinnedContentResolver.isAssignmentInPinnedRevision(course, actorId, assignmentId)) {
            throw new NotFoundException("ASSIGNMENT_NOT_FOUND");
        }
    }

    private void ensureCanReadCourseStructure(Long courseId, Long authorId, CourseStatus courseStatus, Long actorId) {
        if (isAuthorOrAdmin(actorId, authorId)) {
            return;
        }
        if (courseStatus == CourseStatus.PUBLIC) {
            return;
        }
        ensureCanAccessLearningContent(courseId, authorId, actorId);
    }

    private void ensureCanAccessLearningContent(Long courseId, Long authorId, Long actorId) {
        if (isAuthorOrAdmin(actorId, authorId)) {
            return;
        }
        enrollmentRepository.findByCourseIdAndUserId(courseId, actorId)
                .filter(enrollment -> hasLearningAccess(enrollment.getStatus()))
                .orElseThrow(() -> new AccessDeniedException("USER_NOT_ENROLLED"));
    }

    private boolean isAuthorOrAdmin(Long actorId, Long authorId) {
        if (actorId == null) {
            throw new AccessDeniedException("UNAUTHORIZED");
        }
        if (actorId.equals(authorId)) {
            return true;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_CONTENT_ADMIN"));
    }

    private PendingSubmissionItemDTO toPendingSubmissionItem(AssignmentSubmission submission) {
        Assignment assignment = submission.getAssignment();
        var module = assignment.getModule();
        var course = module.getCourse();

        return PendingSubmissionItemDTO.builder()
                .submission(toDetailWithCriteria(submission))
                .courseName(course.getTitle())
                .courseId(course.getId())
                .moduleName(module.getTitle())
                .moduleId(module.getId())
                .assignmentName(assignment.getTitle())
                .build();
    }

    private MentorSubmissionItemDTO toMentorSubmissionItem(AssignmentSubmission submission) {
        Assignment assignment = submission.getAssignment();
        var module = assignment.getModule();
        var course = module.getCourse();

        return MentorSubmissionItemDTO.builder()
                .submission(toDetailWithCriteria(submission))
                .courseName(course.getTitle())
                .courseId(course.getId())
                .moduleName(module.getTitle())
                .moduleId(module.getId())
                .assignmentName(assignment.getTitle())
                .assignmentDueAt(assignment.getDueAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentSubmissionDetailDTO getPriorSubmission(Long submissionId, Long actorId) {
        log.debug("Getting prior submission for submission {} by actor {}", submissionId, actorId);

        AssignmentSubmission current = getSubmissionOrThrow(submissionId);
        Assignment assignment = current.getAssignment();

        // Ensure mentor has permission
        ensureAuthorOrAdmin(actorId, assignment.getModule().getCourse().getAuthor().getId());

        // Prior submission is attempt N-1 for the same user and assignment
        int priorAttempt = current.getAttemptNumber() - 1;
        if (priorAttempt < 1) {
            throw new NotFoundException("No prior submission exists for submission " + submissionId);
        }

        List<AssignmentSubmission> priorList = submissionRepository
                .findByAssignmentIdAndUserIdOrderByAttemptNumberDesc(assignment.getId(), current.getUser().getId())
                .stream()
                .filter(s -> s.getAttemptNumber() == priorAttempt)
                .toList();

        if (priorList.isEmpty()) {
            throw new NotFoundException("No prior submission found for submission " + submissionId);
        }

        AssignmentSubmission prior = priorList.get(0);
        log.debug("Found prior submission {} (attempt {}) for submission {}",
                prior.getId(), prior.getAttemptNumber(), submissionId);

        return toDetailWithCriteria(prior);
    }

    @Override
    public ResponseEntity<byte[]> streamSubmissionFile(Long submissionId, Long actorId) throws IOException {
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));

        // Authorization: only the student who submitted OR the course author/mentor/admin may download
        boolean isOwner = submission.getUser().getId().equals(actorId);
        Long courseAuthorId = submission.getAssignment().getModule().getCourse().getAuthor().getId();
        boolean isMentorOrAdmin = isAuthorOrAdmin(actorId, courseAuthorId);

        if (!isOwner && !isMentorOrAdmin) {
            log.warn("[SECURITY] User {} attempted to download submission {} (owned by user {})",
                    actorId, submissionId, submission.getUser().getId());
            throw new AccessDeniedException("FORBIDDEN");
        }

        Media media = submission.getFileMedia();
        if (media == null) {
            throw new NotFoundException("No file attached to submission: " + submissionId);
        }

        byte[] fileBytes = cloudinaryService.fetchFile(
                media.getCloudinaryPublicId(), media.getCloudinaryResourceType());

        String filename = media.getFileName();
        String contentType = "application/octet-stream";
        if (filename != null) {
            if (filename.endsWith(".pdf")) {
                contentType = "application/pdf";
            } else if (filename.endsWith(".docx")) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (filename.endsWith(".doc")) {
                contentType = "application/msword";
            } else if (filename.endsWith(".pptx")) {
                contentType = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            } else if (filename.endsWith(".ppt")) {
                contentType = "application/vnd.ms-powerpoint";
            } else if (filename.endsWith(".xlsx")) {
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else if (filename.endsWith(".xls")) {
                contentType = "application/vnd.ms-excel";
            } else if (filename.endsWith(".png")) {
                contentType = "image/png";
            } else if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (filename.endsWith(".webp")) {
                contentType = "image/webp";
            }
        }

        log.debug("[STREAM_SUBMISSION] Streaming submission {}: filename={}, size={} bytes",
                submissionId, filename, fileBytes.length);

        return ResponseEntity.ok()
                .header("Content-Type", contentType)
                .header("Content-Length", String.valueOf(fileBytes.length))
                .header("Content-Disposition",
                        "attachment; filename=\"" + (filename != null ? filename : "submission_file") + "\"")
                .body(fileBytes);
    }
}
