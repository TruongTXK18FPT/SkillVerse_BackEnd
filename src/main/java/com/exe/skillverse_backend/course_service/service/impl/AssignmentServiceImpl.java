package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.*;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.mapper.AssignmentMapper;
import com.exe.skillverse_backend.course_service.mapper.AssignmentSubmissionMapper;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.service.AssignmentService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentServiceImpl implements AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final com.exe.skillverse_backend.course_service.repository.ModuleRepository moduleRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final MediaRepository mediaRepository;
    private final NotificationService notificationService;
    private final AssignmentMapper assignmentMapper;
    private final AssignmentSubmissionMapper submissionMapper;
    private final Clock clock;

    @Override
    @Transactional
    public AssignmentDetailDTO createAssignment(Long moduleId, AssignmentCreateDTO dto, Long actorId) {
        log.info("Creating assignment '{}' for module {} by actor {}", dto.getTitle(), moduleId, actorId);
        
        Module module = getModuleOrThrow(moduleId);
        ensureAuthorOrAdmin(actorId, module.getCourse().getAuthor().getId());
        
        validateCreateAssignmentRequest(dto);
        
        Assignment assignment = assignmentMapper.toEntity(dto, module);
        assignment.setCreatedAt(now());
        assignment.setUpdatedAt(now());
        
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
        
        validateUpdateAssignmentRequest(dto);
        
        assignmentMapper.updateEntity(assignment, dto);
        assignment.setUpdatedAt(now());
        
        Assignment saved = assignmentRepository.save(assignment);
        log.info("Assignment {} updated by actor {}", assignmentId, actorId);
        
        return assignmentMapper.toDetailDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentDetailDTO getAssignmentById(Long assignmentId) {
        log.info("Getting assignment details for ID {}", assignmentId);
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
        return assignmentMapper.toDetailDto(assignment);
    }

    @Override
    @Transactional
    public void deleteAssignment(Long assignmentId, Long actorId) {
        log.info("Deleting assignment {} by actor {}", assignmentId, actorId);
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
        ensureAuthorOrAdmin(actorId, assignment.getModule().getCourse().getAuthor().getId());
        
        // Check if there are submissions
        long submissionCount = submissionRepository.countByAssignmentId(assignmentId);
        if (submissionCount > 0) {
            log.warn("Assignment {} has {} submissions, deletion will cascade", assignmentId, submissionCount);
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
        
        log.info("Assignment belongs to course ID: {}", courseId);
        
        // Check enrollment with ENROLLED status
        var enrollmentOpt = enrollmentRepository.findByCourseIdAndUserId(courseId, userId);
        
        if (enrollmentOpt.isEmpty()) {
            log.warn("No enrollment found for user {} in course {}", userId, courseId);
            throw new AccessDeniedException("USER_NOT_ENROLLED");
        }
        
        var enrollment = enrollmentOpt.get();
        log.info("Found enrollment for user {}: status = {}", userId, enrollment.getStatus());
        
        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED) {
            log.warn("User {} has enrollment status {} (not ENROLLED) for course {}", 
                    userId, enrollment.getStatus(), courseId);
            throw new AccessDeniedException("USER_NOT_ENROLLED");
        }
        
        log.info("Enrollment check passed for user {} in course {}", userId, courseId);
        
        // Check if late submission (Coursera pattern: allow but mark as late)
        boolean isLate = false;
        if (assignment.getDueAt() != null && now().isAfter(assignment.getDueAt())) {
            isLate = true;
            log.info("Late submission for assignment {} by user {}", assignmentId, userId);
        }
        
        // Load user entity properly
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND"));
        
        // ===== 2-Version Retention Logic (Coursera pattern) =====
        // Keep only: newest (current) + previous (last) 
        List<AssignmentSubmission> existing = submissionRepository
                .findByAssignmentIdAndUserIdOrderByAttemptNumberDesc(assignmentId, userId);
        
        int nextAttemptNumber = 1;
        
        if (!existing.isEmpty()) {
            nextAttemptNumber = existing.get(0).getAttemptNumber() + 1;
            
            // If we have 2+ versions, delete the oldest one(s) - keep only newest + previous
            if (existing.size() >= 2) {
                // Delete all except the newest one (which will become previous)
                for (int i = 1; i < existing.size(); i++) {
                    submissionRepository.delete(existing.get(i));
                }
            }
            
            // Mark current newest as previous
            AssignmentSubmission currentNewest = existing.get(0);
            currentNewest.setIsNewest(false);
            currentNewest.setIsPrevious(true);
            submissionRepository.save(currentNewest);
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
        
        return submissionMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public AssignmentSubmissionDetailDTO grade(Long submissionId, Long graderId, BigDecimal score, String feedback) {
        log.info("Grading submission {} by grader {} with score {}", submissionId, graderId, score);
        
        AssignmentSubmission submission = getSubmissionOrThrow(submissionId);
        Assignment assignment = submission.getAssignment();
        
        // Check if grader has permission (course author/mentor/admin)
        ensureAuthorOrAdmin(graderId, assignment.getModule().getCourse().getAuthor().getId());
        
        validateGradingRequest(score, assignment.getMaxScore());
        
        // Load grader user properly
        User grader = userRepository.findById(graderId)
                .orElseThrow(() -> new NotFoundException("GRADER_NOT_FOUND"));
        
        submission.setScore(score);
        submission.setFeedback(feedback);
        submission.setGradedBy(grader);
        submission.setGradedAt(now());
        
        AssignmentSubmission saved = submissionRepository.save(submission);
        log.info("Submission {} graded by grader {} with score {}", submissionId, graderId, score);
        
        // Send grading notification to student
        String passStatus = score.compareTo(assignment.getMaxScore().multiply(new BigDecimal("0.7"))) >= 0 
                ? "PASSED ✓" : "Cần cải thiện";
        notificationService.createNotification(
                submission.getUser().getId(),
                "Bài tập đã được chấm điểm",
                "Bài tập '" + assignment.getTitle() + "' đã được chấm: " + score + "/" + assignment.getMaxScore() + " - " + passStatus,
                NotificationType.ASSIGNMENT_GRADED,
                saved.getId().toString(),
                graderId
        );
        
        return submissionMapper.toDetailDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentSubmissionDetailDTO> listSubmissions(Long assignmentId, Pageable pageable) {
        log.debug("Listing submissions for assignment {} with page {}", assignmentId, pageable.getPageNumber());
        
        // Verify assignment exists
        getAssignmentOrThrow(assignmentId);
        
        // TODO: Check permission - mentor can see all, learner only their own
        // For now, return all submissions
        Page<AssignmentSubmission> submissions = submissionRepository.findByAssignmentId(assignmentId, pageable);
        
        return submissions.map(submissionMapper::toDetailDto).toList();
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

    private void ensureAuthorOrAdmin(Long actorId, Long authorId) {
        // Allow if actor is the author
        if (actorId.equals(authorId)) {
            return;
        }
        
        // Check if actor has ADMIN or MENTOR role via SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_MENTOR"))) {
            log.debug("Actor {} allowed via role-based access", actorId);
            return;
        }
        
        throw new AccessDeniedException("FORBIDDEN");
    }

    private void validateCreateAssignmentRequest(AssignmentCreateDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Assignment title is required");
        }
        if (dto.getMaxScore() != null && dto.getMaxScore().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Max score must be positive");
        }
        // TODO: add more validation (due date, requirements, etc.)
    }

    private void validateUpdateAssignmentRequest(AssignmentUpdateDTO dto) {
        if (dto.getTitle() != null && dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Assignment title cannot be blank");
        }
        if (dto.getMaxScore() != null && dto.getMaxScore().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Max score must be positive");
        }
        // TODO: add more validation
    }

    private void validateSubmissionRequest(AssignmentSubmissionCreateDTO dto, Assignment assignment) {
        // Type-specific validation based on assignment submission type
        switch (assignment.getSubmissionType()) {
            case TEXT:
                if (dto.getSubmissionText() == null || dto.getSubmissionText().isBlank()) {
                    throw new IllegalArgumentException("Submission text is required for TEXT type assignments");
                }
                break;
            case FILE:
                if (dto.getFileMediaId() == null) {
                    throw new IllegalArgumentException("File upload is required for FILE type assignments");
                }
                break;
            case LINK:
                if (dto.getLinkUrl() == null || dto.getLinkUrl().isBlank()) {
                    throw new IllegalArgumentException("Link URL is required for LINK type assignments");
                }
                // Basic URL validation
                if (!dto.getLinkUrl().startsWith("http://") && !dto.getLinkUrl().startsWith("https://")) {
                    throw new IllegalArgumentException("Link URL must start with http:// or https://");
                }
                break;
            default:
                // Allow any submission type if not specified
                break;
        }
    }

    private void validateGradingRequest(BigDecimal score, BigDecimal maxScore) {
        if (score == null) {
            throw new IllegalArgumentException("Score is required");
        }
        if (score.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Score cannot be negative");
        }
        if (maxScore != null && score.compareTo(maxScore) > 0) {
            throw new IllegalArgumentException("Score cannot exceed max score");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentSubmissionDetailDTO> getUserSubmissions(Long assignmentId, Long userId) {
        log.info("Getting submissions for user {} on assignment {}", userId, assignmentId);
        
        // Verify assignment exists
        getAssignmentOrThrow(assignmentId);
        
        List<AssignmentSubmission> submissions = submissionRepository
                .findByAssignmentIdAndUserIdOrderByAttemptNumberDesc(assignmentId, userId);
        
        return submissions.stream()
                .map(submissionMapper::toDetailDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignmentSubmissionDetailDTO> getPendingSubmissions(Long assignmentId, Long actorId) {
        log.info("Getting pending submissions for assignment {} by actor {}", assignmentId, actorId);
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
        
        // Check actor has permission (course author/mentor/admin)
        ensureAuthorOrAdmin(actorId, assignment.getModule().getCourse().getAuthor().getId());
        
        List<AssignmentSubmission> submissions = submissionRepository
                .findPendingSubmissionsByAssignmentId(assignmentId);
        
        return submissions.stream()
                .map(submissionMapper::toDetailDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Long countPendingSubmissions(Long assignmentId, Long actorId) {
        log.info("Counting pending submissions for assignment {} by actor {}", assignmentId, actorId);
        
        Assignment assignment = getAssignmentOrThrow(assignmentId);
        
        // Check actor has permission (course author/mentor/admin)
        ensureAuthorOrAdmin(actorId, assignment.getModule().getCourse().getAuthor().getId());
        
        return submissionRepository.countPendingByAssignmentId(assignmentId);
    }

    private Instant now() {
        return Instant.now(clock);
    }
}