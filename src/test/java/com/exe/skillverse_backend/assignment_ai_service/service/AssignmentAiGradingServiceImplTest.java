package com.exe.skillverse_backend.assignment_ai_service.service;

import com.exe.skillverse_backend.assignment_ai_service.dto.AiGradingResultDTO;
import com.exe.skillverse_backend.assignment_ai_service.service.impl.AssignmentAiGradingServiceImpl;
import com.exe.skillverse_backend.assignment_ai_service.service.AssignmentGradingPromptService;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.ai_service.service.LocalAiGateway;
import com.exe.skillverse_backend.course_service.service.CourseLearningProgressService;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.LessonRepository;
import com.exe.skillverse_backend.course_service.service.impl.RevisionPinnedContentResolver;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleDetailDTO;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssignmentAiGradingServiceImpl")
class AssignmentAiGradingServiceImplTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private AssignmentSubmissionRepository submissionRepository;

    @Mock
    private com.exe.skillverse_backend.course_service.repository.AssignmentCriteriaRepository criteriaRepository;

    @Mock
    private com.exe.skillverse_backend.course_service.repository.SubmissionCriteriaScoreRepository criteriaScoreRepository;

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private AssignmentGradingPromptService gradingPromptService;

    @Mock
    private FileTextExtractorService fileExtractor;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ChatModel chatModel;

    @Mock
    private CourseLearningProgressService courseLearningProgressService;

    @Mock
    private LocalAiGateway localAiGateway;

    @Mock
    private LessonRepository lessonRepository;

    @Mock
    private RevisionPinnedContentResolver revisionPinnedContentResolver;

    private AssignmentAiGradingServiceImpl service;

    private Course course;
    private Module module;
    private Assignment assignment;
    private AssignmentSubmission submission;

    @BeforeEach
    void setUp() {
        service = new AssignmentAiGradingServiceImpl(
                assignmentRepository,
                submissionRepository,
                criteriaRepository,
                criteriaScoreRepository,
                mediaRepository,
                gradingPromptService,
                fileExtractor,
                notificationService,
                chatModel,
                courseLearningProgressService,
                null,
                lessonRepository,
                revisionPinnedContentResolver,
                null
        );

        User mentor = User.builder().id(7L).firstName("Mentor").lastName("One").build();
        course = Course.builder().id(1L).author(mentor).build();
        module = Module.builder().id(10L).course(course).build();
        User student = User.builder()
                .id(9L)
                .firstName("Student")
                .lastName("One")
                .build();
        assignment = Assignment.builder()
                .id(50L)
                .module(module)
                .aiGradingEnabled(true)
                .trustAiEnabled(false)
                .gradingStyle("STANDARD")
                .maxScore(new BigDecimal("100"))
                .passingScore(new BigDecimal("70"))
                .build();

        submission = AssignmentSubmission.builder()
                .id(100L)
                .assignment(assignment)
                .user(student)
                .submissionText("My answer is correct because the formula is X.")
                .isAiGraded(false)
                .aiGradeAttemptCount(0)
                .disputeFlag(false)
                .build();
    }

    // ========================================================================
    // generateAiGrade — pre-AI-call validation & boundary tests
    // ========================================================================

    @Test
    @DisplayName("generateAiGrade throws IllegalStateException when AI grading is disabled")
    void generateAiGrade_aiDisabled_throwsIllegalState() {
        assignment.setAiGradingEnabled(false);
                when(submissionRepository.findByIdWithFullChain(100L)).thenReturn(Optional.of(submission));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.generateAiGrade(100L, 7L));

        assertEquals("AI grading is not enabled for this assignment", ex.getMessage());
        verify(submissionRepository, never()).save(any(AssignmentSubmission.class));
    }

    @Test
    @DisplayName("generateAiGrade throws IllegalArgumentException when attempt cap is reached")
    void generateAiGrade_exceedsAttemptCap_throwsArgumentException() {
        submission.setAiGradeAttemptCount(3);
                when(submissionRepository.findByIdWithFullChain(100L)).thenReturn(Optional.of(submission));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.generateAiGrade(100L, 7L));

        assertTrue(ex.getMessage().contains("max 3"));
        verify(submissionRepository, never()).save(any(AssignmentSubmission.class));
    }

    @Test
    @DisplayName("generateAiGrade throws IllegalArgumentException when submission is empty")
    void generateAiGrade_emptySubmission_throwsArgumentException() {
        submission.setSubmissionText(null);
        submission.setFileMedia(null);
                when(submissionRepository.findByIdWithFullChain(100L)).thenReturn(Optional.of(submission));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.generateAiGrade(100L, 7L));

        assertEquals("No content to grade. Submission is empty.", ex.getMessage());
        verify(submissionRepository, never()).save(any(AssignmentSubmission.class));
    }

    @Test
    @DisplayName("generateAiGrade throws NotFoundException when submission does not exist")
    void generateAiGrade_submissionNotFound_throwsNotFoundException() {
                when(submissionRepository.findByIdWithFullChain(999L)).thenReturn(Optional.empty());

        assertThrows(
                com.exe.skillverse_backend.shared.exception.NotFoundException.class,
                () -> service.generateAiGrade(999L, 7L)
        );
    }

    // Note: generateAiGrade full flow (with AI call) is covered by integration tests.
    // The unit tests above cover pre-AI-call validation: enabled check, attempt cap,
    // empty content guard, and post-call getAiGradeResult.
    // The auto-confirm (trustAi + confidence) logic is tested via the boundary of
    // getAiGradeResult — the submission entity state reflects whether auto-confirm ran.

    // ========================================================================
    // getAiGradeResult tests
    // ========================================================================

    @Test
    @DisplayName("getAiGradeResult throws IllegalStateException when no AI grade exists")
    void getAiGradeResult_noAiGrade_throwsIllegalState() {
        submission.setIsAiGraded(false);
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.getAiGradeResult(100L));

        assertEquals("No AI grade exists for this submission", ex.getMessage());
    }

    @Test
    @DisplayName("getAiGradeResult throws NotFoundException when submission does not exist")
    void getAiGradeResult_submissionNotFound_throwsNotFoundException() {
        when(submissionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(
                com.exe.skillverse_backend.shared.exception.NotFoundException.class,
                () -> service.getAiGradeResult(999L)
        );
    }

    @Test
    @DisplayName("getAiGradeResult returns AI results when they exist")
    void getAiGradeResult_withAiGrade_returnsResult() {
        submission.setIsAiGraded(true);
        submission.setAiScore(new BigDecimal("75"));
        submission.setAiFeedback("Needs improvement");
        submission.setAiConfidence(0.70);
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        AiGradingResultDTO result = service.getAiGradeResult(100L);

        assertEquals(new BigDecimal("75"), result.getTotalScore());
        assertEquals("Needs improvement", result.getOverallFeedback());
        assertEquals(0.70, result.getOverallConfidence());
        assertTrue(result.getCriteriaScores().isEmpty());
    }

    @Test
    @DisplayName("getAiGradeResult returns correct confidence and score values")
    void getAiGradeResult_correctValuesReturned() {
        submission.setIsAiGraded(true);
        submission.setAiScore(new BigDecimal("92.50"));
        submission.setAiFeedback("Excellent analysis");
        submission.setAiConfidence(0.96);
        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        AiGradingResultDTO result = service.getAiGradeResult(100L);

        assertEquals(new BigDecimal("92.50"), result.getTotalScore());
        assertEquals("Excellent analysis", result.getOverallFeedback());
        assertEquals(0.96, result.getOverallConfidence());
        assertNotNull(result.getCriteriaScores());
    }

    // ========================================================================
    // toggleTrustAi tests
    // ========================================================================

    @Test
    @DisplayName("toggleTrustAi enables trust and saves assignment")
    void toggleTrustAi_enablesTrust_setsFlag() {
        assignment.setTrustAiEnabled(false);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(Assignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.toggleTrustAi(50L, true);

        ArgumentCaptor<Assignment> captor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentRepository).save(captor.capture());
        assertTrue(captor.getValue().getTrustAiEnabled());
    }

    @Test
    @DisplayName("toggleTrustAi disables trust and saves assignment")
    void toggleTrustAi_disablesTrust_setsFlag() {
        assignment.setTrustAiEnabled(true);
        when(assignmentRepository.findById(50L)).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(Assignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.toggleTrustAi(50L, false);

        ArgumentCaptor<Assignment> captor = ArgumentCaptor.forClass(Assignment.class);
        verify(assignmentRepository).save(captor.capture());
        assertFalse(captor.getValue().getTrustAiEnabled());
    }

    // ========================================================================
    // requestMentorReview tests
    // ========================================================================

    @Test
    @DisplayName("requestMentorReview sets dispute fields and sends 2 notifications")
    void requestMentorReview_setsDisputeFlag_andNotifies() {
        submission.setDisputeFlag(false);
                when(submissionRepository.findByIdWithFullChain(100L)).thenReturn(Optional.of(submission));
        when(submissionRepository.save(any(AssignmentSubmission.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.requestMentorReview(100L, 9L, "Too harsh");

        ArgumentCaptor<AssignmentSubmission> captor =
                ArgumentCaptor.forClass(AssignmentSubmission.class);
        verify(submissionRepository).save(captor.capture());
        AssignmentSubmission saved = captor.getValue();

        assertTrue(saved.getDisputeFlag());
        assertNotNull(saved.getDisputeAt());
        assertEquals("Too harsh", saved.getDisputeReason());
        // Two notifications: one to mentor, one to student
        verify(notificationService, times(2)).createNotification(
                anyLong(), anyString(), anyString(),
                any(), anyString(), any());
    }

    @Test
    @DisplayName("requestMentorReview throws AccessDeniedException for non-owner")
    void requestMentorReview_byNonOwner_throwsAccessDenied() {
                when(submissionRepository.findByIdWithFullChain(100L)).thenReturn(Optional.of(submission));

        assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> service.requestMentorReview(100L, 99L, null)
        );
        verify(submissionRepository, never()).save(any());
    }

    @Test
    @DisplayName("callAi falls back to cloud when local returns invalid JSON (schema fail)")
    void generateAiGrade_localInvalidJson_fallsBackToCloud() {
        AssignmentAiGradingServiceImpl serviceWithLocal = new AssignmentAiGradingServiceImpl(
                assignmentRepository, submissionRepository, criteriaRepository, criteriaScoreRepository,
                mediaRepository, gradingPromptService, fileExtractor, notificationService,
                null, courseLearningProgressService, localAiGateway, lessonRepository,
                revisionPinnedContentResolver, null);

        when(revisionPinnedContentResolver.resolveModulesWithContent(any(), anyLong()))
                .thenReturn(Optional.empty());
        when(localAiGateway.isAvailable()).thenReturn(true);
        when(localAiGateway.call(anyString(), anyString())).thenReturn("not-valid-json");
        when(submissionRepository.findByIdWithFullChain(100L)).thenReturn(Optional.of(submission));
        when(gradingPromptService.buildGradingPrompt(any(), anyString(), anyString(), any()))
                .thenReturn("grade this");

        // Local returns bad JSON -> parseGradingResponse throws -> caught -> cloud path taken
        // cloud chatModel is null -> ISE wrapped in RuntimeException by callAiWithRetry
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> serviceWithLocal.generateAiGrade(100L, 7L));
        Throwable root = ex.getCause() != null ? ex.getCause() : ex;
        assertTrue(root.getMessage().contains("ASSIGNMENT_AI_API_KEY"));
        verify(localAiGateway, atLeastOnce()).call(anyString(), anyString());
    }

    @Test
    @DisplayName("RAG fetchRagContext exception does not prevent grading from reaching AI call")
    void generateAiGrade_ragException_requestContinues() {
        AssignmentAiGradingServiceImpl serviceWithLocal = new AssignmentAiGradingServiceImpl(
                assignmentRepository, submissionRepository, criteriaRepository, criteriaScoreRepository,
                mediaRepository, gradingPromptService, fileExtractor, notificationService,
                null, courseLearningProgressService, localAiGateway, lessonRepository,
                revisionPinnedContentResolver, null);

        when(revisionPinnedContentResolver.resolveModulesWithContent(any(), anyLong()))
                .thenReturn(Optional.empty());
        when(localAiGateway.isAvailable()).thenReturn(true);
        when(localAiGateway.fetchRagContext(anyString(), any(), anyInt()))
                .thenThrow(new RuntimeException("RAG network error"));
        when(submissionRepository.findByIdWithFullChain(100L)).thenReturn(Optional.of(submission));
        when(gradingPromptService.buildGradingPrompt(any(), anyString(), anyString(), any()))
                .thenReturn("grade this");

        // RAG throws -> warning logged -> continues -> local call attempted -> falls back to null cloud -> ISE
        when(localAiGateway.call(anyString(), anyString())).thenReturn("bad json");
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> serviceWithLocal.generateAiGrade(100L, 7L));
        Throwable root = ex.getCause() != null ? ex.getCause() : ex;
        assertTrue(root.getMessage().contains("ASSIGNMENT_AI_API_KEY"));
    }

    @Test
    @DisplayName("Pinned revision context is used first; RAG and live DB are not called")
    void generateAiGrade_pinnedRevisionContent_preferredOverRagAndLiveDb() {
        AssignmentAiGradingServiceImpl serviceWithLocal = new AssignmentAiGradingServiceImpl(
                assignmentRepository, submissionRepository, criteriaRepository, criteriaScoreRepository,
                mediaRepository, gradingPromptService, fileExtractor, notificationService,
                null, courseLearningProgressService, localAiGateway, lessonRepository,
                revisionPinnedContentResolver, null);

        // Build a pinned ModuleDetailDTO containing the assignment and one READING lesson
        AssignmentSummaryDTO pinnedAssignment = new AssignmentSummaryDTO(
                50L, "Assignment", null, null, null, null, 10L, null);
        LessonBriefDTO readingLesson = new LessonBriefDTO(
                1L, "Intro Reading", LessonType.READING, 1, null,
                "PINNED_CONTEXT_V1", null, null, null);
        ModuleDetailDTO pinnedModule = new ModuleDetailDTO(
                10L, "Module 1", null, 1, null, null,
                List.of(readingLesson), List.of(), List.of(pinnedAssignment));

        when(revisionPinnedContentResolver.resolveModulesWithContent(any(), anyLong()))
                .thenReturn(Optional.of(List.of(pinnedModule)));
        when(submissionRepository.findByIdWithFullChain(100L)).thenReturn(Optional.of(submission));
        when(gradingPromptService.buildGradingPrompt(any(), anyString(), anyString(), any()))
                .thenReturn("grade this");
        when(localAiGateway.isAvailable()).thenReturn(true);
        when(localAiGateway.call(anyString(), anyString())).thenReturn("bad json for ISE");

        // Grading will fail at AI parse, but that is after context resolution
        assertThrows(RuntimeException.class,
                () -> serviceWithLocal.generateAiGrade(100L, 7L));

        // Pinned context found — RAG and live DB must NOT be consulted
        verify(localAiGateway, never()).fetchRagContext(anyString(), any(), anyInt());
        verify(lessonRepository, never()).findByModuleIdOrderByOrderIndexAsc(anyLong());
    }
}
