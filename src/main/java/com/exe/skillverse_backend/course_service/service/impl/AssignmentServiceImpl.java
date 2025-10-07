package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.*;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.mapper.AssignmentMapper;
import com.exe.skillverse_backend.course_service.mapper.AssignmentSubmissionMapper;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.service.AssignmentService;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
        
        // Check enrollment
        boolean isEnrolled = enrollmentRepository.findByCourseIdAndUserId(
                assignment.getModule().getCourse().getId(), userId).isPresent();
        if (!isEnrolled) {
            throw new AccessDeniedException("USER_NOT_ENROLLED");
        }
        
        // Check deadline policy
        if (assignment.getDueAt() != null && now().isAfter(assignment.getDueAt())) {
            // TODO: implement late submission policy
            log.warn("Late submission for assignment {} by user {}", assignmentId, userId);
        }
        
        // Check if user already submitted (allow resubmission policy)
        submissionRepository.findByAssignmentIdAndUserId(assignmentId, userId)
                .ifPresent(existing -> {
                    if (existing.getScore() != null) {
                        throw new ConflictException("ASSIGNMENT_ALREADY_GRADED");
                    }
                    // Allow resubmission before grading
                    submissionRepository.delete(existing);
                });
        
        validateSubmissionRequest(dto);
        
        // TODO: Load User entity from userId
        User user = User.builder().id(userId).build(); // Placeholder
        
        // TODO: Load file media if provided in DTO
        Media fileMedia = null; // Placeholder
        
        AssignmentSubmission submission = submissionMapper.toEntity(dto, assignment, user, fileMedia);
        submission.setSubmittedAt(now());
        
        AssignmentSubmission saved = submissionRepository.save(submission);
        log.info("Assignment {} submitted by user {}, submission id {}", assignmentId, userId, saved.getId());
        
        return submissionMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public AssignmentSubmissionDetailDTO grade(Long submissionId, Long graderId, BigDecimal score, String feedback) {
        log.info("Grading submission {} by grader {} with score {}", submissionId, graderId, score);
        
        AssignmentSubmission submission = getSubmissionOrThrow(submissionId);
        
        // Check if grader has permission (course author/mentor/admin)
        ensureAuthorOrAdmin(graderId, submission.getAssignment().getModule().getCourse().getAuthor().getId());
        
        validateGradingRequest(score, submission.getAssignment().getMaxScore());
        
        submission.setScore(score);
        submission.setFeedback(feedback);
        // Note: AssignmentSubmission entity doesn't have gradedAt field
        // TODO: set gradedBy field when User entity is properly loaded
        // TODO: Load grader User entity from graderId
        User grader = User.builder().id(graderId).build(); // Placeholder
        submission.setGradedBy(grader);
        
        AssignmentSubmission saved = submissionRepository.save(submission);
        log.info("Submission {} graded by grader {} with score {}", submissionId, graderId, score);
        
        // TODO: emit ASSIGNMENT.GRADED event
        
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
        // TODO: call Auth/Role service to check if actor is ADMIN/MENTOR
        if (!actorId.equals(authorId)) {
            // TODO: implement proper role checking via AuthService
            throw new AccessDeniedException("FORBIDDEN");
        }
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

    private void validateSubmissionRequest(AssignmentSubmissionCreateDTO dto) {
        if (dto.getSubmissionText() == null || dto.getSubmissionText().isBlank()) {
            throw new IllegalArgumentException("Submission text is required");
        }
        // TODO: add validation for file attachments, submission format, etc.
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

    private Instant now() {
        return Instant.now(clock);
    }
}