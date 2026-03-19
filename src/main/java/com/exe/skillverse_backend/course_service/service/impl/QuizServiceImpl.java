package com.exe.skillverse_backend.course_service.service.impl;

import com.exe.skillverse_backend.course_service.dto.quizdto.*;
import com.exe.skillverse_backend.course_service.dto.quizdto.QuizSummaryDTO;
import com.exe.skillverse_backend.course_service.entity.Module;
import com.exe.skillverse_backend.course_service.entity.Quiz;
import com.exe.skillverse_backend.course_service.entity.QuizQuestion;
import com.exe.skillverse_backend.course_service.entity.QuizOption;
import com.exe.skillverse_backend.course_service.entity.QuizAttempt;
import com.exe.skillverse_backend.course_service.entity.QuizAttemptAnswerSnapshot;
import com.exe.skillverse_backend.course_service.entity.QuizAttemptSession;
import com.exe.skillverse_backend.course_service.entity.enums.QuizGradingMethod;
import com.exe.skillverse_backend.course_service.entity.enums.QuizAttemptSessionStatus;
import com.exe.skillverse_backend.course_service.mapper.QuizMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizQuestionMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizOptionMapper;
import com.exe.skillverse_backend.course_service.mapper.QuizAttemptMapper;
import com.exe.skillverse_backend.course_service.policy.CourseQuizAttemptSessionProperties;
import com.exe.skillverse_backend.course_service.repository.ModuleRepository;
import com.exe.skillverse_backend.course_service.repository.QuizRepository;
import com.exe.skillverse_backend.course_service.repository.QuizQuestionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizOptionRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptAnswerSnapshotRepository;
import com.exe.skillverse_backend.course_service.repository.QuizAttemptSessionRepository;
import com.exe.skillverse_backend.course_service.service.CourseLearningProgressService;
import com.exe.skillverse_backend.course_service.service.QuizService;
import com.exe.skillverse_backend.shared.config.JacksonConfig;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ConflictException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private static final int DEFAULT_ASSESSMENT_COOLDOWN_HOURS = 8;
    private static final int LEGACY_ASSESSMENT_COOLDOWN_HOURS = 24;

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
    private final CourseLearningProgressService courseLearningProgressService;
    private final QuizAttemptAnswerSnapshotRepository attemptAnswerSnapshotRepository;
    private final QuizAttemptSessionRepository attemptSessionRepository;
    private final ObjectMapper objectMapper;
    private final CourseQuizAttemptSessionProperties attemptSessionProperties;

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

        validateCreateOptionRequest(dto, question);

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

        validateUpdateOptionRequest(dto, option.getQuestion(), optionId);

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
        // Allow if actor is the author of the course
        if (actorId.equals(authorId)) {
            return;
        }

        // Only ADMIN can bypass the author check.
        // This prevents Mentor A from editing Mentor B's quizzes.
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_CONTENT_ADMIN"))) {
            log.debug("Actor {} allowed via admin role", actorId);
            return;
        }

        throw new AccessDeniedException("FORBIDDEN");
    }

    private void validateCreateQuizRequest(QuizCreateDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BadRequestException("Quiz title is required");
        }
        validateQuizSettings(
                dto.getPassScore(),
                dto.getMaxAttempts(),
                dto.getTimeLimitMinutes(),
                dto.getRoundingIncrement(),
                dto.getCooldownHours()
        );
    }

    private void validateUpdateQuizRequest(QuizUpdateDTO dto) {
        if (dto.getTitle() != null && dto.getTitle().isBlank()) {
            throw new BadRequestException("Quiz title cannot be blank");
        }
        validateQuizSettings(
                dto.getPassScore(),
                dto.getMaxAttempts(),
                dto.getTimeLimitMinutes(),
                dto.getRoundingIncrement(),
                dto.getCooldownHours()
        );
    }

    private void validateQuizSettings(
            Integer passScore,
            Integer maxAttempts,
            Integer timeLimitMinutes,
            Integer roundingIncrement,
            Integer cooldownHours
    ) {
        if (passScore != null && (passScore < 0 || passScore > 100)) {
            throw new BadRequestException("Pass score must be between 0 and 100");
        }
        if (maxAttempts != null && maxAttempts <= 0) {
            throw new BadRequestException("Max attempts must be greater than 0");
        }
        if (timeLimitMinutes != null && timeLimitMinutes <= 0) {
            throw new BadRequestException("Time limit must be greater than 0");
        }
        if (roundingIncrement != null && roundingIncrement <= 0) {
            throw new BadRequestException("Rounding increment must be greater than 0");
        }
        if (cooldownHours != null && cooldownHours <= 0) {
            throw new BadRequestException("Cooldown hours must be greater than 0");
        }
    }

    private void validateCreateQuestionRequest(QuizQuestionCreateDTO dto) {
        if (dto.getQuestionText() == null || dto.getQuestionText().isBlank()) {
            throw new BadRequestException("Question text is required");
        }
        if (dto.getQuestionType() == null) {
            throw new BadRequestException("Question type is required");
        }
        validateQuestionScore(dto.getScore());
    }

    private void validateUpdateQuestionRequest(QuizQuestionUpdateDTO dto) {
        if (dto.getQuestionText() != null && dto.getQuestionText().isBlank()) {
            throw new BadRequestException("Question text cannot be blank");
        }
        validateQuestionScore(dto.getScore());
    }

    private void validateCreateOptionRequest(QuizOptionCreateDTO dto, QuizQuestion question) {
        if (dto.getOptionText() == null || dto.getOptionText().isBlank()) {
            throw new BadRequestException("Option text is required");
        }
        validateUniqueOptionText(question, dto.getOptionText(), null);
    }

    private void validateUpdateOptionRequest(QuizOptionUpdateDTO dto, QuizQuestion question, Long optionId) {
        if (dto.getOptionText() != null && dto.getOptionText().isBlank()) {
            throw new BadRequestException("Option text cannot be blank");
        }
        if (dto.getOptionText() != null) {
            validateUniqueOptionText(question, dto.getOptionText(), optionId);
        }
    }

    private void validateQuestionScore(Integer score) {
        if (score != null && score <= 0) {
            throw new BadRequestException("Question score must be greater than 0");
        }
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private QuizAttemptSession openOrRefreshAttemptSession(Quiz quiz, Long userId) {
        if (!isAttemptSessionEnabled() || quiz == null || userId == null) {
            return null;
        }

        Instant current = now();
        expireStaleAttemptSessions(quiz.getId(), userId, current);

        Optional<QuizAttemptSession> activeSession = attemptSessionRepository.findLatestActiveSession(
                quiz.getId(),
                userId,
                QuizAttemptSessionStatus.IN_PROGRESS,
                current
        );

        Instant expiresAt = calculateAttemptSessionExpiry(current);
        if (activeSession.isPresent()) {
            attemptSessionRepository.touchSession(activeSession.get().getId(), current, expiresAt);
            QuizAttemptSession existing = activeSession.get();
            existing.setLastSeenAt(current);
            existing.setExpiresAt(expiresAt);
            return existing;
        }

        QuizAttemptSession newSession = QuizAttemptSession.builder()
                .quiz(quiz)
                .userId(userId)
                .sessionToken(UUID.randomUUID().toString().replace("-", ""))
                .status(QuizAttemptSessionStatus.IN_PROGRESS)
                .startedAt(current)
                .lastSeenAt(current)
                .expiresAt(expiresAt)
                .build();
        return attemptSessionRepository.save(newSession);
    }

    private void closeAttemptSessionAfterSubmit(Long quizId, Long userId, String sessionToken) {
        if (!isAttemptSessionEnabled() || quizId == null || userId == null) {
            return;
        }

        Instant submittedAt = now();
        int updatedByToken = 0;
        if (sessionToken != null && !sessionToken.isBlank()) {
            updatedByToken = attemptSessionRepository.markSessionSubmitted(
                    quizId,
                    userId,
                    sessionToken.trim(),
                    QuizAttemptSessionStatus.IN_PROGRESS,
                    QuizAttemptSessionStatus.SUBMITTED,
                    submittedAt
            );
        }

        if (updatedByToken == 0) {
            attemptSessionRepository.markActiveSessionsSubmitted(
                    quizId,
                    userId,
                    QuizAttemptSessionStatus.IN_PROGRESS,
                    QuizAttemptSessionStatus.SUBMITTED,
                    submittedAt
            );
        }
    }

    private void expireStaleAttemptSessions(Long quizId, Long userId, Instant current) {
        if (!isAttemptSessionEnabled() || quizId == null || userId == null || current == null) {
            return;
        }
        attemptSessionRepository.expireStaleSessions(
                quizId,
                userId,
                QuizAttemptSessionStatus.IN_PROGRESS,
                QuizAttemptSessionStatus.EXPIRED,
                current
        );
    }

    private Instant calculateAttemptSessionExpiry(Instant current) {
        int ttlMinutes = attemptSessionProperties != null ? attemptSessionProperties.getTtlMinutes() : 30;
        if (ttlMinutes <= 0) {
            ttlMinutes = 30;
        }
        return current.plus(Duration.ofMinutes(ttlMinutes));
    }

    private boolean isAttemptSessionEnabled() {
        return attemptSessionProperties == null || attemptSessionProperties.isEnabled();
    }

    private QuizAttemptSessionDTO toAttemptSessionDto(QuizAttemptSession session) {
        if (session == null) {
            return null;
        }
        return QuizAttemptSessionDTO.builder()
                .quizId(session.getQuiz() != null ? session.getQuiz().getId() : null)
                .userId(session.getUserId())
                .sessionToken(session.getSessionToken())
                .status(session.getStatus() != null ? session.getStatus().name() : null)
                .startedAt(session.getStartedAt())
                .lastSeenAt(session.getLastSeenAt())
                .expiresAt(session.getExpiresAt())
                .build();
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
    public QuizDetailDTO getQuizForAttempt(Long quizId, Long userId) {
        log.debug("Getting learner-safe quiz details for {} by user {}", quizId, userId);

        Quiz quizEntity = getQuizOrThrow(quizId);
        QuizDetailDTO quiz = quizMapper.toDetailDto(quizEntity);
        sanitizeQuizForLearner(quiz);
        return quiz;
    }

    @Override
    @Transactional
    public QuizAttemptSessionDTO startAttemptSession(Long quizId, Long userId) {
        Quiz quiz = getQuizOrThrow(quizId);
        if (!isAttemptSessionEnabled()) {
            return QuizAttemptSessionDTO.builder()
                    .quizId(quizId)
                    .userId(userId)
                    .status("DISABLED")
                    .build();
        }

        QuizAttemptSession session = openOrRefreshAttemptSession(quiz, userId);
        return toAttemptSessionDto(session);
    }

    @Override
    @Transactional
    public QuizAttemptSessionDTO heartbeatAttemptSession(Long quizId, Long userId, String sessionToken) {
        if (!isAttemptSessionEnabled()) {
            return QuizAttemptSessionDTO.builder()
                    .quizId(quizId)
                    .userId(userId)
                    .status("DISABLED")
                    .build();
        }
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new BadRequestException("QUIZ_ATTEMPT_SESSION_TOKEN_REQUIRED");
        }

        Instant current = now();
        expireStaleAttemptSessions(quizId, userId, current);
        QuizAttemptSession session = attemptSessionRepository.findActiveSessionByToken(
                        quizId,
                        userId,
                        sessionToken.trim(),
                        QuizAttemptSessionStatus.IN_PROGRESS,
                        current
                )
                .orElseThrow(() -> new ConflictException("QUIZ_ATTEMPT_SESSION_NOT_ACTIVE"));

        Instant expiresAt = calculateAttemptSessionExpiry(current);
        attemptSessionRepository.touchSession(session.getId(), current, expiresAt);
        session.setLastSeenAt(current);
        session.setExpiresAt(expiresAt);
        return toAttemptSessionDto(session);
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
        expireStaleAttemptSessions(quizId, userId, now());

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

        Map<Long, SubmitQuizDTO.Answer> answerMap = Optional.ofNullable(submitData.getAnswers())
                .orElse(List.of())
                .stream()
                .filter(answer -> answer.getQuestionId() != null)
                .collect(Collectors.toMap(
                        SubmitQuizDTO.Answer::getQuestionId,
                        answer -> answer,
                        (existing, replacement) -> replacement));

        validateSubmittedAnswers(questionMap, submitData.getAnswers());

        // Grade quiz
        int correctCount = 0;
        int earnedScore = 0;
        int totalQuestions = quizQuestions.size();

        // Calculate total possible score
        int totalPossibleScore = quizQuestions.stream()
                .mapToInt(q -> q.getScore() != null ? q.getScore() : 1)
                .sum();

        for (QuizQuestion question : quizQuestions) {
            SubmitQuizDTO.Answer answer = answerMap.get(question.getId());
            boolean isCorrect = answer != null && hasAnswerContent(question, answer) && evaluateAnswer(question, answer);
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

        List<QuizAttemptAnswerSnapshot> answerSnapshots = quizQuestions.stream()
                .map(question -> buildAttemptAnswerSnapshot(saved, question, answerMap.get(question.getId())))
                .toList();
        attemptAnswerSnapshotRepository.saveAll(answerSnapshots);

        courseLearningProgressService.recalculateCourseProgress(
                quiz.getModule().getCourse().getId(),
                userId
        );
        closeAttemptSessionAfterSubmit(quizId, userId, submitData != null ? submitData.getSessionToken() : null);

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
    public QuizAttemptReviewDTO getMyLatestReview(Long quizId, Long userId) {
        log.debug("Getting latest review for quiz {} by user {}", quizId, userId);

        QuizAttempt latestAttempt = attemptRepository.findByQuizIdAndUserIdOrderBySubmittedAtDesc(quizId, userId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("QUIZ_ATTEMPT_NOT_FOUND"));

        List<QuizAttemptAnswerReviewDTO> answers = attemptAnswerSnapshotRepository
                .findByAttemptIdOrderByQuestionOrderIndexAscIdAsc(latestAttempt.getId())
                .stream()
                .map(snapshot -> QuizAttemptAnswerReviewDTO.builder()
                        .questionId(snapshot.getQuestionId())
                        .questionOrderIndex(snapshot.getQuestionOrderIndex())
                        .questionText(snapshot.getQuestionText())
                        .questionType(snapshot.getQuestionType())
                        .submittedAnswer(readSubmittedAnswerSnapshot(snapshot.getSubmittedAnswerJson()))
                        .optionsSnapshot(readOptionsSnapshot(snapshot.getOptionsSnapshotJson()))
                        .submittedAnswerText(snapshot.getSubmittedAnswerText())
                        .correctAnswerText(snapshot.getCorrectAnswerText())
                        .answered(snapshot.getAnswered())
                        .correct(snapshot.getCorrect())
                        .scoreEarned(snapshot.getScoreEarned())
                        .maxScore(snapshot.getMaxScore())
                        .build())
                .toList();

        return QuizAttemptReviewDTO.builder()
                .attempt(attemptMapper.toDto(latestAttempt))
                .answers(answers)
                .build();
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
            if (quiz.getCooldownHours() == null
                    || quiz.getCooldownHours() <= 0
                    || Objects.equals(quiz.getCooldownHours(), LEGACY_ASSESSMENT_COOLDOWN_HOURS)) {
                quiz.setCooldownHours(DEFAULT_ASSESSMENT_COOLDOWN_HOURS);
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

    private QuizAttemptAnswerSnapshot buildAttemptAnswerSnapshot(
            QuizAttempt attempt,
            QuizQuestion question,
            SubmitQuizDTO.Answer answer
    ) {
        boolean answered = answer != null && hasAnswerContent(question, answer);
        boolean correct = answered && evaluateAnswer(question, answer);
        int maxScore = question.getScore() != null ? question.getScore() : 1;

        return QuizAttemptAnswerSnapshot.builder()
                .attempt(attempt)
                .questionId(question.getId())
                .questionOrderIndex(question.getOrderIndex())
                .questionText(question.getQuestionText())
                .questionType(question.getQuestionType())
                .submittedAnswerJson(writeSubmittedAnswerSnapshot(question, answer))
                .optionsSnapshotJson(writeOptionsSnapshot(question, answer))
                .submittedAnswerText(buildSubmittedAnswerText(question, answer))
                .correctAnswerText(buildCorrectAnswerText(question))
                .answered(answered)
                .correct(correct)
                .scoreEarned(correct ? maxScore : 0)
                .maxScore(maxScore)
                .build();
    }

    private boolean hasAnswerContent(QuizQuestion question, SubmitQuizDTO.Answer answer) {
        if (question.getQuestionType() == null || answer == null) {
            return false;
        }

        return switch (question.getQuestionType()) {
            case SHORT_ANSWER -> answer.getTextAnswer() != null && !answer.getTextAnswer().trim().isEmpty();
            case TRUE_FALSE, MULTIPLE_CHOICE -> !collectSelectedOptionIds(answer).isEmpty();
        };
    }

    private Set<Long> collectSelectedOptionIds(SubmitQuizDTO.Answer answer) {
        Set<Long> selectedOptionIds = new HashSet<>();
        if (answer == null) {
            return selectedOptionIds;
        }
        if (answer.getSelectedOptionIds() != null) {
            selectedOptionIds.addAll(answer.getSelectedOptionIds());
        }
        if (answer.getSelectedOptionId() != null) {
            selectedOptionIds.add(answer.getSelectedOptionId());
        }
        return selectedOptionIds;
    }

    private void validateSubmittedAnswers(
            Map<Long, QuizQuestion> questionMap,
            List<SubmitQuizDTO.Answer> answers
    ) {
        if (answers == null || answers.isEmpty()) {
            return;
        }

        Set<Long> seenQuestionIds = new HashSet<>();
        for (SubmitQuizDTO.Answer answer : answers) {
            if (answer == null || answer.getQuestionId() == null) {
                continue;
            }

            if (!seenQuestionIds.add(answer.getQuestionId())) {
                throw new BadRequestException("Duplicate answers detected for the same question");
            }

            QuizQuestion question = questionMap.get(answer.getQuestionId());
            if (question == null) {
                throw new BadRequestException("Submission contains a question that does not belong to this quiz");
            }

            validateSubmittedAnswer(question, answer);
        }
    }

    private void validateSubmittedAnswer(QuizQuestion question, SubmitQuizDTO.Answer answer) {
        Set<Long> selectedOptionIds = collectSelectedOptionIds(answer);
        boolean hasTextAnswer = answer.getTextAnswer() != null && !answer.getTextAnswer().trim().isEmpty();

        switch (question.getQuestionType()) {
            case SHORT_ANSWER -> {
                if (!selectedOptionIds.isEmpty()) {
                    throw new BadRequestException("Short answer questions do not accept option selections");
                }
            }
            case TRUE_FALSE -> {
                if (hasTextAnswer) {
                    throw new BadRequestException("True/False questions do not accept text answers");
                }
                if (selectedOptionIds.size() > 1) {
                    throw new BadRequestException("True/False questions accept exactly one selected option");
                }
                validateSelectedOptionsBelongToQuestion(question, selectedOptionIds);
            }
            case MULTIPLE_CHOICE -> {
                if (hasTextAnswer) {
                    throw new BadRequestException("Multiple choice questions do not accept text answers");
                }
                validateSelectedOptionsBelongToQuestion(question, selectedOptionIds);
            }
        }
    }

    private void validateSelectedOptionsBelongToQuestion(QuizQuestion question, Set<Long> selectedOptionIds) {
        if (selectedOptionIds.isEmpty()) {
            return;
        }

        Set<Long> allowedOptionIds = Optional.ofNullable(question.getOptions())
                .orElse(List.of())
                .stream()
                .map(QuizOption::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (!allowedOptionIds.containsAll(selectedOptionIds)) {
            throw new BadRequestException("Submission contains invalid option ids for the question");
        }
    }

    private String writeSubmittedAnswerSnapshot(QuizQuestion question, SubmitQuizDTO.Answer answer) {
        try {
            QuizAttemptSubmittedAnswerReviewDTO payload = buildSubmittedAnswerSnapshot(question, answer);
            if (payload == null) {
                return null;
            }
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize quiz submitted answer snapshot", e);
        }
    }

    private String writeOptionsSnapshot(QuizQuestion question, SubmitQuizDTO.Answer answer) {
        try {
            return objectMapper.writeValueAsString(buildOptionsSnapshot(question, answer));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize quiz options snapshot", e);
        }
    }

    private QuizAttemptSubmittedAnswerReviewDTO buildSubmittedAnswerSnapshot(QuizQuestion question, SubmitQuizDTO.Answer answer) {
        if (question.getQuestionType() == null) {
            return null;
        }

        return switch (question.getQuestionType()) {
            case SHORT_ANSWER -> QuizAttemptSubmittedAnswerReviewDTO.builder()
                    .textAnswer(answer != null && answer.getTextAnswer() != null ? answer.getTextAnswer().trim() : null)
                    .build();
            case TRUE_FALSE, MULTIPLE_CHOICE -> {
                Set<Long> selectedOptionIds = collectSelectedOptionIds(answer);
                List<QuizOption> options = Optional.ofNullable(question.getOptions()).orElse(List.of());
                List<String> selectedTexts = options.stream()
                        .filter(option -> selectedOptionIds.contains(option.getId()))
                        .map(QuizOption::getOptionText)
                        .filter(Objects::nonNull)
                        .toList();
                yield QuizAttemptSubmittedAnswerReviewDTO.builder()
                        .selectedOptionIds(selectedOptionIds.stream().sorted().toList())
                        .selectedOptionTexts(selectedTexts)
                        .build();
            }
        };
    }

    private List<QuizAttemptAnswerOptionReviewDTO> buildOptionsSnapshot(QuizQuestion question, SubmitQuizDTO.Answer answer) {
        Set<Long> selectedOptionIds = collectSelectedOptionIds(answer);
        return Optional.ofNullable(question.getOptions())
                .orElse(List.of())
                .stream()
                .sorted(Comparator.comparing(
                        QuizOption::getOrderIndex,
                        Comparator.nullsLast(Integer::compareTo)
                ).thenComparing(QuizOption::getId, Comparator.nullsLast(Long::compareTo)))
                .map(option -> QuizAttemptAnswerOptionReviewDTO.builder()
                        .optionId(option.getId())
                        .orderIndex(option.getOrderIndex())
                        .optionText(option.getOptionText())
                        .correct(Boolean.TRUE.equals(option.getIsCorrect()))
                        .selected(selectedOptionIds.contains(option.getId()))
                        .feedback(option.getFeedback())
                        .build())
                .toList();
    }

    private QuizAttemptSubmittedAnswerReviewDTO readSubmittedAnswerSnapshot(String submittedAnswerJson) {
        if (submittedAnswerJson == null || submittedAnswerJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(submittedAnswerJson, QuizAttemptSubmittedAnswerReviewDTO.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize submitted answer snapshot: {}", e.getMessage());
            return null;
        }
    }

    private List<QuizAttemptAnswerOptionReviewDTO> readOptionsSnapshot(String optionsSnapshotJson) {
        if (optionsSnapshotJson == null || optionsSnapshotJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    optionsSnapshotJson,
                    new TypeReference<List<QuizAttemptAnswerOptionReviewDTO>>() {}
            );
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize options snapshot: {}", e.getMessage());
            return List.of();
        }
    }

    private String buildSubmittedAnswerText(QuizQuestion question, SubmitQuizDTO.Answer answer) {
        if (question.getQuestionType() == null || answer == null) {
            return "Khong tra loi";
        }

        return switch (question.getQuestionType()) {
            case SHORT_ANSWER -> {
                String textAnswer = answer.getTextAnswer() != null ? answer.getTextAnswer().trim() : "";
                yield textAnswer.isEmpty() ? "Khong tra loi" : textAnswer;
            }
            case TRUE_FALSE, MULTIPLE_CHOICE -> {
                Set<Long> selectedOptionIds = collectSelectedOptionIds(answer);
                if (selectedOptionIds.isEmpty()) {
                    yield "Khong tra loi";
                }
                List<String> selectedTexts = Optional.ofNullable(question.getOptions())
                        .orElse(List.of())
                        .stream()
                        .filter(option -> selectedOptionIds.contains(option.getId()))
                        .map(QuizOption::getOptionText)
                        .filter(Objects::nonNull)
                        .toList();
                yield selectedTexts.isEmpty() ? "Khong tra loi" : String.join("\n", selectedTexts);
            }
        };
    }

    private String buildCorrectAnswerText(QuizQuestion question) {
        List<QuizOption> options = Optional.ofNullable(question.getOptions()).orElse(List.of());
        if (options.isEmpty()) {
            return "Dang cap nhat";
        }

        List<String> correctTexts = options.stream()
                .filter(option -> Boolean.TRUE.equals(option.getIsCorrect()))
                .map(QuizOption::getOptionText)
                .filter(Objects::nonNull)
                .toList();

        List<String> acceptedTexts = correctTexts.isEmpty()
                ? options.stream().map(QuizOption::getOptionText).filter(Objects::nonNull).toList()
                : correctTexts;

        return acceptedTexts.isEmpty() ? "Dang cap nhat" : String.join("\n", acceptedTexts);
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
        Set<Long> selectedOptionIds = collectSelectedOptionIds(answer);
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

    private void validateUniqueOptionText(QuizQuestion question, String optionText, Long currentOptionId) {
        String normalizedCandidate = normalizeText(optionText);
        if (normalizedCandidate.isEmpty()) {
            throw new BadRequestException("Option text is required");
        }

        boolean duplicated = Optional.ofNullable(question.getOptions())
                .orElse(List.of())
                .stream()
                .filter(existing -> currentOptionId == null || !Objects.equals(existing.getId(), currentOptionId))
                .map(QuizOption::getOptionText)
                .filter(Objects::nonNull)
                .map(this::normalizeText)
                .anyMatch(normalizedCandidate::equals);

        if (duplicated) {
            throw new BadRequestException("Duplicate option text is not allowed for the same question");
        }
    }

    private void sanitizeQuizForLearner(QuizDetailDTO quiz) {
        if (quiz == null || quiz.getQuestions() == null) {
            return;
        }

        for (QuizQuestionDetailDTO question : quiz.getQuestions()) {
            if (question.getOptions() == null) {
                question.setCorrectOptionCount(0);
                continue;
            }

            int correctCount = 0;

            for (QuizOptionDetailDTO option : question.getOptions()) {
                if (option.isCorrect()) {
                    correctCount++;
                }
                option.setCorrect(false);
                option.setFeedback(null);
            }
            question.setCorrectOptionCount(correctCount);
        }
    }
}
