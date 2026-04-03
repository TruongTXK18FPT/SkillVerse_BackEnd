package com.exe.skillverse_backend.journey_service.service.impl;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.service.AiRoadmapService;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.QuestionInfo;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.TestSubmissionInfo;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.UserAssessmentInfo;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.journey_service.dto.request.StartJourneyRequest;
import com.exe.skillverse_backend.journey_service.dto.request.SubmitTestRequest;
import com.exe.skillverse_backend.journey_service.dto.response.AssessmentTestResponse;
import com.exe.skillverse_backend.journey_service.dto.response.GenerateTestResponse;
import com.exe.skillverse_backend.journey_service.dto.response.JourneySummaryResponse;
import com.exe.skillverse_backend.journey_service.dto.response.TestResultResponse;
import com.exe.skillverse_backend.journey_service.entity.AssessmentTest;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.entity.JourneyProgress;
import com.exe.skillverse_backend.journey_service.entity.TestResult;
import com.exe.skillverse_backend.journey_service.repository.AssessmentTestRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyProgressRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.repository.TestResultRepository;
import com.exe.skillverse_backend.journey_service.service.JourneyService;
import com.exe.skillverse_backend.study_service.dto.request.CreateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.request.GenerateScheduleRequest;
import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskColumnResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskResponse;
import com.exe.skillverse_backend.study_service.entity.TaskPriority;
import com.exe.skillverse_backend.study_service.service.AiStudySupportService;
import com.exe.skillverse_backend.study_service.service.TaskBoardService;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBank;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankResponse;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JourneyServiceImpl implements JourneyService {

    private static final int MAX_ASSESSMENT_ATTEMPTS = 2;
    private static final int MIN_QUESTION_BANK_POOL_SIZE = 25;
    private static final String STUDY_PLAN_LINK_MARKER_PREFIX = "[ROADMAP_NODE_LINK]";
    private static final int MAX_STUDY_TASKS_PER_NODE = 12;
    private static final String DEFAULT_STUDY_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final Pattern OPTION_PREFIX_PATTERN = Pattern.compile("^\\s*([A-D])(?:\\s*[\\.:\\)\\-]|\\s+|$)", Pattern.CASE_INSENSITIVE);
    private static final Set<Journey.JourneyStatus> AUTO_PAUSE_ON_RESUME_STATUSES = EnumSet.of(
            Journey.JourneyStatus.ASSESSMENT_PENDING,
            Journey.JourneyStatus.TEST_IN_PROGRESS,
            Journey.JourneyStatus.EVALUATION_PENDING,
            Journey.JourneyStatus.ROADMAP_GENERATED,
            Journey.JourneyStatus.STUDY_PLAN_IN_PROGRESS,
            Journey.JourneyStatus.ACTIVE
    );

    private final JourneyRepository journeyRepository;
    private final AssessmentTestRepository assessmentTestRepository;
    private final TestResultRepository testResultRepository;
    private final JourneyProgressRepository journeyProgressRepository;
    private final EntityManager entityManager;

    @Qualifier("generateTestChatModel")
    private final ChatModel generateTestChatModel;
    private final AiRoadmapService aiRoadmapService;
    private final AssessmentPromptService assessmentPromptService;
    private final TaskBoardService taskBoardService;
    private final AiStudySupportService aiStudySupportService;
    private final QuestionBankService questionBankService;
    private final ObjectMapper objectMapper;

    private static final class QuestionEvaluation {
        private final Long questionId;
        private final String skillArea;
        private final String difficulty;
        private final String userAnswer;
        private final String correctAnswer;
        private final boolean correct;

        private QuestionEvaluation(Long questionId, String skillArea, String difficulty,
                                   String userAnswer, String correctAnswer, boolean correct) {
            this.questionId = questionId;
            this.skillArea = skillArea;
            this.difficulty = difficulty;
            this.userAnswer = userAnswer;
            this.correctAnswer = correctAnswer;
            this.correct = correct;
        }
    }

    private static final class EvaluationSnapshot {
        private final int totalQuestions;
        private final int answeredQuestions;
        private final int correctAnswers;
        private final int incorrectAnswers;
        private final String scoreBand;
        private final String recommendationMode;
        private final int assessmentConfidence;
        private final boolean reassessmentRecommended;

        private EvaluationSnapshot(int totalQuestions, int answeredQuestions, int correctAnswers, int incorrectAnswers,
                                   String scoreBand, String recommendationMode, int assessmentConfidence,
                                   boolean reassessmentRecommended) {
            this.totalQuestions = totalQuestions;
            this.answeredQuestions = answeredQuestions;
            this.correctAnswers = correctAnswers;
            this.incorrectAnswers = incorrectAnswers;
            this.scoreBand = scoreBand;
            this.recommendationMode = recommendationMode;
            this.assessmentConfidence = assessmentConfidence;
            this.reassessmentRecommended = reassessmentRecommended;
        }
    }

    // Lazy ChatClient instance
    private ChatClient getChatClient() {
        return ChatClient.create(generateTestChatModel);
    }

    @Override
    @Transactional
    public JourneySummaryResponse startJourney(User user, StartJourneyRequest request) {
        log.info("Starting new journey for user: {} with domain: {}", user.getEmail(), request.getDomain());

        // Create journey entity
        Journey journey = Journey.builder()
                .user(user)
                .type(request.getType())
                .domain(request.getDomain())
                .title(buildJourneyTitle(request))
                .subCategory(request.getSubCategory())
                .industry(request.getIndustry())
                .jobRole(request.getJobRole())
                .goal(request.getGoal())
                .status(Journey.JourneyStatus.ASSESSMENT_PENDING)
                .assessmentData(convertRequestToJson(request))
                .progressPercentage(0)
                .startedAt(Instant.now())
                .lastActivityAt(Instant.now())
                .build();

        journey = journeyRepository.save(journey);

        log.info("Journey created with ID: {}", journey.getId());
        return mapToJourneySummary(journey);
    }

    @Override
    public JourneySummaryResponse getJourneyById(User user, Long journeyId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));
        return mapToJourneySummary(journey);
    }

    @Override
    public Page<JourneySummaryResponse> getUserJourneys(User user, Pageable pageable) {
        return journeyRepository.findByUser(user, pageable)
                .map(this::mapToJourneySummary);
    }

    @Override
    @Transactional
    public void deleteJourney(User user, Long journeyId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        entityManager.createNativeQuery(
                        "DELETE FROM test_results WHERE journey_id = ?1 " +
                                "OR assessment_test_id IN (SELECT id FROM assessment_tests WHERE journey_id = ?1)")
                .setParameter(1, journey.getId())
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM assessment_tests WHERE journey_id = ?1")
                .setParameter(1, journey.getId())
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM journey_progress WHERE journey_id = ?1")
                .setParameter(1, journey.getId())
                .executeUpdate();
        entityManager.createNativeQuery("DELETE FROM journeys WHERE id = ?1 AND user_id = ?2")
                .setParameter(1, journey.getId())
                .setParameter(2, user.getId())
                .executeUpdate();

        log.info("Deleted journey {} for user {}", journeyId, user.getId());
    }

    @Override
    public List<JourneySummaryResponse> getActiveJourneys(User user) {
        return journeyRepository.findActiveJourneysByUser(user).stream()
                .map(this::mapToJourneySummary)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public JourneySummaryResponse updateJourneyStatus(User user, Long journeyId, Journey.JourneyStatus newStatus) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        journey.setStatus(newStatus);
        journey.setLastActivityAt(Instant.now());
        journey = journeyRepository.save(journey);

        return mapToJourneySummary(journey);
    }

    @Override
    @Transactional
    public JourneySummaryResponse pauseJourney(User user, Long journeyId) {
        return updateJourneyStatus(user, journeyId, Journey.JourneyStatus.PAUSED);
    }

    @Override
    @Transactional
    public JourneySummaryResponse resumeJourney(User user, Long journeyId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        Journey.JourneyStatus previousStatus = journey.getStatus();
        if (previousStatus != Journey.JourneyStatus.PAUSED) {
            throw new RuntimeException("Can only resume a paused journey");
        }

        pauseOtherJourneysOnResume(user, journey.getId());

        Journey.JourneyStatus resumedStatus = determineResumeStatus(journey);
        journey.setStatus(resumedStatus);
        journey.setLastActivityAt(Instant.now());
        journey = journeyRepository.save(journey);

        return mapToJourneySummary(journey);
    }

    private Journey.JourneyStatus determineResumeStatus(Journey journey) {
        if (journey.getRoadmapSessionId() != null) {
            Integer progress = journey.getProgressPercentage() == null ? 0 : journey.getProgressPercentage();
            if (progress >= 40) {
                return Journey.JourneyStatus.ACTIVE;
            }
            return Journey.JourneyStatus.ROADMAP_GENERATED;
        }

        TestResult latestResult = testResultRepository.findTopByJourneyOrderByCreatedAtDesc(journey).orElse(null);
        if (latestResult != null) {
            return Journey.JourneyStatus.EVALUATION_PENDING;
        }

        AssessmentTest latestTest = assessmentTestRepository.findTopByJourneyOrderByCreatedAtDesc(journey).orElse(null);
        if (latestTest != null) {
            if (latestTest.getStatus() == AssessmentTest.TestStatus.COMPLETED) {
                return Journey.JourneyStatus.EVALUATION_PENDING;
            }
            return Journey.JourneyStatus.TEST_IN_PROGRESS;
        }

        return Journey.JourneyStatus.ASSESSMENT_PENDING;
    }

    private void pauseOtherJourneysOnResume(User user, Long resumedJourneyId) {
        List<Journey> candidates = journeyRepository.findActiveJourneysByUser(user);
        if (candidates.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        List<Journey> journeysToPause = candidates.stream()
                .filter(j -> !Objects.equals(j.getId(), resumedJourneyId))
                .filter(j -> AUTO_PAUSE_ON_RESUME_STATUSES.contains(j.getStatus()))
                .collect(Collectors.toList());

        if (journeysToPause.isEmpty()) {
            return;
        }

        journeysToPause.forEach(j -> {
            j.setStatus(Journey.JourneyStatus.PAUSED);
            j.setLastActivityAt(now);
        });
        journeyRepository.saveAll(journeysToPause);

        log.info("Paused {} other journeys for user {} while resuming journey {}",
                journeysToPause.size(), user.getId(), resumedJourneyId);
    }

    @Override
    @Transactional
    public JourneySummaryResponse cancelJourney(User user, Long journeyId) {
        return updateJourneyStatus(user, journeyId, Journey.JourneyStatus.CANCELLED);
    }

    @Override
    @Transactional
    public JourneySummaryResponse completeJourney(User user, Long journeyId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        journey.setStatus(Journey.JourneyStatus.COMPLETED);
        journey.setProgressPercentage(100);
        journey.setCompletedAt(Instant.now());
        journey.setLastActivityAt(Instant.now());
        journey = journeyRepository.save(journey);

        // Create completion milestone
        JourneyProgress progress = JourneyProgress.builder()
                .journey(journey)
                .user(user)
                .milestone(JourneyProgress.Milestone.JOURNEY_COMPLETED)
                .isCompleted(true)
                .milestoneProgress(100)
                .completedAt(Instant.now())
                .build();
        journeyProgressRepository.save(progress);

        return mapToJourneySummary(journey);
    }

    @Override
    @Transactional
    public GenerateTestResponse generateAssessmentTest(User user, Long journeyId) {
        log.info("Generating assessment test for journey: {}", journeyId);

        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        AssessmentTest latestTest = assessmentTestRepository.findTopByJourneyOrderByCreatedAtDesc(journey).orElse(null);
        if (shouldResumeExistingAssessmentTest(journey, latestTest)) {
            log.info("Reusing existing in-progress assessment test {} for journey {}", latestTest.getId(), journeyId);
            return buildGenerateTestResponse(
                    journey,
                    latestTest,
                    "Bạn đang có bài quiz chưa hoàn thành. Vui lòng tiếp tục bài hiện tại.");
        }

        long generatedTestCount = assessmentTestRepository.countByJourney(journey);
        if (generatedTestCount >= MAX_ASSESSMENT_ATTEMPTS) {
            throw new RuntimeException("Bạn chỉ có 1 lượt tạo lại quiz cho mỗi hành trình.");
        }

        // Parse assessment data
        StartJourneyRequest assessmentData;
        try {
            assessmentData = objectMapper.readValue(journey.getAssessmentData(), StartJourneyRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse assessment data", e);
        }

        // Domain and goal are now directly in the request
        String domain = assessmentData.getDomain();
        String goal = assessmentData.getGoal();
        String jobRole = assessmentData.getJobRole();
        String industry = assessmentData.getIndustry();
        int requestedQuestionCount = resolveAssessmentQuestionCount(assessmentData);
        int requestedTimeLimitMinutes = resolveAssessmentTimeLimitMinutes(assessmentData);

        log.info("Using domain: {}, goal: {}, jobRole: {}, industry: {}, questionCount: {}, timeLimitMinutes: {}",
                domain, goal, jobRole, industry, requestedQuestionCount, requestedTimeLimitMinutes);

        // === NEW: Bank-first test generation ===
        AssessmentTest test = tryGenerateFromQuestionBank(
                journey, user, domain, industry, jobRole, requestedQuestionCount, requestedTimeLimitMinutes);

        if (test != null) {
            // Bank was used (full or partial)
            return buildGenerateTestResponse(journey, test,
                    "Đã tạo bài quiz đánh giá cho " + domain + " từ ngân hàng câu hỏi.");
        }

        // === Fallback: AI generation (no bank or empty bank) ===
        return generateTestFromAI(journey, user, domain, assessmentData, generatedTestCount);
    }

    /**
     * Try to generate test from question bank.
     * @return AssessmentTest if bank was used, null if bank should not be used
     */
    private AssessmentTest tryGenerateFromQuestionBank(Journey journey, User user, String domain,
            String industry, String jobRole, int requestedQuestionCount, int requestedTimeLimitMinutes) {

        Optional<QuestionBankResponse> bankOpt = questionBankService.findActiveBank(domain, industry, jobRole);
        if (bankOpt.isEmpty()) {
            log.info("No question bank found for domain={}, industry={}, jobRole={}. Falling back to AI generation.",
                    domain, industry, jobRole);
            return null;
        }

        QuestionBankResponse bank = bankOpt.get();
        int availableQuestions = bank.getActiveQuestionCount() != null ? bank.getActiveQuestionCount() : 0;
        if (availableQuestions < MIN_QUESTION_BANK_POOL_SIZE) {
            log.info("Question bank {} has only {} active questions (< {}). Falling back to AI generation.",
                    bank.getId(), availableQuestions, MIN_QUESTION_BANK_POOL_SIZE);
            return null;
        }

        List<QuestionInfo> bankQuestions = questionBankService.selectRandomQuestions(
                bank.getId(), requestedQuestionCount, bank.getDifficultyDistribution());

        if (bankQuestions.size() < requestedQuestionCount) {
            log.info("Question bank {} returned only {} / {} requested questions. Falling back to AI generation.",
                    bank.getId(), bankQuestions.size(), requestedQuestionCount);
            return null;
        }

        questionBankService.incrementUsedCount(bankQuestions);

        // Build AssessmentTest from bank questions
        AssessmentTest test = AssessmentTest.builder()
                .journey(journey)
                .questionBank(entityManager.getReference(QuestionBank.class, bank.getId()))
                .title("Bài đánh giá kỹ năng " + domain)
                .description("Bài quiz đánh giá kỹ năng từ ngân hàng câu hỏi cho " + domain)
                .targetField(domain)
                .status(AssessmentTest.TestStatus.PENDING)
                .questionCount(bankQuestions.size())
                .timeLimitMinutes(requestedTimeLimitMinutes)
                .difficultyLevel("MIXED")
                .questionsJson(toQuestionsJson(bankQuestions))
                .generationPrompt("Generated from question bank id=" + bank.getId())
                .build();

        test = assessmentTestRepository.save(test);

        // Update journey status
        journey.setStatus(Journey.JourneyStatus.TEST_IN_PROGRESS);
        journey.setLastActivityAt(Instant.now());
        journeyRepository.save(journey);

        // Create progress milestone
        JourneyProgress progress = JourneyProgress.builder()
                .journey(journey)
                .user(user)
                .milestone(JourneyProgress.Milestone.TEST_GENERATED)
                .isCompleted(true)
                .milestoneProgress(100)
                .completedAt(Instant.now())
                .build();
        journeyProgressRepository.save(progress);

        return test;
    }

    private String getSkillAreasAlreadyCovered(List<QuestionInfo> questions) {
        return questions.stream()
                .map(QuestionInfo::skillArea)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    /**
     * Fallback: Generate test entirely via AI (original behavior).
     */
    private GenerateTestResponse generateTestFromAI(Journey journey, User user, String domain,
            StartJourneyRequest assessmentData, long generatedTestCount) {

        UserAssessmentInfo userInfo = new UserAssessmentInfo(
                assessmentData.getDomain(),
                assessmentData.getGoal(),
                assessmentData.getLevel(),
                assessmentData.getSkills(),
                assessmentData.getFocusAreas(),
                assessmentData.getLanguage(),
                assessmentData.getDuration(),
                resolveAssessmentQuestionCount(assessmentData)
        );

        String prompt = assessmentPromptService.getTestGenerationPrompt(
                domain,
                assessmentData.getIndustry(),
                assessmentData.getJobRole(),
                userInfo
        );

        log.info("Calling AI to generate specialized test for domain: {}", domain);

        String aiResponse;
        try {
            aiResponse = getChatClient().prompt().user(prompt).call().content();
        } catch (Exception e) {
            log.error("AI test generation failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate test: AI service error", e);
        }

        Map<String, Object> testData;
        try {
            String jsonStr = extractJsonFromResponse(aiResponse);
            testData = objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse AI test generation response: {}", aiResponse);
            throw new RuntimeException("Failed to generate test: Invalid AI response", e);
        }

        int requestedQuestionCount = resolveAssessmentQuestionCount(assessmentData);
        int requestedTimeLimitMinutes = resolveAssessmentTimeLimitMinutes(assessmentData);
        List<Object> normalizedQuestions = normalizeGeneratedQuestions(testData.get("questions"), requestedQuestionCount);
        if (normalizedQuestions.isEmpty()) {
            throw new RuntimeException("Failed to generate test: AI response missing questions");
        }
        int finalQuestionCount = Math.min(requestedQuestionCount, normalizedQuestions.size());

        AssessmentTest test = AssessmentTest.builder()
                .journey(journey)
                .title((String) testData.get("title"))
                .description((String) testData.get("description"))
                .targetField((String) testData.get("targetField"))
                .status(AssessmentTest.TestStatus.PENDING)
                .questionCount(finalQuestionCount)
                .timeLimitMinutes(requestedTimeLimitMinutes)
                .difficultyLevel((String) testData.get("difficultyLevel"))
                .questionsJson(objectMapper.valueToTree(normalizedQuestions).toString())
                .generationPrompt(prompt)
                .build();

        test = assessmentTestRepository.save(test);

        // Update journey status
        journey.setStatus(Journey.JourneyStatus.TEST_IN_PROGRESS);
        journey.setLastActivityAt(Instant.now());
        journeyRepository.save(journey);

        // Create progress milestone
        JourneyProgress progress = JourneyProgress.builder()
                .journey(journey)
                .user(user)
                .milestone(JourneyProgress.Milestone.TEST_GENERATED)
                .isCompleted(true)
                .milestoneProgress(100)
                .completedAt(Instant.now())
                .build();
        journeyProgressRepository.save(progress);

        long updatedTestCount = generatedTestCount + 1;
        int remainingRetakes = calculateRemainingAssessmentRetakes(updatedTestCount);
        String message = "Đã tạo bài quiz đánh giá cho " + domain + "."
                + (remainingRetakes > 0
                        ? " Bạn còn " + remainingRetakes + " lượt tạo lại quiz."
                        : " Bạn đã dùng hết lượt tạo lại quiz.");

        return buildGenerateTestResponse(journey, test, message);
    }

    private String toQuestionsJson(List<QuestionInfo> questions) {
        try {
            return objectMapper.writeValueAsString(toQuestionPayloads(questions));
        } catch (Exception e) {
            log.error("Failed to serialize questions to JSON: {}", e.getMessage());
            return "[]";
        }
    }

    private List<Map<String, Object>> toQuestionPayloads(List<QuestionInfo> questions) {
        if (questions == null || questions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> payloads = new ArrayList<>();
        int fallbackQuestionId = 1;
        for (QuestionInfo question : questions) {
            if (question == null) {
                continue;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            long questionId = question.questionId() != null && question.questionId() > 0
                    ? question.questionId()
                    : fallbackQuestionId;
            payload.put("questionId", questionId);
            payload.put("question", question.question());
            payload.put("options", question.options() != null ? question.options() : Collections.emptyList());
            payload.put("correctAnswer", question.correctAnswer());
            payload.put("explanation", question.explanation());
            payload.put("difficulty", question.difficulty());
            payload.put("skillArea", question.skillArea());
            payloads.add(payload);
            fallbackQuestionId++;
        }

        return payloads;
    }

    private int resolveAssessmentQuestionCount(StartJourneyRequest assessmentData) {
        if (assessmentData == null) {
            return 15;
        }

        Integer requestedQuestionCount = assessmentData.getQuestionCount();
        if (requestedQuestionCount != null) {
            if (requestedQuestionCount <= 10) {
                return 10;
            }
            if (requestedQuestionCount <= 15) {
                return 15;
            }
            return 25;
        }

        String duration = assessmentData.getDuration();
        if (duration == null || duration.isBlank()) {
            return 15;
        }

        return switch (duration.trim().toUpperCase(Locale.ROOT)) {
            case "QUICK" -> 10;
            case "DEEP" -> 25;
            default -> 15;
        };
    }

    private int resolveAssessmentTimeLimitMinutes(StartJourneyRequest assessmentData) {
        if (assessmentData == null || assessmentData.getDuration() == null || assessmentData.getDuration().isBlank()) {
            return 15;
        }

        return switch (assessmentData.getDuration().trim().toUpperCase(Locale.ROOT)) {
            case "QUICK" -> 5;
            case "DEEP" -> 30;
            default -> 15;
        };
    }

    private List<Object> normalizeGeneratedQuestions(Object questionsData, int requestedQuestionCount) {
        if (!(questionsData instanceof List<?> rawQuestions) || rawQuestions.isEmpty()) {
            return Collections.emptyList();
        }

        return rawQuestions.stream()
                .filter(Objects::nonNull)
                .limit(requestedQuestionCount)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<QuestionInfo> generateAiQuestionsSupplement(String domain, String industry, String jobRole,
            UserAssessmentInfo userInfo, int count, String excludeSkills) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("Tạo ").append(count)
                .append(" câu hỏi trắc nghiệm bổ sung cho bài đánh giá kỹ năng.\n")
                .append("Lĩnh vực: ").append(domain).append("\n");
        if (jobRole != null) {
            promptBuilder.append("Vai trò: ").append(jobRole).append("\n");
        }
        if (excludeSkills != null && !excludeSkills.isBlank()) {
            promptBuilder.append("Các kỹ năng đã có trong ngân hàng (không tạo trùng): ")
                    .append(excludeSkills).append("\n");
        }
        promptBuilder.append("\nFormat JSON: {\"questions\": [...]}\n");
        promptBuilder.append("Mỗi câu: {\"questionId\":1,\"question\":\"...\",\"options\":[\"A. ...\",\"B. ...\",\"C. ...\",\"D. ...\"],\"correctAnswer\":\"A\",\"explanation\":\"...\",\"difficulty\":\"BEGINNER\",\"skillArea\":\"...\"}");

        try {
            String aiResponse = getChatClient().prompt().user(promptBuilder.toString()).call().content();
            String jsonStr = extractJsonFromResponse(aiResponse);
            Map<String, Object> parsed = objectMapper.readValue(jsonStr, Map.class);
            List<?> questionsList = (List<?>) parsed.get("questions");
            List<QuestionInfo> result = new ArrayList<>();

            if (questionsList != null) {
                for (Object q : questionsList) {
                    if (!(q instanceof Map)) continue;
                    Map<String, Object> question = (Map<String, Object>) q;
                    List<String> options = new ArrayList<>();
                    Object opts = question.get("options");
                    if (opts instanceof List) {
                        for (int i = 0; i < ((List<?>) opts).size(); i++) {
                            options.add(((List<?>) opts).get(i).toString());
                        }
                    }
                    result.add(new QuestionInfo(
                            0L,
                            (String) question.get("question"),
                            options,
                            (String) question.get("correctAnswer"),
                            (String) question.get("explanation"),
                            (String) question.get("difficulty"),
                            (String) question.get("skillArea")
                    ));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to generate AI supplement questions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private boolean shouldResumeExistingAssessmentTest(Journey journey, AssessmentTest latestTest) {
        if (journey == null || latestTest == null) {
            return false;
        }

        AssessmentTest.TestStatus status = latestTest.getStatus();
        boolean isInProgress = status == AssessmentTest.TestStatus.PENDING || status == AssessmentTest.TestStatus.IN_PROGRESS;
        if (!isInProgress) {
            return false;
        }

        return testResultRepository.findByJourneyAndAssessmentTest(journey, latestTest).isEmpty();
    }

    private int calculateRemainingAssessmentRetakes(long generatedTestCount) {
        long safeCount = Math.max(0L, generatedTestCount);
        return (int) Math.max(0L, MAX_ASSESSMENT_ATTEMPTS - Math.max(1L, safeCount));
    }

    private GenerateTestResponse buildGenerateTestResponse(Journey journey, AssessmentTest test, String message) {
        return GenerateTestResponse.builder()
                .journeyId(journey.getId())
                .testId(test.getId())
                .title(test.getTitle())
                .description(test.getDescription())
                .targetField(test.getTargetField())
                .questionCount(test.getQuestionCount())
                .timeLimitMinutes(test.getTimeLimitMinutes())
                .difficultyLevel(test.getDifficultyLevel())
                .questionsJson(test.getQuestionsJson())
                .message(message)
                .build();
    }

    private AssessmentTest ensureAssessmentTestQuestionsReady(AssessmentTest test) {
        if (test == null || hasRenderableQuestionPayload(test.getQuestionsJson())) {
            return test;
        }

        Long bankId = resolveQuestionBankId(test);
        if (bankId == null) {
            log.warn("Assessment test {} has invalid questionsJson and no question bank marker to recover from.",
                    test.getId());
            return test;
        }

        try {
            QuestionBankResponse bank = questionBankService.getBankById(bankId);
            int targetCount = test.getQuestionCount() != null && test.getQuestionCount() > 0
                    ? test.getQuestionCount()
                    : MIN_QUESTION_BANK_POOL_SIZE;

            List<QuestionInfo> recoveredQuestions = questionBankService.selectRandomQuestions(
                    bankId, targetCount, bank.getDifficultyDistribution());

            if (recoveredQuestions.size() < targetCount) {
                log.warn("Could not recover enough questions for test {} from bank {}. Got {} / {}.",
                        test.getId(), bankId, recoveredQuestions.size(), targetCount);
                return test;
            }

            test.setQuestionsJson(toQuestionsJson(recoveredQuestions));
            test.setQuestionCount(recoveredQuestions.size());
            if (test.getQuestionBank() == null) {
                test.setQuestionBank(entityManager.getReference(QuestionBank.class, bankId));
            }
            AssessmentTest recovered = assessmentTestRepository.save(test);
            log.info("Recovered questionsJson for assessment test {} from question bank {}", test.getId(), bankId);
            return recovered;
        } catch (Exception ex) {
            log.warn("Failed to recover questionsJson for assessment test {} from question bank {}: {}",
                    test.getId(), bankId, ex.getMessage());
            return test;
        }
    }

    private boolean hasRenderableQuestionPayload(String questionsJson) {
        List<Map<String, Object>> questions = parseQuestionsJson(questionsJson);
        if (questions.isEmpty()) {
            return false;
        }

        return questions.stream().allMatch(this::isRenderableQuestionItem);
    }

    private boolean isRenderableQuestionItem(Map<String, Object> question) {
        if (question == null || question.isEmpty()) {
            return false;
        }

        String questionText = Optional.ofNullable(toText(question.get("question"))).orElse("").trim();
        if (questionText.isBlank()) {
            return false;
        }

        Object optionsValue = question.get("options");
        if (!(optionsValue instanceof List<?> options)) {
            return false;
        }

        return options.stream().filter(Objects::nonNull).count() >= 2;
    }

    private Long extractQuestionBankId(String generationPrompt) {
        if (generationPrompt == null || generationPrompt.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile("question bank id=(\\d+)").matcher(generationPrompt);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long resolveQuestionBankId(AssessmentTest test) {
        if (test == null) {
            return null;
        }
        if (test.getQuestionBank() != null && test.getQuestionBank().getId() != null) {
            return test.getQuestionBank().getId();
        }
        return extractQuestionBankId(test.getGenerationPrompt());
    }

    @Override
    public AssessmentTestResponse getAssessmentTest(User user, Long journeyId, Long testId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        AssessmentTest test = assessmentTestRepository.findByIdAndJourney(testId, journey)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        test = ensureAssessmentTestQuestionsReady(test);

        return AssessmentTestResponse.builder()
                .id(test.getId())
                .title(test.getTitle())
                .description(test.getDescription())
                .targetField(test.getTargetField())
                .status(test.getStatus())
                .questionCount(test.getQuestionCount())
                .timeLimitMinutes(test.getTimeLimitMinutes())
                .difficultyLevel(test.getDifficultyLevel())
                .questionsJson(test.getQuestionsJson())
                .createdAt(test.getCreatedAt())
                .showResults(test.getStatus() == AssessmentTest.TestStatus.COMPLETED)
                .build();
    }

    @Override
    @Transactional
    public TestResultResponse submitTest(User user, Long journeyId, SubmitTestRequest request) {
        log.info("Submitting test for journey: {}", journeyId);

        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        AssessmentTest test = assessmentTestRepository.findByIdAndJourney(request.getTestId(), journey)
                .orElseThrow(() -> new RuntimeException("Test not found"));
        test = ensureAssessmentTestQuestionsReady(test);

        // Parse questions
        List<Map<String, Object>> questions;
        try {
            questions = objectMapper.readValue(test.getQuestionsJson(), List.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse test questions", e);
        }

        String domain = test.getTargetField() != null ? test.getTargetField() : journey.getDomain();
        StartJourneyRequest assessmentData;
        try {
            assessmentData = objectMapper.readValue(journey.getAssessmentData(), StartJourneyRequest.class);
        } catch (Exception e) {
            assessmentData = null;
        }
        String goal = assessmentData != null ? assessmentData.getGoal() : null;

        Map<Long, String> normalizedUserAnswers = normalizeUserAnswers(request.getAnswers());
        List<QuestionEvaluation> questionEvaluations = evaluateQuestionAnswers(questions, normalizedUserAnswers);

        int totalQuestions = questionEvaluations.size();
        int correctAnswers = (int) questionEvaluations.stream().filter(q -> q.correct).count();
        int answeredQuestions = (int) questionEvaluations.stream()
                .filter(q -> q.userAnswer != null && !q.userAnswer.isBlank())
                .count();
        int scorePercentage = calculateScorePercentage(correctAnswers, totalQuestions);
        Journey.SkillLevel evaluatedLevel = determineSkillLevel(scorePercentage);

        EvaluationSnapshot snapshot = createEvaluationSnapshot(totalQuestions, answeredQuestions, correctAnswers, scorePercentage);

        List<Map<String, Object>> derivedSkillGaps = buildSkillGaps(questionEvaluations, domain, snapshot.recommendationMode);
        List<Map<String, Object>> derivedStrengths = buildStrengths(questionEvaluations);

        String deterministicSummary = buildDeterministicSummary(
                domain,
                scorePercentage,
                evaluatedLevel,
                snapshot.totalQuestions,
                snapshot.correctAnswers,
                snapshot.incorrectAnswers,
                snapshot.recommendationMode,
                snapshot.reassessmentRecommended
        );

        String aiSummary = null;
        String aiDetailedFeedback = null;
        List<Map<String, Object>> aiSkillGaps = Collections.emptyList();
        List<Map<String, Object>> aiStrengths = Collections.emptyList();
        List<String> aiHighlightKeywords = Collections.emptyList();
        List<String> aiRecommendations = Collections.emptyList();
        try {
            List<QuestionInfo> questionInfos = questions.stream()
                    .map(q -> new QuestionInfo(
                            ((Number) q.get("questionId")).longValue(),
                            (String) q.get("question"),
                            (List<String>) q.get("options"),
                            (String) q.get("correctAnswer"),
                            (String) q.get("explanation"),
                            (String) q.get("difficulty"),
                            (String) q.get("skillArea")
                    ))
                    .collect(Collectors.toList());

            TestSubmissionInfo submissionInfo = new TestSubmissionInfo(
                    test.getTitle(),
                    test.getTargetField(),
                    domain,
                    domain,
                    goal,
                    questionInfos,
                    request.getAnswers()
            );

            String prompt = assessmentPromptService.getEvaluationPrompt(
                    domain,
                    domain,
                    goal,
                    submissionInfo
            );

            String aiResponse = getChatClient()
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();

            String jsonStr = extractJsonFromResponse(aiResponse);
            Map<String, Object> evaluation = objectMapper.readValue(jsonStr, Map.class);
            aiSummary = toText(evaluation.get("evaluationSummary"));
            aiDetailedFeedback = toText(evaluation.get("detailedFeedback"));
            aiSkillGaps = extractInsightList(evaluation.get("skillGaps"), true);
            aiStrengths = extractInsightList(evaluation.get("strengths"), false);
            aiHighlightKeywords = extractStringList(evaluation.get("highlightKeywords"));
            aiRecommendations = extractStringList(evaluation.get("recommendations"));
        } catch (Exception e) {
            log.warn("AI evaluation enrichment failed for journey {}. Fallback to deterministic summary.", journeyId, e);
        }

        List<Map<String, Object>> finalSkillGaps = mergeSkillGapInsights(derivedSkillGaps, aiSkillGaps);
        List<Map<String, Object>> finalStrengths = mergeStrengthInsights(derivedStrengths, aiStrengths);
        String finalSummary = aiSummary != null && !aiSummary.isBlank()
                ? aiSummary.trim()
                : deterministicSummary;
        String deterministicDetailedFeedback = buildDeterministicDetailedFeedback(
                domain,
                scorePercentage,
                evaluatedLevel,
                snapshot,
                finalSkillGaps,
                finalStrengths,
                aiRecommendations
        );
        String finalDetailedFeedback = combineDetailedFeedback(deterministicDetailedFeedback, aiDetailedFeedback);
        List<String> finalHighlightKeywords = buildHighlightKeywords(
                aiHighlightKeywords,
                finalSkillGaps,
                finalStrengths,
                domain,
                evaluatedLevel,
                snapshot.recommendationMode
        );

        String skillGapsJson;
        String strengthsJson;
        String highlightKeywordsJson;
        String userAnswersJson;
        try {
            skillGapsJson = objectMapper.writeValueAsString(finalSkillGaps);
            strengthsJson = objectMapper.writeValueAsString(finalStrengths);
            highlightKeywordsJson = objectMapper.writeValueAsString(finalHighlightKeywords);
            userAnswersJson = objectMapper.writeValueAsString(normalizedUserAnswers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize evaluation result", e);
        }

        // Create test result
        TestResult result = TestResult.builder()
                .journey(journey)
                .assessmentTest(test)
                .scorePercentage(scorePercentage)
                .evaluatedLevel(evaluatedLevel)
                .skillGapsJson(skillGapsJson)
                .strengthsJson(strengthsJson)
                .evaluationSummary(finalSummary)
                .detailedFeedback(finalDetailedFeedback)
                .highlightKeywordsJson(highlightKeywordsJson)
                .userAnswersJson(userAnswersJson)
                .correctAnswersJson(test.getQuestionsJson())
                .evaluatedAt(Instant.now())
                .build();

        result = testResultRepository.save(result);

        // Update journey
        journey.setCurrentLevel(evaluatedLevel);
        journey.setStatus(Journey.JourneyStatus.EVALUATION_PENDING);
        journey.setLastActivityAt(Instant.now());
        journeyRepository.save(journey);

        // Update test status
        test.setStatus(AssessmentTest.TestStatus.COMPLETED);
        assessmentTestRepository.save(test);

        // Create evaluation milestones
        JourneyProgress progress = JourneyProgress.builder()
                .journey(journey)
                .user(user)
                .milestone(JourneyProgress.Milestone.TEST_COMPLETED)
                .isCompleted(true)
                .milestoneProgress(100)
                .completedAt(Instant.now())
                .notes("Score: " + scorePercentage + "%, Level: " + evaluatedLevel + ", Mode: " + snapshot.recommendationMode)
                .build();
        journeyProgressRepository.save(progress);

        JourneyProgress evalProgress = JourneyProgress.builder()
                .journey(journey)
                .user(user)
                .milestone(JourneyProgress.Milestone.EVALUATION_COMPLETED)
                .isCompleted(true)
                .milestoneProgress(100)
                .completedAt(Instant.now())
                .build();
        journeyProgressRepository.save(evalProgress);

        return mapToTestResultResponse(result, totalQuestions);
    }

    @Override
    public TestResultResponse getTestResult(User user, Long journeyId, Long resultId) {
        TestResult result = testResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("Test result not found"));

        if (!result.getJourney().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Access denied");
        }

        return mapToTestResultResponse(result, null);
    }

    @Override
    @Transactional
    public JourneySummaryResponse generateRoadmap(User user, Long journeyId) {
        log.info("Generating roadmap for journey: {}", journeyId);

        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        TestResult latestResult = testResultRepository.findTopByJourneyOrderByCreatedAtDesc(journey)
                .orElseThrow(() -> new RuntimeException("No test result found. Please complete the assessment test first."));

        // Get skill gaps and strengths for roadmap generation
        List<Map<String, Object>> skillGaps;
        List<Map<String, Object>> strengths;
        try {
            skillGaps = objectMapper.readValue(latestResult.getSkillGapsJson(), List.class);
            strengths = objectMapper.readValue(latestResult.getStrengthsJson(), List.class);
        } catch (Exception e) {
            skillGaps = Collections.emptyList();
            strengths = Collections.emptyList();
        }

        // Call roadmap service with evaluation data
        Long roadmapSessionId = createRoadmapSessionFromEvaluation(journey, latestResult, skillGaps, strengths);

        journey.setRoadmapSessionId(roadmapSessionId);
        journey.setStatus(Journey.JourneyStatus.ROADMAP_GENERATED);
        journey.setLastActivityAt(Instant.now());
        journey.setProgressPercentage(30);
        journey = journeyRepository.save(journey);

        JourneyProgress progress = JourneyProgress.builder()
                .journey(journey)
                .user(user)
                .milestone(JourneyProgress.Milestone.ROADMAP_CREATED)
                .isCompleted(true)
                .milestoneProgress(100)
                .completedAt(Instant.now())
                .build();
        journeyProgressRepository.save(progress);

        return mapToJourneySummary(journey);
    }

    @Override
    public Object getRoadmapForJourney(User user, Long journeyId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        if (journey.getRoadmapSessionId() == null) {
            throw new RuntimeException("No roadmap generated for this journey");
        }

        return aiRoadmapService.getRoadmapById(journey.getRoadmapSessionId(), user.getId());
    }

    @Override
    @Transactional
    public Object generateStudyPlans(User user, Long journeyId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        RoadmapResponse roadmap = requireRoadmapForJourney(user, journey);
        List<RoadmapResponse.RoadmapNode> nodes = roadmap.getRoadmap() != null ? roadmap.getRoadmap() : Collections.emptyList();

        if (nodes.isEmpty()) {
            throw new RuntimeException("Roadmap does not contain any nodes to convert into study tasks");
        }

        String nextEligibleNodeId = findNextEligibleNodeId(roadmap, nodes);
        String nextEligibleNodeTitle = resolveNodeDisplayTitle(nodes, nextEligibleNodeId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Study plan chỉ được tạo theo yêu cầu từng node, không tự động tạo hàng loạt.");
        response.put("journeyId", journey.getId());
        response.put("roadmapId", journey.getRoadmapSessionId());
        response.put("totalNodes", nodes.size());
        response.put("nextEligibleNodeId", nextEligibleNodeId);
        response.put("nextEligibleNodeTitle", nextEligibleNodeTitle);
        response.put("allNodesCompleted", nextEligibleNodeId == null);

        if (nextEligibleNodeId == null) {
            response.put("recommendation", "Tất cả node đã hoàn thành. Bạn có thể tổng kết journey hoặc tạo roadmap mới.");
        } else {
            response.put("recommendation", "Hoàn thành node hiện tại rồi mới mở node kế tiếp.");
        }

        return response;
    }

    @Override
    @Transactional
    public Object createStudyPlanForNode(User user, Long journeyId, String nodeId, GenerateScheduleRequest request) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        return createStudyPlanForRoadmapNodeInternal(user, journey, nodeId, request);
    }

    @Override
    @Transactional
    public Object createStudyPlanForRoadmapNode(User user, Long roadmapSessionId, String nodeId, GenerateScheduleRequest request) {
        Journey journey = journeyRepository.findByRoadmapSessionId(roadmapSessionId)
                .orElseThrow(() -> new RuntimeException("Journey not found for roadmap session"));

        if (!Objects.equals(journey.getUser().getId(), user.getId())) {
            throw new RuntimeException("Journey not found");
        }

        return createStudyPlanForRoadmapNodeInternal(user, journey, nodeId, request);
    }

    private Object createStudyPlanForRoadmapNodeInternal(User user, Journey journey, String nodeId, GenerateScheduleRequest request) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new RuntimeException("Node id is required");
        }

        String normalizedNodeId = nodeId.trim();
        RoadmapResponse roadmap = requireRoadmapForJourney(user, journey);
        List<RoadmapResponse.RoadmapNode> roadmapNodes = roadmap.getRoadmap() != null
                ? roadmap.getRoadmap()
                : Collections.emptyList();

        RoadmapResponse.RoadmapNode node = roadmapNodes.stream()
                .filter(item -> item != null && normalizedNodeId.equals(item.getId()))
                .findFirst()
                .orElse(null);

        if (node == null) {
            throw new RuntimeException("Roadmap node not found for id: " + normalizedNodeId);
        }

        if (isRoadmapNodeCompleted(roadmap, normalizedNodeId)) {
            return Map.of(
                    "message", "Node này đã hoàn thành. Hãy tạo/kích hoạt plan cho node tiếp theo.",
                    "created", false,
                    "journeyId", journey.getId(),
                    "roadmapSessionId", journey.getRoadmapSessionId(),
                    "nodeId", normalizedNodeId);
        }

        String nextEligibleNodeId = findNextEligibleNodeId(roadmap, roadmapNodes);
        if (nextEligibleNodeId != null && !normalizedNodeId.equals(nextEligibleNodeId)) {
            String nextNodeTitle = resolveNodeDisplayTitle(roadmapNodes, nextEligibleNodeId);
            throw new IllegalArgumentException(
                    String.format("Bạn cần hoàn thành node '%s' trước khi tạo plan cho node này.", nextNodeTitle));
        }

        int nodeOrder = resolveNodeOrder(roadmapNodes, normalizedNodeId);
        int totalRoadmapNodes = countTrackableRoadmapNodes(roadmapNodes);

        List<TaskColumnResponse> board = taskBoardService.getBoard(user.getId());
        UUID todoColumnId = resolveTodoColumnId(board);
        List<TaskResponse> existingTasks = flattenBoardTasks(board);
        String marker = buildStudyPlanMarker(journey.getId(), journey.getRoadmapSessionId(), normalizedNodeId);
        List<TaskResponse> existingTasksForNode = findExistingNodeTasks(existingTasks, marker);

        if (!existingTasksForNode.isEmpty()) {
            TaskResponse firstTask = existingTasksForNode.get(0);
            return Map.of(
                    "message", "Roadmap node is already linked to study planner tasks.",
                    "created", false,
                    "journeyId", journey.getId(),
                    "roadmapSessionId", journey.getRoadmapSessionId(),
                    "nodeId", normalizedNodeId,
                    "taskCount", existingTasksForNode.size(),
                    "task", toTaskSummary(firstTask),
                    "tasks", existingTasksForNode.stream()
                            .map(this::toTaskSummary)
                            .collect(Collectors.toList()));
        }

        GenerateScheduleRequest scheduleRequest = buildRoadmapNodeScheduleRequest(journey, node, request);
        List<StudySessionResponse> plannedSessions = generateNodeStudySessions(user, node, scheduleRequest);
        List<TaskResponse> createdTasks = createTasksFromPlannedSessions(
                user,
                journey,
                node,
                todoColumnId,
                marker,
                plannedSessions,
                nodeOrder,
                totalRoadmapNodes,
                scheduleRequest);

        if (createdTasks.isEmpty()) {
            throw new RuntimeException("Unable to create study tasks for this roadmap node");
        }

        journey.setStatus(Journey.JourneyStatus.STUDY_PLAN_IN_PROGRESS);
        journey.setLastActivityAt(Instant.now());
        if (journey.getProgressPercentage() == null || journey.getProgressPercentage() < 40) {
            journey.setProgressPercentage(40);
        }
        journeyRepository.save(journey);

        return Map.of(
                "message", "Study planner tasks created from roadmap node.",
                "created", true,
                "journeyId", journey.getId(),
                "roadmapSessionId", journey.getRoadmapSessionId(),
                "nodeId", normalizedNodeId,
                "taskCount", createdTasks.size(),
                "task", toTaskSummary(createdTasks.get(0)),
                "tasks", createdTasks.stream()
                        .map(this::toTaskSummary)
                        .collect(Collectors.toList())
        );
    }

    private RoadmapResponse requireRoadmapForJourney(User user, Journey journey) {
        if (journey.getRoadmapSessionId() == null) {
            throw new RuntimeException("Please generate roadmap first");
        }
        return aiRoadmapService.getRoadmapById(journey.getRoadmapSessionId(), user.getId());
    }

    private UUID resolveTodoColumnId(List<TaskColumnResponse> board) {
        if (board == null || board.isEmpty()) {
            throw new RuntimeException("Study planner board is unavailable");
        }

        return board.stream()
                .filter(column -> column.getName() != null && "to do".equalsIgnoreCase(column.getName().trim()))
                .map(TaskColumnResponse::getId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> board.stream()
                        .map(TaskColumnResponse::getId)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("No study planner column found")));
    }

    private List<TaskResponse> flattenBoardTasks(List<TaskColumnResponse> board) {
        if (board == null || board.isEmpty()) {
            return new ArrayList<>();
        }
        return board.stream()
                .filter(Objects::nonNull)
                .flatMap(column -> {
                    List<TaskResponse> tasks = column.getTasks();
                    return tasks == null ? Collections.<TaskResponse>emptyList().stream() : tasks.stream();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private String buildStudyPlanMarker(Long journeyId, Long roadmapSessionId, String nodeId) {
        return String.format("%s journey=%d roadmap=%d node=%s",
                STUDY_PLAN_LINK_MARKER_PREFIX,
                journeyId,
                roadmapSessionId,
                nodeId);
    }

    private int countTrackableRoadmapNodes(List<RoadmapResponse.RoadmapNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (RoadmapResponse.RoadmapNode node : nodes) {
            if (node == null || node.getId() == null || node.getId().isBlank()) {
                continue;
            }
            count++;
        }
        return count;
    }

    private int resolveNodeOrder(List<RoadmapResponse.RoadmapNode> nodes, String nodeId) {
        if (nodes == null || nodes.isEmpty() || nodeId == null || nodeId.isBlank()) {
            return -1;
        }
        int index = 0;
        for (RoadmapResponse.RoadmapNode node : nodes) {
            if (node == null || node.getId() == null || node.getId().isBlank()) {
                continue;
            }
            index++;
            if (nodeId.equals(node.getId())) {
                return index;
            }
        }
        return -1;
    }

    private String buildStudyPlanTaskNotes(
            String marker,
            RoadmapResponse.RoadmapNode node,
            StudySessionResponse session,
            int step,
            int totalSteps,
            int nodeOrder,
            int totalRoadmapNodes) {

        String nodeOrderLabel = nodeOrder > 0 && totalRoadmapNodes > 0
                ? String.format(Locale.ROOT, "%d/%d", nodeOrder, totalRoadmapNodes)
                : "?/?";
        String nodeTitle = sanitizeNoteValue(firstNonBlank(node.getTitle(), node.getId(), "Roadmap node"), 180);
        String sessionTitle = sanitizeNoteValue(firstNonBlank(session.getTitle(), "Study session " + step), 220);

        return String.format(
                Locale.ROOT,
                "Node %s - %s%nTask %d/%d - %s%n%s nodeOrder=%s step=%d/%d",
                nodeOrderLabel,
                nodeTitle,
                step,
                totalSteps,
                sessionTitle,
                marker,
                nodeOrderLabel,
                step,
                totalSteps);
    }

    private String sanitizeNoteValue(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return safeTruncate(normalized, maxLength, "");
    }

    private List<TaskResponse> findExistingNodeTasks(List<TaskResponse> existingTasks, String marker) {
        return existingTasks.stream()
                .filter(task -> task.getUserNotes() != null && task.getUserNotes().contains(marker))
                .collect(Collectors.toList());
    }

    private Map<String, Object> toTaskSummary(TaskResponse task) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", task.getId());
        summary.put("title", task.getTitle());
        summary.put("status", task.getStatus() != null ? task.getStatus() : "");
        summary.put("columnId", task.getColumnId());
        summary.put("priority", task.getPriority() != null ? task.getPriority().name() : "MEDIUM");
        summary.put("startDate", task.getStartDate());
        summary.put("endDate", task.getEndDate());
        summary.put("deadline", task.getDeadline());
        return summary;
    }

    private GenerateScheduleRequest buildRoadmapNodeScheduleRequest(
            Journey journey,
            RoadmapResponse.RoadmapNode node,
            GenerateScheduleRequest inputRequest) {

        GenerateScheduleRequest request = new GenerateScheduleRequest();
        copyScheduleRequest(inputRequest, request);

        ZoneId zoneId = resolveStudyTimeZone(request.getTimezone());
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : LocalDate.now(zoneId);
        int durationMinutes = safeDurationMinutes(request.getDurationMinutes());
        int maxSessionsPerDay = safeMaxSessionsPerDay(request.getMaxSessionsPerDay());

        request.setSubjectName(safeTruncate(firstNonBlank(node.getTitle(), journey.getTitle(), "Roadmap node"), 200, "Roadmap node"));
        request.setTopics(collectNodeTopics(node, 16));
        request.setDesiredOutcome(firstNonBlank(request.getDesiredOutcome(), buildDefaultDesiredOutcome(journey, node)));
        request.setFreeTimeDescription(firstNonBlank(
                request.getFreeTimeDescription(),
                "Auto-generated from roadmap node and user preferences"));
        request.setDurationMinutes(durationMinutes);
        request.setStartDate(startDate);
        request.setTimezone(firstNonBlank(request.getTimezone(), DEFAULT_STUDY_TIMEZONE));
        request.setPreferredDays(normalizePreferredDays(request.getPreferredDays()));
        if (request.getPreferredTimeWindows() == null || request.getPreferredTimeWindows().isEmpty()) {
            request.setPreferredTimeWindows(defaultPreferredTimeWindows(request.getStudyPreference()));
        }
        if (request.getDeadline() == null || request.getDeadline().isBefore(startDate)) {
            request.setDeadline(resolveDefaultDeadline(node, startDate, durationMinutes, maxSessionsPerDay));
        }
        if (request.getIntensityLevel() == null || request.getIntensityLevel().isBlank()) {
            request.setIntensityLevel("balanced");
        }
        if (request.getBreakMinutesBetweenSessions() == null || request.getBreakMinutesBetweenSessions() < 0) {
            request.setBreakMinutesBetweenSessions(10);
        }
        if (request.getMaxSessionsPerDay() == null || request.getMaxSessionsPerDay() <= 0) {
            request.setMaxSessionsPerDay(maxSessionsPerDay);
        }
        if (request.getMaxDailyStudyMinutes() == null || request.getMaxDailyStudyMinutes() <= 0) {
            request.setMaxDailyStudyMinutes(durationMinutes * maxSessionsPerDay);
        }
        if (request.getStudyMethod() == null || request.getStudyMethod().isBlank()) {
            request.setStudyMethod("Active Recall + Practical Exercise");
        }
        if (request.getResourcesPreference() == null || request.getResourcesPreference().isBlank()) {
            request.setResourcesPreference("mixed resources");
        }
        if (request.getAvoidLateNight() == null && request.getAllowLateNight() == null) {
            request.setAvoidLateNight(Boolean.TRUE);
        }
        return request;
    }

    private void copyScheduleRequest(GenerateScheduleRequest source, GenerateScheduleRequest target) {
        if (source == null || target == null) {
            return;
        }
        target.setSubjectName(source.getSubjectName());
        target.setFreeTimeDescription(source.getFreeTimeDescription());
        target.setDurationMinutes(source.getDurationMinutes());
        target.setDeadline(source.getDeadline());
        target.setStartDate(source.getStartDate());
        target.setTimezone(source.getTimezone());
        target.setPreferredDays(source.getPreferredDays() != null ? new ArrayList<>(source.getPreferredDays()) : null);
        target.setPreferredTimeWindows(source.getPreferredTimeWindows() != null
                ? new ArrayList<>(source.getPreferredTimeWindows())
                : null);
        target.setTopics(source.getTopics() != null ? new ArrayList<>(source.getTopics()) : null);
        target.setDesiredOutcome(source.getDesiredOutcome());
        target.setIntensityLevel(source.getIntensityLevel());
        target.setBreakMinutesBetweenSessions(source.getBreakMinutesBetweenSessions());
        target.setMaxSessionsPerDay(source.getMaxSessionsPerDay());
        target.setStudyMethod(source.getStudyMethod());
        target.setResourcesPreference(source.getResourcesPreference());
        target.setStudyPreference(source.getStudyPreference());
        target.setAvoidLateNight(source.getAvoidLateNight());
        target.setAllowLateNight(source.getAllowLateNight());
        target.setConfirmLateNight(source.getConfirmLateNight());
        target.setEarliestStartLocalTime(source.getEarliestStartLocalTime());
        target.setLatestEndLocalTime(source.getLatestEndLocalTime());
        target.setMaxDailyStudyMinutes(source.getMaxDailyStudyMinutes());
        target.setChronotype(source.getChronotype());
        target.setIdealFocusWindows(source.getIdealFocusWindows() != null
                ? new ArrayList<>(source.getIdealFocusWindows())
                : null);
    }

    private List<StudySessionResponse> generateNodeStudySessions(
            User user,
            RoadmapResponse.RoadmapNode node,
            GenerateScheduleRequest request) {

        try {
            List<StudySessionResponse> sessions = aiStudySupportService.generateProposedSchedule(user.getId(), request);
            List<StudySessionResponse> normalized = normalizeGeneratedSessions(node, sessions, request);
            if (!normalized.isEmpty()) {
                return normalized;
            }
        } catch (Exception ex) {
            log.warn("AI session generation failed for node {}: {}", node.getId(), ex.getMessage());
        }
        return buildFallbackSessions(node, request);
    }

    private List<StudySessionResponse> normalizeGeneratedSessions(
            RoadmapResponse.RoadmapNode node,
            List<StudySessionResponse> sessions,
            GenerateScheduleRequest request) {
        if (sessions == null || sessions.isEmpty()) {
            return Collections.emptyList();
        }

        int durationMinutes = safeDurationMinutes(request.getDurationMinutes());
        int breakMinutes = safeBreakMinutes(request.getBreakMinutesBetweenSessions());
        LocalDate baseDate = request.getStartDate() != null
                ? request.getStartDate()
                : LocalDate.now(resolveStudyTimeZone(request.getTimezone()));
        LocalDateTime fallbackCursor = LocalDateTime.of(baseDate, resolvePreferredStartTime(request));
        List<StudySessionResponse> normalized = new ArrayList<>();

        for (StudySessionResponse session : sessions) {
            if (session == null) {
                continue;
            }
            LocalDateTime startTime = session.getStartTime();
            if (startTime == null || startTime.toLocalDate().isBefore(baseDate)) {
                startTime = fallbackCursor;
            }

            LocalDateTime endTime = session.getEndTime();
            if (endTime == null || !endTime.isAfter(startTime)) {
                endTime = startTime.plusMinutes(durationMinutes);
            }

            String title = safeTruncate(
                    firstNonBlank(session.getTitle(), node.getTitle(), "Study session"),
                    255,
                    "Study session");
            String description = safeTruncate(
                    firstNonBlank(session.getDescription(), buildNodeContextDescription(node)),
                    5000,
                    "Roadmap node practice");

            normalized.add(StudySessionResponse.builder()
                    .title(title)
                    .description(description)
                    .startTime(startTime)
                    .endTime(endTime)
                    .status(session.getStatus())
                    .build());

            fallbackCursor = endTime.plusMinutes(breakMinutes);
            if (normalized.size() >= MAX_STUDY_TASKS_PER_NODE) {
                break;
            }
        }

        return normalized;
    }

    private List<StudySessionResponse> buildFallbackSessions(
            RoadmapResponse.RoadmapNode node,
            GenerateScheduleRequest request) {
        int durationMinutes = safeDurationMinutes(request.getDurationMinutes());
        int maxSessionsPerDay = safeMaxSessionsPerDay(request.getMaxSessionsPerDay());
        int breakMinutes = safeBreakMinutes(request.getBreakMinutesBetweenSessions());

        LocalDate startDate = request.getStartDate() != null
                ? request.getStartDate()
                : LocalDate.now(resolveStudyTimeZone(request.getTimezone()));
        LocalDate deadline = request.getDeadline() != null
                ? request.getDeadline()
                : resolveDefaultDeadline(node, startDate, durationMinutes, maxSessionsPerDay);
        LocalTime firstSlot = resolvePreferredStartTime(request);

        int estimatedMinutes = node.getEstimatedTimeMinutes() != null && node.getEstimatedTimeMinutes() > 0
                ? node.getEstimatedTimeMinutes()
                : durationMinutes * 3;
        int sessionCount = Math.max(3, (int) Math.ceil((double) estimatedMinutes / durationMinutes));
        sessionCount = Math.min(MAX_STUDY_TASKS_PER_NODE, sessionCount);

        List<String> focusItems = collectNodeTopics(node, 20);
        if (focusItems.isEmpty()) {
            focusItems = List.of(firstNonBlank(node.getDescription(), node.getTitle(), "Core topic"));
        }

        List<StudySessionResponse> fallbackSessions = new ArrayList<>();
        for (int i = 0; i < sessionCount; i++) {
            int dayOffset = i / maxSessionsPerDay;
            int slotOffset = i % maxSessionsPerDay;
            LocalDate sessionDate = startDate.plusDays(dayOffset);
            if (sessionDate.isAfter(deadline)) {
                sessionDate = deadline;
            }

            LocalDateTime startTime = LocalDateTime.of(sessionDate, firstSlot)
                    .plusMinutes((long) slotOffset * (durationMinutes + breakMinutes));
            LocalDateTime endTime = startTime.plusMinutes(durationMinutes);
            String focus = focusItems.get(i % focusItems.size());

            fallbackSessions.add(StudySessionResponse.builder()
                    .title(safeTruncate(
                            String.format("%s - Step %d", firstNonBlank(node.getTitle(), "Roadmap node"), i + 1),
                            255,
                            "Roadmap step"))
                    .description(safeTruncate(
                            String.format("Focus: %s%n%n%s", focus, buildNodeContextDescription(node)),
                            5000,
                            "Roadmap node practice"))
                    .startTime(startTime)
                    .endTime(endTime)
                    .build());
        }

        return fallbackSessions;
    }

    private List<TaskResponse> createTasksFromPlannedSessions(
            User user,
            Journey journey,
            RoadmapResponse.RoadmapNode node,
            UUID todoColumnId,
            String marker,
            List<StudySessionResponse> sessions,
            int nodeOrder,
            int totalRoadmapNodes,
            GenerateScheduleRequest request) {

        List<StudySessionResponse> sourceSessions = sessions == null || sessions.isEmpty()
                ? buildFallbackSessions(node, request)
                : sessions.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        StudySessionResponse::getStartTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAX_STUDY_TASKS_PER_NODE)
                .collect(Collectors.toList());

        int durationMinutes = safeDurationMinutes(request.getDurationMinutes());
        int breakMinutes = safeBreakMinutes(request.getBreakMinutesBetweenSessions());
        LocalDate baseDate = request.getStartDate() != null
                ? request.getStartDate()
                : LocalDate.now(resolveStudyTimeZone(request.getTimezone()));
        LocalDateTime fallbackCursor = LocalDateTime.of(baseDate, resolvePreferredStartTime(request));

        List<TaskResponse> createdTasks = new ArrayList<>();
        int totalSteps = sourceSessions.size();

        for (int i = 0; i < totalSteps; i++) {
            StudySessionResponse session = sourceSessions.get(i);
            LocalDateTime startTime = session.getStartTime() != null ? session.getStartTime() : fallbackCursor;
            LocalDateTime endTime = session.getEndTime() != null && session.getEndTime().isAfter(startTime)
                    ? session.getEndTime()
                    : startTime.plusMinutes(durationMinutes);

            CreateTaskRequest taskRequest = new CreateTaskRequest();
            taskRequest.setColumnId(todoColumnId);
            taskRequest.setTitle(buildTaskTitleFromSession(node, session, i + 1, totalSteps));
            taskRequest.setDescription(buildTaskDescriptionFromSession(journey, node, session, i + 1, totalSteps));
            taskRequest.setStartDate(startTime);
            taskRequest.setEndDate(endTime);
            taskRequest.setDeadline(endTime);
            taskRequest.setPriority(resolveTaskPriority(node));
            taskRequest.setUserProgress(0);
            taskRequest.setUserNotes(buildStudyPlanTaskNotes(
                    marker,
                    node,
                    session,
                    i + 1,
                    totalSteps,
                    nodeOrder,
                    totalRoadmapNodes));

            createdTasks.add(taskBoardService.createTask(user.getId(), taskRequest));
            fallbackCursor = endTime.plusMinutes(breakMinutes);
        }

        return createdTasks;
    }

    private TaskPriority resolveTaskPriority(RoadmapResponse.RoadmapNode node) {
        String difficulty = node.getDifficulty() != null ? node.getDifficulty().toLowerCase(Locale.ROOT) : "";
        boolean hardNode = difficulty.contains("hard") || difficulty.contains("advanced") || difficulty.contains("expert");
        boolean easyNode = difficulty.contains("easy") || difficulty.contains("beginner") || difficulty.contains("foundation");
        boolean mainNode = node.getType() == RoadmapResponse.RoadmapNode.NodeType.MAIN;

        if (hardNode || (mainNode && !easyNode)) {
            return TaskPriority.HIGH;
        }
        if (easyNode || !mainNode) {
            return TaskPriority.LOW;
        }
        return TaskPriority.MEDIUM;
    }

    private String buildTaskDescriptionFromNode(Journey journey, RoadmapResponse.RoadmapNode node) {
        StringBuilder description = new StringBuilder();
        if (node.getDescription() != null && !node.getDescription().isBlank()) {
            description.append(node.getDescription().trim()).append("\n\n");
        }

        appendTaskSection(description, "Mục tiêu học tập", node.getLearningObjectives());
        appendTaskSection(description, "Khái niệm cốt lõi", node.getKeyConcepts());
        appendTaskSection(description, "Bài tập thực hành", node.getPracticalExercises());
        appendTaskSection(description, "Tài nguyên gợi ý", node.getSuggestedResources());
        appendTaskSection(description, "Tiêu chí hoàn thành", node.getSuccessCriteria());

        description.append("Nguồn: Journey #")
                .append(journey.getId())
                .append(" • Roadmap #")
                .append(journey.getRoadmapSessionId())
                .append(" • Node ")
                .append(node.getId());

        return safeTruncate(description.toString().trim(), 5000, "Task được tạo từ roadmap node");
    }

    private String buildTaskTitleFromSession(
            RoadmapResponse.RoadmapNode node,
            StudySessionResponse session,
            int step,
            int totalSteps) {
        String nodeTitle = firstNonBlank(node.getTitle(), "Roadmap node");
        String sessionTitle = firstNonBlank(session.getTitle(), "Session " + step);
        return safeTruncate(
                String.format("%s | %d/%d - %s", nodeTitle, step, totalSteps, sessionTitle),
                255,
                nodeTitle + " | " + step + "/" + totalSteps);
    }

    private String buildTaskDescriptionFromSession(
            Journey journey,
            RoadmapResponse.RoadmapNode node,
            StudySessionResponse session,
            int step,
            int totalSteps) {
        StringBuilder description = new StringBuilder();
        if (session.getDescription() != null && !session.getDescription().isBlank()) {
            description.append(session.getDescription().trim()).append("\n\n");
        } else {
            description.append(buildNodeContextDescription(node)).append("\n\n");
        }

        description.append("Step ").append(step).append("/").append(totalSteps).append("\n");
        description.append("Source: Journey #").append(journey.getId())
                .append(" | Roadmap #").append(journey.getRoadmapSessionId())
                .append(" | Node ").append(node.getId());

        return safeTruncate(description.toString().trim(), 5000, "Roadmap study task");
    }

    private String buildNodeContextDescription(RoadmapResponse.RoadmapNode node) {
        StringBuilder description = new StringBuilder();
        if (node.getDescription() != null && !node.getDescription().isBlank()) {
            description.append(node.getDescription().trim()).append("\n\n");
        }

        appendTaskSection(description, "Learning Objectives", node.getLearningObjectives());
        appendTaskSection(description, "Key Concepts", node.getKeyConcepts());
        appendTaskSection(description, "Practical Exercises", node.getPracticalExercises());
        appendTaskSection(description, "Suggested Resources", node.getSuggestedResources());
        appendTaskSection(description, "Success Criteria", node.getSuccessCriteria());

        return safeTruncate(description.toString().trim(), 5000, "Roadmap node practice");
    }

    private void appendTaskSection(StringBuilder builder, String title, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }

        List<String> sanitizedValues = values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toList());

        if (sanitizedValues.isEmpty()) {
            return;
        }

        builder.append(title).append(":\n");
        for (String value : sanitizedValues) {
            builder.append("- ").append(value).append("\n");
        }
        builder.append("\n");
    }

    private List<String> collectNodeTopics(RoadmapResponse.RoadmapNode node, int limit) {
        LinkedHashSet<String> topics = new LinkedHashSet<>();
        topics.addAll(sanitizeTextList(node.getLearningObjectives(), limit));
        topics.addAll(sanitizeTextList(node.getKeyConcepts(), limit));
        topics.addAll(sanitizeTextList(node.getPracticalExercises(), limit));
        if (topics.isEmpty()) {
            topics.add(firstNonBlank(node.getTitle(), "Core roadmap topic"));
        }
        return topics.stream().limit(limit).collect(Collectors.toList());
    }

    private List<String> sanitizeTextList(List<String> values, int limit) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String buildDefaultDesiredOutcome(Journey journey, RoadmapResponse.RoadmapNode node) {
        String role = firstNonBlank(journey.getJobRole(), journey.getSubCategory(), journey.getDomain(), "target role");
        return safeTruncate(
                String.format("Master node '%s' and move closer to %s", firstNonBlank(node.getTitle(), "this topic"), role),
                300,
                "Master this roadmap node");
    }

    private LocalDate resolveDefaultDeadline(
            RoadmapResponse.RoadmapNode node,
            LocalDate startDate,
            int durationMinutes,
            int maxSessionsPerDay) {
        int estimatedMinutes = node.getEstimatedTimeMinutes() != null && node.getEstimatedTimeMinutes() > 0
                ? node.getEstimatedTimeMinutes()
                : durationMinutes * 3;
        int sessionsNeeded = Math.max(3, (int) Math.ceil((double) estimatedMinutes / durationMinutes));
        int daysNeeded = Math.max(2, (int) Math.ceil((double) sessionsNeeded / Math.max(1, maxSessionsPerDay)));
        return startDate.plusDays(daysNeeded);
    }

    private ZoneId resolveStudyTimeZone(String timezone) {
        String normalized = firstNonBlank(timezone, DEFAULT_STUDY_TIMEZONE);
        try {
            return ZoneId.of(normalized);
        } catch (Exception ex) {
            return ZoneId.of(DEFAULT_STUDY_TIMEZONE);
        }
    }

    private LocalTime resolvePreferredStartTime(GenerateScheduleRequest request) {
        if (request.getPreferredTimeWindows() != null) {
            for (String window : request.getPreferredTimeWindows()) {
                LocalTime parsed = parseTimeRangeStart(window);
                if (parsed != null) {
                    return parsed;
                }
            }
        }

        if (request.getEarliestStartLocalTime() != null && !request.getEarliestStartLocalTime().isBlank()) {
            try {
                return LocalTime.parse(request.getEarliestStartLocalTime().trim());
            } catch (Exception ignored) {
                // Ignore invalid custom time and fallback.
            }
        }

        String studyPreference = request.getStudyPreference() != null
                ? request.getStudyPreference().trim().toLowerCase(Locale.ROOT)
                : "";
        return switch (studyPreference) {
            case "morning" -> LocalTime.of(7, 0);
            case "afternoon" -> LocalTime.of(14, 0);
            case "evening", "night" -> LocalTime.of(19, 0);
            default -> LocalTime.of(18, 30);
        };
    }

    private LocalTime parseTimeRangeStart(String range) {
        if (range == null || range.isBlank()) {
            return null;
        }
        String[] parts = range.split("-");
        if (parts.length == 0) {
            return null;
        }
        try {
            return LocalTime.parse(parts[0].trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private int safeDurationMinutes(int durationMinutes) {
        if (durationMinutes <= 0) {
            return 90;
        }
        return Math.max(30, Math.min(180, durationMinutes));
    }

    private int safeMaxSessionsPerDay(Integer maxSessionsPerDay) {
        if (maxSessionsPerDay == null || maxSessionsPerDay <= 0) {
            return 2;
        }
        return Math.max(1, Math.min(5, maxSessionsPerDay));
    }

    private int safeBreakMinutes(Integer breakMinutes) {
        if (breakMinutes == null || breakMinutes < 0) {
            return 10;
        }
        return Math.max(5, Math.min(45, breakMinutes));
    }

    private List<String> normalizePreferredDays(List<String> preferredDays) {
        if (preferredDays == null || preferredDays.isEmpty()) {
            return defaultPreferredDays();
        }

        Map<String, String> dayAlias = new HashMap<>();
        dayAlias.put("MON", "MONDAY");
        dayAlias.put("MONDAY", "MONDAY");
        dayAlias.put("TUE", "TUESDAY");
        dayAlias.put("TUESDAY", "TUESDAY");
        dayAlias.put("WED", "WEDNESDAY");
        dayAlias.put("WEDNESDAY", "WEDNESDAY");
        dayAlias.put("THU", "THURSDAY");
        dayAlias.put("THURSDAY", "THURSDAY");
        dayAlias.put("FRI", "FRIDAY");
        dayAlias.put("FRIDAY", "FRIDAY");
        dayAlias.put("SAT", "SATURDAY");
        dayAlias.put("SATURDAY", "SATURDAY");
        dayAlias.put("SUN", "SUNDAY");
        dayAlias.put("SUNDAY", "SUNDAY");

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String day : preferredDays) {
            if (day == null || day.isBlank()) {
                continue;
            }
            String key = day.trim().toUpperCase(Locale.ROOT);
            if (dayAlias.containsKey(key)) {
                normalized.add(dayAlias.get(key));
            }
        }

        if (normalized.isEmpty()) {
            return defaultPreferredDays();
        }
        return new ArrayList<>(normalized);
    }

    private List<String> defaultPreferredDays() {
        return new ArrayList<>(List.of(
                "MONDAY",
                "TUESDAY",
                "WEDNESDAY",
                "THURSDAY",
                "FRIDAY",
                "SATURDAY"));
    }

    private List<String> defaultPreferredTimeWindows(String studyPreference) {
        if (studyPreference == null || studyPreference.isBlank()) {
            return new ArrayList<>(List.of("18:30-21:30"));
        }
        String normalized = studyPreference.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "morning" -> new ArrayList<>(List.of("07:00-10:00"));
            case "afternoon" -> new ArrayList<>(List.of("13:30-17:00"));
            case "night", "evening" -> new ArrayList<>(List.of("18:30-22:00"));
            default -> new ArrayList<>(List.of("18:30-21:30"));
        };
    }

    private String findNextEligibleNodeId(RoadmapResponse roadmap, List<RoadmapResponse.RoadmapNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        for (RoadmapResponse.RoadmapNode node : nodes) {
            if (node == null || node.getId() == null || node.getId().isBlank()) {
                continue;
            }
            if (!isRoadmapNodeCompleted(roadmap, node.getId())) {
                return node.getId();
            }
        }
        return null;
    }

    private boolean isRoadmapNodeCompleted(RoadmapResponse roadmap, String nodeId) {
        if (roadmap == null || roadmap.getProgress() == null || nodeId == null || nodeId.isBlank()) {
            return false;
        }
        RoadmapResponse.QuestProgress progress = roadmap.getProgress().get(nodeId);
        return progress != null && "COMPLETED".equalsIgnoreCase(progress.getStatus());
    }

    private String resolveNodeDisplayTitle(List<RoadmapResponse.RoadmapNode> nodes, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        return nodes.stream()
                .filter(node -> node != null && nodeId.equals(node.getId()))
                .map(node -> {
                    if (node.getTitle() == null || node.getTitle().isBlank()) {
                        return nodeId;
                    }
                    return node.getTitle();
                })
                .findFirst()
                .orElse(nodeId);
    }

    @Override
    @Transactional
    public JourneySummaryResponse generateAiReport(User user, Long journeyId) {
        log.info("Generating AI report for journey: {}", journeyId);

        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        List<JourneyProgress> progressList = journeyProgressRepository.findByJourney(journey);
        TestResult latestResult = testResultRepository.findTopByJourneyOrderByCreatedAtDesc(journey).orElse(null);

        // Build detailed report using AssessmentPromptService approach
        StringBuilder reportPrompt = new StringBuilder();
        reportPrompt.append("Bạn là chuyên gia tư vấn nghề nghiệp hàng đầu. Hãy tạo báo cáo tổng kết hành trình học tập chi tiết.\n\n");

        reportPrompt.append("## Thông tin hành trình:\n");
        reportPrompt.append("- Lĩnh vực: ").append(journey.getDomain()).append("\n");
        reportPrompt.append("- Mục tiêu: ").append(journey.getGoal()).append("\n");
        reportPrompt.append("- Cấp độ hiện tại: ").append(journey.getCurrentLevel()).append("\n");
        reportPrompt.append("- Tiến độ: ").append(journey.getProgressPercentage()).append("%\n\n");

        if (latestResult != null) {
            reportPrompt.append("## Kết quả đánh giá kỹ năng:\n");
            reportPrompt.append("- Điểm số: ").append(latestResult.getScorePercentage()).append("%\n");
            reportPrompt.append("- Cấp độ đánh giá: ").append(latestResult.getEvaluatedLevel()).append("\n");
            reportPrompt.append("- Tóm tắt: ").append(latestResult.getEvaluationSummary()).append("\n");

            try {
                List<?> skillGaps = objectMapper.readValue(latestResult.getSkillGapsJson(), List.class);
                reportPrompt.append("\n### Các kỹ năng cần cải thiện:\n");
                for (Object gap : skillGaps) {
                    if (gap instanceof Map) {
                        Map<String, Object> gapMap = (Map<String, Object>) gap;
                        reportPrompt.append("- ").append(gapMap.get("skill"))
                                .append(": ").append(gapMap.get("description")).append("\n");
                    }
                }

                List<?> strengths = objectMapper.readValue(latestResult.getStrengthsJson(), List.class);
                reportPrompt.append("\n### Điểm mạnh:\n");
                for (Object strength : strengths) {
                    if (strength instanceof Map) {
                        Map<String, Object> strengthMap = (Map<String, Object>) strength;
                        reportPrompt.append("- ").append(strengthMap.get("skill"))
                                .append(": ").append(strengthMap.get("description")).append("\n");
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse skill gaps/strengths", e);
            }
        }

        reportPrompt.append("\n## Các mốc đã hoàn thành:\n");
        for (JourneyProgress p : progressList) {
            if (Boolean.TRUE.equals(p.getIsCompleted())) {
                reportPrompt.append("- [x] ").append(p.getMilestone());
                if (p.getCompletedAt() != null) {
                    reportPrompt.append(" (").append(p.getCompletedAt()).append(")");
                }
                reportPrompt.append("\n");
            }
        }

        reportPrompt.append("\n## Yêu cầu báo cáo:\n");
        reportPrompt.append("Hãy tạo báo cáo chi tiết với:\n");
        reportPrompt.append("1. Tổng quan tiến độ học tập\n");
        reportPrompt.append("2. Phân tích điểm mạnh chi tiết\n");
        reportPrompt.append("3. Phân tích kỹ năng cần cải thiện với ưu tiên\n");
        reportPrompt.append("4. Lộ trình học tập khuyến nghị (ngắn/trung/dài hạn)\n");
        reportPrompt.append("5. Tài nguyên học tập cụ thể\n");
        reportPrompt.append("6. Động lực và lời khuyên cá nhân hóa\n");
        reportPrompt.append("7. Dự đoán thời gian đạt được mục tiêu\n");

        String aiReport = getChatClient()
                .prompt()
                .user(reportPrompt.toString())
                .call()
                .content();

        journey.setAiSummaryReport(aiReport);
        journey.setLastActivityAt(Instant.now());
        journey = journeyRepository.save(journey);

        return mapToJourneySummary(journey);
    }

    @Override
    public JourneySummaryResponse getCurrentJourneyProgress(User user) {
        List<Journey> activeJourneys = journeyRepository.findActiveJourneysByUser(user);
        if (activeJourneys.isEmpty()) {
            return null;
        }
        return mapToJourneySummary(activeJourneys.get(0));
    }

    // ==================== Helper Methods ====================

    private JourneySummaryResponse mapToJourneySummary(Journey journey) {
        List<JourneyProgress> progressList = journeyProgressRepository.findByJourney(journey);
        List<JourneySummaryResponse.MilestoneResponse> milestones = progressList.stream()
                .map(p -> JourneySummaryResponse.MilestoneResponse.builder()
                        .milestone(p.getMilestone().name())
                        .isCompleted(p.getIsCompleted())
                        .completedAt(p.getCompletedAt())
                        .build())
                .collect(Collectors.toList());

        // Get latest test result
        TestResult latestResult = testResultRepository.findTopByJourneyOrderByCreatedAtDesc(journey).orElse(null);
        JourneySummaryResponse.TestResultSummaryResponse testResultSummary = null;
        if (latestResult != null) {
            try {
                List<?> skillGaps = null;
                List<?> strengths = null;
                if (latestResult.getSkillGapsJson() != null) {
                    skillGaps = objectMapper.readValue(latestResult.getSkillGapsJson(), List.class);
                }
                if (latestResult.getStrengthsJson() != null) {
                    strengths = objectMapper.readValue(latestResult.getStrengthsJson(), List.class);
                }
                testResultSummary = JourneySummaryResponse.TestResultSummaryResponse.builder()
                        .resultId(latestResult.getId())
                        .scorePercentage(latestResult.getScorePercentage())
                        .evaluatedLevel(latestResult.getEvaluatedLevel())
                        .skillGapsCount(skillGaps != null ? skillGaps.size() : 0)
                        .strengthsCount(strengths != null ? strengths.size() : 0)
                        .evaluatedAt(latestResult.getEvaluatedAt())
                        .build();
            } catch (Exception e) {
                log.warn("Failed to parse test result JSON", e);
            }
        }

        // Get latest assessment test
        AssessmentTest latestTest = assessmentTestRepository.findTopByJourneyOrderByCreatedAtDesc(journey).orElse(null);
        long assessmentAttemptCount = assessmentTestRepository.countByJourney(journey);
        int remainingAssessmentRetakes = calculateRemainingAssessmentRetakes(assessmentAttemptCount);

        return JourneySummaryResponse.builder()
                .id(journey.getId())
                .type(journey.getType())
                .domain(journey.getDomain() != null ? journey.getDomain() : "Unknown")
                .subCategory(journey.getSubCategory())
                .jobRole(journey.getJobRole())
                .goal(journey.getGoal() != null ? journey.getGoal() : "Unknown")
                .status(journey.getStatus() != null ? journey.getStatus() : Journey.JourneyStatus.NOT_STARTED)
                .currentLevel(journey.getCurrentLevel())
                .assessmentTestId(latestTest != null ? latestTest.getId() : null)
                .assessmentTestTitle(latestTest != null ? latestTest.getTitle() : null)
                .assessmentTestQuestionCount(latestTest != null ? latestTest.getQuestionCount() : null)
                .assessmentTestStatus(latestTest != null ? latestTest.getStatus().name() : null)
                .assessmentAttemptCount((int) assessmentAttemptCount)
                .maxAssessmentAttempts(MAX_ASSESSMENT_ATTEMPTS)
                .remainingAssessmentRetakes(remainingAssessmentRetakes)
                .progressPercentage(journey.getProgressPercentage() != null ? journey.getProgressPercentage() : 0)
                .aiSummaryReport(journey.getAiSummaryReport())
                .startedAt(journey.getStartedAt())
                .completedAt(journey.getCompletedAt())
                .lastActivityAt(journey.getLastActivityAt())
                .createdAt(journey.getCreatedAt())
                .roadmapSessionId(journey.getRoadmapSessionId())
                .totalNodesCompleted(calculateNodesCompleted(journey))
                .milestones(milestones)
                .latestTestResult(testResultSummary)
                .build();
    }

    private TestResultResponse mapToTestResultResponse(TestResult result, Integer totalQuestions) {
        EvaluationSnapshot snapshot = buildSnapshotFromStoredResult(result, totalQuestions);
        return TestResultResponse.builder()
                .id(result.getId())
                .journeyId(result.getJourney().getId())
                .assessmentTestId(result.getAssessmentTest().getId())
                .scorePercentage(result.getScorePercentage())
                .evaluatedLevel(result.getEvaluatedLevel())
                .skillGapsJson(result.getSkillGapsJson())
                .strengthsJson(result.getStrengthsJson())
                .evaluationSummary(result.getEvaluationSummary())
                .detailedFeedback(result.getDetailedFeedback())
                .highlightKeywordsJson(result.getHighlightKeywordsJson())
                .userAnswersJson(result.getUserAnswersJson())
                .correctAnswersJson(result.getCorrectAnswersJson())
                .evaluatedAt(result.getEvaluatedAt())
                .createdAt(result.getCreatedAt())
                .totalQuestions(snapshot.totalQuestions)
                .correctAnswers(snapshot.correctAnswers)
                .incorrectAnswers(snapshot.incorrectAnswers)
                .answeredQuestions(snapshot.answeredQuestions)
                .scoreBand(snapshot.scoreBand)
                .recommendationMode(snapshot.recommendationMode)
                .assessmentConfidence(snapshot.assessmentConfidence)
                .reassessmentRecommended(snapshot.reassessmentRecommended)
                .build();
    }

    private Map<Long, String> normalizeUserAnswers(Map<Long, Object> answers) {
        if (answers == null || answers.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> normalized = new HashMap<>();
        for (Map.Entry<Long, Object> entry : answers.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(entry.getKey(), Optional.ofNullable(toText(entry.getValue())).orElse("").trim());
        }
        return normalized;
    }

    private List<QuestionEvaluation> evaluateQuestionAnswers(List<Map<String, Object>> questions, Map<Long, String> userAnswers) {
        if (questions == null || questions.isEmpty()) {
            return Collections.emptyList();
        }

        List<QuestionEvaluation> evaluations = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> question = questions.get(i);
            Long questionId = toLongValue(question.get("questionId"), (long) (i + 1));
            List<String> options = extractOptions(question.get("options"));

            String correctAnswer = Optional.ofNullable(toText(question.get("correctAnswer"))).orElse("").trim();
            String userAnswer = Optional.ofNullable(userAnswers.get(questionId)).orElse("").trim();

            String correctOptionKey = extractOptionKey(correctAnswer, options);
            String userOptionKey = extractOptionKey(userAnswer, options);

            String correctText = normalizeAnswerContent(resolveAnswerText(correctAnswer, correctOptionKey, options));
            String userText = normalizeAnswerContent(resolveAnswerText(userAnswer, userOptionKey, options));

            boolean isCorrect = false;
            if (!correctOptionKey.isBlank() && !userOptionKey.isBlank()) {
                isCorrect = correctOptionKey.equals(userOptionKey);
            } else if (!correctText.isBlank() && !userText.isBlank()) {
                isCorrect = correctText.equals(userText);
            }

            evaluations.add(new QuestionEvaluation(
                    questionId,
                    normalizeSkillArea(question.get("skillArea")),
                    normalizeDifficulty(question.get("difficulty")),
                    userAnswer,
                    correctAnswer,
                    isCorrect
            ));
        }

        return evaluations;
    }

    private EvaluationSnapshot createEvaluationSnapshot(int totalQuestions, int answeredQuestions, int correctAnswers, int scorePercentage) {
        int safeTotal = Math.max(0, totalQuestions);
        int safeAnswered = Math.max(0, Math.min(answeredQuestions, safeTotal));
        int safeCorrect = Math.max(0, Math.min(correctAnswers, safeTotal));
        int safeIncorrect = Math.max(0, safeTotal - safeCorrect);
        int safeScore = clampScore(scorePercentage);

        String scoreBand = determineScoreBand(safeScore);
        String recommendationMode = determineRecommendationMode(safeScore, safeTotal, safeCorrect);
        int confidence = calculateAssessmentConfidence(safeTotal, safeAnswered, safeCorrect);
        boolean reassessmentRecommended = shouldRecommendReassessment(safeTotal, safeAnswered, safeCorrect);

        return new EvaluationSnapshot(
                safeTotal,
                safeAnswered,
                safeCorrect,
                safeIncorrect,
                scoreBand,
                recommendationMode,
                confidence,
                reassessmentRecommended
        );
    }

    private EvaluationSnapshot buildSnapshotFromStoredResult(TestResult result, Integer totalQuestionsOverride) {
        List<Map<String, Object>> storedQuestions = parseQuestionsJson(result.getCorrectAnswersJson());
        Map<Long, String> storedAnswers = parseUserAnswersJson(result.getUserAnswersJson());

        if (!storedQuestions.isEmpty()) {
            List<QuestionEvaluation> evaluations = evaluateQuestionAnswers(storedQuestions, storedAnswers);
            int total = totalQuestionsOverride != null && totalQuestionsOverride > 0
                    ? totalQuestionsOverride
                    : evaluations.size();
            int answered = (int) evaluations.stream()
                    .filter(q -> q.userAnswer != null && !q.userAnswer.isBlank())
                    .count();
            int correct = (int) evaluations.stream().filter(q -> q.correct).count();
            int score = result.getScorePercentage() != null
                    ? clampScore(result.getScorePercentage())
                    : calculateScorePercentage(correct, total);
            return createEvaluationSnapshot(total, answered, correct, score);
        }

        int total = totalQuestionsOverride != null && totalQuestionsOverride > 0
                ? totalQuestionsOverride
                : storedAnswers.size();
        int score = clampScore(result.getScorePercentage());
        int correct = total > 0 ? (int) Math.round((score / 100.0) * total) : 0;
        int answered = Math.min(total, storedAnswers.size());
        return createEvaluationSnapshot(total, answered, correct, score);
    }

    private List<Map<String, Object>> buildSkillGaps(List<QuestionEvaluation> evaluations, String domain, String recommendationMode) {
        List<Map<String, Object>> skillGaps = new ArrayList<>();
        Map<String, List<QuestionEvaluation>> grouped = groupBySkillArea(evaluations);

        grouped.entrySet().stream()
                .sorted(Comparator.comparingDouble(entry -> {
                    long total = entry.getValue().size();
                    long correct = entry.getValue().stream().filter(q -> q.correct).count();
                    return total == 0 ? 0 : ((double) correct / total);
                }))
                .forEach(entry -> {
                    List<QuestionEvaluation> items = entry.getValue();
                    long total = items.size();
                    long correct = items.stream().filter(q -> q.correct).count();
                    long incorrect = total - correct;
                    if (total == 0 || incorrect == 0) {
                        return;
                    }

                    double accuracy = (double) correct / total;
                    String priority = accuracy <= 0.35 ? "high" : "medium";
                    String questionRefs = formatQuestionRefs(items.stream()
                            .filter(q -> !q.correct)
                            .map(q -> q.questionId)
                            .collect(Collectors.toList()));

                    Map<String, Object> gap = new LinkedHashMap<>();
                    gap.put("skill", entry.getKey());
                    gap.put("description", String.format(
                            "Nhóm %s đúng %d/%d câu, cần cải thiện ở %s.",
                            entry.getKey(),
                            correct,
                            total,
                            questionRefs
                    ));
                    gap.put("priority", priority);
                    gap.put("howToImprove", String.format(
                            "Ôn lại kiến thức nền %s và luyện thêm bài tập tình huống trước khi làm lại bài đánh giá.",
                            entry.getKey()
                    ));
                    skillGaps.add(gap);
                });

        if ("FROM_ZERO".equals(recommendationMode)) {
            Map<String, Object> zeroBaseGap = new LinkedHashMap<>();
            zeroBaseGap.put("skill", "Nền tảng " + (domain != null && !domain.isBlank() ? domain : "chuyên ngành"));
            zeroBaseGap.put("description", "Kết quả cho thấy bạn cần xây lại kiến thức nền từ đầu để tránh hổng kiến thức cốt lõi.");
            zeroBaseGap.put("priority", "high");
            zeroBaseGap.put("howToImprove", "Đi theo lộ trình từ zero: học khái niệm cốt lõi, làm bài cơ bản, sau đó mới tăng dần độ khó.");
            skillGaps.add(0, zeroBaseGap);
        }

        return skillGaps;
    }

    private List<Map<String, Object>> buildStrengths(List<QuestionEvaluation> evaluations) {
        List<Map<String, Object>> strengths = new ArrayList<>();
        Map<String, List<QuestionEvaluation>> grouped = groupBySkillArea(evaluations);

        grouped.entrySet().stream()
                .sorted((a, b) -> {
                    double accA = calculateAccuracy(a.getValue());
                    double accB = calculateAccuracy(b.getValue());
                    return Double.compare(accB, accA);
                })
                .forEach(entry -> {
                    List<QuestionEvaluation> items = entry.getValue();
                    long total = items.size();
                    long correct = items.stream().filter(q -> q.correct).count();
                    if (total == 0 || correct == 0) {
                        return;
                    }

                    double accuracy = (double) correct / total;
                    if (accuracy < 0.65) {
                        return;
                    }

                    String questionRefs = formatQuestionRefs(items.stream()
                            .filter(q -> q.correct)
                            .map(q -> q.questionId)
                            .collect(Collectors.toList()));

                    Map<String, Object> strength = new LinkedHashMap<>();
                    strength.put("skill", entry.getKey());
                    strength.put("description", String.format(
                            "Thể hiện tốt nhóm %s với %d/%d câu đúng (%s).",
                            entry.getKey(),
                            correct,
                            total,
                            questionRefs
                    ));
                    strength.put("level", accuracy >= 0.9 ? "vung" : "can_cung_co");
                    strengths.add(strength);
                });

        return strengths;
    }

    private Map<String, List<QuestionEvaluation>> groupBySkillArea(List<QuestionEvaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            return Collections.emptyMap();
        }
        return evaluations.stream()
                .collect(Collectors.groupingBy(
                        q -> q.skillArea != null && !q.skillArea.isBlank() ? q.skillArea : "Kỹ năng tổng quát",
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private List<Map<String, Object>> extractInsightList(Object rawValue, boolean skillGap) {
        if (!(rawValue instanceof List<?> rawList) || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> insights = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> insight = new LinkedHashMap<>();
                String skill = Optional.ofNullable(toText(rawMap.get("skill"))).orElse("").trim();
                if (skill.isBlank()) {
                    continue;
                }

                insight.put("skill", skill);
                putIfPresent(insight, "description", Optional.ofNullable(toText(rawMap.get("description"))).orElse("").trim());
                if (skillGap) {
                    putIfPresent(insight, "priority", Optional.ofNullable(toText(rawMap.get("priority"))).orElse("").trim());
                    putIfPresent(insight, "howToImprove", Optional.ofNullable(toText(rawMap.get("howToImprove"))).orElse("").trim());
                } else {
                    putIfPresent(insight, "level", Optional.ofNullable(toText(rawMap.get("level"))).orElse("").trim());
                }
                insights.add(insight);
                continue;
            }

            String text = Optional.ofNullable(toText(item)).orElse("").trim();
            if (!text.isBlank()) {
                Map<String, Object> insight = new LinkedHashMap<>();
                insight.put("skill", text);
                insight.put("description", text);
                insights.add(insight);
            }
        }

        return insights;
    }

    private List<Map<String, Object>> mergeSkillGapInsights(List<Map<String, Object>> deterministic,
                                                            List<Map<String, Object>> aiGenerated) {
        return mergeInsightLists(deterministic, aiGenerated, true);
    }

    private List<Map<String, Object>> mergeStrengthInsights(List<Map<String, Object>> deterministic,
                                                            List<Map<String, Object>> aiGenerated) {
        return mergeInsightLists(deterministic, aiGenerated, false);
    }

    private List<Map<String, Object>> mergeInsightLists(List<Map<String, Object>> deterministic,
                                                        List<Map<String, Object>> aiGenerated,
                                                        boolean skillGap) {
        LinkedHashMap<String, Map<String, Object>> merged = new LinkedHashMap<>();

        for (Map<String, Object> item : Optional.ofNullable(deterministic).orElse(Collections.emptyList())) {
            Map<String, Object> normalized = normalizeInsightMap(item, skillGap);
            if (!normalized.isEmpty()) {
                merged.put(normalizeInsightKey(normalized), normalized);
            }
        }

        for (Map<String, Object> item : Optional.ofNullable(aiGenerated).orElse(Collections.emptyList())) {
            Map<String, Object> normalized = normalizeInsightMap(item, skillGap);
            if (normalized.isEmpty()) {
                continue;
            }

            String key = normalizeInsightKey(normalized);
            Map<String, Object> existing = merged.get(key);
            if (existing == null) {
                merged.put(key, normalized);
                continue;
            }

            putIfPresent(existing, "description", firstNonBlankText(normalized, "description"));
            if (skillGap) {
                putIfPresent(existing, "priority", firstNonBlankText(normalized, "priority"));
                putIfPresent(existing, "howToImprove", firstNonBlankText(normalized, "howToImprove"));
            } else {
                putIfPresent(existing, "level", firstNonBlankText(normalized, "level"));
            }
        }

        return new ArrayList<>(merged.values());
    }

    private Map<String, Object> normalizeInsightMap(Map<String, Object> source, boolean skillGap) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }

        String skill = firstNonBlankText(source, "skill");
        if (skill == null || skill.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("skill", skill.trim());
        putIfPresent(normalized, "description", firstNonBlankText(source, "description"));
        if (skillGap) {
            putIfPresent(normalized, "priority", firstNonBlankText(source, "priority"));
            putIfPresent(normalized, "howToImprove", firstNonBlankText(source, "howToImprove"));
        } else {
            putIfPresent(normalized, "level", firstNonBlankText(source, "level"));
        }
        return normalized;
    }

    private String normalizeInsightKey(Map<String, Object> item) {
        return firstNonBlankText(item, "skill")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String firstNonBlankText(Map<String, Object> source, String... keys) {
        if (source == null || source.isEmpty() || keys == null) {
            return null;
        }

        for (String key : keys) {
            String value = Optional.ofNullable(toText(source.get(key))).orElse("").trim();
            if (!value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (target == null || key == null || value == null || value.isBlank()) {
            return;
        }
        target.put(key, value.trim());
    }

    private List<String> extractStringList(Object rawValue) {
        if (!(rawValue instanceof List<?> rawList) || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Object item : rawList) {
            String text = Optional.ofNullable(toText(item)).orElse("").trim();
            if (!text.isBlank()) {
                values.add(text);
            }
        }
        return new ArrayList<>(values);
    }

    private String buildDeterministicDetailedFeedback(String domain,
                                                      int scorePercentage,
                                                      Journey.SkillLevel evaluatedLevel,
                                                      EvaluationSnapshot snapshot,
                                                      List<Map<String, Object>> skillGaps,
                                                      List<Map<String, Object>> strengths,
                                                      List<String> recommendations) {
        String domainText = domain != null && !domain.isBlank() ? domain : "lĩnh vực đã chọn";
        StringBuilder feedback = new StringBuilder();

        feedback.append("## Bức tranh hiện tại\n");
        feedback.append(String.format("- Bạn đang ở mức **%s** trong nhóm năng lực **%s**.\n",
                toSkillLevelLabel(evaluatedLevel),
                domainText));
        feedback.append(String.format("- Kết quả hiện tại là **%d%%** với **%d/%d câu đúng**.\n",
                scorePercentage,
                snapshot.correctAnswers,
                Math.max(snapshot.totalQuestions, 1)));
        feedback.append(String.format("- Hướng học phù hợp lúc này là **%s**.\n",
                toRecommendationModeLabel(snapshot.recommendationMode)));
        if (snapshot.reassessmentRecommended) {
            feedback.append("- Hệ thống khuyến nghị bạn làm lại quiz sau vòng học nền tảng đầu tiên để đo lại tiến bộ.\n");
        }

        feedback.append("\n## Điểm mạnh nổi bật\n");
        appendInsightSection(
                feedback,
                strengths,
                false,
                "- Chưa có nhóm điểm mạnh nào đủ nổi bật. Hãy tiếp tục luyện thêm để hệ thống nhận diện rõ hơn.\n");

        feedback.append("\n## Kỹ năng cần ưu tiên\n");
        appendInsightSection(
                feedback,
                skillGaps,
                true,
                "- Chưa xác định được lỗ hổng quá lớn, nhưng bạn vẫn nên tiếp tục luyện đều để củng cố nền tảng.\n");

        feedback.append("\n## Hành động đề xuất\n");
        LinkedHashSet<String> actions = new LinkedHashSet<>();
        for (Map<String, Object> skillGap : Optional.ofNullable(skillGaps).orElse(Collections.emptyList())) {
            String action = firstNonBlankText(skillGap, "howToImprove");
            if (action != null && !action.isBlank()) {
                actions.add(action);
            }
        }
        actions.addAll(Optional.ofNullable(recommendations).orElse(Collections.emptyList()));

        if (actions.isEmpty()) {
            actions.add("Chọn một kỹ năng yếu nhất và dành 30-45 phút mỗi ngày để ôn lại phần nền tảng.");
            actions.add("Làm thêm bài tập tình huống tương tự để tăng khả năng áp dụng thực tế.");
            actions.add("Sau 1-2 tuần, hãy quay lại quiz để đo đúng mức tiến bộ của bạn.");
        }

        int actionCount = 0;
        for (String action : actions) {
            if (action == null || action.isBlank()) {
                continue;
            }
            feedback.append("- ").append(action.trim()).append("\n");
            actionCount++;
            if (actionCount >= 5) {
                break;
            }
        }

        feedback.append("\n## Lời nhắn từ Meowl\n");
        if (scorePercentage >= 80) {
            feedback.append("Bạn đang đi rất đúng hướng. Chỉ cần tiếp tục giữ nhịp học và đào sâu đúng chủ điểm, tốc độ tiến bộ sẽ rất rõ.");
        } else if (scorePercentage >= 50) {
            feedback.append("Bạn đã có nền tảng để bứt lên. Hãy tập trung đúng điểm yếu và duy trì nhịp học đều tay, kết quả sẽ cải thiện nhanh.");
        } else {
            feedback.append("Đừng nản. Kết quả này rất hữu ích vì nó chỉ ra chính xác điểm bắt đầu để bạn xây một roadmap hiệu quả hơn.");
        }

        return feedback.toString().trim();
    }

    private void appendInsightSection(StringBuilder builder,
                                      List<Map<String, Object>> insights,
                                      boolean skillGap,
                                      String emptyMessage) {
        List<Map<String, Object>> items = Optional.ofNullable(insights).orElse(Collections.emptyList());
        if (items.isEmpty()) {
            builder.append(emptyMessage);
            return;
        }

        int count = 0;
        for (Map<String, Object> item : items) {
            String skill = firstNonBlankText(item, "skill");
            if (skill == null || skill.isBlank()) {
                continue;
            }

            builder.append("- **").append(skill.trim()).append("**");
            String description = firstNonBlankText(item, "description");
            if (description != null && !description.isBlank()) {
                builder.append(": ").append(description.trim());
            }

            if (skillGap) {
                String priority = firstNonBlankText(item, "priority");
                if (priority != null && !priority.isBlank()) {
                    builder.append(" _(ưu tiên ").append(toPriorityLabel(priority)).append(")_");
                }
            } else {
                String level = firstNonBlankText(item, "level");
                if (level != null && !level.isBlank()) {
                    builder.append(" _(mức ").append(toStrengthLevelLabel(level)).append(")_");
                }
            }

            builder.append("\n");
            count++;
            if (count >= 5) {
                break;
            }
        }
    }

    private List<String> buildHighlightKeywords(List<String> aiKeywords,
                                                List<Map<String, Object>> skillGaps,
                                                List<Map<String, Object>> strengths,
                                                String domain,
                                                Journey.SkillLevel evaluatedLevel,
                                                String recommendationMode) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();

        keywords.addAll(Optional.ofNullable(aiKeywords).orElse(Collections.emptyList()).stream()
                .map(item -> item == null ? "" : item.trim())
                .filter(item -> !item.isBlank())
                .collect(Collectors.toList()));

        if (domain != null && !domain.isBlank()) {
            keywords.add(domain.trim());
        }
        keywords.add(toSkillLevelLabel(evaluatedLevel));
        keywords.add(toRecommendationModeLabel(recommendationMode));

        for (Map<String, Object> item : Optional.ofNullable(strengths).orElse(Collections.emptyList())) {
            String skill = firstNonBlankText(item, "skill");
            if (skill != null && !skill.isBlank()) {
                keywords.add(skill.trim());
            }
            if (keywords.size() >= 12) {
                break;
            }
        }

        for (Map<String, Object> item : Optional.ofNullable(skillGaps).orElse(Collections.emptyList())) {
            String skill = firstNonBlankText(item, "skill");
            if (skill != null && !skill.isBlank()) {
                keywords.add(skill.trim());
            }
            if (keywords.size() >= 12) {
                break;
            }
        }

        return keywords.stream().limit(12).collect(Collectors.toList());
    }

    private String combineDetailedFeedback(String deterministicFeedback, String aiDetailedFeedback) {
        if (aiDetailedFeedback == null || aiDetailedFeedback.isBlank()) {
            return deterministicFeedback;
        }

        String normalizedAi = aiDetailedFeedback.trim();
        if (normalizedAi.equalsIgnoreCase(deterministicFeedback.trim())) {
            return deterministicFeedback;
        }

        if (normalizedAi.contains("## ")) {
            return normalizedAi;
        }

        return deterministicFeedback + "\n\n## Góc nhìn bổ sung từ AI\n" + normalizedAi;
    }

    private String toSkillLevelLabel(Journey.SkillLevel level) {
        if (level == null) {
            return "Chưa xác định";
        }
        return switch (level) {
            case BEGINNER -> "Mới bắt đầu";
            case ELEMENTARY -> "Sơ cấp";
            case INTERMEDIATE -> "Trung cấp";
            case ADVANCED -> "Nâng cao";
            case EXPERT -> "Chuyên sâu";
        };
    }

    private String toRecommendationModeLabel(String recommendationMode) {
        if (recommendationMode == null || recommendationMode.isBlank()) {
            return "Lộ trình tiêu chuẩn";
        }

        return switch (recommendationMode) {
            case "FROM_ZERO" -> "Lộ trình từ zero";
            case "FOUNDATION" -> "Lộ trình nền tảng";
            case "STANDARD" -> "Lộ trình tiêu chuẩn";
            case "ADVANCED" -> "Lộ trình nâng cao";
            case "FAST_TRACK" -> "Lộ trình tăng tốc";
            default -> "Lộ trình tiêu chuẩn";
        };
    }

    private String toPriorityLabel(String priority) {
        if (priority == null || priority.isBlank()) {
            return "vừa";
        }

        return switch (priority.toLowerCase(Locale.ROOT)) {
            case "high" -> "cao";
            case "low" -> "thấp";
            default -> "vừa";
        };
    }

    private String toStrengthLevelLabel(String level) {
        if (level == null || level.isBlank()) {
            return "ổn";
        }

        return switch (level.toLowerCase(Locale.ROOT)) {
            case "vung" -> "vững";
            case "can_cung_co" -> "cần tiếp tục duy trì";
            default -> "ổn";
        };
    }

    private String buildDeterministicSummary(String domain, int scorePercentage, Journey.SkillLevel evaluatedLevel,
                                             int totalQuestions, int correctAnswers, int incorrectAnswers,
                                             String recommendationMode, boolean reassessmentRecommended) {
        String domainText = domain != null && !domain.isBlank() ? domain : "ngành đã chọn";
        StringBuilder summary = new StringBuilder();
        summary.append(String.format(
                "Kết quả khách quan: %d%% (%d/%d câu đúng) trong lĩnh vực %s. Mức hiện tại: %s.",
                scorePercentage,
                correctAnswers,
                Math.max(totalQuestions, 1),
                domainText,
                evaluatedLevel
        ));

        if ("FROM_ZERO".equals(recommendationMode)) {
            summary.append(" Hệ thống đề xuất lộ trình từ zero để xây nền tảng trước khi học nâng cao.");
        } else if ("FOUNDATION".equals(recommendationMode)) {
            summary.append(" Hệ thống đề xuất lộ trình nền tảng, tập trung củng cố kiến thức lõi và kỹ năng thực hành cơ bản.");
        } else if ("STANDARD".equals(recommendationMode)) {
            summary.append(" Hệ thống đề xuất lộ trình tiêu chuẩn, cân bằng giữa củng cố nền tảng và bài tập ứng dụng.");
        } else if ("ADVANCED".equals(recommendationMode)) {
            summary.append(" Hệ thống đề xuất lộ trình nâng cao, tăng cường bài tập tình huống và kỹ thuật chuyên sâu.");
        } else {
            summary.append(" Hệ thống đề xuất lộ trình tăng tốc với các bài tập chuyên sâu và dự án thực tế.");
        }

        if (incorrectAnswers > 0) {
            summary.append(String.format(" Bạn còn %d câu sai cần ưu tiên cải thiện trong các vòng học đầu.", incorrectAnswers));
        }

        if (reassessmentRecommended) {
            summary.append(" Khuyến nghị làm lại bài đánh giá sau giai đoạn học đầu để hiệu chỉnh lộ trình chính xác hơn.");
        }

        return summary.toString();
    }

    private String combineSummaries(String deterministicSummary, String aiSummary) {
        if (aiSummary == null || aiSummary.isBlank()) {
            return deterministicSummary;
        }
        String normalizedAi = aiSummary.trim();
        if (normalizedAi.equalsIgnoreCase(deterministicSummary.trim())) {
            return deterministicSummary;
        }
        return deterministicSummary + "\n\nPhân tích bổ sung từ AI: " + normalizedAi;
    }

    private int calculateScorePercentage(int correctAnswers, int totalQuestions) {
        if (totalQuestions <= 0) {
            return 0;
        }
        return (int) Math.round((correctAnswers * 100.0) / totalQuestions);
    }

    private Journey.SkillLevel determineSkillLevel(int scorePercentage) {
        int score = clampScore(scorePercentage);
        if (score <= 40) {
            return Journey.SkillLevel.BEGINNER;
        }
        if (score <= 70) {
            return Journey.SkillLevel.INTERMEDIATE;
        }
        if (score <= 85) {
            return Journey.SkillLevel.ADVANCED;
        }
        return Journey.SkillLevel.EXPERT;
    }

    private String determineScoreBand(int scorePercentage) {
        int score = clampScore(scorePercentage);
        if (score <= 20) {
            return "ZERO_BASE";
        }
        if (score <= 45) {
            return "FOUNDATION";
        }
        if (score <= 70) {
            return "CORE";
        }
        if (score <= 85) {
            return "ADVANCED";
        }
        return "EXPERT";
    }

    private String determineRecommendationMode(int scorePercentage, int totalQuestions, int correctAnswers) {
        if (totalQuestions > 0 && correctAnswers == 0) {
            return "FROM_ZERO";
        }
        if (scorePercentage <= 45) {
            return "FOUNDATION";
        }
        if (scorePercentage <= 70) {
            return "STANDARD";
        }
        if (scorePercentage <= 90) {
            return "ADVANCED";
        }
        return "FAST_TRACK";
    }

    private boolean shouldRecommendReassessment(int totalQuestions, int answeredQuestions, int correctAnswers) {
        if (totalQuestions <= 0) {
            return true;
        }
        if (answeredQuestions < Math.max(3, (int) Math.ceil(totalQuestions * 0.6))) {
            return true;
        }
        return correctAnswers == 0;
    }

    private int calculateAssessmentConfidence(int totalQuestions, int answeredQuestions, int correctAnswers) {
        if (totalQuestions <= 0) {
            return 40;
        }

        double coverage = answeredQuestions / (double) totalQuestions;
        double sampleQuality = Math.min(totalQuestions, 30) / 30.0;

        int confidence = (int) Math.round(45 + (coverage * 35) + (sampleQuality * 20));
        if (correctAnswers == 0) {
            confidence = Math.max(55, confidence - 10);
        }

        return Math.max(40, Math.min(98, confidence));
    }

    private List<Map<String, Object>> parseQuestionsJson(String questionsJson) {
        if (questionsJson == null || questionsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(questionsJson, List.class);
        } catch (Exception e) {
            log.warn("Failed to parse stored question payload for test result response", e);
            return Collections.emptyList();
        }
    }

    private Map<Long, String> parseUserAnswersJson(String userAnswersJson) {
        if (userAnswersJson == null || userAnswersJson.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(userAnswersJson, Map.class);
            Map<Long, String> parsed = new HashMap<>();
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                try {
                    Long key = Long.valueOf(entry.getKey());
                    parsed.put(key, Optional.ofNullable(toText(entry.getValue())).orElse(""));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed key and continue parsing remaining answers
                }
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to parse stored user answers for test result response", e);
            return Collections.emptyMap();
        }
    }

    private List<String> extractOptions(Object rawOptions) {
        if (!(rawOptions instanceof List<?> rawList)) {
            return Collections.emptyList();
        }
        return rawList.stream()
                .map(this::toText)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(option -> !option.isBlank())
                .collect(Collectors.toList());
    }

    private String extractOptionKey(String answer, List<String> options) {
        if (answer == null || answer.isBlank()) {
            return "";
        }

        var matcher = OPTION_PREFIX_PATTERN.matcher(answer);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase(Locale.ROOT);
        }

        String normalized = normalizeAnswerContent(answer);
        if (normalized.isBlank()) {
            return "";
        }

        for (int i = 0; i < options.size(); i++) {
            String normalizedOption = normalizeAnswerContent(options.get(i));
            if (!normalizedOption.isBlank() && normalizedOption.equals(normalized)) {
                return String.valueOf((char) ('A' + i));
            }
        }

        return "";
    }

    private String resolveAnswerText(String rawAnswer, String optionKey, List<String> options) {
        if (optionKey != null && !optionKey.isBlank() && optionKey.length() == 1) {
            int optionIndex = optionKey.charAt(0) - 'A';
            if (optionIndex >= 0 && optionIndex < options.size()) {
                return removeOptionPrefix(options.get(optionIndex));
            }
        }
        return removeOptionPrefix(rawAnswer);
    }

    private String normalizeAnswerContent(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = removeOptionPrefix(value)
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
        return normalized;
    }

    private String removeOptionPrefix(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceFirst("^\\s*[A-Da-d]\\s*[\\.:\\)\\-]\\s*", "").trim();
    }

    private String normalizeSkillArea(Object value) {
        String text = Optional.ofNullable(toText(value)).orElse("").trim();
        return text.isBlank() ? "Kỹ năng tổng quát" : text;
    }

    private String normalizeDifficulty(Object value) {
        String text = Optional.ofNullable(toText(value)).orElse("").trim();
        return text.isBlank() ? "unknown" : text;
    }

    private Long toLongValue(Object value, Long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.valueOf(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String toText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        return String.valueOf(value);
    }

    private int clampScore(Integer scorePercentage) {
        if (scorePercentage == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, scorePercentage));
    }

    private String formatQuestionRefs(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return "không xác định";
        }
        return questionIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .map(id -> "Q" + id)
                .collect(Collectors.joining(", "));
    }

    private double calculateAccuracy(List<QuestionEvaluation> evaluations) {
        if (evaluations == null || evaluations.isEmpty()) {
            return 0;
        }
        long correct = evaluations.stream().filter(q -> q.correct).count();
        return (double) correct / evaluations.size();
    }

    private int calculateNodesCompleted(Journey journey) {
        return journey.getProgressPercentage() / 10;
    }

    private String convertRequestToJson(StartJourneyRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.error("Failed to convert request to JSON", e);
            return "{}";
        }
    }

    private String buildJourneyTitle(StartJourneyRequest request) {
        String domain = request.getDomain() != null ? request.getDomain().trim() : "";
        String subCategory = request.getSubCategory() != null ? request.getSubCategory().trim() : "";
        String jobRole = request.getJobRole() != null ? request.getJobRole().trim() : "";

        if (!jobRole.isBlank()) {
            return String.format("Lộ trình %s - %s", domain, jobRole);
        }
        if (!subCategory.isBlank()) {
            return String.format("Lộ trình %s - %s", domain, subCategory);
        }
        if (!domain.isBlank()) {
            return String.format("Lộ trình %s", domain);
        }
        return "Lộ trình học tập";
    }

    private String extractJsonFromResponse(String response) {
        String jsonStr = response.trim();
        if (jsonStr.startsWith("```json")) {
            jsonStr = jsonStr.substring(7);
        } else if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.substring(3);
        }
        if (jsonStr.endsWith("```")) {
            jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
        }
        return jsonStr.trim();
    }

    private StartJourneyRequest parseAssessmentData(String assessmentDataJson) {
        if (assessmentDataJson == null || assessmentDataJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(assessmentDataJson, StartJourneyRequest.class);
        } catch (Exception e) {
            log.warn("Failed to parse journey assessment data. Falling back to defaults.", e);
            return null;
        }
    }

    private GenerateRoadmapRequest buildRoadmapRequestFromEvaluation(
            Journey journey,
            StartJourneyRequest assessmentData,
            TestResult testResult,
            EvaluationSnapshot snapshot,
            List<Map<String, Object>> skillGaps,
            List<Map<String, Object>> strengths) {

        boolean careerJourney = isCareerJourney(journey);
        GenerateRoadmapRequest.RoadmapMode roadmapMode = careerJourney
                ? GenerateRoadmapRequest.RoadmapMode.CAREER_BASED
                : GenerateRoadmapRequest.RoadmapMode.SKILL_BASED;

        String domain = firstNonBlank(journey.getDomain(), "general");
        String target = careerJourney
                ? firstNonBlank(journey.getJobRole(), journey.getSubCategory(), domain + " role")
                : firstNonBlank(journey.getSubCategory(), journey.getJobRole(), domain + " fundamentals");

        String journeyGoal = firstNonBlank(
                journey.getGoal(),
                assessmentData != null ? assessmentData.getGoal() : null,
                "Improve practical capability");

        String experience = mapJourneyLevelToExperience(
                testResult.getEvaluatedLevel(),
                assessmentData != null ? assessmentData.getLevel() : null);

        String duration = resolveRoadmapDuration(
                assessmentData != null ? assessmentData.getDuration() : null,
                snapshot.recommendationMode);

        String dailyTime = resolveDailyLearningTime(
                assessmentData != null ? assessmentData.getDuration() : null,
                snapshot.recommendationMode);

        List<String> gapSkills = extractSkillNames(skillGaps);
        List<String> strengthSkills = extractSkillNames(strengths);
        List<String> improvementHints = extractImprovementHints(skillGaps);
        List<String> toolPreferences = resolveToolPreferences(assessmentData, strengthSkills);

        String goalForRoadmap = safeTruncate(
                String.format(
                        "Học %s trong lĩnh vực %s để đạt mục tiêu %s, dựa trên kết quả đánh giá %d%%.",
                        target,
                        domain,
                        journeyGoal,
                        clampScore(testResult.getScorePercentage())),
                500,
                "Học kỹ năng cốt lõi theo lộ trình cá nhân hóa");

        String finalObjective = safeTruncate(
                careerJourney
                        ? String.format("Be job-ready for %s with clear evidence projects", target)
                        : String.format("Reach practical proficiency in %s through structured practice", target),
                100,
                "Reach practical proficiency with project evidence");

        String background = safeTruncate(
                String.format(
                        "Assessment score=%d%%, level=%s, scoreBand=%s, recommendation=%s, strengths=%s, gaps=%s.",
                        clampScore(testResult.getScorePercentage()),
                        normalizeRoadmapLevel(testResult.getEvaluatedLevel()),
                        snapshot.scoreBand,
                        snapshot.recommendationMode,
                        strengthSkills.isEmpty() ? "none" : String.join(", ", strengthSkills),
                        gapSkills.isEmpty() ? "none" : String.join(", ", gapSkills)),
                150,
                "Assessment-based learning profile");

        String difficultyConcern = safeTruncate(
                improvementHints.isEmpty()
                        ? "Need guided progression with milestone checks."
                        : improvementHints.get(0),
                100,
                "Need guided progression with milestone checks.");

        String learningStyle = "hands-on project-based";
        if (assessmentData != null && assessmentData.getFocusAreas() != null && !assessmentData.getFocusAreas().isEmpty()) {
            String primaryFocus = assessmentData.getFocusAreas().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .findFirst()
                    .orElse("practical_skills");
            learningStyle = "hands-on with focus on " + primaryFocus.toLowerCase(Locale.ROOT);
        }

        GenerateRoadmapRequest.GenerateRoadmapRequestBuilder builder = GenerateRoadmapRequest.builder()
                .roadmapMode(roadmapMode)
                .roadmapType(careerJourney ? "career" : "skill")
                .goal(goalForRoadmap)
                .target(safeTruncate(target, 200, domain))
                .industry(safeTruncate(domain, 100, "general"))
                .finalObjective(finalObjective)
                .duration(safeTruncate(duration, 50, "8 weeks"))
                .desiredDuration(safeTruncate(duration, 50, "8 weeks"))
                .experience(safeTruncate(experience, 50, "beginner"))
                .currentLevel(safeTruncate(experience, 50, "beginner"))
                .style(safeTruncate(learningStyle, 50, "hands-on"))
                .learningStyle(safeTruncate(learningStyle, 100, "hands-on"))
                .background(background)
                .dailyTime(safeTruncate(dailyTime, 50, "60 minutes/day"))
                .dailyLearningTime(safeTruncate(dailyTime, 50, "60 minutes/day"))
                .targetEnvironment(safeTruncate(firstNonBlank(journey.getSubCategory(), domain), 100, domain))
                .location("Vietnam")
                .priority(safeTruncate(mapPriorityFromRecommendation(snapshot.recommendationMode), 50, "Balanced"))
                .toolPreferences(toolPreferences)
                .toolPreference(toolPreferences)
                .difficultyConcern(difficultyConcern)
                .incomeGoal(inferIncomeGoal(journeyGoal));

        if (careerJourney) {
            builder
                    .targetRole(safeTruncate(target, 120, domain + " role"))
                    .careerTrack(safeTruncate(firstNonBlank(journey.getSubCategory(), domain), 120, domain))
                    .targetSeniority(safeTruncate(mapSeniorityFromLevel(testResult.getEvaluatedLevel(), journeyGoal), 50, "JUNIOR"))
                    .workMode("FULL_TIME")
                    .targetMarket("VIETNAM")
                    .companyType("SME")
                    .timelineToWork(mapTimelineToWork(duration))
                    .incomeExpectation(inferIncomeGoal(journeyGoal))
                    .workExperience(mapWorkExperience(testResult.getEvaluatedLevel()))
                    .transferableSkills(!strengthSkills.isEmpty())
                    .confidenceLevel(mapConfidenceBand(snapshot.assessmentConfidence));
        } else {
            builder
                    .skillName(safeTruncate(target, 120, domain))
                    .skillCategory(safeTruncate(domain, 100, "general"))
                    .desiredDepth(mapDesiredDepth(snapshot.recommendationMode))
                    .learnerType(mapLearnerType(snapshot.recommendationMode))
                    .currentSkillLevel(mapSkillCurrentLevel(testResult.getEvaluatedLevel(),
                            assessmentData != null ? assessmentData.getLevel() : null))
                    .learningGoal(mapSkillLearningGoal(snapshot.recommendationMode, testResult.getScorePercentage()))
                    .dailyLearningTime(mapDailyLearningTimeSlot(dailyTime))
                    .assessmentPreference("MIXED")
                    .difficultyTolerance(mapDifficultyTolerance(snapshot.scoreBand));
        }

        return builder.build();
    }

    private boolean isCareerJourney(Journey journey) {
        if (journey == null) {
            return false;
        }
        if ("CAREER".equalsIgnoreCase(journey.getType())) {
            return true;
        }
        return journey.getJobRole() != null && !journey.getJobRole().isBlank();
    }

    private String resolveRoadmapDuration(String durationPreference, String recommendationMode) {
        if (durationPreference != null) {
            String normalized = durationPreference.trim().toUpperCase(Locale.ROOT);
            if ("QUICK".equals(normalized)) {
                return "4 weeks";
            }
            if ("STANDARD".equals(normalized)) {
                return "8 weeks";
            }
            if ("DEEP".equals(normalized)) {
                return "12 weeks";
            }
        }

        return switch (recommendationMode) {
            case "FROM_ZERO" -> "16 weeks";
            case "FOUNDATION" -> "12 weeks";
            case "STANDARD" -> "10 weeks";
            case "ADVANCED" -> "8 weeks";
            case "FAST_TRACK" -> "6 weeks";
            default -> "8 weeks";
        };
    }

    private String resolveDailyLearningTime(String durationPreference, String recommendationMode) {
        if (durationPreference != null) {
            String normalized = durationPreference.trim().toUpperCase(Locale.ROOT);
            if ("QUICK".equals(normalized)) {
                return "90 minutes/day";
            }
            if ("DEEP".equals(normalized)) {
                return "45 minutes/day";
            }
        }

        return switch (recommendationMode) {
            case "FROM_ZERO", "FOUNDATION" -> "60 minutes/day";
            case "STANDARD" -> "75 minutes/day";
            case "ADVANCED", "FAST_TRACK" -> "90 minutes/day";
            default -> "60 minutes/day";
        };
    }

    private String mapJourneyLevelToExperience(Journey.SkillLevel evaluatedLevel, String fallbackLevel) {
        if (evaluatedLevel != null) {
            return normalizeRoadmapLevel(evaluatedLevel);
        }
        if (fallbackLevel == null || fallbackLevel.isBlank()) {
            return "beginner";
        }
        String normalized = fallbackLevel.trim().toUpperCase(Locale.ROOT);
        if ("BEGINNER".equals(normalized)) {
            return "beginner";
        }
        if ("ELEMENTARY".equals(normalized)) {
            return "beginner";
        }
        if ("INTERMEDIATE".equals(normalized) || "UPPER_INTERMEDIATE".equals(normalized)) {
            return "intermediate";
        }
        if ("ADVANCED".equals(normalized) || "EXPERT".equals(normalized)) {
            return "advanced";
        }
        return "beginner";
    }

    private String normalizeRoadmapLevel(Journey.SkillLevel level) {
        if (level == null) {
            return "beginner";
        }
        return switch (level) {
            case BEGINNER -> "beginner";
            case ELEMENTARY -> "beginner";
            case INTERMEDIATE -> "intermediate";
            case ADVANCED -> "advanced";
            case EXPERT -> "expert";
        };
    }

    private String mapPriorityFromRecommendation(String recommendationMode) {
        if (recommendationMode == null) {
            return "Balanced";
        }
        return switch (recommendationMode) {
            case "FROM_ZERO" -> "Foundation first";
            case "FOUNDATION" -> "Core fundamentals";
            case "STANDARD" -> "Balanced";
            case "ADVANCED" -> "Skill depth";
            case "FAST_TRACK" -> "Nhanh đi làm";
            default -> "Balanced";
        };
    }

    private String mapSeniorityFromLevel(Journey.SkillLevel level, String goalText) {
        String normalizedGoal = goalText == null ? "" : goalText.toLowerCase(Locale.ROOT);
        if (normalizedGoal.contains("intern")) {
            return "INTERN";
        }
        if (normalizedGoal.contains("fresher")) {
            return "INTERN";
        }
        if (level == null) {
            return "JUNIOR";
        }
        return switch (level) {
            case BEGINNER, ELEMENTARY -> "INTERN";
            case INTERMEDIATE -> "JUNIOR";
            case ADVANCED, EXPERT -> "FREELANCER";
        };
    }

    private String mapConfidenceBand(int confidence) {
        if (confidence >= 80) {
            return "HIGH";
        }
        if (confidence >= 60) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String mapDesiredDepth(String recommendationMode) {
        if (recommendationMode == null) {
            return "SOLID";
        }
        return switch (recommendationMode) {
            case "FROM_ZERO", "FOUNDATION" -> "BASIC";
            case "STANDARD" -> "SOLID";
            case "ADVANCED", "FAST_TRACK" -> "ADVANCED";
            default -> "SOLID";
        };
    }

    private String mapLearnerType(String recommendationMode) {
        if (recommendationMode == null) {
            return "Student";
        }
        return switch (recommendationMode) {
            case "FROM_ZERO", "FOUNDATION" -> "Student";
            case "STANDARD" -> "Working";
            case "ADVANCED", "FAST_TRACK" -> "Explorer";
            default -> "Student";
        };
    }

    private String mapDifficultyTolerance(String scoreBand) {
        if (scoreBand == null) {
            return "MEDIUM";
        }
        return switch (scoreBand) {
            case "ZERO_BASE", "FOUNDATION" -> "EASY";
            case "CORE" -> "MEDIUM";
            case "ADVANCED", "EXPERT" -> "HARD";
            default -> "MEDIUM";
        };
    }

    private String mapWorkExperience(Journey.SkillLevel level) {
        if (level == null) {
            return "NONE";
        }
        return switch (level) {
            case BEGINNER, ELEMENTARY -> "NONE";
            case INTERMEDIATE, ADVANCED, EXPERT -> "RELATED";
        };
    }

    private String mapSkillCurrentLevel(Journey.SkillLevel level, String fallbackLevel) {
        if (level != null) {
            return switch (level) {
                case BEGINNER, ELEMENTARY -> "BASIC";
                case INTERMEDIATE, ADVANCED, EXPERT -> "INTERMEDIATE";
            };
        }

        if (fallbackLevel == null || fallbackLevel.isBlank()) {
            return "BASIC";
        }

        String normalized = fallbackLevel.trim().toUpperCase(Locale.ROOT);
        if ("ZERO".equals(normalized)) {
            return "ZERO";
        }
        if ("BEGINNER".equals(normalized) || "ELEMENTARY".equals(normalized)) {
            return "BASIC";
        }
        return "INTERMEDIATE";
    }

    private String mapSkillLearningGoal(String recommendationMode, int scorePercentage) {
        if ("FROM_ZERO".equals(recommendationMode) || "FOUNDATION".equals(recommendationMode) || scorePercentage < 35) {
            return "UNDERSTAND";
        }
        if ("ADVANCED".equals(recommendationMode) || scorePercentage >= 80) {
            return "MASTER";
        }
        return "APPLY";
    }

    private String mapDailyLearningTimeSlot(String dailyTime) {
        if (dailyTime == null || dailyTime.isBlank()) {
            return "1_HOUR";
        }

        String normalized = dailyTime.toLowerCase(Locale.ROOT);
        if (normalized.contains("90") || normalized.contains("2 hour") || normalized.contains("2h")) {
            return "2_HOURS";
        }
        if (normalized.contains("60") || normalized.contains("1 hour") || normalized.contains("1h")) {
            return "1_HOUR";
        }
        return "30_MIN";
    }

    private String mapTimelineToWork(String duration) {
        if (duration == null || duration.isBlank()) {
            return "6M";
        }

        String normalized = duration.toLowerCase(Locale.ROOT);
        Integer quantity = parseLeadingInteger(normalized);

        if (normalized.contains("week")) {
            int weeks = quantity == null ? 8 : quantity;
            if (weeks <= 8) {
                return "3M";
            }
            if (weeks <= 16) {
                return "6M";
            }
            return "12M";
        }

        if (normalized.contains("month")) {
            int months = quantity == null ? 6 : quantity;
            if (months <= 3) {
                return "3M";
            }
            if (months <= 6) {
                return "6M";
            }
            return "12M";
        }

        if (normalized.contains("year")) {
            return "12M";
        }

        return "6M";
    }

    private Integer parseLeadingInteger(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+)").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean inferIncomeGoal(String goalText) {
        if (goalText == null || goalText.isBlank()) {
            return false;
        }
        String normalized = goalText.toLowerCase(Locale.ROOT);
        return normalized.contains("intern")
                || normalized.contains("fresher")
                || normalized.contains("job")
                || normalized.contains("career");
    }

    private List<String> resolveToolPreferences(StartJourneyRequest assessmentData, List<String> strengthSkills) {
        LinkedHashSet<String> toolPreferenceSet = new LinkedHashSet<>();

        if (assessmentData != null && assessmentData.getSkills() != null) {
            assessmentData.getSkills().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(skill -> !skill.isBlank())
                    .limit(5)
                    .forEach(toolPreferenceSet::add);
        }

        if (toolPreferenceSet.isEmpty() && strengthSkills != null) {
            strengthSkills.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(skill -> !skill.isBlank())
                    .limit(4)
                    .forEach(toolPreferenceSet::add);
        }

        if (toolPreferenceSet.isEmpty()) {
            toolPreferenceSet.add("Practice labs");
            toolPreferenceSet.add("Mini projects");
            toolPreferenceSet.add("Mock interviews");
        }

        return new ArrayList<>(toolPreferenceSet);
    }

    private List<String> extractSkillNames(List<Map<String, Object>> skillItems) {
        if (skillItems == null || skillItems.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> skills = new LinkedHashSet<>();
        for (Map<String, Object> item : skillItems) {
            if (item == null) {
                continue;
            }
            String skill = Optional.ofNullable(toText(item.get("skill"))).orElse("").trim();
            if (!skill.isBlank()) {
                skills.add(skill);
            }
            if (skills.size() >= 6) {
                break;
            }
        }
        return new ArrayList<>(skills);
    }

    private List<String> extractImprovementHints(List<Map<String, Object>> skillGaps) {
        if (skillGaps == null || skillGaps.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> hints = new ArrayList<>();
        for (Map<String, Object> gap : skillGaps) {
            if (gap == null) {
                continue;
            }
            String hint = Optional.ofNullable(toText(gap.get("howToImprove"))).orElse("").trim();
            if (!hint.isBlank()) {
                hints.add(hint);
            }
            if (hints.size() >= 5) {
                break;
            }
        }
        return hints;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String safeTruncate(String value, int maxLength, String fallback) {
        String normalized = (value == null || value.isBlank()) ? fallback : value.trim();
        if (normalized == null) {
            return null;
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private Long createRoadmapSessionFromEvaluation(Journey journey, TestResult testResult,
                                                   List<Map<String, Object>> skillGaps,
                                                   List<Map<String, Object>> strengths) {
        int gapCount = skillGaps == null ? 0 : skillGaps.size();
        int strengthCount = strengths == null ? 0 : strengths.size();
        log.info(
                "Creating roadmap session from evaluation for journey {} (score={}%, level={}, gaps={}, strengths={})",
                journey.getId(),
                testResult.getScorePercentage(),
                testResult.getEvaluatedLevel(),
                gapCount,
                strengthCount);

        StartJourneyRequest assessmentData = parseAssessmentData(journey.getAssessmentData());
        EvaluationSnapshot snapshot = buildSnapshotFromStoredResult(testResult, null);
        GenerateRoadmapRequest roadmapRequest = buildRoadmapRequestFromEvaluation(
                journey,
                assessmentData,
                testResult,
                snapshot,
                skillGaps != null ? skillGaps : Collections.emptyList(),
                strengths != null ? strengths : Collections.emptyList());

        RoadmapResponse roadmapResponse = aiRoadmapService.generateRoadmap(roadmapRequest, journey.getUser());
        if (roadmapResponse == null || roadmapResponse.getSessionId() == null || roadmapResponse.getSessionId() <= 0) {
            throw new RuntimeException("Failed to create roadmap session from assessment evaluation");
        }

        return roadmapResponse.getSessionId();
    }
}
