package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentGradeDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionDetailDTO;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.mapper.AssignmentMapper;
import com.exe.skillverse_backend.course_service.mapper.AssignmentSubmissionMapper;
import com.exe.skillverse_backend.course_service.repository.AssignmentCriteriaRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.repository.SubmissionCriteriaScoreRepository;
import com.exe.skillverse_backend.course_service.service.impl.AssignmentServiceImpl;
import com.exe.skillverse_backend.course_service.service.impl.RevisionPinnedContentResolver;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.shared.service.MediaService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssignmentServiceImpl — AI Grading Flows")
class AssignmentServiceImplAiGradeTest {

    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private AssignmentCriteriaRepository criteriaRepository;
    @Mock
    private SubmissionCriteriaScoreRepository criteriaScoreRepository;
    @Mock
    private AssignmentSubmissionRepository submissionRepository;
    @Mock
    private ModuleRepository moduleRepository;
    @Mock
    private CourseEnrollmentRepository enrollmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private AssignmentMapper assignmentMapper;
    @Mock
    private AssignmentSubmissionMapper submissionMapper;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private CourseLearningProgressService courseLearningProgressService;
    @Mock
    private RevisionPinnedContentResolver revisionPinnedContentResolver;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private MediaService mediaService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private AssignmentServiceImpl service;

    private Course course;
    private Module module;
    private Assignment assignment;
    private User student;
    private User mentor;
    private AssignmentSubmission submission;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemUTC();
        service = new AssignmentServiceImpl(
                assignmentRepository,
                criteriaRepository,
                criteriaScoreRepository,
                submissionRepository,
                moduleRepository,
                enrollmentRepository,
                userRepository,
                mediaRepository,
                notificationService,
                eventPublisher,
                assignmentMapper,
                submissionMapper,
                userProfileRepository,
                clock,
                courseLearningProgressService,
                revisionPinnedContentResolver,
                cloudinaryService,
                mediaService
        );

        course = Course.builder().id(30L).author(User.builder().id(7L).build()).build();
        module = Module.builder().id(10L).course(course).build();
        assignment = Assignment.builder()
                .id(50L)
                .module(module)
                .title("AI Assignment")
                .maxScore(new BigDecimal("100"))
                .passingScore(new BigDecimal("70"))
                .build();
        student = User.builder().id(9L).primaryRole(PrimaryRole.USER).firstName("Student").lastName("One").build();
        mentor = User.builder().id(7L).primaryRole(PrimaryRole.MENTOR).firstName("Mentor").lastName("One").build();
        submission = AssignmentSubmission.builder()
                .id(100L)
                .assignment(assignment)
                .user(student)
                .isNewest(true)
                .score(null)
                .isPassed(null)
                .isAiGraded(false)
                .disputeFlag(false)
                .aiGradeAttemptCount(1)
                .build();

