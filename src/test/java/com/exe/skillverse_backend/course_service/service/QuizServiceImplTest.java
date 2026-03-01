package com.exe.skillverse_backend.course_service.service;

import com.exe.skillverse_backend.course_service.dto.quizdto.QuizAttemptDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizDetailDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizOptionDetailDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizQuestionDetailDTO;
import com.exe.skillverse_backend.course_service.dto.quizdto.SubmitQuizDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.QuizAttempt;
import com.exe.skillverse_backend.course_service.entity.QuizOption;
import com.exe.skillverse_backend.course_service.entity.QuizQuestion;
import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import com.exe.skillverse_backend.course_service.mapper.QuizAttemptMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizOptionMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizQuestionMapper;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptRepository;
import com.exe.skillverse_backend.course_service.repository.QuizOptionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizQuestionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;
import com.exe.skillverse_backend.course_service.service.impl.QuizServiceImpl;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
    private Clock clock;

    @Mock
    private CourseLearningProgressService courseLearningProgressService;

    @InjectMocks
    private QuizServiceImpl quizService;

    @Test
    void getQuizForAttempt_stripsCorrectFlagsAndFeedback() {
        Quiz quiz = Quiz.builder().id(9L).build();
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
        when(quizMapper.toDetailDto(quiz)).thenReturn(detail);

        QuizDetailDTO learnerView = quizService.getQuizForAttempt(9L);

        assertFalse(learnerView.getQuestions().get(0).getOptions().get(0).isCorrect());
        assertEquals(null, learnerView.getQuestions().get(0).getOptions().get(0).getFeedback());
        assertEquals(1, learnerView.getQuestions().get(0).getCorrectOptionCount());
    }

    @Test
    void submitQuiz_shortAnswerIgnoresCaseAndExtraWhitespace() {
        Course course = Course.builder().id(99L).build();
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

        when(clock.instant()).thenReturn(Instant.parse("2026-03-01T09:00:00Z"));
        when(quizRepository.findById(12L)).thenReturn(Optional.of(quiz));
        when(questionRepository.findByQuizIdWithOptions(12L)).thenReturn(List.of(question));
        when(attemptRepository.findByQuizIdAndUserIdOrderBySubmittedAtDesc(12L, 7L)).thenReturn(List.of());
        when(attemptRepository.save(any(QuizAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, QuizAttempt.class));
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
    }
}
