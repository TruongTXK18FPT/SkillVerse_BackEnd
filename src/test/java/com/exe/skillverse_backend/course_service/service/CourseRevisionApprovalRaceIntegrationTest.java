package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionCreateDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSubmissionDetailDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizAttemptDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.SubmitQuizDTO;
import com.exe.skillverse_backend.course_service.entity.Assignment;
import com.exe.skillverse_backend.course_service.entity.AssignmentSubmission;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.CourseRevision;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.QuizAttempt;
import com.exe.skillverse_backend.course_service.entity.QuizOption;
import com.exe.skillverse_backend.course_service.entity.QuizQuestion;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import com.exe.skillverse_backend.course_service.mapper.AssignmentMapper;
import com.exe.skillverse_backend.course_service.mapper.AssignmentSubmissionMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizAttemptMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizOptionMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizQuestionMapper;
import com.exe.skillverse_backend.course_service.policy.CourseQuizAttemptSessionProperties;
import com.exe.skillverse_backend.course_service.policy.CourseRevisionFeatureProperties;
import com.exe.skillverse_backend.course_service.repository.AssignmentCriteriaRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentRepository;
import com.exe.skillverse_backend.course_service.repository.AssignmentSubmissionRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.repository.CourseRevisionRepository;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptAnswerSnapshotRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptSessionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizOptionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizQuestionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;
import com.exe.skillverse_backend.course_service.repository.SubmissionCriteriaScoreRepository;
import com.exe.skillverse_backend.course_service.service.impl.AssignmentServiceImpl;
import com.exe.skillverse_backend.course_service.service.impl.CourseAutoCompatibleUpgradeExecutor;
import com.exe.skillverse_backend.course_service.service.impl.CourseRevisionServiceImpl;
import com.exe.skillverse_backend.course_service.service.impl.QuizServiceImpl;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseRevisionApprovalRaceIntegrationTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseRevisionRepository courseRevisionRepository;
    @Mock
    private CourseRevisionFeatureProperties courseRevisionFeatureProperties;
    @Mock
    private CourseAutoCompatibleUpgradeExecutor autoCompatibleUpgradeExecutor;
    @Mock
    private Clock revisionClock;
    @Mock
    private ObjectMapper revisionObjectMapper;

    @Mock
    private QuizRepository quizRepository;
    @Mock
    private QuizQuestionRepository questionRepository;
    @Mock
    private QuizOptionRepository optionRepository;
    @Mock
    private ModuleRepository moduleRepository;
    @Mock
    private QuizAttemptRepository attemptRepository;
    @Mock
    private QuizMapper quizMapper;
    @Mock
    private QuizQuestionMapper questionMapper;
    @Mock
    private QuizOptionMapper optionMapper;
    @Mock
    private QuizAttemptMapper attemptMapper;
    @Mock
    private QuizAttemptAnswerSnapshotRepository attemptAnswerSnapshotRepository;
    @Mock
    private Clock quizClock;
    @Mock
    private CourseLearningProgressService courseLearningProgressService;
    @Mock
    private ObjectMapper quizObjectMapper;
    @Mock
    private QuizAttemptSessionRepository attemptSessionRepository;
    @Mock
    private CourseQuizAttemptSessionProperties attemptSessionProperties;

    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private AssignmentCriteriaRepository criteriaRepository;
    @Mock
    private SubmissionCriteriaScoreRepository criteriaScoreRepository;
    @Mock
    private AssignmentSubmissionRepository submissionRepository;
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
    private AssignmentSubmissionMapper assignmentSubmissionMapper;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private Clock assignmentClock;
    @Mock
    private MeterRegistry meterRegistry;

    private CourseRevisionServiceImpl courseRevisionService;
    private QuizServiceImpl quizService;
    private AssignmentServiceImpl assignmentService;

    @BeforeEach
    void setUp() {
        courseRevisionService = new CourseRevisionServiceImpl(
                courseRepository,
                courseRevisionRepository,
                courseRevisionFeatureProperties,
                autoCompatibleUpgradeExecutor,
                revisionClock,
                revisionObjectMapper,
                meterRegistry
        );
        quizService = new QuizServiceImpl(
                quizRepository,
                questionRepository,
                optionRepository,
                moduleRepository,
                attemptRepository,
                quizMapper,
                questionMapper,
                optionMapper,
                attemptMapper,
                quizClock,
                courseLearningProgressService,
                attemptAnswerSnapshotRepository,
                attemptSessionRepository,
                quizObjectMapper,
                attemptSessionProperties
        );
        assignmentService = new AssignmentServiceImpl(
                assignmentRepository,
                criteriaRepository,
                criteriaScoreRepository,
                submissionRepository,
                moduleRepository,
                enrollmentRepository,
                userRepository,
                mediaRepository,
                notificationService,
                assignmentMapper,
                assignmentSubmissionMapper,
                userProfileRepository,
                assignmentClock,
                courseLearningProgressService
        );
    }

    @Test
    void approveRevision_parallelWithSubmitQuiz_keepsSubmissionAndRevisionConsistency() throws Exception {
        long revisionId = 501L;
        long userId = 9L;
        long adminId = 3L;
        long courseId = 100L;
        long oldRevisionId = 401L;
        Instant now = Instant.parse("2026-03-18T09:00:00Z");

        User author = User.builder().id(77L).build();
        Course course = Course.builder()
                .id(courseId)
                .author(author)
                .status(CourseStatus.PUBLIC)
                .activeRevisionId(oldRevisionId)
                .latestRevisionId(oldRevisionId)
                .build();
        CourseRevision pendingRevision = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .status(CourseRevisionStatus.PENDING)
                .build();

        when(courseRevisionFeatureProperties.isApprovalEnabled()).thenReturn(true);
        when(courseRevisionRepository.findByIdForApproval(revisionId)).thenReturn(Optional.of(pendingRevision));
        when(courseRepository.findByIdForRevisionApproval(courseId)).thenReturn(Optional.of(course));
        when(revisionClock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(courseRepository.save(course)).thenReturn(course);
        when(autoCompatibleUpgradeExecutor.executeAfterRevisionApproval(eq(course), eq(oldRevisionId), eq(pendingRevision)))
                .thenReturn(CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult.upgraded(1));

        Course quizCourse = Course.builder().id(courseId).build();
        Module module = Module.builder().id(88L).course(quizCourse).build();
        Quiz quiz = Quiz.builder().id(12L).module(module).passScore(70).maxAttempts(3).build();
        QuizQuestion question = QuizQuestion.builder()
                .id(1L)
                .questionType(QuestionType.MULTIPLE_CHOICE)
                .score(1)
                .options(List.of(QuizOption.builder().id(10L).isCorrect(true).optionText("A").build()))
                .build();
        SubmitQuizDTO submitQuizDTO = SubmitQuizDTO.builder()
                .quizId(12L)
                .answers(List.of(new SubmitQuizDTO.Answer(1L, 10L, null, null)))
                .build();

        when(quizClock.instant()).thenReturn(now);
        when(quizRepository.findById(12L)).thenReturn(Optional.of(quiz));
        when(questionRepository.findByQuizIdWithOptions(12L)).thenReturn(List.of(question));
        when(attemptRepository.findByQuizIdAndUserIdOrderBySubmittedAtDesc(12L, userId)).thenReturn(List.of());
        when(attemptRepository.save(any(QuizAttempt.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(quizObjectMapper.writeValueAsString(any())).thenReturn("{}");
        when(attemptMapper.toDto(any(QuizAttempt.class))).thenReturn(QuizAttemptDTO.builder().score(100).passed(true).build());

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<CourseRevisionDTO> approveFuture = pool.submit(() -> {
                startLatch.await(3, TimeUnit.SECONDS);
                return courseRevisionService.approveRevision(revisionId, adminId);
            });
            Future<QuizAttemptDTO> submitFuture = pool.submit(() -> {
                startLatch.await(3, TimeUnit.SECONDS);
                return quizService.submitQuiz(12L, submitQuizDTO, userId);
            });

            startLatch.countDown();
            CourseRevisionDTO approved = approveFuture.get(5, TimeUnit.SECONDS);
            QuizAttemptDTO submitted = submitFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(approved);
            assertNotNull(submitted);
            assertEquals(CourseRevisionStatus.APPROVED, approved.getStatus());
            assertEquals(100, submitted.getScore());
            assertEquals(revisionId, course.getActiveRevisionId());
            verify(attemptRepository).save(any(QuizAttempt.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void approveRevision_parallelWithSubmitAssignment_preservesSubmissionAndNoUpgradeDuplication() throws Exception {
        long revisionId = 601L;
        long userId = 15L;
        long adminId = 3L;
        long courseId = 200L;
        long oldRevisionId = 551L;
        Instant now = Instant.parse("2026-03-18T09:10:00Z");

        User author = User.builder().id(70L).build();
        Course course = Course.builder()
                .id(courseId)
                .author(author)
                .status(CourseStatus.PUBLIC)
                .activeRevisionId(oldRevisionId)
                .latestRevisionId(oldRevisionId)
                .build();
        CourseRevision pendingRevision = CourseRevision.builder()
                .id(revisionId)
                .course(course)
                .status(CourseRevisionStatus.PENDING)
                .build();

        when(courseRevisionFeatureProperties.isApprovalEnabled()).thenReturn(true);
        when(courseRevisionRepository.findByIdForApproval(revisionId)).thenReturn(Optional.of(pendingRevision));
        when(courseRepository.findByIdForRevisionApproval(courseId)).thenReturn(Optional.of(course));
        when(revisionClock.instant()).thenReturn(now);
        when(courseRevisionRepository.save(any(CourseRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(courseRepository.save(course)).thenReturn(course);
        when(autoCompatibleUpgradeExecutor.executeAfterRevisionApproval(eq(course), eq(oldRevisionId), eq(pendingRevision)))
                .thenReturn(CourseAutoCompatibleUpgradeExecutor.AutoUpgradeExecutionResult.upgraded(1));

        User learner = User.builder().id(userId).build();
        Module module = Module.builder().id(99L).course(course).build();
        Assignment assignment = Assignment.builder()
                .id(71L)
                .module(module)
                .submissionType(SubmissionType.TEXT)
                .build();
        CourseEnrollment enrollment = CourseEnrollment.builder().status(EnrollmentStatus.ENROLLED).build();
        AssignmentSubmission submission = AssignmentSubmission.builder().id(7001L).build();

        when(assignmentRepository.findById(71L)).thenReturn(Optional.of(assignment));
        when(enrollmentRepository.findByCourseIdAndUserId(courseId, userId)).thenReturn(Optional.of(enrollment));
        when(userRepository.findById(userId)).thenReturn(Optional.of(learner));
        when(submissionRepository.findByAssignmentIdAndUserIdOrderByAttemptNumberDesc(71L, userId)).thenReturn(List.of());
        when(assignmentSubmissionMapper.toEntity(any(), eq(assignment), eq(learner), eq(null))).thenReturn(submission);
        when(assignmentClock.instant()).thenReturn(now);
        when(submissionRepository.save(any(AssignmentSubmission.class))).thenReturn(submission);
        when(assignmentSubmissionMapper.toDetailDto(submission)).thenReturn(AssignmentSubmissionDetailDTO.builder().id(7001L).build());
        when(criteriaScoreRepository.findBySubmissionId(7001L)).thenReturn(List.of());

        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<CourseRevisionDTO> approveFuture = pool.submit(() -> {
                startLatch.await(3, TimeUnit.SECONDS);
                return courseRevisionService.approveRevision(revisionId, adminId);
            });
            Future<AssignmentSubmissionDetailDTO> submitFuture = pool.submit(() -> {
                startLatch.await(3, TimeUnit.SECONDS);
                AssignmentSubmissionCreateDTO request = new AssignmentSubmissionCreateDTO();
                request.setSubmissionText("answer");
                return assignmentService.submit(71L, userId, request);
            });

            startLatch.countDown();
            CourseRevisionDTO approved = approveFuture.get(5, TimeUnit.SECONDS);
            AssignmentSubmissionDetailDTO submitted = submitFuture.get(5, TimeUnit.SECONDS);

            assertNotNull(approved);
            assertNotNull(submitted);
            assertEquals(CourseRevisionStatus.APPROVED, approved.getStatus());
            assertEquals(7001L, submitted.getId());
            verify(submissionRepository).save(any(AssignmentSubmission.class));
            verify(autoCompatibleUpgradeExecutor).executeAfterRevisionApproval(eq(course), eq(oldRevisionId), eq(pendingRevision));
        } finally {
            pool.shutdownNow();
        }
    }
}