        // Mock security context so isAuthorOrAdmin passes
        mockSecurityContextForMentor();
    }

    private void mockSecurityContextForMentor() {
        Authentication auth = mock(Authentication.class);
        SecurityContext ctx = mock(SecurityContext.class);
        SecurityContextHolder.setContext(ctx);
        when(ctx.getAuthentication()).thenReturn(auth);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getAuthorities()).thenReturn((Collection) Collections.emptyList());
    }

    // ========================================================================
    // grade with isAiGrade = true (mentor confirms AI pre-grade)
    // ========================================================================

    @Test
    @DisplayName("grade with isAiGrade=true sets isAiGraded=true and mentorConfirmed=true")
    void grade_withIsAiGradeTrue_setsAiGradedAndMentorConfirmed() {
        AssignmentGradeDTO grading = new AssignmentGradeDTO(
                new BigDecimal("80"),
                "Good work from AI",
                null,
                true  // isAiGrade
        );

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentor));
        when(criteriaScoreRepository.findBySubmissionId(100L)).thenReturn(List.of());
        when(submissionRepository.save(any(AssignmentSubmission.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(submissionMapper.toDetailDto(any())).thenReturn(new AssignmentSubmissionDetailDTO());
        when(submissionMapper.toDetailDto(submission)).thenReturn(
                AssignmentSubmissionDetailDTO.builder().id(100L).build()
        );

        AssignmentSubmissionDetailDTO result = service.grade(100L, 7L, grading, null, null);

        ArgumentCaptor<AssignmentSubmission> captor = ArgumentCaptor.forClass(AssignmentSubmission.class);
        verify(submissionRepository).save(captor.capture());
        AssignmentSubmission saved = captor.getValue();

        assertEquals(new BigDecimal("80"), saved.getScore());
        assertEquals("Good work from AI", saved.getFeedback());
        assertTrue(saved.getIsAiGraded());
        assertTrue(saved.getMentorConfirmed());
        assertTrue(saved.getIsPassed()); // 80 >= 70 passing threshold
    }

    @Test
    @DisplayName("grade with isAiGrade=true notifies student of grading result")
    void grade_withIsAiGradeTrue_notifiesStudentOfResult() {
        AssignmentGradeDTO grading = new AssignmentGradeDTO(
                new BigDecimal("85"),
                "Excellent",
                null,
                true
        );

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentor));
        when(criteriaScoreRepository.findBySubmissionId(100L)).thenReturn(List.of());
        when(submissionRepository.save(any(AssignmentSubmission.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(submissionMapper.toDetailDto(any())).thenReturn(
                AssignmentSubmissionDetailDTO.builder().id(100L).build()
        );

        service.grade(100L, 7L, grading, null, null);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createNotification(
                eq(9L),
                eq("Bài tập đã được chấm điểm"),
                msgCaptor.capture(),
                eq(NotificationType.ASSIGNMENT_GRADED),
                eq("100"),
                eq(7L)
        );
        assertTrue(msgCaptor.getValue().contains("85"));
        assertTrue(msgCaptor.getValue().contains("PASSED"));
    }

    // ========================================================================
    // grade re-grading a disputed submission
    // ========================================================================

    @Test
    @DisplayName("grade clears dispute fields when re-grading a disputed submission")
    void grade_clearsDisputeFlag_whenReGradingDisputedSubmission() {
        submission.setScore(new BigDecimal("40"));
        submission.setIsPassed(false);
        submission.setDisputeFlag(true);
        submission.setDisputeAt(Instant.now());
        submission.setDisputeReason("Too harsh");

        AssignmentGradeDTO grading = new AssignmentGradeDTO(
                new BigDecimal("85"),
                "Re-graded: 85 is fair",
                null,
                true
        );

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentor));
        when(criteriaScoreRepository.findBySubmissionId(100L)).thenReturn(List.of());
        when(submissionRepository.save(any(AssignmentSubmission.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(submissionMapper.toDetailDto(any())).thenReturn(
                AssignmentSubmissionDetailDTO.builder().id(100L).build()
        );

        service.grade(100L, 7L, grading, null, null);

        ArgumentCaptor<AssignmentSubmission> captor = ArgumentCaptor.forClass(AssignmentSubmission.class);
        verify(submissionRepository).save(captor.capture());
        AssignmentSubmission saved = captor.getValue();

        assertFalse(saved.getDisputeFlag());
        assertNull(saved.getDisputeAt());
        assertNull(saved.getDisputeReason());
        assertEquals(new BigDecimal("85"), saved.getScore());
        assertTrue(saved.getIsPassed()); // 85 >= 70
    }

    @Test
    @DisplayName("grade sends two notifications when re-grading disputed submission")
    void grade_sendsReGradeAndGradingNotification_whenReGradingDisputed() {
        submission.setScore(new BigDecimal("40"));
        submission.setIsPassed(false);
        submission.setDisputeFlag(true);
        submission.setDisputeAt(Instant.now());
        submission.setDisputeReason("Too harsh");

        AssignmentGradeDTO grading = new AssignmentGradeDTO(
                new BigDecimal("90"),
                "Re-graded",
                null,
                true
        );

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentor));
        when(criteriaScoreRepository.findBySubmissionId(100L)).thenReturn(List.of());
        when(submissionRepository.save(any(AssignmentSubmission.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(submissionMapper.toDetailDto(any())).thenReturn(
                AssignmentSubmissionDetailDTO.builder().id(100L).build()
        );

        service.grade(100L, 7L, grading, null, null);

        // One notification for dispute clear, one for grading result
        verify(notificationService, org.mockito.Mockito.times(2))
                .createNotification(anyLong(), any(), any(), any(NotificationType.class), any(), any());
    }

    // ========================================================================
    // grade — manual mentor grading (isAiGrade = false / null)
    // ========================================================================

    @Test
    @DisplayName("grade with isAiGrade=false does NOT set AI fields")
    void grade_withIsAiGradeFalse_doesNotSetAiFields() {
        AssignmentGradeDTO grading = new AssignmentGradeDTO(
                new BigDecimal("60"),
                "Manual grade",
                null,
                false
        );

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentor));
        when(submissionRepository.save(any(AssignmentSubmission.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(submissionMapper.toDetailDto(any())).thenReturn(
                AssignmentSubmissionDetailDTO.builder().id(100L).build()
        );

        service.grade(100L, 7L, grading, null, null);

        ArgumentCaptor<AssignmentSubmission> captor = ArgumentCaptor.forClass(AssignmentSubmission.class);
        verify(submissionRepository).save(captor.capture());
        AssignmentSubmission saved = captor.getValue();

        // AI fields should remain unchanged (false/null from setUp)
        assertFalse(saved.getIsAiGraded());
        assertNull(saved.getMentorConfirmed());
        assertFalse(saved.getDisputeFlag());
        // But score and pass should be set
        assertEquals(new BigDecimal("60"), saved.getScore());
        assertFalse(saved.getIsPassed()); // 60 < 70
    }

    @Test
    @DisplayName("grade with manual override deletes existing criteria scores and passes based on total score")
    void grade_manualOverride_deletesCriteriaScoresAndPasses() {
        AssignmentGradeDTO grading = new AssignmentGradeDTO(
                new BigDecimal("85"),
                "Manual override pass",
                null,
                false
        );

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentor));
        when(submissionRepository.save(any(AssignmentSubmission.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(submissionMapper.toDetailDto(any())).thenReturn(
                AssignmentSubmissionDetailDTO.builder().id(100L).build()
        );

        service.grade(100L, 7L, grading, null, null);

        ArgumentCaptor<AssignmentSubmission> captor = ArgumentCaptor.forClass(AssignmentSubmission.class);
        verify(submissionRepository).save(captor.capture());
        AssignmentSubmission saved = captor.getValue();

        assertEquals(new BigDecimal("85"), saved.getScore());
        assertTrue(saved.getIsPassed()); // 85 >= 70 passing score
        verify(criteriaScoreRepository).deleteBySubmissionId(100L);
    }

    // ========================================================================
    // grade — error / guard path tests
    // ========================================================================

    @Test
    @DisplayName("grade throws BadRequestException when grading a previous submission")
    void grade_cannotGradePreviousSubmission_throwsBadRequest() {
        submission.setIsNewest(false);
        submission.setScore(new BigDecimal("50"));

        AssignmentGradeDTO grading = new AssignmentGradeDTO(
                new BigDecimal("80"),
                "Should fail",
                null,
                true
        );

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.grade(100L, 7L, grading, null, null));

        assertTrue(ex.getMessage().contains("previous submission"));
        verify(submissionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("grade throws BadRequestException when score exceeds max score")
    void grade_failsOnScoreExceedingMaxScore() {
        AssignmentGradeDTO grading = new AssignmentGradeDTO(
                new BigDecimal("150"),
                "Invalid",
                null,
                true
        );

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentor));
        when(criteriaScoreRepository.findBySubmissionId(100L)).thenReturn(List.of());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.grade(100L, 7L, grading, null, null));

        assertTrue(ex.getMessage().contains("cannot exceed max score"));
        verify(submissionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("grade recalculates course progress after grading")
    void grade_recalculatesCourseProgress_afterGrading() {
        AssignmentGradeDTO grading = new AssignmentGradeDTO(
                new BigDecimal("90"),
                "Great",
                null,
                true
        );

        when(submissionRepository.findById(100L)).thenReturn(Optional.of(submission));
        when(userRepository.findById(7L)).thenReturn(Optional.of(mentor));
        when(criteriaScoreRepository.findBySubmissionId(100L)).thenReturn(List.of());
        when(submissionRepository.save(any(AssignmentSubmission.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(submissionMapper.toDetailDto(any())).thenReturn(
                AssignmentSubmissionDetailDTO.builder().id(100L).build()
        );

        service.grade(100L, 7L, grading, null, null);

        verify(courseLearningProgressService).recalculateCourseProgress(30L, 9L);
    }
}
