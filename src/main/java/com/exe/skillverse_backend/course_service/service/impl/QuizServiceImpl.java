package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.quizdto.*;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizSummaryDTO;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.QuizQuestion;
import com.exe.skillverse_backend.course_service.entity.QuizOption;
import com.exe.skillverse_backend.course_service.entity.QuizAttempt;
import com.exe.skillverse_backend.course_service.entity.enums.QuizGradingMethod;
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
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

        // Check if quiz already exists for this module (Allowed now)
        // Optional<Quiz> existingQuiz = quizRepository.findByModuleId(moduleId);
        // if (existingQuiz.isPresent()) {
        // log.warn("Quiz already exists for module {}, returning existing quiz",
        // moduleId);
        // return quizMapper.toDetailDto(existingQuiz.get());
        // }

        Quiz quiz = quizMapper.toEntity(dto, module);
        applyQuizDefaults(quiz);
        quiz.setCreatedAt(now());

        try {
            Quiz saved = quizRepository.save(quiz);
            log.info("Quiz {} created for module {} by actor {}", saved.getId(), moduleId, actorId);
            return quizMapper.toDetailDto(saved);
        } catch (Exception e) {
            log.error("Failed to create quiz for module {}: {}", moduleId, e.getMessage());
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
        applyQuizDefaults(quiz);

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

        // Auto-generate orderIndex if not provided
        Integer orderIndex = dto.getOrderIndex();
        if (orderIndex == null) {
            orderIndex = (int) (optionRepository.countByQuestionId(questionId) + 1);
        }

        QuizOption option = optionMapper.toEntity(dto, question);
        option.setOrderIndex(orderIndex);

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
            throw new BadRequestException("Quiz title is required");
        }
        validateQuizSettings(dto.getMaxAttempts(), dto.getTimeLimitMinutes(), dto.getRoundingIncrement());
    }

    private void validateUpdateQuizRequest(QuizUpdateDTO dto) {
        if (dto.getTitle() != null && dto.getTitle().isBlank()) {
            throw new BadRequestException("Quiz title cannot be blank");
        }
        validateQuizSettings(dto.getMaxAttempts(), dto.getTimeLimitMinutes(), dto.getRoundingIncrement());
    }

    private void validateQuizSettings(Integer maxAttempts, Integer timeLimitMinutes, Integer roundingIncrement) {
        if (maxAttempts != null && maxAttempts <= 0) {
            throw new BadRequestException("Max attempts must be greater than 0");
        }
        if (timeLimitMinutes != null && timeLimitMinutes <= 0) {
            throw new BadRequestException("Time limit must be greater than 0");
        }
        if (roundingIncrement != null && roundingIncrement <= 0) {
            throw new BadRequestException("Rounding increment must be greater than 0");
        }
    }

    private void validateCreateQuestionRequest(QuizQuestionCreateDTO dto) {
        if (dto.getQuestionText() == null || dto.getQuestionText().isBlank()) {
            throw new BadRequestException("Question text is required");
        }
        if (dto.getQuestionType() == null) {
            throw new BadRequestException("Question type is required");
        }
        // TODO: add more validation (score validation, type-specific rules, etc.)
    }

    private void validateUpdateQuestionRequest(QuizQuestionUpdateDTO dto) {
        if (dto.getQuestionText() != null && dto.getQuestionText().isBlank()) {
            throw new BadRequestException("Question text cannot be blank");
        }
        // TODO: add more validation
    }

    private void validateCreateOptionRequest(QuizOptionCreateDTO dto) {
        if (dto.getOptionText() == null || dto.getOptionText().isBlank()) {
            throw new BadRequestException("Option text is required");
        }
        // TODO: add more validation (ensure at least one correct option, etc.)
    }

    private void validateUpdateOptionRequest(QuizOptionUpdateDTO dto) {
        if (dto.getOptionText() != null && dto.getOptionText().isBlank()) {
            throw new BadRequestException("Option text cannot be blank");
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
        applyQuizDefaults(quiz);

        List<QuizQuestion> quizQuestions = questionRepository.findByQuizIdWithOptions(quizId);
        Map<Long, QuizQuestion> questionMap = quizQuestions.stream()
                .collect(Collectors.toMap(QuizQuestion::getId, q -> q, (a, b) -> a));

        List<QuizAttempt> attemptEntities = attemptRepository.findByQuizIdAndUserIdOrderBySubmittedAtDesc(quizId, userId);
        int maxAttempts = quiz.getMaxAttempts() != null ? quiz.getMaxAttempts() : 3;
        boolean useWindow = Boolean.TRUE.equals(quiz.getIsAssessment())
                && quiz.getCooldownHours() != null
                && quiz.getCooldownHours() > 0;
        if (useWindow) {
            Instant windowStart = now().minus(Duration.ofHours(quiz.getCooldownHours()));
            List<QuizAttempt> windowAttempts = attemptEntities.stream()
                    .filter(a -> a.getSubmittedAt() != null && !a.getSubmittedAt().isBefore(windowStart))
                    .toList();
            if (windowAttempts.size() >= maxAttempts) {
                Instant earliest = windowAttempts.stream()
                        .map(QuizAttempt::getSubmittedAt)
                        .filter(Objects::nonNull)
                        .min(Instant::compareTo)
                        .orElse(null);
                long waitHours = 0;
                if (earliest != null) {
                    Instant nextRetryAt = earliest.plus(Duration.ofHours(quiz.getCooldownHours()));
                    waitHours = Math.max(1, Duration.between(now(), nextRetryAt).toHours());
                }
                log.warn("[QUIZ_SUBMIT] User {} exceeded max attempts (window) for quiz {}", userId, quizId);
                String message = waitHours > 0
                        ? "Bạn đã hết lượt làm bài. Vui lòng thử lại sau " + waitHours + " giờ."
                        : "Bạn đã hết lượt làm bài.";
                throw new BadRequestException(message);
            }
        } else {
            if (attemptEntities.size() >= maxAttempts) {
                log.warn("[QUIZ_SUBMIT] User {} exceeded max attempts for quiz {}", userId, quizId);
                throw new BadRequestException("Bạn đã hết lượt làm bài.");
            }
        }

        // Grade quiz
        int correctCount = 0;
        int earnedScore = 0;
        int totalQuestions = quizQuestions.size();

        // Calculate total possible score
        int totalPossibleScore = quizQuestions.stream()
                .mapToInt(q -> q.getScore() != null ? q.getScore() : 1)
                .sum();

        for (SubmitQuizDTO.Answer answer : submitData.getAnswers()) {
            QuizQuestion question = questionMap.get(answer.getQuestionId());
            if (question == null) {
                continue;
            }

            boolean isCorrect = evaluateAnswer(question, answer);
            if (isCorrect) {
                correctCount++;
                earnedScore += question.getScore() != null ? question.getScore() : 1;
            }
        }

        int score = totalPossibleScore > 0 ? (earnedScore * 100) / totalPossibleScore : 0;
        score = applyRounding(score, quiz.getRoundingIncrement());
        int passScore = quiz.getPassScore() != null ? quiz.getPassScore() : 0;
        boolean passed = score >= passScore;

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

    @Override
    @Transactional(readOnly = true)
    public List<QuizAttemptDTO> getUserAttemptsBatch(List<Long> quizIds, Long userId) {
        if (quizIds == null || quizIds.isEmpty()) {
            return List.of();
        }
        log.debug("Getting attempts for quizzes {} by user {}", quizIds, userId);

        List<QuizAttempt> attempts = attemptRepository.findByQuizIdInAndUserIdOrderBySubmittedAtDesc(quizIds, userId);
        return attempts.stream()
                .map(attemptMapper::toDto)
                .toList();
    }
    
    @Override
    @Transactional(readOnly = true)
    public QuizAttemptStatusDTO getAttemptStatus(Long quizId, Long userId) {
        log.debug("Getting attempt status for quiz {} by user {}", quizId, userId);
        Quiz quiz = getQuizOrThrow(quizId);
        applyQuizDefaults(quiz);
        
        List<QuizAttemptDTO> allAttempts = getUserAttempts(quizId, userId);

        int maxAttempts = quiz.getMaxAttempts() != null ? quiz.getMaxAttempts() : 3;
        boolean useWindow = Boolean.TRUE.equals(quiz.getIsAssessment())
                && quiz.getCooldownHours() != null
                && quiz.getCooldownHours() > 0;
        List<QuizAttemptDTO> windowAttempts = allAttempts;
        if (useWindow) {
            Instant windowStart = now().minus(Duration.ofHours(quiz.getCooldownHours()));
            windowAttempts = allAttempts.stream()
                    .filter(a -> a.getSubmittedAt() != null && !a.getSubmittedAt().isBefore(windowStart))
                    .toList();
        }

        int attemptsUsed = windowAttempts.size();
        boolean canRetry = attemptsUsed < maxAttempts;

        long secondsUntilRetry = 0;
        Instant nextRetryAt = null;
        if (!canRetry && useWindow && !windowAttempts.isEmpty()) {
            Instant earliest = windowAttempts.stream()
                    .map(QuizAttemptDTO::getSubmittedAt)
                    .filter(Objects::nonNull)
                    .min(Instant::compareTo)
                    .orElse(null);
            if (earliest != null) {
                nextRetryAt = earliest.plus(Duration.ofHours(quiz.getCooldownHours()));
                secondsUntilRetry = Math.max(0, Duration.between(now(), nextRetryAt).toSeconds());
            }
        }

        // Check if passed
        boolean hasPassed = allAttempts.stream().anyMatch(a -> Boolean.TRUE.equals(a.getPassed()));
        Integer bestScore = calculateBestScore(allAttempts, quiz.getGradingMethod());
        
        return QuizAttemptStatusDTO.builder()
                .quizId(quizId)
                .userId(userId)
                .attemptsUsed(attemptsUsed)
                .maxAttempts(maxAttempts)
                .canRetry(canRetry)
                .hasPassed(hasPassed)
                .bestScore(bestScore != null ? bestScore : 0)
                .secondsUntilRetry(secondsUntilRetry)
                .nextRetryAt(nextRetryAt)
                .recentAttempts(windowAttempts)
                .build();
    }

    private void applyQuizDefaults(Quiz quiz) {
        if (quiz.getMaxAttempts() == null) {
            quiz.setMaxAttempts(3);
        }
        if (quiz.getRoundingIncrement() == null || quiz.getRoundingIncrement() <= 0) {
            quiz.setRoundingIncrement(1);
        }
        if (quiz.getGradingMethod() == null) {
            quiz.setGradingMethod(QuizGradingMethod.HIGHEST);
        }
        if (quiz.getIsAssessment() == null) {
            quiz.setIsAssessment(false);
        }
        if (Boolean.TRUE.equals(quiz.getIsAssessment())) {
            if (quiz.getCooldownHours() == null || quiz.getCooldownHours() <= 0) {
                quiz.setCooldownHours(24);
            }
        } else {
            quiz.setCooldownHours(null);
        }
    }

    private int applyRounding(int score, Integer roundingIncrement) {
        int increment = roundingIncrement != null && roundingIncrement > 0 ? roundingIncrement : 1;
        int rounded = Math.round(score / (float) increment) * increment;
        if (rounded < 0) return 0;
        if (rounded > 100) return 100;
        return rounded;
    }

    private boolean evaluateAnswer(QuizQuestion question, SubmitQuizDTO.Answer answer) {
        if (question.getQuestionType() == null) {
            return false;
        }

        return switch (question.getQuestionType()) {
            case SHORT_ANSWER -> evaluateShortAnswer(question, answer.getTextAnswer());
            case TRUE_FALSE, MULTIPLE_CHOICE -> evaluateOptionAnswer(question, answer);
        };
    }

    private boolean evaluateOptionAnswer(QuizQuestion question, SubmitQuizDTO.Answer answer) {
        Set<Long> selectedOptionIds = new HashSet<>();
        if (answer.getSelectedOptionIds() != null) {
            selectedOptionIds.addAll(answer.getSelectedOptionIds());
        }
        if (answer.getSelectedOptionId() != null) {
            selectedOptionIds.add(answer.getSelectedOptionId());
        }
        if (selectedOptionIds.isEmpty()) {
            return false;
        }

        List<QuizOption> options = question.getOptions();
        if (options == null || options.isEmpty()) {
            return false;
        }

        Set<Long> correctIds = options.stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                .map(QuizOption::getId)
                .collect(Collectors.toSet());

        if (correctIds.isEmpty()) {
            return false;
        }

        return selectedOptionIds.equals(correctIds);
    }

    private boolean evaluateShortAnswer(QuizQuestion question, String textAnswer) {
        if (textAnswer == null) {
            return false;
        }
        String normalizedAnswer = normalizeText(textAnswer);
        if (normalizedAnswer.isEmpty()) {
            return false;
        }

        List<QuizOption> options = question.getOptions();
        if (options == null || options.isEmpty()) {
            return false;
        }

        List<QuizOption> correctOptions = options.stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                .toList();
        List<QuizOption> accepted = correctOptions.isEmpty() ? options : correctOptions;

        return accepted.stream()
                .map(QuizOption::getOptionText)
                .filter(Objects::nonNull)
                .map(this::normalizeText)
                .anyMatch(normalizedAnswer::equals);
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private Integer calculateBestScore(List<QuizAttemptDTO> attempts, QuizGradingMethod method) {
        if (attempts == null || attempts.isEmpty()) return 0;
        QuizGradingMethod gradingMethod = method != null ? method : QuizGradingMethod.HIGHEST;
        return switch (gradingMethod) {
            case AVERAGE -> {
                double avg = attempts.stream()
                        .map(QuizAttemptDTO::getScore)
                        .filter(Objects::nonNull)
                        .mapToInt(Integer::intValue)
                        .average()
                        .orElse(0);
                yield (int) Math.round(avg);
            }
            case FIRST -> {
                QuizAttemptDTO firstAttempt = attempts.stream()
                        .filter(a -> a.getSubmittedAt() != null)
                        .min(Comparator.comparing(QuizAttemptDTO::getSubmittedAt))
                        .orElse(null);
                yield firstAttempt != null && firstAttempt.getScore() != null ? firstAttempt.getScore() : 0;
            }
            case LAST -> attempts.get(0).getScore() != null ? attempts.get(0).getScore() : 0;
            case HIGHEST -> attempts.stream()
                    .map(QuizAttemptDTO::getScore)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(0);
        };
    }
}
