package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.quizdto.*;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizSummaryDTO;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.QuizQuestion;
import com.exe.skillverse_backend.course_service.entity.QuizOption;
import com.exe.skillverse_backend.course_service.entity.QuizAttempt;
import com.exe.skillverse_backend.course_service.mapper.QuizMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizQuestionMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizOptionMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizAttemptMapper;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;
import com.exe.skillverse_backend.course_service.repository.QuizQuestionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizOptionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptRepository;
import com.exe.skillverse_backend.course_service.service.QuizService;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository questionRepository;
    private final QuizOptionRepository optionRepository;
    private final ModuleRepository moduleRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizMapper quizMapper;
    private final QuizQuestionMapper questionMapper;
    private final QuizOptionMapper optionMapper;
    private final QuizAttemptMapper attemptMapper;
    private final Clock clock;

    @Override
    @Transactional
    public QuizDetailDTO createQuiz(Long moduleId, QuizCreateDTO dto, Long actorId) {
        log.info("Creating quiz '{}' for module {} by actor {}", dto.getTitle(), moduleId, actorId);

        Module module = getModuleOrThrow(moduleId);
        ensureAuthorOrAdmin(actorId, module.getCourse().getAuthor().getId());

        validateCreateQuizRequest(dto);

        // Check if quiz already exists for this module
        Optional<Quiz> existingQuiz = quizRepository.findByModuleId(moduleId);
        if (existingQuiz.isPresent()) {
            log.warn("Quiz already exists for module {}, returning existing quiz", moduleId);
            return quizMapper.toDetailDto(existingQuiz.get());
        }

        Quiz quiz = quizMapper.toEntity(dto, module);
        quiz.setCreatedAt(now());

        try {
            Quiz saved = quizRepository.save(quiz);
            log.info("Quiz {} created for module {} by actor {}", saved.getId(), moduleId, actorId);
            return quizMapper.toDetailDto(saved);
        } catch (Exception e) {
            log.error("Failed to create quiz for module {}: {}", moduleId, e.getMessage());
            // Check if it's a unique constraint violation
            if (e.getMessage() != null && e.getMessage().contains("unique constraint")) {
                log.warn("Quiz already exists for module {}, attempting to find existing quiz", moduleId);
                Optional<Quiz> existingQuizRetry = quizRepository.findByModuleId(moduleId);
                if (existingQuizRetry.isPresent()) {
                    return quizMapper.toDetailDto(existingQuizRetry.get());
                }
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public QuizDetailDTO updateQuiz(Long quizId, QuizUpdateDTO dto, Long actorId) {
        log.info("Updating quiz {} by actor {}", quizId, actorId);

        Quiz quiz = getQuizOrThrow(quizId);
        ensureAuthorOrAdmin(actorId, quiz.getModule().getCourse().getAuthor().getId());

        validateUpdateQuizRequest(dto);

        quizMapper.updateEntity(quiz, dto);

        Quiz saved = quizRepository.save(quiz);
        log.info("Quiz {} updated by actor {}", quizId, actorId);

        return quizMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public void deleteQuiz(Long quizId, Long actorId) {
        log.info("Deleting quiz {} by actor {}", quizId, actorId);

        Quiz quiz = getQuizOrThrow(quizId);
        ensureAuthorOrAdmin(actorId, quiz.getModule().getCourse().getAuthor().getId());

        // Cascade delete will handle questions and options
        quizRepository.delete(quiz);
        log.info("Quiz {} deleted by actor {}", quizId, actorId);
    }

    @Override
    @Transactional
    public QuizQuestionDetailDTO addQuestion(Long quizId, QuizQuestionCreateDTO dto, Long actorId) {
        log.info("Adding question to quiz {} by actor {}", quizId, actorId);

        Quiz quiz = getQuizOrThrow(quizId);
        ensureAuthorOrAdmin(actorId, quiz.getModule().getCourse().getAuthor().getId());

        validateCreateQuestionRequest(dto);

        // Auto-generate orderIndex if not provided
        Integer orderIndex = dto.getOrderIndex();
        if (orderIndex == null) {
            orderIndex = (int) (questionRepository.countByQuizId(quizId) + 1);
        }

        QuizQuestion question = questionMapper.toEntity(dto, quiz);
        question.setOrderIndex(orderIndex);

        QuizQuestion saved = questionRepository.save(question);
        log.info("Question {} added to quiz {} by actor {}", saved.getId(), quizId, actorId);

        return questionMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public QuizQuestionDetailDTO updateQuestion(Long questionId, QuizQuestionUpdateDTO dto, Long actorId) {
        log.info("Updating question {} by actor {}", questionId, actorId);

        QuizQuestion question = getQuestionOrThrow(questionId);
        ensureAuthorOrAdmin(actorId, question.getQuiz().getModule().getCourse().getAuthor().getId());

        validateUpdateQuestionRequest(dto);

        questionMapper.updateEntity(question, dto);

        QuizQuestion saved = questionRepository.save(question);
        log.info("Question {} updated by actor {}", questionId, actorId);

        return questionMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public void deleteQuestion(Long questionId, Long actorId) {
        log.info("Deleting question {} by actor {}", questionId, actorId);

        QuizQuestion question = getQuestionOrThrow(questionId);
        ensureAuthorOrAdmin(actorId, question.getQuiz().getModule().getCourse().getAuthor().getId());

        // Cascade delete will handle options
        questionRepository.delete(question);
        log.info("Question {} deleted by actor {}", questionId, actorId);
    }

    @Override
    @Transactional
    public QuizOptionDetailDTO addOption(Long questionId, QuizOptionCreateDTO dto, Long actorId) {
        log.info("Adding option to question {} by actor {}", questionId, actorId);

        QuizQuestion question = getQuestionOrThrow(questionId);
        ensureAuthorOrAdmin(actorId, question.getQuiz().getModule().getCourse().getAuthor().getId());

        validateCreateOptionRequest(dto);

        // QuizOption doesn't have orderIndex, so we don't set it
        QuizOption option = optionMapper.toEntity(dto, question);

        QuizOption saved = optionRepository.save(option);
        log.info("Option {} added to question {} by actor {}", saved.getId(), questionId, actorId);

        return optionMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public QuizOptionDetailDTO updateOption(Long optionId, QuizOptionUpdateDTO dto, Long actorId) {
        log.info("Updating option {} by actor {}", optionId, actorId);

        QuizOption option = getOptionOrThrow(optionId);
        ensureAuthorOrAdmin(actorId, option.getQuestion().getQuiz().getModule().getCourse().getAuthor().getId());

        validateUpdateOptionRequest(dto);

        optionMapper.updateEntity(option, dto);

        QuizOption saved = optionRepository.save(option);
        log.info("Option {} updated by actor {}", optionId, actorId);

        return optionMapper.toDetailDto(saved);
    }

    @Override
    @Transactional
    public void deleteOption(Long optionId, Long actorId) {
        log.info("Deleting option {} by actor {}", optionId, actorId);

        QuizOption option = getOptionOrThrow(optionId);
        ensureAuthorOrAdmin(actorId, option.getQuestion().getQuiz().getModule().getCourse().getAuthor().getId());

        optionRepository.delete(option);
        log.info("Option {} deleted by actor {}", optionId, actorId);
    }

    // ===== Helper Methods =====

    private Module getModuleOrThrow(Long moduleId) {
        return moduleRepository.findById(moduleId)
                .orElseThrow(() -> new NotFoundException("MODULE_NOT_FOUND"));
    }

    private Quiz getQuizOrThrow(Long quizId) {
        return quizRepository.findById(quizId)
                .orElseThrow(() -> new NotFoundException("QUIZ_NOT_FOUND"));
    }

    private QuizQuestion getQuestionOrThrow(Long questionId) {
        return questionRepository.findById(questionId)
                .orElseThrow(() -> new NotFoundException("QUESTION_NOT_FOUND"));
    }

    private QuizOption getOptionOrThrow(Long optionId) {
        return optionRepository.findById(optionId)
                .orElseThrow(() -> new NotFoundException("OPTION_NOT_FOUND"));
    }

    private void ensureAuthorOrAdmin(Long actorId, Long authorId) {
        // TODO: call Auth/Role service to check if actor is ADMIN
        if (!actorId.equals(authorId)) {
            // TODO: implement proper role checking via AuthService
            throw new AccessDeniedException("FORBIDDEN");
        }
    }

    private void validateCreateQuizRequest(QuizCreateDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Quiz title is required");
        }
        // TODO: add more validation (time limits, question requirements, etc.)
    }

    private void validateUpdateQuizRequest(QuizUpdateDTO dto) {
        if (dto.getTitle() != null && dto.getTitle().isBlank()) {
            throw new IllegalArgumentException("Quiz title cannot be blank");
        }
        // TODO: add more validation
    }

    private void validateCreateQuestionRequest(QuizQuestionCreateDTO dto) {
        if (dto.getQuestionText() == null || dto.getQuestionText().isBlank()) {
            throw new IllegalArgumentException("Question text is required");
        }
        if (dto.getQuestionType() == null) {
            throw new IllegalArgumentException("Question type is required");
        }
        // TODO: add more validation (score validation, type-specific rules, etc.)
    }

    private void validateUpdateQuestionRequest(QuizQuestionUpdateDTO dto) {
        if (dto.getQuestionText() != null && dto.getQuestionText().isBlank()) {
            throw new IllegalArgumentException("Question text cannot be blank");
        }
        // TODO: add more validation
    }

    private void validateCreateOptionRequest(QuizOptionCreateDTO dto) {
        if (dto.getOptionText() == null || dto.getOptionText().isBlank()) {
            throw new IllegalArgumentException("Option text is required");
        }
        // TODO: add more validation (ensure at least one correct option, etc.)
    }

    private void validateUpdateOptionRequest(QuizOptionUpdateDTO dto) {
        if (dto.getOptionText() != null && dto.getOptionText().isBlank()) {
            throw new IllegalArgumentException("Option text cannot be blank");
        }
        // TODO: add more validation
    }

    private Instant now() {
        return Instant.now(clock);
    }

    // ========== Quiz Query Operations ==========

    @Override
    @Transactional(readOnly = true)
    public QuizDetailDTO getQuiz(Long quizId) {
        log.debug("Getting quiz details for {}", quizId);

        Quiz quiz = getQuizOrThrow(quizId);
        return quizMapper.toDetailDto(quiz);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizSummaryDTO> listQuizzesByModule(Long moduleId) {
        log.debug("Listing quizzes for module {}", moduleId);

        // Verify module exists
        getModuleOrThrow(moduleId);

        List<Quiz> quizzes = quizRepository.findByModuleIdWithQuestions(moduleId);
        return quizzes.stream()
                .map(quizMapper::toSummaryDto)
                .toList();
    }

    // ========== Quiz Attempt & Submission ==========

    @Override
    @Transactional
    public QuizAttemptDTO submitQuiz(Long quizId, SubmitQuizDTO submitData, Long userId) {
        log.info("[QUIZ_SUBMIT] User {} submitting quiz {}", userId, quizId);

        Quiz quiz = getQuizOrThrow(quizId);

        // Check attempts in last 24 hours
        Instant yesterday = Instant.now(clock).minusSeconds(24 * 60 * 60);
        Long recentAttempts = attemptRepository.countByQuizIdAndUserIdAndSubmittedAtAfter(quizId, userId, yesterday);

        if (recentAttempts >= 3) {
            log.warn("[QUIZ_SUBMIT] User {} exceeded max attempts for quiz {}", userId, quizId);
            throw new IllegalStateException("Bạn đã hết lượt làm bài. Vui lòng quay lại sau 24 giờ.");
        }

        // Grade quiz
        int correctCount = 0;
        int totalQuestions = quiz.getQuestions().size();

        for (SubmitQuizDTO.Answer answer : submitData.getAnswers()) {
            QuizOption option = optionRepository.findById(answer.getSelectedOptionId()).orElse(null);
            if (option != null && Boolean.TRUE.equals(option.getIsCorrect())) {
                correctCount++;
            }
        }

        int score = totalQuestions > 0 ? (correctCount * 100) / totalQuestions : 0;
        boolean passed = score >= quiz.getPassScore();

        log.info("[QUIZ_SUBMIT] Score: {}/{} = {}% (Pass: {})", correctCount, totalQuestions, score, passed);

        // Save attempt
        QuizAttempt attempt = QuizAttempt.builder()
                .quiz(quiz)
                .userId(userId)
                .score(score)
                .passed(passed)
                .correctAnswers(correctCount)
                .totalQuestions(totalQuestions)
                .submittedAt(Instant.now(clock))
                .build();

        QuizAttempt saved = attemptRepository.save(attempt);
        log.info("[QUIZ_SUBMIT] Attempt saved: id={}", saved.getId());

        return attemptMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizAttemptDTO> getUserAttempts(Long quizId, Long userId) {
        log.debug("Getting attempts for quiz {} by user {}", quizId, userId);

        List<QuizAttempt> attempts = attemptRepository.findByQuizIdAndUserIdOrderBySubmittedAtDesc(quizId, userId);
        return attempts.stream()
                .map(attemptMapper::toDto)
                .toList();
    }
}