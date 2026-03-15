package com.exe.skillverse_backend.journey_service.service.impl;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.service.AiRoadmapService;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.*;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.journey_service.dto.request.StartJourneyRequest;
import com.exe.skillverse_backend.journey_service.dto.request.SubmitTestRequest;
import com.exe.skillverse_backend.journey_service.dto.response.*;
import com.exe.skillverse_backend.journey_service.entity.*;
import com.exe.skillverse_backend.journey_service.repository.*;
import com.exe.skillverse_backend.journey_service.service.JourneyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JourneyServiceImpl implements JourneyService {

    private static final int MAX_ASSESSMENT_ATTEMPTS = 2;
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

    @Qualifier("generateTestChatModel")
    private final ChatModel generateTestChatModel;
    private final AiRoadmapService aiRoadmapService;
    private final AssessmentPromptService assessmentPromptService;
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

        log.info("Using domain: {}, goal: {}", domain, goal);

        // Build UserAssessmentInfo record (simplified)
        UserAssessmentInfo userInfo = new UserAssessmentInfo(
                assessmentData.getDomain(),
                assessmentData.getGoal(),
                assessmentData.getLevel(),
                assessmentData.getSkills(),
                assessmentData.getFocusAreas(),
                assessmentData.getLanguage(),
                assessmentData.getDuration()
        );

        // Get specialized prompt using AssessmentPromptService
        String prompt = assessmentPromptService.getTestGenerationPrompt(
                domain,
                null, // industry - not needed in simplified version
                null, // role - not needed in simplified version
                userInfo
        );

        log.info("Calling AI to generate specialized test for domain: {}", domain);

        String aiResponse = getChatClient()
                .prompt()
                .user(prompt)
                .call()
                .content();

        // Parse AI response
        Map<String, Object> testData;
        try {
            String jsonStr = extractJsonFromResponse(aiResponse);
            testData = objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse AI test generation response: {}", aiResponse);
            throw new RuntimeException("Failed to generate test: Invalid AI response", e);
        }

        // Create assessment test entity
        AssessmentTest test = AssessmentTest.builder()
                .journey(journey)
                .title((String) testData.get("title"))
                .description((String) testData.get("description"))
                .targetField((String) testData.get("targetField"))
                .status(AssessmentTest.TestStatus.PENDING)
                .questionCount((Integer) testData.get("questionCount"))
                .timeLimitMinutes((Integer) testData.get("timeLimitMinutes"))
                .difficultyLevel((String) testData.get("difficultyLevel"))
                .questionsJson(objectMapper.valueToTree(testData.get("questions")).toString())
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

    @Override
    public AssessmentTestResponse getAssessmentTest(User user, Long journeyId, Long testId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        AssessmentTest test = assessmentTestRepository.findByIdAndJourney(testId, journey)
                .orElseThrow(() -> new RuntimeException("Test not found"));

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
        } catch (Exception e) {
            log.warn("AI evaluation enrichment failed for journey {}. Fallback to deterministic summary.", journeyId, e);
        }

        String finalSummary = combineSummaries(deterministicSummary, aiSummary);

        String skillGapsJson;
        String strengthsJson;
        String userAnswersJson;
        try {
            skillGapsJson = objectMapper.writeValueAsString(derivedSkillGaps);
            strengthsJson = objectMapper.writeValueAsString(derivedStrengths);
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
    public Object generateStudyPlans(User user, Long journeyId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        if (journey.getRoadmapSessionId() == null) {
            throw new RuntimeException("Please generate roadmap first");
        }

        journey.setStatus(Journey.JourneyStatus.STUDY_PLAN_IN_PROGRESS);
        journey.setLastActivityAt(Instant.now());
        journeyRepository.save(journey);

        return Map.of(
                "message", "Study plan generation triggered",
                "roadmapId", journey.getRoadmapSessionId(),
                "skillGaps", getSkillGapsForStudyPlan(journey)
        );
    }

    @Override
    public Object createStudyPlanForNode(User user, Long journeyId, Long nodeId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        return Map.of(
                "message", "Study plan created for node",
                "nodeId", nodeId,
                "journeyId", journeyId
        );
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
                        "Create a personalized roadmap for %s in %s to achieve %s based on assessment score %d%%.",
                        target,
                        domain,
                        journeyGoal,
                        clampScore(testResult.getScorePercentage())),
                500,
                "Create a personalized learning roadmap");

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
            case BEGINNER -> "INTERN";
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
            case BEGINNER -> "NONE";
            case INTERMEDIATE, ADVANCED, EXPERT -> "RELATED";
        };
    }

    private String mapSkillCurrentLevel(Journey.SkillLevel level, String fallbackLevel) {
        if (level != null) {
            return switch (level) {
                case BEGINNER -> "BASIC";
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
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
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

    private List<String> getSkillGapsForStudyPlan(Journey journey) {
        TestResult latestResult = testResultRepository.findTopByJourneyOrderByCreatedAtDesc(journey).orElse(null);
        if (latestResult == null) {
            return Collections.emptyList();
        }

        try {
            List<Map<String, Object>> skillGaps = objectMapper.readValue(
                    latestResult.getSkillGapsJson(), List.class);
            return skillGaps.stream()
                    .map(gap -> (String) gap.get("skill"))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to parse skill gaps", e);
            return Collections.emptyList();
        }
    }
}
