package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.*;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentCriteria;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.SubmissionCriteriaScore;
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
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AssignmentMapper assignmentMapper;
    private final AssignmentSubmissionMapper submissionMapper;
    private final UserProfileRepository userProfileRepository;
    private final Clock clock;
    private final CourseLearningProgressService courseLearningProgressService;

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
    public AssignmentDetailDTO updateAssignment(Long assignmentId, AssignmentUpdateDTO dto, Long actorId) {
        log.info("Updating assignment {} by actor {}", assignmentId, actorId);
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
        ensureAuthorOrAdmin(actorId, assignment.getModule().getCourse().getAuthor().getId());
        
        validateUpdateAssignmentRequest(dto, assignment);
        
        assignmentMapper.updateEntity(assignment, dto);
        assignment.setUpdatedAt(now());
        if (dto.getCriteria() != null) {
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
        
        return assignmentMapper.toDetailDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentDetailDTO getAssignmentById(Long assignmentId) {
        log.debug("Getting assignment details for ID {}", assignmentId);
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
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
        Long courseId = assignment.getModule().getCourse().getId();
        
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
        
        // Check if late submission (Coursera pattern: allow but mark as late)
        boolean isLate = false;
        if (assignment.getDueAt() != null && now().isAfter(assignment.getDueAt())) {
            isLate = true;
            log.info("Late submission for assignment {} by user {}", assignmentId, userId);
        }
        
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
        
        validateSubmissionRequest(dto, assignment);
        
        // Load file media if provided
        Media fileMedia = null;
        if (dto.getFileMediaId() != null) {
            fileMedia = mediaRepository.findById(dto.getFileMediaId())
                    .orElseThrow(() -> new NotFoundException("MEDIA_NOT_FOUND"));
        }
        
        AssignmentSubmission submission = submissionMapper.toEntity(dto, assignment, user, fileMedia);
        submission.setSubmittedAt(now());
        submission.setAttemptNumber(nextAttemptNumber);
        submission.setIsNewest(true);
        submission.setIsPrevious(false);
        submission.setIsLate(isLate);
        
        AssignmentSubmission saved = submissionRepository.save(submission);
        log.info("Assignment {} submitted by user {}, submission id {}, attempt #{}", 
                assignmentId, userId, saved.getId(), nextAttemptNumber);
        
        // Send late submission notification
        if (isLate) {
            notificationService.createNotification(
                    userId,
                    "Nộp bài muộn",
                    "Bài tập '" + assignment.getTitle() + "' đã được nộp sau deadline. Mentor có thể xem xét việc này.",
                    NotificationType.ASSIGNMENT_LATE,
                    saved.getId().toString()
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
                : new AssignmentGradeDTO(legacyScore, legacyFeedback, null);

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

        // Persist isPassed once at grading time (immune to later criteria edits)
        List<CriteriaScoreDTO> gradedCriteriaScores = loadCriteriaScores(submission.getId());
        boolean passed = computeIsPassed(assignment, gradedCriteriaScores, totalScore);
        submission.setIsPassed(passed);
        
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
                graderId
        );
        
        return detail;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentSubmissionDetailDTO> listSubmissions(Long assignmentId, Pageable pageable) {
        log.debug("Listing submissions for assignment {} with page {}", assignmentId, pageable.getPageNumber());
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
        
        // Ensure only the course author or admin can list all submissions
        Long actorId = getCurrentUserId();
        ensureAuthorOrAdmin(actorId, assignment.getModule().getCourse().getAuthor().getId());
        
        // Only show newest version per student (avoid duplicates from previous versions)
        List<AssignmentSubmission> submissions = submissionRepository
                .findLatestSubmissionsByAssignmentId(assignmentId);
        
        return submissions.stream()
                .map(this::toDetailWithCriteria)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentSummaryDTO> listAssignmentsByModule(Long moduleId) {
        log.debug("Listing assignments for module {}", moduleId);
        
        // Verify module exists
        getModuleOrThrow(moduleId);
        
        List<Assignment> assignments = assignmentRepository.findByModuleId(moduleId);
        
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
        // Allow if actor is the author of the course
        if (actorId.equals(authorId)) {
            return;
        }

        // Only ADMIN (not just any MENTOR) can bypass the author check.
        // This prevents Mentor A from grading/editing Mentor B's assignments.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_CONTENT_ADMIN"))) {
            log.debug("Actor {} allowed via admin role", actorId);
            return;
        }

        throw new AccessDeniedException("FORBIDDEN");
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

    private void validateSubmissionRequest(AssignmentSubmissionCreateDTO dto, Assignment assignment) {
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
        
        // Verify assignment exists
        getAssignmentOrThrow(assignmentId);
        
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
}
