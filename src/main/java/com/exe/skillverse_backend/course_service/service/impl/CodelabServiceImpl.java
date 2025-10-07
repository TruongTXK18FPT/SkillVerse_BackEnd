package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.codingdto.*;
import com.exe.skillverse_backend.course_service.entity.CodingExercise;
import com.exe.skillverse_backend.course_service.entity.CodingSubmission;
import com.exe.skillverse_backend.course_service.entity.CodingTestCase;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.enums.CodeSubmissionStatus;
import com.exe.skillverse_backend.course_service.mapper.CodingExerciseMapper;
import com.exe.skillverse_backend.course_service.mapper.CodingSubmissionMapper;
import com.exe.skillverse_backend.course_service.mapper.CodingTestCaseMapper;
import com.exe.skillverse_backend.course_service.repository.*;
import com.exe.skillverse_backend.course_service.service.CodelabService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodelabServiceImpl implements CodelabService {

    private final CodingExerciseRepository exerciseRepository;
    private final CodingTestCaseRepository testCaseRepository;
    private final CodingSubmissionRepository submissionRepository;
    private final com.exe.skillverse_backend.course_service.repository.ModuleRepository moduleRepository;
    private final CourseEnrollmentRepository enrollmentRepository;
    private final CodingExerciseMapper exerciseMapper;
    private final CodingTestCaseMapper testCaseMapper;
    private final CodingSubmissionMapper submissionMapper;
    private final Clock clock;

    @Override
    @Transactional
    public CodingExerciseDetailDTO createExercise(Long moduleId, CodingExerciseCreateDTO dto, Long actorId) {
        log.info("Creating coding exercise '{}' for module {} by actor {}", dto.getTitle(), moduleId, actorId);
        
        Module module = getModuleOrThrow(moduleId);
        ensureAuthorOrAdmin(actorId, module.getCourse().getAuthor().getId());
        
        validateCreateExerciseRequest(dto);
        
        CodingExercise exercise = exerciseMapper.toEntity(dto, module);
        
        CodingExercise saved = exerciseRepository.save(exercise);
        log.info("Coding exercise {} created for module {} by actor {}", saved.getId(), moduleId, actorId);
        
        return exerciseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public CodingExerciseDetailDTO updateExercise(Long exerciseId, CodingExerciseUpdateDTO dto, Long actorId) {
        log.info("Updating coding exercise {} by actor {}", exerciseId, actorId);
        
        CodingExercise exercise = getExerciseOrThrow(exerciseId);
        ensureAuthorOrAdmin(actorId, exercise.getModule().getCourse().getAuthor().getId());
        
        validateUpdateExerciseRequest(dto);
        
        exerciseMapper.updateEntity(exercise, dto);
        
        CodingExercise saved = exerciseRepository.save(exercise);
        log.info("Coding exercise {} updated by actor {}", exerciseId, actorId);
        
        return exerciseMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public void deleteExercise(Long exerciseId, Long actorId) {
        log.info("Deleting coding exercise {} by actor {}", exerciseId, actorId);
        
        CodingExercise exercise = getExerciseOrThrow(exerciseId);
        ensureAuthorOrAdmin(actorId, exercise.getModule().getCourse().getAuthor().getId());
        
        // Check if there are submissions
        long submissionCount = submissionRepository.countByExerciseId(exerciseId);
        if (submissionCount > 0) {
            log.warn("Coding exercise {} has {} submissions, deletion will cascade", exerciseId, submissionCount);
        }
        
        exerciseRepository.delete(exercise);
        log.info("Coding exercise {} deleted by actor {}", exerciseId, actorId);
    }

    @Override
    @Transactional
    public CodingTestCaseDTO addTestCase(Long exerciseId, CodingTestCaseCreateDTO dto, Long actorId) {
        log.info("Adding test case to coding exercise {} by actor {}", exerciseId, actorId);
        
        CodingExercise exercise = getExerciseOrThrow(exerciseId);
        ensureAuthorOrAdmin(actorId, exercise.getModule().getCourse().getAuthor().getId());
        
        validateCreateTestCaseRequest(dto);
        
        // Auto-generate orderIndex if not provided
        Integer orderIndex = dto.getOrderIndex();
        if (orderIndex == null) {
            orderIndex = (int) (testCaseRepository.countByExerciseId(exerciseId) + 1);
        }
        
        CodingTestCase testCase = testCaseMapper.toEntity(dto, exercise);
        testCase.setOrderIndex(orderIndex);
        
        CodingTestCase saved = testCaseRepository.save(testCase);
        log.info("Test case {} added to coding exercise {} by actor {}", saved.getId(), exerciseId, actorId);
        
        return testCaseMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CodingTestCaseDTO updateTestCase(Long testCaseId, CodingTestCaseUpdateDTO dto, Long actorId) {
        log.info("Updating test case {} by actor {}", testCaseId, actorId);
        
        CodingTestCase testCase = getTestCaseOrThrow(testCaseId);
        ensureAuthorOrAdmin(actorId, testCase.getExercise().getModule().getCourse().getAuthor().getId());
        
        validateUpdateTestCaseRequest(dto);
        
        testCaseMapper.updateEntity(testCase, dto);
        
        CodingTestCase saved = testCaseRepository.save(testCase);
        log.info("Test case {} updated by actor {}", testCaseId, actorId);
        
        return testCaseMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void deleteTestCase(Long testCaseId, Long actorId) {
        log.info("Deleting test case {} by actor {}", testCaseId, actorId);
        
        CodingTestCase testCase = getTestCaseOrThrow(testCaseId);
        ensureAuthorOrAdmin(actorId, testCase.getExercise().getModule().getCourse().getAuthor().getId());
        
        testCaseRepository.delete(testCase);
        log.info("Test case {} deleted by actor {}", testCaseId, actorId);
    }

    @Override
    @Transactional
    public CodingSubmissionDetailDTO submit(Long exerciseId, Long userId, CodingSubmissionCreateDTO dto) {
        log.info("Submitting coding exercise {} by user {}", exerciseId, userId);
        
        CodingExercise exercise = getExerciseOrThrow(exerciseId);
        
        // Check enrollment
        boolean isEnrolled = enrollmentRepository.findByCourseIdAndUserId(
                exercise.getModule().getCourse().getId(), userId).isPresent();
        if (!isEnrolled) {
            throw new AccessDeniedException("USER_NOT_ENROLLED");
        }
        
        validateSubmissionRequest(dto);
        
        // User entity will be loaded by AuthService integration
        User user = User.builder().id(userId).build(); // Placeholder for AuthService integration
        
        CodingSubmission submission = submissionMapper.toEntity(dto, exercise, user);
        submission.setStatus(CodeSubmissionStatus.QUEUED);
        submission.setSubmittedAt(now());
        
        CodingSubmission saved = submissionRepository.save(submission);
        log.info("Coding exercise {} submitted by user {}, submission id {}", exerciseId, userId, saved.getId());
        
        // Judge queue integration for async code evaluation will be implemented
        
        return submissionMapper.toDetailDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CodingSubmissionDetailDTO> listSubmissions(Long exerciseId, Pageable pageable) {
        log.debug("Listing submissions for coding exercise {} with page {}", exerciseId, pageable.getPageNumber());
        
        // Verify exercise exists
        getExerciseOrThrow(exerciseId);
        
        // Permission check will be enhanced with role-based access control
        // For now, return all submissions ordered by submission time DESC
        Page<CodingSubmission> submissions = submissionRepository.findByExerciseId(exerciseId, pageable);
        
        return PageResponse.<CodingSubmissionDetailDTO>builder()
                .items(submissions.map(submissionMapper::toDetailDto).toList())
                .page(submissions.getNumber())
                .size(submissions.getSize())
                .total(submissions.getTotalElements())
                .build();
    }

    // ===== Helper Methods =====
    
    private Module getModuleOrThrow(Long moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("MODULE_NOT_FOUND"));
    }
    
    private CodingExercise getExerciseOrThrow(Long exerciseId) {
        return exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new NotFoundException("EXERCISE_NOT_FOUND"));
    }
    
    private CodingTestCase getTestCaseOrThrow(Long testCaseId) {
        return testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new NotFoundException("TEST_CASE_NOT_FOUND"));
    }

    private void ensureAuthorOrAdmin(Long actorId, Long authorId) {
        // Auth/Role service integration will be implemented for admin checking
        if (!actorId.equals(authorId)) {
            // Role checking via AuthService will be integrated here
            throw new AccessDeniedException("FORBIDDEN");
        }
    }

    private void validateCreateExerciseRequest(CodingExerciseCreateDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Exercise title is required");
        }
        if (dto.getPrompt() == null || dto.getPrompt().isBlank()) {
            throw new IllegalArgumentException("Problem statement is required");
        }
        // Additional validation can be added for time/memory limits, language constraints
    }

    private void validateUpdateExerciseRequest(CodingExerciseUpdateDTO dto) {
        if (dto.getTitle() != null && dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Exercise title cannot be blank");
        }
        if (dto.getPrompt() != null && dto.getPrompt().isBlank()) {
            throw new IllegalArgumentException("Problem statement cannot be blank");
        }
        // Additional validation for update operations
    }

    private void validateCreateTestCaseRequest(CodingTestCaseCreateDTO dto) {
        if (dto.getInput() == null) {
            throw new IllegalArgumentException("Test case input is required");
        }
        if (dto.getExpectedOutput() == null) {
            throw new IllegalArgumentException("Test case expected output is required");
        }
        // Additional validation for input/output format and size limits
    }

    private void validateUpdateTestCaseRequest(CodingTestCaseUpdateDTO dto) {
        if (dto.getInput() != null && dto.getInput().isBlank()) {
            throw new IllegalArgumentException("Test case input cannot be blank");
        }
        if (dto.getExpectedOutput() != null && dto.getExpectedOutput().isBlank()) {
            throw new IllegalArgumentException("Test case expected output cannot be blank");
        }
        // Additional validation for test case updates
    }

    private void validateSubmissionRequest(CodingSubmissionCreateDTO dto) {
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new IllegalArgumentException("Source code is required");
        }
        // Language validation will be done by the judge service
        // Additional validation for code size limits and language support
    }

    private Instant now() {
        return Instant.now(clock);
    }
}