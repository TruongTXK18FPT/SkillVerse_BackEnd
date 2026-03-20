package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.quizdto.QuizAttemptDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizDetailDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizOptionDetailDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizQuestionDetailDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizAttemptSessionDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.SubmitQuizDTO;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.QuizAttempt;
import com.exe.skillverse_backend.course_service.entity.QuizOption;
import com.exe.skillverse_backend.course_service.entity.QuizQuestion;
import com.exe.skillverse_backend.course_service.entity.CourseEnrollment;
import com.exe.skillverse_backend.course_service.entity.enums.EnrollmentStatus;
import com.exe.skillverse_backend.course_service.entity.enums.QuizAttemptSessionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import com.exe.skillverse_backend.course_service.mapper.QuizAttemptMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizOptionMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizQuestionMapper;
import com.exe.skillverse_backend.course_service.policy.CourseQuizAttemptSessionProperties;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.repository.CourseEnrollmentRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptAnswerSnapshotRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptSessionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizOptionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizQuestionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;
import com.exe.skillverse_backend.course_service.service.impl.QuizServiceImpl;
import com.exe.skillverse_backend.course_service.service.impl.RevisionPinnedContentResolver;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class QuizServiceImplTest {

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizQuestionRepository questionRepository;

    @Mock
    private QuizOptionRepository optionRepository;

    @Mock
    private ModuleRepository moduleRepository;

    @Mock
    private CourseEnrollmentRepository enrollmentRepository;

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
    private QuizAttemptSessionRepository attemptSessionRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private CourseQuizAttemptSessionProperties attemptSessionProperties;

    @Mock
    private Clock clock;

    @Mock
    private CourseLearningProgressService courseLearningProgressService;

        @Mock
        private RevisionPinnedContentResolver revisionPinnedContentResolver;

    @InjectMocks
    private QuizServiceImpl quizService;

    @Test
    void getQuizForAttempt_stripsCorrectFlagsAndFeedback() {
        Course course = Course.builder().id(42L).author(User.builder().id(1000L).build()).build();
        Module module = Module.builder().id(21L).course(course).build();
        Quiz quiz = Quiz.builder().id(9L).module(module).build();
        CourseEnrollment enrollment = CourseEnrollment.builder().status(EnrollmentStatus.ENROLLED).build();
        QuizOptionDetailDTO option = new QuizOptionDetailDTO(1L, "Option A", true, "secret", 1);
        QuizQuestionDetailDTO question = new QuizQuestionDetailDTO(
                5L,
                "Cau hoi",
                QuestionType.MULTIPLE_CHOICE,
                1,
                1,
                List.of(option),
                null
        );
        QuizDetailDTO detail = new QuizDetailDTO(
                9L,
                "Quiz",
                "desc",
                70,
                3,
                null,
                1,
                null,
                false,
                null,
                1,
                2L,
                Instant.parse("2026-03-01T08:00:00Z"),
                Instant.parse("2026-03-01T08:00:00Z"),
                List.of(question)
        );

        when(quizRepository.findById(9L)).thenReturn(Optional.of(quiz));
        when(enrollmentRepository.findByCourseIdAndUserId(42L, 88L)).thenReturn(Optional.of(enrollment));
        when(quizMapper.toDetailDto(quiz)).thenReturn(detail);

        QuizDetailDTO learnerView = quizService.getQuizForAttempt(9L, 88L);

        assertFalse(learnerView.getQuestions().get(0).getOptions().get(0).isCorrect());
        assertEquals(null, learnerView.getQuestions().get(0).getOptions().get(0).getFeedback());
        assertEquals(1, learnerView.getQuestions().get(0).getCorrectOptionCount());
        verifyNoInteractions(attemptSessionRepository);
    }

    @Test
    void submitQuiz_shortAnswerIgnoresCaseAndExtraWhitespace() throws Exception {
        Course course = Course.builder().id(99L).author(User.builder().id(1001L).build()).build();
        Module module = Module.builder().id(5L).course(course).build();
        Quiz quiz = Quiz.builder()
                .id(12L)
                .passScore(70)
                .maxAttempts(3)
                .module(module)
                .build();
        QuizQuestion question = QuizQuestion.builder()
                .id(44L)
                .questionType(QuestionType.SHORT_ANSWER)
                .score(1)
                .options(List.of(
                        QuizOption.builder().id(101L).optionText("Java").isCorrect(true).build()
                ))
                .build();
        SubmitQuizDTO submitQuizDTO = SubmitQuizDTO.builder()
                .quizId(12L)
                .answers(List.of(new SubmitQuizDTO.Answer(44L, null, null, "  jAvA   ")))
                .build();
        CourseEnrollment enrollment = CourseEnrollment.builder().status(EnrollmentStatus.ENROLLED).build();

        when(clock.instant()).thenReturn(Instant.parse("2026-03-01T09:00:00Z"));
        when(attemptSessionProperties.isEnabled()).thenReturn(true);
        when(quizRepository.findById(12L)).thenReturn(Optional.of(quiz));
        when(enrollmentRepository.findByCourseIdAndUserId(99L, 7L)).thenReturn(Optional.of(enrollment));
        when(questionRepository.findByQuizIdWithOptions(12L)).thenReturn(List.of(question));
        when(attemptRepository.findByQuizIdAndUserIdOrderBySubmittedAtDesc(12L, 7L)).thenReturn(List.of());
        when(attemptRepository.save(any(QuizAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, QuizAttempt.class));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(attemptMapper.toDto(any(QuizAttempt.class)))
                .thenAnswer(invocation -> {
                    QuizAttempt saved = invocation.getArgument(0, QuizAttempt.class);
                    return QuizAttemptDTO.builder()
                            .score(saved.getScore())
                            .passed(saved.getPassed())
                            .correctAnswers(saved.getCorrectAnswers())
                            .totalQuestions(saved.getTotalQuestions())
                            .build();
                });

        QuizAttemptDTO result = quizService.submitQuiz(12L, submitQuizDTO, 7L);

        assertEquals(100, result.getScore());
        assertTrue(result.getPassed());
        assertEquals(1, result.getCorrectAnswers());
        verify(attemptSessionRepository).markActiveSessionsSubmitted(
                12L,
                7L,
                QuizAttemptSessionStatus.IN_PROGRESS,
                QuizAttemptSessionStatus.SUBMITTED,
                Instant.parse("2026-03-01T09:00:00Z")
        );
    }

    @Test
    void startAttemptSession_createsSessionWhenNoActiveSession() {
        Course course = Course.builder().id(77L).author(User.builder().id(1002L).build()).build();
        Module module = Module.builder().id(55L).course(course).build();
        Quiz quiz = Quiz.builder()
                .id(33L)
                .module(module)
                .build();
        CourseEnrollment enrollment = CourseEnrollment.builder().status(EnrollmentStatus.ENROLLED).build();

        when(clock.instant()).thenReturn(Instant.parse("2026-03-01T08:00:00Z"));
        when(attemptSessionProperties.isEnabled()).thenReturn(true);
        when(attemptSessionProperties.getTtlMinutes()).thenReturn(30);
        when(quizRepository.findById(33L)).thenReturn(Optional.of(quiz));
        when(enrollmentRepository.findByCourseIdAndUserId(77L, 99L)).thenReturn(Optional.of(enrollment));
        when(attemptSessionRepository.findLatestActiveSession(33L, 99L, QuizAttemptSessionStatus.IN_PROGRESS, Instant.parse("2026-03-01T08:00:00Z")))
                .thenReturn(Optional.empty());
        when(attemptSessionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuizAttemptSessionDTO session = quizService.startAttemptSession(33L, 99L);

        assertNotNull(session);
        assertEquals(33L, session.getQuizId());
        assertEquals(99L, session.getUserId());
        assertEquals("IN_PROGRESS", session.getStatus());
        assertNotNull(session.getSessionToken());
    }

        @Test
        void startAttemptSession_throwsWhenLearnerAlreadyPassedQuiz() {
                Course course = Course.builder().id(78L).author(User.builder().id(1002L).build()).build();
                Module module = Module.builder().id(56L).course(course).build();
                Quiz quiz = Quiz.builder().id(34L).module(module).build();
                CourseEnrollment enrollment = CourseEnrollment.builder().status(EnrollmentStatus.ENROLLED).build();
                QuizAttempt passedAttempt = QuizAttempt.builder().id(1001L).passed(true).build();

                when(quizRepository.findById(34L)).thenReturn(Optional.of(quiz));
                when(enrollmentRepository.findByCourseIdAndUserId(78L, 199L)).thenReturn(Optional.of(enrollment));
                when(attemptRepository.findByQuizIdAndUserIdOrderBySubmittedAtDesc(34L, 199L))
                                .thenReturn(List.of(passedAttempt));

                BadRequestException ex = assertThrows(BadRequestException.class, () -> quizService.startAttemptSession(34L, 199L));
                assertTrue(ex.getMessage().contains("QUIZ_RETRY_LOCKED_BY_PASS"));
        }

        @Test
        void submitQuiz_throwsWhenLearnerAlreadyPassedQuiz() {
                Course course = Course.builder().id(79L).author(User.builder().id(1002L).build()).build();
                Module module = Module.builder().id(57L).course(course).build();
                Quiz quiz = Quiz.builder().id(35L).module(module).passScore(70).maxAttempts(3).build();
                CourseEnrollment enrollment = CourseEnrollment.builder().status(EnrollmentStatus.ENROLLED).build();
                QuizAttempt passedAttempt = QuizAttempt.builder().id(1002L).passed(true).build();
                SubmitQuizDTO submitQuizDTO = SubmitQuizDTO.builder().quizId(35L).answers(List.of()).build();

                when(clock.instant()).thenReturn(Instant.parse("2026-03-02T09:00:00Z"));
                when(quizRepository.findById(35L)).thenReturn(Optional.of(quiz));
                when(enrollmentRepository.findByCourseIdAndUserId(79L, 200L)).thenReturn(Optional.of(enrollment));
                when(attemptRepository.findByQuizIdAndUserIdOrderBySubmittedAtDesc(35L, 200L))
                                .thenReturn(List.of(passedAttempt));

                BadRequestException ex = assertThrows(BadRequestException.class, () -> quizService.submitQuiz(35L, submitQuizDTO, 200L));
                assertTrue(ex.getMessage().contains("QUIZ_RETRY_LOCKED_BY_PASS"));
        }

        @Test
        void getAttemptStatus_setsCanRetryFalseWhenAnyAttemptPassed() {
                Course course = Course.builder().id(80L).author(User.builder().id(1004L).build()).build();
                Module module = Module.builder().id(58L).course(course).build();
                Quiz quiz = Quiz.builder().id(36L).module(module).passScore(70).maxAttempts(3).build();
                CourseEnrollment enrollment = CourseEnrollment.builder().status(EnrollmentStatus.ENROLLED).build();
                QuizAttempt passedAttempt = QuizAttempt.builder()
                                .id(1003L)
                                .score(85)
                                .passed(true)
                                .submittedAt(Instant.parse("2026-03-02T10:00:00Z"))
                                .build();

                when(quizRepository.findById(36L)).thenReturn(Optional.of(quiz));
                when(enrollmentRepository.findByCourseIdAndUserId(80L, 201L)).thenReturn(Optional.of(enrollment));
                when(attemptRepository.findByQuizIdAndUserIdOrderBySubmittedAtDesc(36L, 201L))
                                .thenReturn(List.of(passedAttempt));
                when(attemptMapper.toDto(any(QuizAttempt.class))).thenReturn(
                                QuizAttemptDTO.builder()
                                                .quizId(36L)
                                                .userId(201L)
                                                .score(85)
                                                .passed(true)
                                                .submittedAt(Instant.parse("2026-03-02T10:00:00Z"))
                                                .build()
                );

                var status = quizService.getAttemptStatus(36L, 201L);

                assertTrue(status.isHasPassed());
                assertFalse(status.isCanRetry());
                assertEquals(1, status.getAttemptsUsed());
                assertEquals(3, status.getMaxAttempts());
                assertEquals(85, status.getBestScore());
                verify(attemptRepository).findByQuizIdAndUserIdOrderBySubmittedAtDesc(eq(36L), eq(201L));
        }

        @Test
        void getQuizForAttempt_throwsNotFoundWhenQuizNotInPinnedSnapshot() {
                Course course = Course.builder()
                                .id(120L)
                                .revisioningEnabled(true)
                                .author(User.builder().id(1003L).build())
                                .build();
                Module module = Module.builder().id(45L).course(course).build();
                Quiz quiz = Quiz.builder().id(100L).module(module).build();
                CourseEnrollment enrollment = CourseEnrollment.builder().status(EnrollmentStatus.ENROLLED).build();

                when(quizRepository.findById(100L)).thenReturn(Optional.of(quiz));
                when(enrollmentRepository.findByCourseIdAndUserId(120L, 88L)).thenReturn(Optional.of(enrollment));
                when(revisionPinnedContentResolver.hasLearningAccessEnrollment(course, 88L)).thenReturn(true);
                when(revisionPinnedContentResolver.isQuizInPinnedRevision(course, 88L, 100L)).thenReturn(false);

                assertThrows(NotFoundException.class, () -> quizService.getQuizForAttempt(100L, 88L));
        }
}
