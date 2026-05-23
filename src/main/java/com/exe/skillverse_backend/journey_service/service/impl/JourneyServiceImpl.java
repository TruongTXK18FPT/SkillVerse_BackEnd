package com.exe.skillverse_backend.journey_service.service.impl;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.ai_service.service.AiRoadmapService;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.QuestionInfo;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.TestSubmissionInfo;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.UserAssessmentInfo;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.career_taxonomy_service.entity.Domain;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPosition;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrack;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPositionTrackSkill;
import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import com.exe.skillverse_backend.career_taxonomy_service.enums.TaxonomyStatus;
import com.exe.skillverse_backend.career_taxonomy_service.repository.DomainRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionTrackRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionTrackSkillRepository;
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
import com.exe.skillverse_backend.journey_service.node_mentoring.service.FinalVerificationGateService;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.repository.TestResultRepository;
import com.exe.skillverse_backend.journey_service.service.JourneyService;
import com.exe.skillverse_backend.study_service.dto.request.CreateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.request.GenerateScheduleRequest;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.entity.StudySessionStatus;
import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskColumnResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskResponse;
import com.exe.skillverse_backend.study_service.entity.TaskPriority;
import com.exe.skillverse_backend.study_service.service.AiStudySupportService;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankResponse;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBank;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankService;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankQuestionService;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.roadmap_package_service.service.RoadmapTemplateService;
import com.exe.skillverse_backend.shared.enums.SkillStatus;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
import com.exe.skillverse_backend.study_service.service.TaskBoardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
    private static final int DEFAULT_ASSESSMENT_QUESTION_COUNT = 50;
    private static final int DEFAULT_ASSESSMENT_TIME_LIMIT_MINUTES = 50;
    private static final int DEFAULT_RECOVERED_QUESTION_COUNT = DEFAULT_ASSESSMENT_QUESTION_COUNT;
    private static final int MIN_SKILL_JOURNEY_BANK_SEED_QUESTIONS = 5;
    private static final int MAX_CONCURRENT_LEARNING_JOURNEYS = 5;
    private static final String QUESTION_BANK_PROMPT_MARKER = "question bank id=";
    private static final String FULL_QB_PROMPT_PREFIX = "Full QB: " + QUESTION_BANK_PROMPT_MARKER;
    private static final String HYBRID_QB_PROMPT_PREFIX = "Hybrid QB: " + QUESTION_BANK_PROMPT_MARKER;
    private static final String ASSESSMENT_PHASE_PLACEMENT = "PLACEMENT";
    private static final String ASSESSMENT_PHASE_CHALLENGE_UP = "CHALLENGE_UP";
    private static final String QUESTION_SOURCE_BANK = "QUESTION_BANK";
    private static final String QUESTION_SOURCE_AI = "AI";
    private static final int CHALLENGE_TRIGGER_SCORE = 85;
    private static final int MAX_STUDY_TASKS_PER_NODE = 12;
    private static final int SLOT_SEARCH_MAX_DAYS = 60;
    private static final int SLOT_SEARCH_BUFFER_MINUTES = 30;

    Clock studyClock = Clock.systemDefaultZone();
    private static final int CHALLENGE_PASS_SCORE = 70;
    private static final int EXPERT_CHALLENGE_PASS_SCORE = 80;
    private static final double MIN_PROMOTION_ANSWER_COVERAGE = 0.80;
    private static final String STUDY_PLAN_LINK_MARKER_PREFIX = "[ROADMAP_NODE_LINK]";
    private static final String DEFAULT_STUDY_TIMEZONE = "Asia/Ho_Chi_Minh";
    private static final Pattern OPTION_PREFIX_PATTERN = Pattern.compile("^\\s*([A-D])(?:\\s*[\\.:\\)\\-]|\\s+|$)", Pattern.CASE_INSENSITIVE);
    private final JourneyRepository journeyRepository;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final AssessmentTestRepository assessmentTestRepository;
    private final TestResultRepository testResultRepository;
    private final JourneyProgressRepository journeyProgressRepository;
    private final UserRoadmapProgressRepository userRoadmapProgressRepository;
    private final FinalVerificationGateService finalVerificationGateService;
    private final EntityManager entityManager;

    @Qualifier("generateTestChatModel")
    private final ChatModel generateTestChatModel;
    private final AiRoadmapService aiRoadmapService;
    private final RoadmapTemplateService roadmapTemplateService;
    private final AssessmentPromptService assessmentPromptService;
    private final TaskBoardService taskBoardService;
    private final AiStudySupportService aiStudySupportService;
    private final QuestionBankService questionBankService;
    private final QuestionBankQuestionService questionBankQuestionService;
    private final StudySessionRepository studySessionRepository;
    private final BookingRepository bookingRepository;
    private final PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;
    private final DomainRepository domainRepository;
    private final JobPositionRepository jobPositionRepository;
    private final JobPositionTrackRepository jobPositionTrackRepository;
    private final JobPositionTrackSkillRepository jobPositionTrackSkillRepository;
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

    private record RoadmapNodeLinkRef(Long journeyId, Long roadmapSessionId, String nodeId) {
    }

    private record AssessmentGenerationContext(
            String phase,
            Journey.SkillLevel baseLevel,
            Journey.SkillLevel testedLevel,
            Long parentTestId
    ) {
    }

    private record JobPositionJourneyContext(
            Domain domain,
            JobPosition jobPosition,
            JobPositionTrack track,
            List<JobPositionTrackSkill> trackSkills
    ) {
    }

    // Lazy ChatClient instance
    private ChatClient getChatClient() {
        return ChatClient.create(generateTestChatModel);
    }

    @Override
    @Transactional
    public JourneySummaryResponse startJourney(User user, StartJourneyRequest request) {
        log.info("Starting new journey for user: {} with domain: {}", user.getEmail(), request.getDomain());
        normalizeJourneyRequestDefaults(request);

        long currentLearningJourneys = journeyRepository.countConcurrentLearningJourneys(user);
        if (currentLearningJourneys >= MAX_CONCURRENT_LEARNING_JOURNEYS) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Bạn đang học tối đa 5 hành trình cùng lúc. Hãy hoàn thành, tạm dừng hoặc xóa một hành trình trước khi tạo mới.");
        }

        validateAndNormalizeAdminManagedDomain(request);

        JobPositionJourneyContext jobContext = resolveJobPositionJourneyContext(request).orElse(null);
        if (jobContext != null) {
            applyJobPositionContextToRequest(request, jobContext);
        }
        List<Long> focusSkillIds = jobContext != null
                ? jobContext.trackSkills().stream()
                        .map(JobPositionTrackSkill::getSkillId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList()
                : List.of();

        // V3: Extract single skillName from request if available
        String skillName = null;
        if (request.getSkills() != null && !request.getSkills().isEmpty()) {
            skillName = SkillNameUtils.normalize(request.getSkills().get(0));
        }
        skillName = resolvePrimarySkillName(jobContext, request);

        // Create journey entity
        Journey journey = Journey.builder()
                .user(user)
                .type(jobContext != null ? "CAREER" : request.getType())
                .domain(request.getDomain())
                .title(buildJourneyTitle(request))
                .subCategory(request.getSubCategory())
                .industry(request.getIndustry())
                .jobRole(request.getJobRole())
                .goal(request.getGoal())
                .skillName(skillName)
                .status(Journey.JourneyStatus.ASSESSMENT_PENDING)
                .assessmentData(convertRequestToJson(request))
                .jobPositionTrackId(jobContext != null ? jobContext.track().getId() : request.getJobPositionTrackId())
                .targetLevel(null)
                .focusSkillIdsJson(focusSkillIds.isEmpty() ? null : writeJson(focusSkillIds))
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

        // V3 Phase 3: Block deletion when journey has active mentor bookings.
        // Learner must complete the learning path and release funds before deleting.
        if (bookingRepository.hasActiveBookingsForJourney(journey.getId())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Không thể xóa hành trình đã được book mentor. Bạn cần hoàn thành lộ trình học và giải phóng tiền cho mentor trước.");
        }

        // Cascade: delete linked roadmap session when deleting journey
        Long roadmapSessionId = journey.getRoadmapSessionId();

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

        // Delete node mentoring artifacts linked to the roadmap session
        if (roadmapSessionId != null) {
            entityManager.createNativeQuery("DELETE FROM roadmap_node_reviews WHERE submission_id IN (SELECT id FROM roadmap_node_submissions WHERE roadmap_session_id = ?1)")
                    .setParameter(1, roadmapSessionId)
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM roadmap_node_submissions WHERE roadmap_session_id = ?1")
                    .setParameter(1, roadmapSessionId)
                    .executeUpdate();
            entityManager.createNativeQuery("DELETE FROM roadmap_node_assignments WHERE roadmap_session_id = ?1")
                    .setParameter(1, roadmapSessionId)
                    .executeUpdate();
        }

        entityManager.createNativeQuery("DELETE FROM journeys WHERE id = ?1 AND user_id = ?2")
                .setParameter(1, journey.getId())
                .setParameter(2, user.getId())
                .executeUpdate();

        // Cascade: delete roadmap session and all related data
        if (roadmapSessionId != null) {
            // Archive study tasks linked to this roadmap
            taskBoardService.archiveTasksByRoadmapSession(user.getId(), roadmapSessionId);

            // Delete user_roadmap_progress (FK to roadmap_sessions — must delete before parent)
            entityManager.createNativeQuery("DELETE FROM user_roadmap_progress WHERE roadmap_session_id = ?1")
                    .setParameter(1, roadmapSessionId)
                    .executeUpdate();

            // Delete the roadmap session itself
            entityManager.createNativeQuery("DELETE FROM roadmap_sessions WHERE id = ?1 AND user_id = ?2")
                    .setParameter(1, roadmapSessionId)
                    .setParameter(2, user.getId())
                    .executeUpdate();

            log.info("Cascade deleted roadmap session {} for journey {} user {}",
                    roadmapSessionId, journeyId, user.getId());
        }

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

        Long previousRoadmapSessionId = journey.getRoadmapSessionId();
        journey.setStatus(newStatus);
        journey.setLastActivityAt(Instant.now());
        journey = journeyRepository.save(journey);

        // When pausing or cancelling a journey, archive all its roadmap-linked tasks
        // so they no longer clutter the task board but are preserved in DB for audit.
        if ((newStatus == Journey.JourneyStatus.PAUSED || newStatus == Journey.JourneyStatus.CANCELLED)
                && previousRoadmapSessionId != null) {
            int archived = taskBoardService.archiveTasksByRoadmapSession(user.getId(), previousRoadmapSessionId);
            log.info("Archived {} tasks for roadmap session {} when journey {} set to {}",
                    archived, previousRoadmapSessionId, journeyId, newStatus);
        }

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

        Journey.JourneyStatus resumedStatus = determineResumeStatus(journey);
        journey.setStatus(resumedStatus);
        journey.setLastActivityAt(Instant.now());
        journey = journeyRepository.save(journey);

        // BUG-6 FIX: When resuming a journey, restore archived tasks so they reappear on the board
        Long roadmapSessionId = journey.getRoadmapSessionId();
        if (roadmapSessionId != null) {
            int unarchived = taskBoardService.unarchiveTasksByRoadmapSession(user.getId(), roadmapSessionId);
            log.info("Restored {} tasks for roadmap session {} when journey {} resumed",
                    unarchived, roadmapSessionId, journeyId);
        }

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

    @Override
    @Transactional
    public JourneySummaryResponse cancelJourney(User user, Long journeyId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        // Block cancellation when journey has active mentor bookings.
        // Learner must cancel bookings in the Booking tab first.
        if (bookingRepository.hasActiveBookingsForJourney(journey.getId())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Không thể hủy hành trình đã có lịch hẹn mentor. Vui lòng hủy các lịch hẹn trong tab Booking trước.");
        }

        return updateJourneyStatus(user, journeyId, Journey.JourneyStatus.CANCELLED);
    }

    @Override
    @Transactional
    public JourneySummaryResponse completeJourney(User user, Long journeyId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        // V3 Phase 1: hard-gate on final verification when enabled on the journey.
        // Throws ApiException(CONFLICT) with blocking reasons if not passed.
        finalVerificationGateService.requireGatePassed(journey);

        Journey.JourneyStatus finalStatus = Boolean.TRUE.equals(journey.getFinalVerificationRequired())
                ? Journey.JourneyStatus.COMPLETED_VERIFIED
                : Journey.JourneyStatus.COMPLETED_UNVERIFIED;
        journey.setStatus(finalStatus);
        journey.setProgressPercentage(100);
        journey.setCompletedAt(Instant.now());
        journey.setLastActivityAt(Instant.now());
        journey = journeyRepository.save(journey);
        syncCompletedJourneySkillToPortfolio(journey);

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
    public JourneySummaryResponse requestVerification(User user, Long journeyId) {
        Journey journey = journeyRepository.findByIdAndUser(journeyId, user)
                .orElseThrow(() -> new RuntimeException("Journey not found"));

        if (!Boolean.TRUE.equals(journey.getFinalVerificationRequired())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Journey does not require final verification");
        }

        Journey.JourneyStatus current = journey.getStatus();
        boolean allowedTransition = current == Journey.JourneyStatus.ACTIVE
                || current == Journey.JourneyStatus.COMPLETED_UNVERIFIED;
        if (!allowedTransition) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Cannot request verification from status: " + current);
        }

        journey.setStatus(Journey.JourneyStatus.AWAITING_VERIFICATION);
        journey.setLastActivityAt(Instant.now());
        journey = journeyRepository.save(journey);
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
        normalizeJourneyRequestDefaults(assessmentData);

        // Domain and goal are now directly in the request
        String domain = assessmentData.getDomain();
        String goal = assessmentData.getGoal();
        String jobRole = assessmentData.getJobRole();
        String industry = assessmentData.getIndustry();
        int requestedQuestionCount = resolveAssessmentQuestionCount(assessmentData);
        int requestedTimeLimitMinutes = resolveAssessmentTimeLimitMinutes(assessmentData);
        TestResult latestResult = testResultRepository.findTopByJourneyOrderByCreatedAtDesc(journey).orElse(null);
        AssessmentGenerationContext generationContext = resolveAssessmentGenerationContext(assessmentData, latestResult);
        String userLevel = generationContext.testedLevel().name();

        log.info("Using domain: {}, goal: {}, jobRole: {}, industry: {}, questionCount: {}, timeLimitMinutes: {}, phase: {}, baseLevel: {}, testedLevel: {}",
                domain, goal, jobRole, industry, requestedQuestionCount, requestedTimeLimitMinutes,
                generationContext.phase(), generationContext.baseLevel(), generationContext.testedLevel());

        // === Bank-first test generation ===
        AssessmentTest test = tryGenerateFromQuestionBank(
                journey, user, assessmentData, requestedQuestionCount, requestedTimeLimitMinutes, generationContext);

        if (test != null) {
            // Bank was used (full or partial)
            return buildGenerateTestResponse(journey, test,
                    "Đã tạo bài quiz đánh giá cho " + domain + " từ ngân hàng câu hỏi.");
        }

        if (ASSESSMENT_PHASE_CHALLENGE_UP.equals(generationContext.phase())) {
            log.info("Challenge-up for journey {} cannot use a ready question bank. Falling back to the original placement level.",
                    journeyId);
            generationContext = new AssessmentGenerationContext(
                    ASSESSMENT_PHASE_PLACEMENT,
                    generationContext.baseLevel(),
                    generationContext.baseLevel(),
                    null);
            userLevel = generationContext.testedLevel().name();
        }

        // === Fallback: AI generation (no bank or empty bank) ===
        return generateTestFromAI(journey, user, domain, assessmentData, generatedTestCount, generationContext);
    }

    /**
     * Try to generate test from question bank.
     * Uses per-difficulty threshold: ALL four levels must have
     * >= QuestionBankService.MIN_READY_QUESTION_COUNT_PER_LEVEL.
     * Falls back to AI if any level is insufficient or bank is not found.
     * @return AssessmentTest if bank was used, null if bank should not be used
     */
    private AssessmentTest tryGenerateFromQuestionBank(Journey journey, User user, StartJourneyRequest assessmentData,
            int requestedQuestionCount, int requestedTimeLimitMinutes, AssessmentGenerationContext generationContext) {

        String domain = assessmentData.getDomain();
        String industry = assessmentData.getIndustry();
        String jobRole = assessmentData.getJobRole();
        String userLevel = generationContext.testedLevel().name();

        JobPositionJourneyContext jobContext = resolveJobPositionJourneyContext(assessmentData)
                .or(() -> resolveJobPositionJourneyContext(journey))
                .orElse(null);
        if (jobContext != null) {
            List<QuestionInfo> jobPositionQuestions = selectJobPositionTrackQuestions(
                    jobContext,
                    requestedQuestionCount,
                    userLevel,
                    assessmentData);
            if (jobPositionQuestions.size() >= requestedQuestionCount) {
                questionBankService.incrementUsedCount(jobPositionQuestions);
                return saveQuestionBankAssessmentTest(
                        journey,
                        user,
                        domain,
                        null,
                        jobPositionQuestions.stream().limit(requestedQuestionCount).toList(),
                        requestedTimeLimitMinutes,
                        generationContext,
                        buildJobPositionQuestionBankPrompt(jobContext, requestedQuestionCount, assessmentData, userLevel));
            }
            log.info("Job-position bank selection returned only {} / {} questions for track {}.",
                    jobPositionQuestions.size(), requestedQuestionCount, jobContext.track().getId());
            log.info("Falling back to AI generation for job-position track {}. AI-generated questions will be saved to the matching question bank after submission.",
                    jobContext.track().getId());
            return null;
        }

        Optional<QuestionBankResponse> bankOpt = resolveQuestionBankForJourney(journey, domain, industry, jobRole);
        if (bankOpt.isEmpty()) {
            log.info("No question bank found for domain={}, industry={}, jobRole={}, type={}. Falling back to AI generation.",
                    domain, industry, jobRole, journey.getType());
            return null;
        }

        QuestionBankResponse bank = bankOpt.get();
        Long bankId = bank.getId();

        // === Per-difficulty threshold: ALL 4 bank difficulties must be ready ===
        if (!questionBankService.isBankReadyForAllLevels(bankId)) {
            Map<String, Long> breakdown = bank.getDifficultyBreakdown();
            String detail = (breakdown != null)
                    ? breakdown.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining(", "))
                    : "n/a";
            log.info("QB {} not ready (all levels must be >= {}): {}. Falling back to AI — questions will be saved to bank for future use.",
                    bankId, QuestionBankService.MIN_READY_QUESTION_COUNT_PER_LEVEL, detail);
            return null;
        }

        List<QuestionInfo> bankQuestions = questionBankService.selectRandomQuestionsByLevel(
                bankId,
                requestedQuestionCount,
                userLevel);

        if (bankQuestions.size() < requestedQuestionCount) {
            log.info("QB {} returned only {} / {} questions. Falling back to AI.",
                    bankId, bankQuestions.size(), requestedQuestionCount);
            return null;
        }

        questionBankService.incrementUsedCount(bankQuestions);

        return saveQuestionBankAssessmentTest(
                journey,
                user,
                domain,
                bankId,
                bankQuestions,
                requestedTimeLimitMinutes,
                generationContext,
                FULL_QB_PROMPT_PREFIX + bankId + " (total=" + requestedQuestionCount + ")");
    }

    private AssessmentTest saveQuestionBankAssessmentTest(
            Journey journey,
            User user,
            String domain,
            Long bankId,
            List<QuestionInfo> questions,
            int requestedTimeLimitMinutes,
            AssessmentGenerationContext generationContext,
            String generationPrompt) {
        AssessmentTest test = AssessmentTest.builder()
                .journey(journey)
                .questionBank(bankId != null ? entityManager.getReference(QuestionBank.class, bankId) : null)
                .title("Bài đánh giá kỹ năng " + domain)
                .description("Bài quiz đánh giá kỹ năng từ ngân hàng câu hỏi cho " + domain)
                .targetField(domain)
                .status(AssessmentTest.TestStatus.PENDING)
                .questionCount(questions.size())
                .timeLimitMinutes(requestedTimeLimitMinutes)
                .difficultyLevel(generationContext.testedLevel().name())
                .assessmentPhase(generationContext.phase())
                .baseLevel(generationContext.baseLevel().name())
                .testedLevel(generationContext.testedLevel().name())
                .parentTestId(generationContext.parentTestId())
                .questionSource(QUESTION_SOURCE_BANK)
                .questionsJson(toQuestionsJson(questions))
                .generationPrompt(generationPrompt)
                .build();

        test = assessmentTestRepository.save(test);

        journey.setStatus(Journey.JourneyStatus.TEST_IN_PROGRESS);
        journey.setLastActivityAt(Instant.now());
        journeyRepository.save(journey);

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

    private Optional<QuestionBankResponse> resolveQuestionBankForJourney(
            Journey journey, String domain, String industry, String jobRole) {
        String skillName = journey != null ? journey.getSkillName() : null;
        log.info("resolveQuestionBankForJourney: journeyId={}, type={}, skillName={}, domain={}",
                journey != null ? journey.getId() : null,
                journey != null ? journey.getType() : null,
                skillName,
                domain);

        return Optional.empty(); // Should be updated or removed if not needed since we use domain/jobPosition/skill now
    }

    private boolean matchesSkillScopedBank(QuestionBankResponse bank, String requestedSkillName) {
        String requested = SkillNameUtils.normalize(requestedSkillName);
        String bankSkill = SkillNameUtils.normalize(bank != null ? bank.getSkillName() : null);
        return requested != null && !requested.isBlank() && requested.equals(bankSkill);
    }

    private List<QuestionInfo> selectJobPositionTrackQuestions(
            JobPositionJourneyContext context,
            int requestedQuestionCount,
            String userLevel,
            StartJourneyRequest assessmentData) {
        if (context == null || context.trackSkills().isEmpty() || requestedQuestionCount <= 0) {
            return Collections.emptyList();
        }

        List<JobPositionTrackSkill> skills = assessmentCoreTrackSkills(context.trackSkills());
        int[] targets = allocateQuestionTargetsByWeight(skills, requestedQuestionCount);
        String selectionLevel = resolveQuestionBankSelectionLevel(userLevel, assessmentData);

        Map<String, QuestionInfo> selected = new LinkedHashMap<>();

        Optional<QuestionBankResponse> roleBank = findRoleQuestionBank(context);
        roleBank.ifPresent(bank -> addUniqueQuestions(
                selected,
                selectSkillScopedQuestions(bank.getId(), skills, targets, requestedQuestionCount, selectionLevel),
                requestedQuestionCount));

        for (int index = 0; index < skills.size() && selected.size() < requestedQuestionCount; index++) {
            JobPositionTrackSkill trackSkill = skills.get(index);
            String skillName = resolveTrackSkillName(trackSkill);
            if (skillName == null || skillName.isBlank()) {
                continue;
            }
            int targetForSkill = targets[index];
            if (targetForSkill <= 0) continue;

            Optional<QuestionBankResponse> skillBank = findSkillQuestionBank(context, trackSkill);
            if (skillBank.isEmpty()) {
                log.info("No question bank found for job-position skill {}", skillName);
                continue;
            }
            List<QuestionInfo> skillQuestions = questionBankService.selectRandomQuestionsByLevel(
                    skillBank.get().getId(), targetForSkill, selectionLevel);
            addUniqueQuestions(selected, skillQuestions, requestedQuestionCount);
        }

        if (selected.size() < requestedQuestionCount) {
            for (JobPositionTrackSkill trackSkill : skills) {
                if (selected.size() >= requestedQuestionCount) {
                    break;
                }
                String skillName = resolveTrackSkillName(trackSkill);
                Optional<QuestionBankResponse> skillBank = findSkillQuestionBank(context, trackSkill);
                if (skillBank.isEmpty()) {
                    continue;
                }
                List<QuestionInfo> fill = questionBankService.selectRandomQuestions(
                        skillBank.get().getId(),
                        requestedQuestionCount - selected.size(),
                        skillBank.get().getDifficultyDistribution());
                addUniqueQuestions(selected, fill, requestedQuestionCount);
            }
        }

        List<QuestionInfo> result = new ArrayList<>(selected.values());
        Collections.shuffle(result);
        return result;
    }

    private List<JobPositionTrackSkill> assessmentCoreTrackSkills(List<JobPositionTrackSkill> trackSkills) {
        if (trackSkills == null || trackSkills.isEmpty()) {
            return List.of();
        }
        List<JobPositionTrackSkill> coreSkills = trackSkills.stream()
                .filter(this::isAssessmentCoreTrackSkill)
                .toList();
        if (!coreSkills.isEmpty()) {
            return coreSkills;
        }
        return trackSkills;
    }

    private boolean isAssessmentCoreTrackSkill(JobPositionTrackSkill trackSkill) {
        RequirementType type = trackSkill != null && trackSkill.getRequirementType() != null
                ? trackSkill.getRequirementType().normalized()
                : RequirementType.REQUIRED;
        return type == RequirementType.REQUIRED;
    }

    private int[] allocateQuestionTargetsByWeight(List<JobPositionTrackSkill> skills, int requestedQuestionCount) {
        int[] targets = new int[skills.size()];
        if (skills.isEmpty() || requestedQuestionCount <= 0) {
            return targets;
        }

        int totalWeight = skills.stream()
                .mapToInt(this::questionSelectionWeight)
                .sum();
        if (totalWeight <= 0) {
            totalWeight = skills.size();
        }

        int assigned = 0;
        double[] remainders = new double[skills.size()];
        for (int i = 0; i < skills.size(); i++) {
            double exact = (questionSelectionWeight(skills.get(i)) / (double) totalWeight) * requestedQuestionCount;
            int base = (int) Math.floor(exact);
            targets[i] = base;
            remainders[i] = exact - base;
            assigned += base;
        }

        int remaining = requestedQuestionCount - assigned;
        while (remaining > 0) {
            int bestIndex = 0;
            for (int i = 1; i < remainders.length; i++) {
                if (remainders[i] > remainders[bestIndex]) {
                    bestIndex = i;
                }
            }
            targets[bestIndex]++;
            remainders[bestIndex] = -1;
            remaining--;
        }
        return targets;
    }

    private int questionSelectionWeight(JobPositionTrackSkill skill) {
        if (skill == null || skill.getWeight() == null) {
            return 1;
        }
        return Math.max(1, Math.min(10, skill.getWeight()));
    }

    private String resolveQuestionBankSelectionLevel(String userLevel, StartJourneyRequest assessmentData) {
        String normalizedLevel = userLevel != null ? userLevel.trim().toUpperCase(Locale.ROOT) : "";
        String goalText = assessmentData != null && assessmentData.getGoal() != null
                ? assessmentData.getGoal().toLowerCase(Locale.ROOT)
                : "";
        Set<String> focusAreas = assessmentData != null && assessmentData.getFocusAreas() != null
                ? assessmentData.getFocusAreas().stream()
                        .filter(Objects::nonNull)
                        .map(value -> value.trim().toUpperCase(Locale.ROOT))
                        .collect(Collectors.toCollection(LinkedHashSet::new))
                : Set.of();

        boolean jobReadinessGoal = focusAreas.contains("JOB_READINESS")
                || goalText.contains("job")
                || goalText.contains("interview")
                || goalText.contains("phỏng vấn")
                || goalText.contains("xin việc")
                || goalText.contains("đi làm");
        if (jobReadinessGoal) {
            return nextAssessmentLevel(normalizedLevel);
        }
        if (focusAreas.contains("FUNDAMENTALS") || goalText.contains("nền tảng") || goalText.contains("foundation")) {
            return previousAssessmentLevel(normalizedLevel);
        }
        return normalizedLevel.isBlank() ? "INTERMEDIATE" : normalizedLevel;
    }

    private String nextAssessmentLevel(String level) {
        return switch (level) {
            case "BEGINNER" -> "ELEMENTARY";
            case "ELEMENTARY" -> "INTERMEDIATE";
            case "INTERMEDIATE" -> "ADVANCED";
            case "ADVANCED", "EXPERT" -> "EXPERT";
            default -> "INTERMEDIATE";
        };
    }

    private String previousAssessmentLevel(String level) {
        return switch (level) {
            case "EXPERT" -> "ADVANCED";
            case "ADVANCED" -> "INTERMEDIATE";
            case "INTERMEDIATE" -> "ELEMENTARY";
            case "ELEMENTARY", "BEGINNER" -> "BEGINNER";
            default -> "BEGINNER";
        };
    }

    private Optional<QuestionBankResponse> findSkillQuestionBank(
            JobPositionJourneyContext context,
            JobPositionTrackSkill trackSkill) {
        if (context == null || trackSkill == null || trackSkill.getSkillId() == null) {
            return Optional.empty();
        }
        Optional<QuestionBankResponse> scoped = questionBankService.findActiveBank(
                context.domain().getId(),
                context.jobPosition().getId(),
                trackSkill.getSkillId());
        if (scoped.isPresent() && matchesTrackSkillScopedBank(scoped.get(), trackSkill)) {
            return scoped;
        }
        return Optional.empty();
    }

    private Optional<QuestionBankResponse> findRoleQuestionBank(JobPositionJourneyContext context) {
        if (context == null) {
            return Optional.empty();
        }
        return questionBankService.findActiveBank(context.domain().getId(), context.jobPosition().getId())
                .filter(bank -> bank.getSkillId() == null && !hasText(bank.getSkillName()));
    }

    private boolean matchesTrackSkillScopedBank(QuestionBankResponse bank, JobPositionTrackSkill trackSkill) {
        if (bank == null || trackSkill == null) {
            return false;
        }
        if (bank.getSkillId() != null && trackSkill.getSkillId() != null) {
            return Objects.equals(bank.getSkillId(), trackSkill.getSkillId());
        }
        return matchesSkillScopedBank(bank, resolveTrackSkillName(trackSkill));
    }

    private String buildJobPositionQuestionBankPrompt(
            JobPositionJourneyContext context,
            int requestedQuestionCount,
            StartJourneyRequest assessmentData,
            String userLevel) {
        List<JobPositionTrackSkill> assessedSkills = assessmentCoreTrackSkills(context.trackSkills());
        String skillNames = assessedSkills.stream()
                .map(this::resolveTrackSkillName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
        String selectionLevel = resolveQuestionBankSelectionLevel(userLevel, assessmentData);
        return "Job-position QB: trackId=" + context.track().getId()
                + ", jobPositionId=" + context.jobPosition().getId()
                + ", total=" + requestedQuestionCount
                + ", requirementScope=REQUIRED_OR_IMPORTANT"
                + ", requestedLevel=" + userLevel
                + ", selectionLevel=" + selectionLevel
                + ", goal=" + (assessmentData != null ? assessmentData.getGoal() : null)
                + ", skills=[" + skillNames + "]";
    }

    private List<QuestionInfo> selectQuestionsFromBank(
            QuestionBankResponse bank, int requestedQuestionCount, String userLevel) {
        if (bank == null || bank.getId() == null) {
            return Collections.emptyList();
        }
        return userLevel != null
                ? questionBankService.selectRandomQuestionsByLevel(bank.getId(), requestedQuestionCount, userLevel)
                : questionBankService.selectRandomQuestions(bank.getId(), requestedQuestionCount, bank.getDifficultyDistribution());
    }

    private List<QuestionInfo> selectSkillScopedQuestions(
            Long bankId,
            List<JobPositionTrackSkill> requestedSkills,
            int[] targets,
            int requestedQuestionCount,
            String userLevel) {
        if (bankId == null || requestedQuestionCount <= 0 || requestedSkills == null || requestedSkills.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, String> availableSkillAreas = new LinkedHashMap<>();
        for (Object[] row : questionBankService.countBySkillAreaAndDifficulty(bankId)) {
            if (row == null || row.length == 0 || !(row[0] instanceof String skillArea) || skillArea.isBlank()) {
                continue;
            }
            availableSkillAreas.putIfAbsent(canonicalizeSkillKey(skillArea), skillArea);
        }
        if (availableSkillAreas.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> preferredDifficulties = preferredDifficultiesFor(userLevel);
        Map<String, QuestionInfo> selected = new LinkedHashMap<>();

        for (int index = 0; index < requestedSkills.size() && selected.size() < requestedQuestionCount; index++) {
            JobPositionTrackSkill trackSkill = requestedSkills.get(index);
            String requestedSkill = resolveTrackSkillName(trackSkill);
            if (requestedSkill == null || requestedSkill.isBlank()) {
                continue;
            }
            String actualSkillArea = availableSkillAreas.get(canonicalizeSkillKey(requestedSkill));
            if (actualSkillArea == null) {
                continue;
            }
            int remainingForSkill = index < targets.length ? targets[index] : 0;
            for (String difficulty : preferredDifficulties) {
                if (selected.size() >= requestedQuestionCount || remainingForSkill <= 0) {
                    break;
                }
                List<QuestionInfo> matches = questionBankService.selectRandomQuestionsBySkillAreaAndDifficulty(
                        bankId,
                        actualSkillArea,
                        difficulty,
                        remainingForSkill);
                int added = addUniqueQuestions(selected, matches, requestedQuestionCount);
                remainingForSkill -= added;
            }
        }

        if (selected.size() < requestedQuestionCount) {
            for (JobPositionTrackSkill trackSkill : requestedSkills) {
                String requestedSkill = resolveTrackSkillName(trackSkill);
                if (requestedSkill == null || requestedSkill.isBlank()) {
                    continue;
                }
                String actualSkillArea = availableSkillAreas.get(canonicalizeSkillKey(requestedSkill));
                if (actualSkillArea == null) {
                    continue;
                }
                for (String difficulty : preferredDifficulties) {
                    if (selected.size() >= requestedQuestionCount) {
                        break;
                    }
                    List<QuestionInfo> matches = questionBankService.selectRandomQuestionsBySkillAreaAndDifficulty(
                            bankId,
                            actualSkillArea,
                            difficulty,
                            requestedQuestionCount - selected.size());
                    addUniqueQuestions(selected, matches, requestedQuestionCount);
                }
            }
        }

        return new ArrayList<>(selected.values());
    }

    private List<QuestionInfo> selectSkillScopedQuestions(
            Long bankId, List<String> requestedSkills, int requestedQuestionCount, String userLevel) {
        if (bankId == null || requestedQuestionCount <= 0 || requestedSkills == null || requestedSkills.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, String> availableSkillAreas = new LinkedHashMap<>();
        for (Object[] row : questionBankService.countBySkillAreaAndDifficulty(bankId)) {
            if (row == null || row.length == 0 || !(row[0] instanceof String skillArea) || skillArea.isBlank()) {
                continue;
            }
            availableSkillAreas.putIfAbsent(canonicalizeSkillKey(skillArea), skillArea);
        }
        if (availableSkillAreas.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalizedSkills = requestedSkills.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(skill -> !skill.isBlank())
                .distinct()
                .toList();
        if (normalizedSkills.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> preferredDifficulties = preferredDifficultiesFor(userLevel);
        int perSkillTarget = Math.max(1, (int) Math.ceil((double) requestedQuestionCount / normalizedSkills.size()));
        Map<String, QuestionInfo> selected = new LinkedHashMap<>();

        for (String requestedSkill : normalizedSkills) {
            String actualSkillArea = availableSkillAreas.get(canonicalizeSkillKey(requestedSkill));
            if (actualSkillArea == null) {
                continue;
            }

            int remainingForSkill = perSkillTarget;
            for (String difficulty : preferredDifficulties) {
                if (selected.size() >= requestedQuestionCount || remainingForSkill <= 0) {
                    break;
                }
                List<QuestionInfo> matches = questionBankService.selectRandomQuestionsBySkillAreaAndDifficulty(
                        bankId,
                        actualSkillArea,
                        difficulty,
                        remainingForSkill);
                int added = addUniqueQuestions(selected, matches, requestedQuestionCount);
                remainingForSkill -= added;
            }
        }

        if (selected.size() < requestedQuestionCount) {
            for (String requestedSkill : normalizedSkills) {
                String actualSkillArea = availableSkillAreas.get(canonicalizeSkillKey(requestedSkill));
                if (actualSkillArea == null) {
                    continue;
                }
                for (String difficulty : preferredDifficulties) {
                    if (selected.size() >= requestedQuestionCount) {
                        break;
                    }
                    List<QuestionInfo> matches = questionBankService.selectRandomQuestionsBySkillAreaAndDifficulty(
                            bankId,
                            actualSkillArea,
                            difficulty,
                            requestedQuestionCount - selected.size());
                    addUniqueQuestions(selected, matches, requestedQuestionCount);
                }
            }
        }

        return new ArrayList<>(selected.values());
    }

    private List<String> preferredDifficultiesFor(String userLevel) {
        String normalizedLevel = userLevel != null ? userLevel.trim().toUpperCase(Locale.ROOT) : "";
        return switch (normalizedLevel) {
            case "BEGINNER", "ELEMENTARY" -> List.of("BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT");
            case "INTERMEDIATE" -> List.of("INTERMEDIATE", "BEGINNER", "ADVANCED", "EXPERT");
            case "ADVANCED" -> List.of("ADVANCED", "INTERMEDIATE", "EXPERT", "BEGINNER");
            case "EXPERT" -> List.of("EXPERT", "ADVANCED", "INTERMEDIATE", "BEGINNER");
            default -> List.of("INTERMEDIATE", "BEGINNER", "ADVANCED", "EXPERT");
        };
    }

    private int minimumSkillJourneyBankCount(int requestedQuestionCount) {
        return Math.min(
                requestedQuestionCount,
                Math.max(MIN_SKILL_JOURNEY_BANK_SEED_QUESTIONS, Math.min(10, requestedQuestionCount / 2)));
    }

    private List<QuestionInfo> mergeUniqueQuestions(
            List<QuestionInfo> primary, List<QuestionInfo> secondary, int limit) {
        Map<String, QuestionInfo> merged = new LinkedHashMap<>();
        addUniqueQuestions(merged, primary, limit);
        addUniqueQuestions(merged, secondary, limit);
        return new ArrayList<>(merged.values());
    }

    private int addUniqueQuestions(
            Map<String, QuestionInfo> target, List<QuestionInfo> candidates, int limit) {
        if (target.size() >= limit || candidates == null || candidates.isEmpty()) {
            return 0;
        }

        int added = 0;
        for (QuestionInfo question : candidates) {
            if (question == null || target.size() >= limit) {
                break;
            }
            String key = questionIdentityKey(question);
            if (key.isBlank() || target.containsKey(key)) {
                continue;
            }
            target.put(key, question);
            added++;
        }
        return added;
    }

    private String questionIdentityKey(QuestionInfo question) {
        if (question == null) {
            return "";
        }
        Long questionId = question.questionId();
        if (questionId != null && questionId > 0) {
            return "id:" + questionId;
        }
        String normalizedQuestion = canonicalizeSkillKey(question.question());
        return normalizedQuestion.isBlank() ? "" : "text:" + normalizedQuestion;
    }

    private String canonicalizeSkillKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("[.,;:'\"!?()\\[\\]{}]", "");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String getSkillAreasAlreadyCovered(List<QuestionInfo> questions) {
        return questions.stream()
                .map(QuestionInfo::skillArea)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(", "));
    }

    private UserAssessmentInfo buildUserAssessmentInfo(StartJourneyRequest assessmentData) {
        return new UserAssessmentInfo(
                assessmentData.getDomain(),
                assessmentData.getGoal(),
                assessmentData.getLevel(),
                assessmentData.getSkills(),
            assessmentData.getExistingSkills(),
                assessmentData.getFocusAreas(),
                assessmentData.getLanguage(),
                assessmentData.getDuration(),
                resolveAssessmentQuestionCount(assessmentData)
        );
    }

    /**
     * Fallback: Generate test entirely via AI (original behavior).
     */
    private String callChatApiWithRetry(String prompt) {
        String aiResponse = null;
        int maxAttempts = 2;
        Exception lastException = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                aiResponse = getChatClient().prompt().user(prompt).call().content();
                lastException = null;
                break;
            } catch (Exception e) {
                lastException = e;
                boolean isTimeout = e instanceof java.net.SocketTimeoutException
                        || (e.getCause() instanceof java.net.SocketTimeoutException)
                        || (e.getMessage() != null && e.getMessage().contains("Read timed out"));
                if (isTimeout && attempt < maxAttempts) {
                    log.warn("AI test generation timed out on attempt {}/{}. Retrying...", attempt, maxAttempts);
                    try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    log.error("AI test generation failed on attempt {}/{}: {}", attempt, maxAttempts, e.getMessage());
                    throw new RuntimeException(
                            "Dịch vụ AI hiện đang bận hoặc mất kết nối. Vui lòng thử lại sau vài phút.", e);
                }
            }
        }
        if (lastException != null || aiResponse == null) {
            throw new RuntimeException(
                    "Dịch vụ AI hiện đang bận hoặc mất kết nối. Vui lòng thử lại sau vài phút.", lastException);
        }
        return aiResponse;
    }

    private GenerateTestResponse generateTestFromAI(Journey journey, User user, String domain,
            StartJourneyRequest assessmentData, long generatedTestCount, AssessmentGenerationContext generationContext) {

        normalizeJourneyRequestDefaults(assessmentData);
        assessmentData.setLevel(generationContext.testedLevel().name());

        int requestedQuestionCount = resolveAssessmentQuestionCount(assessmentData);
        int requestedTimeLimitMinutes = resolveAssessmentTimeLimitMinutes(assessmentData);

        log.info("Calling AI to generate specialized test for domain in 2 batches of 25 questions. Domain: {}", domain);

        List<Object> allQuestions = new ArrayList<>();
        Map<String, Object> finalTestData = new LinkedHashMap<>();

        // Batch 1: Request 25 questions
        UserAssessmentInfo userInfo1 = new UserAssessmentInfo(
                assessmentData.getDomain(),
                assessmentData.getGoal(),
                assessmentData.getLevel(),
                assessmentData.getSkills(),
                assessmentData.getExistingSkills(),
                assessmentData.getFocusAreas(),
                assessmentData.getLanguage(),
                "STANDARD",
                25
        );
        String prompt1 = assessmentPromptService.getTestGenerationPrompt(
                domain,
                assessmentData.getIndustry(),
                assessmentData.getJobRole(),
                userInfo1
        );

        String response1 = callChatApiWithRetry(prompt1);
        Map<String, Object> testData1;
        try {
            String jsonStr = extractJsonFromResponse(response1);
            testData1 = objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse Batch 1 AI response: {}", response1);
            throw new RuntimeException("Failed to generate test: Invalid AI response in Batch 1", e);
        }

        List<Object> questions1 = normalizeGeneratedQuestions(testData1.get("questions"), 25);
        if (questions1.isEmpty()) {
            throw new RuntimeException("Failed to generate test: Batch 1 AI response missing questions");
        }
        allQuestions.addAll(questions1);

        finalTestData.put("title", testData1.get("title"));
        finalTestData.put("description", testData1.get("description"));
        finalTestData.put("targetField", testData1.get("targetField"));

        // Extract generated skill areas to exclude in Batch 2
        String excludeSkills = questions1.stream()
                .filter(Map.class::isInstance)
                .map(q -> (String) ((Map<?, ?>) q).get("skillArea"))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(", "));

        // Batch 2: Request 25 questions
        UserAssessmentInfo userInfo2 = new UserAssessmentInfo(
                assessmentData.getDomain(),
                assessmentData.getGoal(),
                assessmentData.getLevel(),
                assessmentData.getSkills(),
                assessmentData.getExistingSkills(),
                assessmentData.getFocusAreas(),
                assessmentData.getLanguage(),
                "STANDARD",
                25
        );
        String prompt2 = assessmentPromptService.getTestGenerationPrompt(
                domain,
                assessmentData.getIndustry(),
                assessmentData.getJobRole(),
                userInfo2
        ) + "\n\nYÊU CẦU BỔ SUNG: Đây là phần 2 của bài kiểm tra. Vui lòng tạo 25 câu hỏi KHÁC BIỆT hoàn toàn với các chủ đề sau đã có trong phần 1: " + excludeSkills + ". Bắt đầu ID câu hỏi từ " + (questions1.size() + 1) + " đến " + (questions1.size() + 25) + ".";

        String response2 = callChatApiWithRetry(prompt2);
        Map<String, Object> testData2;
        try {
            String jsonStr = extractJsonFromResponse(response2);
            testData2 = objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse Batch 2 AI response: {}", response2);
            throw new RuntimeException("Failed to generate test: Invalid AI response in Batch 2", e);
        }

        List<Object> questions2 = normalizeGeneratedQuestions(testData2.get("questions"), 25);
        if (questions2.isEmpty()) {
            throw new RuntimeException("Failed to generate test: Batch 2 AI response missing questions");
        }

        // Correct question IDs for Batch 2 so they are sequential
        int startId = questions1.size() + 1;
        for (Object q : questions2) {
            if (q instanceof Map) {
                ((Map<String, Object>) q).put("questionId", startId++);
            }
        }
        allQuestions.addAll(questions2);

        int finalQuestionCount = allQuestions.size();

        AssessmentTest test = AssessmentTest.builder()
                .journey(journey)
                .title((String) finalTestData.get("title"))
                .description((String) finalTestData.get("description"))
                .targetField((String) finalTestData.get("targetField"))
                .status(AssessmentTest.TestStatus.PENDING)
                .questionCount(finalQuestionCount)
                .timeLimitMinutes(requestedTimeLimitMinutes)
                .difficultyLevel(generationContext.testedLevel().name())
                .assessmentPhase(generationContext.phase())
                .baseLevel(generationContext.baseLevel().name())
                .testedLevel(generationContext.testedLevel().name())
                .parentTestId(generationContext.parentTestId())
                .questionSource(QUESTION_SOURCE_AI)
                .questionsJson(objectMapper.valueToTree(allQuestions).toString())
                .generationPrompt(prompt1 + "\n\n=== BATCH 2 ===\n\n" + prompt2)
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

    private void validateAndNormalizeAdminManagedDomain(StartJourneyRequest request) {
        String normalizedDomain = normalizeDomainCode(request != null ? request.getDomain() : null);
        if (normalizedDomain == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Domain không hợp lệ. Vui lòng chọn domain đang hoạt động do admin quản lý.");
        }

        Domain domain = domainRepository.findByCodeIgnoreCase(normalizedDomain)
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                        "Domain không hợp lệ. Vui lòng chọn domain đang hoạt động do admin quản lý."));
        if (domain.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Domain đã ngừng kích hoạt. Vui lòng chọn domain khác do admin quản lý.");
        }

        request.setDomain(normalizeDomainCode(domain.getCode()));
    }

    private String normalizeDomainCode(String domainCode) {
        if (domainCode == null || domainCode.isBlank()) {
            return null;
        }
        return domainCode.trim().toUpperCase(Locale.ROOT);
    }

    private Optional<JobPositionJourneyContext> resolveJobPositionJourneyContext(StartJourneyRequest request) {
        if (request == null) {
            return Optional.empty();
        }
        Long trackId = request.getJobPositionTrackId();
        Long jobPositionId = request.getJobPositionId();
        if (trackId == null) {
            trackId = inferTrackIdFromRequest(request);
        }
        if (trackId == null) {
            return Optional.empty();
        }
        Long resolvedTrackId = trackId;

        JobPositionTrack track = jobPositionTrackRepository.findById(resolvedTrackId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Job position track not found: " + resolvedTrackId));
        if (track.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "Job position track is not active");
        }
        if (jobPositionId != null && !Objects.equals(jobPositionId, track.getJobPositionId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Job position track does not belong to selected job position");
        }

        JobPosition jobPosition = jobPositionRepository.findById(track.getJobPositionId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Job position not found: " + track.getJobPositionId()));
        if (jobPosition.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "Job position is not active");
        }
        Domain domain = domainRepository.findById(jobPosition.getDomainId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Domain not found: " + jobPosition.getDomainId()));
        if (domain.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new ApiException(ErrorCode.CONFLICT, "Domain is not active");
        }

        List<JobPositionTrackSkill> trackSkills = jobPositionTrackSkillRepository
                .findActiveSkillsByTrackId(track.getId(), SkillStatus.ACTIVE);
        if (trackSkills.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "Job position track does not have active skills for assessment");
        }
        return Optional.of(new JobPositionJourneyContext(domain, jobPosition, track, trackSkills));
    }

    private Optional<JobPositionJourneyContext> resolveJobPositionJourneyContext(Journey journey) {
        if (journey == null || journey.getJobPositionTrackId() == null) {
            return Optional.empty();
        }
        StartJourneyRequest request = StartJourneyRequest.builder()
                .domain(journey.getDomain())
                .jobRole(journey.getJobRole())
                .jobPositionTrackId(journey.getJobPositionTrackId())
                .build();
        return resolveJobPositionJourneyContext(request);
    }

    private Long inferTrackIdFromRequest(StartJourneyRequest request) {
        if (request == null || request.getJobRole() == null || request.getJobRole().isBlank()) {
            return null;
        }
        String requestedRole = canonicalizeSkillKey(request.getJobRole());
        List<JobPositionTrack> candidates = jobPositionTrackRepository.findAllActiveWithActiveParentChain(TaxonomyStatus.ACTIVE);
        return candidates.stream()
                .filter(track -> request.getJobPositionId() == null
                        || Objects.equals(track.getJobPositionId(), request.getJobPositionId()))
                .filter(track -> {
                    String trackName = canonicalizeSkillKey(track.getName());
                    String trackCode = canonicalizeSkillKey(track.getCode());
                    return trackName.equals(requestedRole)
                            || trackCode.equals(requestedRole)
                            || trackName.contains(requestedRole)
                            || requestedRole.contains(trackName);
                })
                .map(JobPositionTrack::getId)
                .findFirst()
                .orElse(null);
    }

    private void applyJobPositionContextToRequest(StartJourneyRequest request, JobPositionJourneyContext context) {
        request.setType("CAREER");
        request.setDomain(context.domain().getCode());
        request.setJobPositionId(context.jobPosition().getId());
        request.setJobPositionTrackId(context.track().getId());
        request.setJobRole(context.jobPosition().getName());
        request.setSubCategory(context.track().getName());
        request.setIndustry(context.jobPosition().getName());
        request.setQuestionCount(DEFAULT_ASSESSMENT_QUESTION_COUNT);
        request.setDuration("STANDARD");
        request.setSkills(context.trackSkills().stream()
                .map(this::resolveTrackSkillName)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    }

    private String resolvePrimarySkillName(JobPositionJourneyContext context, StartJourneyRequest request) {
        if (context != null) {
            return context.trackSkills().stream()
                    .map(this::resolveTrackSkillName)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .map(SkillNameUtils::normalize)
                    .orElse(null);
        }
        if (request != null && request.getSkills() != null && !request.getSkills().isEmpty()) {
            return SkillNameUtils.normalize(request.getSkills().get(0));
        }
        return null;
    }

    private String resolveTrackSkillName(JobPositionTrackSkill trackSkill) {
        if (trackSkill == null || trackSkill.getSkill() == null) {
            return null;
        }
        return firstNonBlank(trackSkill.getSkill().getName(), trackSkill.getSkill().getCanonicalKey());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize journey metadata: {}", e.getMessage());
            return null;
        }
    }

    private int resolveAssessmentQuestionCount(StartJourneyRequest assessmentData) {
        return DEFAULT_ASSESSMENT_QUESTION_COUNT;
    }

    private int resolveAssessmentTimeLimitMinutes(StartJourneyRequest assessmentData) {
        return DEFAULT_ASSESSMENT_TIME_LIMIT_MINUTES;
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

    private void normalizeJourneyRequestDefaults(StartJourneyRequest request) {
        if (request == null) {
            return;
        }
        request.setLanguage("VI");
        if (request.getLevel() == null || request.getLevel().isBlank()) {
            request.setLevel(Journey.SkillLevel.BEGINNER.name());
        }
        request.setQuestionCount(DEFAULT_ASSESSMENT_QUESTION_COUNT);
        request.setDuration("STANDARD");
    }

    private StartJourneyRequest readAssessmentData(Journey journey) {
        if (journey == null || journey.getAssessmentData() == null || journey.getAssessmentData().isBlank()) {
            return null;
        }
        try {
            StartJourneyRequest assessmentData = objectMapper.readValue(journey.getAssessmentData(), StartJourneyRequest.class);
            normalizeJourneyRequestDefaults(assessmentData);
            return assessmentData;
        } catch (Exception e) {
            log.warn("Failed to parse journey assessment data for journey {}", journey.getId(), e);
            return null;
        }
    }

    private AssessmentGenerationContext resolveAssessmentGenerationContext(
            StartJourneyRequest assessmentData,
            TestResult latestResult) {
        Journey.SkillLevel baseLevel = resolveBaseLevel(assessmentData);
        if (latestResult != null) {
            EvaluationSnapshot snapshot = buildSnapshotFromStoredResult(latestResult, null);
            if (shouldRecommendChallengeUp(latestResult, snapshot, assessmentData)) {
                AssessmentTest latestTest = latestResult.getAssessmentTest();
                Journey.SkillLevel latestTestedLevel = resolveTestedLevel(latestTest, assessmentData);
                Journey.SkillLevel nextLevel = nextLevel(latestTestedLevel);
                if (nextLevel != null) {
                    return new AssessmentGenerationContext(
                            ASSESSMENT_PHASE_CHALLENGE_UP,
                            baseLevel,
                            nextLevel,
                            latestTest != null ? latestTest.getId() : null);
                }
            }
        }
        return new AssessmentGenerationContext(
                ASSESSMENT_PHASE_PLACEMENT,
                baseLevel,
                baseLevel,
                null);
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
                .assessmentPhase(test.getAssessmentPhase())
                .baseLevel(test.getBaseLevel())
                .testedLevel(test.getTestedLevel())
                .parentTestId(test.getParentTestId())
                .questionSource(test.getQuestionSource())
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
                    : DEFAULT_RECOVERED_QUESTION_COUNT;

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

    private boolean shouldEnrichQuestionBank(AssessmentTest test) {
        if (test == null) {
            return false;
        }
        if (test.getQuestionBank() == null) {
            return true;
        }
        String generationPrompt = Optional.ofNullable(test.getGenerationPrompt()).orElse("");
        return generationPrompt.startsWith(HYBRID_QB_PROMPT_PREFIX);
    }

    private QuestionBankResponse resolveOrCreateQuestionBankForEnrichment(
            Journey journey,
            AssessmentTest test,
            String domain,
            String industry,
            String jobRole) {
        Long bankId = resolveQuestionBankId(test);
        if (bankId != null) {
            return questionBankService.getBankById(bankId);
        }

        JobPositionJourneyContext jobContext = resolveJobPositionJourneyContext(journey).orElse(null);
        if (jobContext != null) {
            Optional<QuestionBankResponse> roleBank = questionBankService.findActiveBank(
                    jobContext.domain().getId(),
                    jobContext.jobPosition().getId());
            if (roleBank.isPresent()) {
                return roleBank.get();
            }

            var createRequest = com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankRequest.builder()
                    .domainId(jobContext.domain().getId())
                    .jobPositionId(jobContext.jobPosition().getId())
                    .domain(jobContext.domain().getCode())
                    .title("Auto bank: " + jobContext.domain().getCode() + " / " + jobContext.jobPosition().getName())
                    .description("Auto-generated question bank from AI test submissions")
                    .build();
            QuestionBankResponse createdBank = questionBankService.createBank(createRequest);
            log.info("Auto-created taxonomy question bank {} for domainId={}, jobPositionId={}",
                    createdBank.getId(), jobContext.domain().getId(), jobContext.jobPosition().getId());
            return createdBank;
        }

        Optional<QuestionBankResponse> existingBank = resolveQuestionBankForJourney(
                journey,
                domain,
                firstNonBlank(industry, journey != null ? journey.getIndustry() : null, journey != null ? journey.getSubCategory() : null),
                firstNonBlank(jobRole, journey != null ? journey.getJobRole() : null));
        if (existingBank.isPresent()) {
            return existingBank.get();
        }

        String resolvedIndustry = firstNonBlank(
                industry,
                journey != null ? journey.getIndustry() : null,
                journey != null ? journey.getSubCategory() : null);
        String resolvedJobRole = firstNonBlank(jobRole, journey != null ? journey.getJobRole() : null);

        var createRequest = com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankRequest.builder()
                .domain(domain)
                .title("Auto bank: " + domain + " / general")
                .description("Auto-generated question bank from AI test submissions")
                .build();
        QuestionBankResponse createdBank = questionBankService.createBank(createRequest);
        log.info("Auto-created question bank {} for domain={}, industry={}, jobRole={}",
                createdBank.getId(), domain, resolvedIndustry, resolvedJobRole);
        return createdBank;
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
                .assessmentPhase(test.getAssessmentPhase())
                .baseLevel(test.getBaseLevel())
                .testedLevel(test.getTestedLevel())
                .parentTestId(test.getParentTestId())
                .questionSource(test.getQuestionSource())
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
        normalizeJourneyRequestDefaults(assessmentData);
        String goal = assessmentData != null ? assessmentData.getGoal() : null;
        String industry = assessmentData != null ? assessmentData.getIndustry() : null;
        String jobRole = assessmentData != null ? assessmentData.getJobRole() : null;

        Map<Long, String> normalizedUserAnswers = normalizeUserAnswers(request.getAnswers());
        List<QuestionEvaluation> questionEvaluations = evaluateQuestionAnswers(questions, normalizedUserAnswers);

        int totalQuestions = questionEvaluations.size();
        int correctAnswers = (int) questionEvaluations.stream().filter(q -> q.correct).count();
        int answeredQuestions = (int) questionEvaluations.stream()
                .filter(q -> q.userAnswer != null && !q.userAnswer.isBlank())
                .count();
        int scorePercentage = calculateScorePercentage(correctAnswers, totalQuestions);
        Journey.SkillLevel evaluatedLevel = determineEvaluatedLevel(
                scorePercentage,
                answeredQuestions,
                totalQuestions,
                test,
                assessmentData);

        EvaluationSnapshot snapshot = createEvaluationSnapshot(
                totalQuestions,
                answeredQuestions,
                correctAnswers,
                scorePercentage,
                evaluatedLevel);

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

        // [Nghiệp vụ] Dùng deterministic scoring thuần túy — không gọi AI để tránh chậm trễ.
        // Level và scoreBand được xác định trực tiếp từ % điểm đúng/sai, không phụ thuộc LLM.
        List<Map<String, Object>> finalSkillGaps = derivedSkillGaps;
        List<Map<String, Object>> finalStrengths = derivedStrengths;
        String finalSummary = deterministicSummary;
        String finalDetailedFeedback = buildDeterministicDetailedFeedback(
                domain,
                scorePercentage,
                evaluatedLevel,
                snapshot,
                finalSkillGaps,
                finalStrengths,
                Collections.emptyList()
        );
        List<String> finalHighlightKeywords = buildHighlightKeywords(
                Collections.emptyList(),
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

        // === Save AI-generated questions to question bank for enrichment ===
        if (shouldEnrichQuestionBank(test)) {
            try {
                log.info("Attempting to save {} questions to question bank for domain={}, industry={}, jobRole={}",
                        questions.size(), domain, industry, jobRole);
                QuestionBankResponse bank = resolveOrCreateQuestionBankForEnrichment(
                        journey,
                        test,
                        domain,
                        industry,
                        jobRole);
                Optional<QuestionBankResponse> bankOpt = Optional.of(bank);
                if (bankOpt.isEmpty()) {
                    log.info("No question bank found for domain={}, jobRole={} — auto-creating one",
                            domain, jobRole);
                    var createRequest = com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankRequest.builder()
                            .domain(domain)
                            .title("Auto bank: " + domain + " / " + (jobRole != null ? jobRole : "general"))
                            .description("Auto-generated question bank from AI test submissions")
                            .build();
                    bank = questionBankService.createBank(createRequest);
                    log.info("Auto-created question bank {} for domain={}, industry={}, jobRole={}",
                            bank.getId(), domain, industry, jobRole);
                } else {
                    bank = bankOpt.get();
                    log.info("Found existing question bank {} for enrichment", bank.getId());
                }
                List<QuestionInfo> questionInfos = questions.stream()
                        .map(q -> new QuestionInfo(
                                0L,
                                (String) q.get("question"),
                                (List<String>) q.get("options"),
                                (String) q.get("correctAnswer"),
                                (String) q.get("explanation"),
                                (String) q.get("difficulty"),
                                (String) q.get("skillArea")
                        ))
                        .collect(Collectors.toList());
                questionBankQuestionService.saveQuestionsFromTest(bank.getId(), questionInfos);
                log.info("Enriched question bank {} with {} AI-generated questions after test submission",
                        bank.getId(), questionInfos.size());
            } catch (Exception e) {
                log.warn("Failed to enrich question bank after test submission for journey {}: {}",
                        journeyId, e.getMessage(), e);
            }
        } else {
            log.info("Test {} used bank {} — skipping question bank enrichment",
                    test.getId(), test.getQuestionBank().getId());
        }

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

        StartJourneyRequest assessmentData = readAssessmentData(journey);
        EvaluationSnapshot latestSnapshot = buildSnapshotFromStoredResult(latestResult, null);
        if (canGenerateChallengeUp(latestResult, latestSnapshot, assessmentData)) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Bạn đủ điều kiện làm bài challenge-up trước khi tạo roadmap. Vui lòng hoàn thành bài challenge-up để công nhận level chính xác hơn.");
        }

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
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Journey not found"));

        RoadmapSession roadmapSession = requireOwnedRoadmapSession(user, journey.getRoadmapSessionId());
        return createStudyPlanForRoadmapNodeInternal(user, roadmapSession, journey, nodeId, request);
    }

    @Override
    @Transactional
    public Object createStudyPlanForRoadmapNode(User user, Long roadmapSessionId, String nodeId, GenerateScheduleRequest request) {
        RoadmapSession roadmapSession = requireOwnedRoadmapSession(user, roadmapSessionId);
        Journey journey = findJourneyContextForRoadmapSession(user, roadmapSessionId).orElse(null);
        return createStudyPlanForRoadmapNodeInternal(user, roadmapSession, journey, nodeId, request);
    }

    private Object createStudyPlanForRoadmapNodeInternal(
            User user,
            RoadmapSession roadmapSession,
            Journey journey,
            String nodeId,
            GenerateScheduleRequest request) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Node id is required");
        }

        String normalizedNodeId = nodeId.trim();
        RoadmapResponse roadmap = requireRoadmapForSession(user, roadmapSession);
        List<RoadmapResponse.RoadmapNode> roadmapNodes = roadmap.getRoadmap() != null
                ? roadmap.getRoadmap()
                : Collections.emptyList();

        RoadmapResponse.RoadmapNode node = roadmapNodes.stream()
                .filter(item -> item != null && normalizedNodeId.equals(item.getId()))
                .findFirst()
                .orElse(null);

        if (node == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Roadmap node not found for id: " + normalizedNodeId);
        }

        if ("LOCKED".equals(node.getNodeStatus())) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Node này đang bị khóa. Vui lòng hoàn thành các node prerequisite trước.");
        }

        if (isRoadmapNodeCompleted(roadmap, normalizedNodeId)) {
            return Map.of(
                    "message", "Node này đã hoàn thành. Hãy tạo/kích hoạt plan cho node tiếp theo.",
                    "created", false,
                    "journeyId", journey != null ? journey.getId() : null,
                    "roadmapSessionId", roadmapSession.getId(),
                    "nodeId", normalizedNodeId);
        }

        RoadmapResponse.RoadmapNode targetNode = findNodeById(roadmapNodes, normalizedNodeId);
        if (targetNode == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Node not found");
        }

        // Only enforce next eligible check for MAIN nodes. SIDE nodes are optional.
        if (targetNode.getType() == RoadmapResponse.RoadmapNode.NodeType.MAIN) {
            String nextEligibleMainNodeId = findNextEligibleMainNodeId(roadmap, roadmapNodes);
            if (nextEligibleMainNodeId != null && !normalizedNodeId.equals(nextEligibleMainNodeId)) {
                String nextNodeTitle = resolveNodeDisplayTitle(roadmapNodes, nextEligibleMainNodeId);
                throw new ApiException(ErrorCode.FORBIDDEN,
                        String.format("Bạn cần hoàn thành node '%s' trước khi tạo plan cho node này.", nextNodeTitle));
            }
        }

        int nodeOrder = resolveNodeOrder(roadmapNodes, normalizedNodeId);
        int totalRoadmapNodes = countTrackableRoadmapNodes(roadmapNodes);

        List<TaskColumnResponse> board = taskBoardService.getBoard(user.getId());
        UUID todoColumnId = resolveTodoColumnId(board);
        List<TaskResponse> existingTasks = flattenBoardTasks(board);
        String marker = buildStudyPlanMarker(journey != null ? journey.getId() : null, roadmapSession.getId(), normalizedNodeId);
        List<TaskResponse> existingTasksForNode = findExistingNodeTasks(existingTasks, roadmapSession.getId(), normalizedNodeId);

        if (!existingTasksForNode.isEmpty()) {
            TaskResponse firstTask = existingTasksForNode.get(0);
            return buildStudyPlanResponse(
                    "message", "Roadmap node is already linked to study planner tasks.",
                    "created", false,
                    "journeyId", journey != null ? journey.getId() : null,
                    "roadmapSessionId", roadmapSession.getId(),
                    "nodeId", normalizedNodeId,
                    "taskCount", existingTasksForNode.size(),
                    "task", toTaskSummary(firstTask),
                    "tasks", existingTasksForNode.stream()
                            .map(this::toTaskSummary)
                            .collect(Collectors.toList()));
        }

        GenerateScheduleRequest scheduleRequest = buildRoadmapNodeScheduleRequest(roadmapSession, journey, node, request);
        List<StudySessionResponse> plannedSessions = generateNodeStudySessions(user, node, scheduleRequest);
        List<TaskResponse> createdTasks = createTasksFromPlannedSessions(
                user,
                roadmapSession,
                journey,
                node,
                todoColumnId,
                marker,
                plannedSessions,
                nodeOrder,
                totalRoadmapNodes,
                scheduleRequest);

        if (createdTasks.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Unable to create study tasks for this roadmap node");
        }

        if (journey != null) {
            journey.setStatus(Journey.JourneyStatus.STUDY_PLAN_IN_PROGRESS);
            journey.setLastActivityAt(Instant.now());
            if (journey.getProgressPercentage() == null || journey.getProgressPercentage() < 40) {
                journey.setProgressPercentage(40);
            }
            journeyRepository.save(journey);
        }

        return buildStudyPlanResponse(
                "message", "Study planner tasks created from roadmap node.",
                "created", true,
                "journeyId", journey != null ? journey.getId() : null,
                "roadmapSessionId", roadmapSession.getId(),
                "nodeId", normalizedNodeId,
                "taskCount", createdTasks.size(),
                "task", toTaskSummary(createdTasks.get(0)),
                "tasks", createdTasks.stream()
                        .map(this::toTaskSummary)
                        .collect(Collectors.toList()));
    }

    private RoadmapSession requireOwnedRoadmapSession(User user, Long roadmapSessionId) {
        if (roadmapSessionId == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Roadmap session id is required");
        }
        return roadmapSessionRepository.findByIdAndUserId(roadmapSessionId, user.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found"));
    }

    private Optional<Journey> findJourneyContextForRoadmapSession(User user, Long roadmapSessionId) {
        Optional<Journey> journey = journeyRepository.findByRoadmapSessionId(roadmapSessionId);
        if (journey.isPresent() && !Objects.equals(journey.get().getUser().getId(), user.getId())) {
            log.warn("Ignoring mismatched journey {} for roadmap session {} and user {}",
                    journey.get().getId(), roadmapSessionId, user.getId());
            return Optional.empty();
        }
        return journey;
    }

    private RoadmapResponse requireRoadmapForSession(User user, RoadmapSession roadmapSession) {
        if (roadmapSession == null || roadmapSession.getId() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Please generate roadmap first");
        }
        return aiRoadmapService.getRoadmapById(roadmapSession.getId(), user.getId());
    }

    private RoadmapResponse requireRoadmapForJourney(User user, Journey journey) {
        return requireRoadmapForSession(user, requireOwnedRoadmapSession(user, journey != null ? journey.getRoadmapSessionId() : null));
    }

    private UUID resolveTodoColumnId(List<TaskColumnResponse> board) {
        if (board == null || board.isEmpty()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Study planner board is unavailable");
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
                        .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "No study planner column found")));
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
        StringBuilder marker = new StringBuilder(STUDY_PLAN_LINK_MARKER_PREFIX);
        if (journeyId != null) {
            marker.append(" journey=").append(journeyId);
        }
        marker.append(" roadmap=").append(roadmapSessionId);
        marker.append(" node=").append(nodeId);
        return marker.toString();
    }

    private Optional<RoadmapNodeLinkRef> parseStudyPlanMarker(String notes) {
        if (notes == null || notes.isBlank() || !notes.contains(STUDY_PLAN_LINK_MARKER_PREFIX)) {
            return Optional.empty();
        }

        Matcher roadmapMatcher = Pattern.compile("\\broadmap=(\\d+)\\b", Pattern.CASE_INSENSITIVE).matcher(notes);
        Matcher nodeMatcher = Pattern.compile("\\bnode=([^\\s]+)\\b", Pattern.CASE_INSENSITIVE).matcher(notes);
        if (!roadmapMatcher.find() || !nodeMatcher.find()) {
            return Optional.empty();
        }

        Matcher journeyMatcher = Pattern.compile("\\bjourney=(\\d+)\\b", Pattern.CASE_INSENSITIVE).matcher(notes);
        Long journeyId = journeyMatcher.find() ? safeParseLong(journeyMatcher.group(1)) : null;
        Long matchedRoadmapId = safeParseLong(roadmapMatcher.group(1));
        String matchedNodeId = nodeMatcher.group(1) != null ? nodeMatcher.group(1).trim() : null;
        if (matchedRoadmapId == null || matchedNodeId == null || matchedNodeId.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new RoadmapNodeLinkRef(journeyId, matchedRoadmapId, matchedNodeId));
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

    private List<TaskResponse> findExistingNodeTasks(List<TaskResponse> existingTasks, Long roadmapSessionId, String nodeId) {
        return existingTasks.stream()
                .filter(task -> parseStudyPlanMarker(task.getUserNotes())
                        .map(link -> Objects.equals(link.roadmapSessionId(), roadmapSessionId)
                                && Objects.equals(link.nodeId(), nodeId))
                        .orElse(false))
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildStudyPlanResponse(Object... entries) {
        Map<String, Object> response = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            Object value = entries[i + 1];
            if (value != null) {
                response.put(String.valueOf(entries[i]), value);
            }
        }
        return response;
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
            RoadmapSession roadmapSession,
            Journey journey,
            RoadmapResponse.RoadmapNode node,
            GenerateScheduleRequest inputRequest) {

        GenerateScheduleRequest request = new GenerateScheduleRequest();
        copyScheduleRequest(inputRequest, request);

        LocalDate today = nowInStudyZone(request.getTimezone()).toLocalDate();
        LocalDate requestedStartDate = request.getStartDate() != null ? request.getStartDate() : today;
        LocalDate startDate = requestedStartDate.isAfter(today) ? requestedStartDate : today.plusDays(1);
        int durationMinutes = safeDurationMinutes(request.getDurationMinutes());
        int maxSessionsPerDay = safeMaxSessionsPerDay(request.getMaxSessionsPerDay());

        request.setSubjectName(safeTruncate(firstNonBlank(
                node.getTitle(),
                journey != null ? journey.getTitle() : null,
                roadmapSession != null ? roadmapSession.getTitle() : null,
                "Roadmap node"), 200, "Roadmap node"));
        request.setTopics(collectNodeTopics(node, 16));
        request.setDesiredOutcome(firstNonBlank(request.getDesiredOutcome(), buildDefaultDesiredOutcome(roadmapSession, journey, node)));
        request.setFreeTimeDescription(firstNonBlank(
                request.getFreeTimeDescription(),
                "Auto-generated from roadmap node and user preferences"));
        request.setDurationMinutes(durationMinutes);
        request.setStartDate(startDate);
        request.setTimezone(firstNonBlank(request.getTimezone(), DEFAULT_STUDY_TIMEZONE));
        request.setPreferredDays(normalizeRoadmapNodePreferredDays(request.getPreferredDays()));
        if (request.getStudyPreference() == null || request.getStudyPreference().isBlank()) {
            request.setStudyPreference("flexible");
        }
        if (request.getPreferredTimeWindows() == null || request.getPreferredTimeWindows().isEmpty()) {
            request.setPreferredTimeWindows(defaultPreferredTimeWindows(request.getStudyPreference()));
        }
        // Defensive: if user submitted deadline < startDate, auto-override and log
        if (request.getDeadline() != null && request.getStartDate() != null
                && request.getDeadline().isBefore(request.getStartDate())) {
            log.warn("[StudyPlan] Invalid deadline {} < startDate {} for node {}, auto-overriding",
                    request.getDeadline(), request.getStartDate(), node.getId());
            request.setDeadline(null);
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
        // Propagate suggestedModuleIds from the roadmap node so AiStudySupportServiceImpl
        // can load course content for the AI prompt
        request.setSuggestedModuleIds(node.getSuggestedModuleIds());
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
        target.setSuggestedModuleIds(source.getSuggestedModuleIds() != null
                ? new ArrayList<>(source.getSuggestedModuleIds())
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
        int maxSessionsPerDay = safeMaxSessionsPerDay(request.getMaxSessionsPerDay());
        int targetSessionCount = resolveRoadmapNodeTargetSessionCount(node, request);
        LocalDate baseDate = request.getStartDate() != null
                ? request.getStartDate()
                : LocalDate.now(resolveStudyTimeZone(request.getTimezone()));
        List<String> preferredDays = normalizeRoadmapNodePreferredDays(request.getPreferredDays());
        
        LocalDateTime nowInZone = nowInStudyZone(request.getTimezone());
        
        Map<LocalDate, Integer> sessionsPerDay = new HashMap<>();
        Map<LocalDate, List<LocalDateTime[]>> occupiedSlotsByDay = new HashMap<>();
        List<StudySessionResponse> normalized = new ArrayList<>();
        LocalDateTime fallbackCursor = LocalDateTime.of(baseDate, resolvePreferredStartTime(request));

        for (StudySessionResponse session : sessions) {
            if (normalized.size() >= targetSessionCount) {
                break;
            }
            if (session == null) {
                continue;
            }
            LocalDateTime startTime = resolveNextStudySlot(
                fallbackCursor,
                request,
                durationMinutes,
                sessionsPerDay,
                occupiedSlotsByDay,
                preferredDays,
                maxSessionsPerDay,
                breakMinutes,
                nowInZone
            );

            LocalDateTime endTime = startTime.plusMinutes(durationMinutes);

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
        }

        List<String> focusItems = collectNodeTopics(node, 20);
        if (focusItems.isEmpty()) {
            focusItems = List.of(firstNonBlank(node.getDescription(), node.getTitle(), "Core topic"));
        }

        while (normalized.size() < targetSessionCount) {
            LocalDateTime startTime = resolveNextStudySlot(
                fallbackCursor,
                request,
                durationMinutes,
                sessionsPerDay,
                occupiedSlotsByDay,
                preferredDays,
                maxSessionsPerDay,
                breakMinutes,
                nowInZone
            );
            LocalDateTime endTime = startTime.plusMinutes(durationMinutes);
            int step = normalized.size() + 1;
            String focus = focusItems.get((step - 1) % focusItems.size());
            normalized.add(StudySessionResponse.builder()
                    .title(safeTruncate(
                            String.format("%s - Step %d", firstNonBlank(node.getTitle(), "Roadmap node"), step),
                            255,
                            "Roadmap step"))
                    .description(safeTruncate(
                            String.format("Focus: %s%n%n%s", focus, buildNodeContextDescription(node)),
                            5000,
                            "Roadmap node practice"))
                    .startTime(startTime)
                    .endTime(endTime)
                    .build());
            fallbackCursor = endTime.plusMinutes(breakMinutes);
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
        List<String> preferredDays = normalizeRoadmapNodePreferredDays(request.getPreferredDays());
        
        LocalDateTime nowInZone = nowInStudyZone(request.getTimezone());
        
        Map<LocalDate, Integer> sessionsPerDay = new HashMap<>();
        Map<LocalDate, List<LocalDateTime[]>> occupiedSlotsByDay = new HashMap<>();

        int sessionCount = resolveRoadmapNodeTargetSessionCount(node, request);

        List<String> focusItems = collectNodeTopics(node, 20);
        if (focusItems.isEmpty()) {
            focusItems = List.of(firstNonBlank(node.getDescription(), node.getTitle(), "Core topic"));
        }

        List<StudySessionResponse> fallbackSessions = new ArrayList<>();
        LocalDateTime cursor = LocalDateTime.of(startDate, resolvePreferredStartTime(request));
        
        for (int i = 0; i < sessionCount; i++) {
            LocalDateTime startTime = resolveNextStudySlot(
                cursor,
                request,
                durationMinutes,
                sessionsPerDay,
                occupiedSlotsByDay,
                preferredDays,
                maxSessionsPerDay,
                breakMinutes,
                nowInZone
            );
            
            LocalDateTime endTime = startTime.plusMinutes(durationMinutes);
            
            if (startTime.toLocalDate().isAfter(deadline)) {
                break;
            }
            
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
                    
            cursor = endTime.plusMinutes(breakMinutes);
        }

        return fallbackSessions;
    }

    private List<TaskResponse> createTasksFromPlannedSessions(
            User user,
            RoadmapSession roadmapSession,
            Journey journey,
            RoadmapResponse.RoadmapNode node,
            UUID todoColumnId,
            String marker,
            List<StudySessionResponse> sessions,
            int nodeOrder,
            int totalRoadmapNodes,
            GenerateScheduleRequest request) {

        int targetSessionCount = resolveRoadmapNodeTargetSessionCount(node, request);
        List<StudySessionResponse> sourceSessions = sessions == null || sessions.isEmpty()
                ? buildFallbackSessions(node, request)
                : sessions.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        StudySessionResponse::getStartTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(targetSessionCount)
                .collect(Collectors.toList());

        int durationMinutes = safeDurationMinutes(request.getDurationMinutes());
        int breakMinutes = safeBreakMinutes(request.getBreakMinutesBetweenSessions());
        int maxSessionsPerDay = safeMaxSessionsPerDay(request.getMaxSessionsPerDay());
        LocalDate baseDate = request.getStartDate() != null
                ? request.getStartDate()
                : LocalDate.now(resolveStudyTimeZone(request.getTimezone()));
        List<String> preferredDays = normalizeRoadmapNodePreferredDays(request.getPreferredDays());
        
        LocalDateTime nowInZone = nowInStudyZone(request.getTimezone());
        
        Map<LocalDate, List<LocalDateTime[]>> occupiedSlotsByDay = new HashMap<>();
        Map<LocalDate, Integer> sessionsPerDay = new HashMap<>();
        LocalDateTime fallbackCursor = LocalDateTime.of(baseDate, resolvePreferredStartTime(request));

        // Pre-create StudySession entities so they can be linked to tasks.
        // This enables checkAndCompleteLinkedTasks() to auto-move tasks to Done
        // when all linked sessions are marked COMPLETED (GAP-2 fix).
        List<StudySession> createdSessions = new ArrayList<>();
        for (StudySessionResponse sessionResponse : sourceSessions) {
            LocalDateTime startTime = sessionResponse.getStartTime() != null ? sessionResponse.getStartTime() : fallbackCursor;
            startTime = resolveNextStudySlot(
                startTime,
                request,
                durationMinutes,
                sessionsPerDay,
                occupiedSlotsByDay,
                preferredDays,
                maxSessionsPerDay,
                breakMinutes,
                nowInZone
            );
            LocalDateTime endTime = startTime.plusMinutes(durationMinutes);

            StudySession entity = StudySession.builder()
                    .title(sessionResponse.getTitle())
                    .startTime(startTime)
                    .endTime(endTime)
                    .status(StudySessionStatus.SCHEDULED)
                    .user(user)
                    .fullDescription(sessionResponse.getDescription())
                    .build();
            createdSessions.add(entity);
            fallbackCursor = endTime.plusMinutes(breakMinutes);
        }

        // Persist sessions so they have IDs before linking to tasks
        List<StudySession> savedSessions = studySessionRepository.saveAll(createdSessions);
        List<UUID> savedSessionIds = savedSessions.stream()
                .map(StudySession::getId)
                .collect(Collectors.toList());

        List<TaskResponse> createdTasks = new ArrayList<>();
        int totalSteps = savedSessions.size();

        for (int i = 0; i < totalSteps; i++) {
            StudySession savedSession = savedSessions.get(i);
            StudySessionResponse sessionResponse = sourceSessions.get(i);

            CreateTaskRequest taskRequest = new CreateTaskRequest();
            taskRequest.setColumnId(todoColumnId);
            taskRequest.setTitle(buildTaskTitleFromSession(node, sessionResponse, i + 1, totalSteps));
            taskRequest.setDescription(buildTaskDescriptionFromSession(roadmapSession, journey, node, sessionResponse, i + 1, totalSteps));
            taskRequest.setStartDate(savedSession.getStartTime());
            taskRequest.setEndDate(savedSession.getEndTime());
            taskRequest.setDeadline(savedSession.getEndTime());
            taskRequest.setPriority(resolveTaskPriority(node));
            taskRequest.setUserProgress(0);
            // Link the task to its StudySession — this is what enables
            // checkAndCompleteLinkedTasks() to auto-complete tasks when sessions complete
            taskRequest.setLinkedSessionIds(List.of(savedSession.getId()));
            taskRequest.setUserNotes(buildStudyPlanTaskNotes(
                    marker,
                    node,
                    sessionResponse,
                    i + 1,
                    totalSteps,
                    nodeOrder,
                    totalRoadmapNodes));

            createdTasks.add(taskBoardService.createTask(user.getId(), taskRequest));
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

    private String buildTaskDescriptionFromNode(RoadmapSession roadmapSession, Journey journey, RoadmapResponse.RoadmapNode node) {
        StringBuilder description = new StringBuilder();
        if (node.getDescription() != null && !node.getDescription().isBlank()) {
            description.append(node.getDescription().trim()).append("\n\n");
        }

        appendTaskSection(description, "Mục tiêu học tập", node.getLearningObjectives());
        appendTaskSection(description, "Khái niệm cốt lõi", node.getKeyConcepts());
        appendTaskSection(description, "Bài tập thực hành", node.getPracticalExercises());
        appendTaskSection(description, "Tài nguyên gợi ý", node.getSuggestedResources());
        appendTaskSection(description, "Tiêu chí hoàn thành", node.getSuccessCriteria());

        description.append(buildRoadmapSourceLabel(roadmapSession, journey, node));

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
            RoadmapSession roadmapSession,
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

        description.append("Task ").append(step).append(" trong ").append(totalSteps).append(" tổng cộng\n");
        description.append(buildRoadmapSourceLabel(roadmapSession, journey, node).replace(" • ", " | "));

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

    private String buildDefaultDesiredOutcome(RoadmapSession roadmapSession, Journey journey, RoadmapResponse.RoadmapNode node) {
        String role = firstNonBlank(
                journey != null ? journey.getJobRole() : null,
                journey != null ? journey.getSubCategory() : null,
                journey != null ? journey.getDomain() : null,
                roadmapSession != null ? roadmapSession.getTarget() : null,
                roadmapSession != null ? roadmapSession.getFinalObjective() : null,
                roadmapSession != null ? roadmapSession.getOriginalGoal() : null,
                "target goal");
        return safeTruncate(
                String.format("Master node '%s' and move closer to %s", firstNonBlank(node.getTitle(), "this topic"), role),
                300,
                "Master this roadmap node");
    }

    private String buildRoadmapSourceLabel(RoadmapSession roadmapSession, Journey journey, RoadmapResponse.RoadmapNode node) {
        StringBuilder source = new StringBuilder();
        if (journey != null && journey.getId() != null) {
            source.append("Journey #").append(journey.getId()).append(" • ");
        }
        source.append("Roadmap #")
                .append(roadmapSession != null ? roadmapSession.getId() : null)
                .append(" • Node ")
                .append(node.getId());
        return source.toString();
    }

    private Long safeParseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveQuestionBankSkillNameForEnrichment(Journey journey) {
        if (journey == null || journey.getJobPositionTrackId() != null) {
            return null;
        }
        return journey.getSkillName();
    }

    private int resolveRoadmapNodeTargetSessionCount(
            RoadmapResponse.RoadmapNode node,
            GenerateScheduleRequest request) {
        int durationMinutes = safeDurationMinutes(request.getDurationMinutes());
        int maxSessionsPerDay = safeMaxSessionsPerDay(request.getMaxSessionsPerDay());
        LocalDate startDate = request.getStartDate() != null
                ? request.getStartDate()
                : nowInStudyZone(request.getTimezone()).toLocalDate().plusDays(1);
        List<String> preferredDays = normalizeRoadmapNodePreferredDays(request.getPreferredDays());

        if (request.getDeadline() != null && !request.getDeadline().isBefore(startDate)) {
            int studyDays = 0;
            LocalDate cursor = startDate;
            while (!cursor.isAfter(request.getDeadline())) {
                if (isValidStudyDay(cursor, preferredDays)) {
                    studyDays++;
                }
                cursor = cursor.plusDays(1);
            }
            return Math.max(1, Math.min(MAX_STUDY_TASKS_PER_NODE, studyDays * maxSessionsPerDay));
        }

        int estimatedMinutes = node.getEstimatedTimeMinutes() != null && node.getEstimatedTimeMinutes() > 0
                ? node.getEstimatedTimeMinutes()
                : durationMinutes * maxSessionsPerDay;
        int sessionsNeeded = Math.max(1, (int) Math.ceil((double) estimatedMinutes / durationMinutes));
        return Math.max(1, Math.min(MAX_STUDY_TASKS_PER_NODE, sessionsNeeded));
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

    private LocalTime parseTimeRangeEnd(String range) {
        if (range == null || range.isBlank()) {
            return null;
        }
        String[] parts = range.split("-");
        if (parts.length < 2) {
            return null;
        }
        try {
            return LocalTime.parse(parts[1].trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isValidStudyDay(LocalDate date, List<String> preferredDays) {
        if (preferredDays == null || preferredDays.isEmpty()) {
            return true;
        }
        String dayName = date.getDayOfWeek().name();
        return preferredDays.contains(dayName);
    }

    private LocalDateTime nowInStudyZone(String timezone) {
        ZoneId zoneId = resolveStudyTimeZone(timezone);
        return Instant.now(studyClock).atZone(zoneId).toLocalDateTime();
    }

    private LocalDateTime roundUpToQuarterHour(LocalDateTime value) {
        int minute = value.getMinute();
        int roundedMinute = ((minute + 14) / 15) * 15;
        if (roundedMinute >= 60) {
            return value.plusHours(1).withMinute(0).withSecond(0).withNano(0);
        }
        return value.withMinute(roundedMinute).withSecond(0).withNano(0);
    }

    private LocalTime resolveFirstWindowStart(List<String> timeWindows) {
        if (timeWindows != null) {
            for (String window : timeWindows) {
                LocalTime parsed = parseTimeRangeStart(window);
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return LocalTime.of(8, 0);
    }

    private LocalDateTime resolveNextStudySlot(
            LocalDateTime candidate,
            GenerateScheduleRequest request,
            int durationMinutes,
            Map<LocalDate, Integer> sessionsPerDay,
            Map<LocalDate, List<LocalDateTime[]>> occupiedSlotsByDay,
            List<String> preferredDays,
            int maxSessionsPerDay,
            int breakMinutes,
            LocalDateTime nowInZone) {
        List<String> timeWindows = request.getPreferredTimeWindows();
        if (timeWindows == null || timeWindows.isEmpty()) {
            timeWindows = defaultPreferredTimeWindows(request.getStudyPreference());
        }

        LocalDate candidateDate = candidate.toLocalDate();
        LocalDate today = nowInZone.toLocalDate();
        
        LocalDateTime minimumStart = roundUpToQuarterHour(nowInZone.plusMinutes(SLOT_SEARCH_BUFFER_MINUTES));
        LocalDateTime adjustedCandidate;
        if (candidateDate.isEqual(today) && candidate.isBefore(minimumStart)) {
            adjustedCandidate = minimumStart;
        } else {
            adjustedCandidate = roundUpToQuarterHour(candidate);
        }

        LocalTime firstWindowStart = resolveFirstWindowStart(timeWindows);

        for (int dayOffset = 0; dayOffset < SLOT_SEARCH_MAX_DAYS; dayOffset++) {
            LocalDate searchDate = adjustedCandidate.toLocalDate().plusDays(dayOffset);
            
            if (!isValidStudyDay(searchDate, preferredDays)) {
                continue;
            }

            int currentSessions = sessionsPerDay.getOrDefault(searchDate, 0);
            if (currentSessions >= maxSessionsPerDay) {
                continue;
            }

            LocalDateTime searchStart = dayOffset == 0 ? adjustedCandidate : LocalDateTime.of(searchDate, firstWindowStart);
            
            for (String window : timeWindows) {
                LocalTime windowStart = parseTimeRangeStart(window);
                LocalTime windowEnd = parseTimeRangeEnd(window);
                if (windowStart == null || windowEnd == null) {
                    continue;
                }

                LocalDateTime slotStart = LocalDateTime.of(searchDate, windowStart);
                if (dayOffset == 0 && slotStart.isBefore(searchStart)) {
                    slotStart = searchStart;
                }

                LocalDateTime slotEnd = slotStart.plusMinutes(durationMinutes);
                LocalDateTime windowEndDateTime = LocalDateTime.of(searchDate, windowEnd);

                while (!slotEnd.isAfter(windowEndDateTime) &&
                        overlapsAny(slotStart, slotEnd, occupiedSlotsByDay.getOrDefault(searchDate, List.of()))) {
                    LocalDateTime nextStart = nextStartAfterOccupiedSlot(
                            slotStart,
                            slotEnd,
                            occupiedSlotsByDay.getOrDefault(searchDate, List.of()),
                            breakMinutes);
                    if (!nextStart.isAfter(slotStart)) {
                        break;
                    }
                    slotStart = nextStart;
                    slotEnd = slotStart.plusMinutes(durationMinutes);
                }

                if (!slotEnd.isAfter(windowEndDateTime) &&
                        !overlapsAny(slotStart, slotEnd, occupiedSlotsByDay.getOrDefault(searchDate, List.of()))) {
                    sessionsPerDay.put(searchDate, currentSessions + 1);
                    occupiedSlotsByDay
                            .computeIfAbsent(searchDate, ignored -> new ArrayList<>())
                            .add(new LocalDateTime[] { slotStart, slotEnd });
                    return slotStart;
                }
            }
        }

        throw new ApiException(ErrorCode.BAD_REQUEST, 
            "Cannot find available study slot within " + SLOT_SEARCH_MAX_DAYS + " days. " +
            "Please adjust your study preferences or reduce session duration.");
    }

    private Map<LocalDate, List<LocalDateTime[]>> loadExistingStudySlots(
            Long userId,
            LocalDate startDate,
            LocalDate endDate) {
        return studySessionRepository
                .findByUserIdAndStartTimeBetween(userId, startDate.atStartOfDay(), endDate.plusDays(1).atStartOfDay())
                .stream()
                .filter(session -> session.getStartTime() != null && session.getEndTime() != null)
                .collect(Collectors.groupingBy(
                        session -> session.getStartTime().toLocalDate(),
                        Collectors.mapping(
                                session -> new LocalDateTime[] { session.getStartTime(), session.getEndTime() },
                                Collectors.toCollection(ArrayList::new))));
    }

    private Map<LocalDate, Integer> countOccupiedSlotsByDay(Map<LocalDate, List<LocalDateTime[]>> occupiedSlotsByDay) {
        Map<LocalDate, Integer> sessionsPerDay = new HashMap<>();
        occupiedSlotsByDay.forEach((date, slots) -> sessionsPerDay.put(date, slots.size()));
        return sessionsPerDay;
    }

    private boolean overlapsAny(
            LocalDateTime startTime,
            LocalDateTime endTime,
            List<LocalDateTime[]> occupiedSlots) {
        return occupiedSlots.stream()
                .anyMatch(slot -> startTime.isBefore(slot[1]) && slot[0].isBefore(endTime));
    }

    private LocalDateTime nextStartAfterOccupiedSlot(
            LocalDateTime startTime,
            LocalDateTime endTime,
            List<LocalDateTime[]> occupiedSlots,
            int breakMinutes) {
        return occupiedSlots.stream()
                .filter(slot -> startTime.isBefore(slot[1]) && slot[0].isBefore(endTime))
                .map(slot -> slot[1].plusMinutes(breakMinutes))
                .max(LocalDateTime::compareTo)
                .orElse(startTime);
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

    private List<String> normalizeRoadmapNodePreferredDays(List<String> preferredDays) {
        if (preferredDays == null || preferredDays.isEmpty()) {
            return new ArrayList<>();
        }
        return normalizePreferredDays(preferredDays);
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
            case "flexible" -> new ArrayList<>(List.of("08:00-10:30", "13:30-17:30", "19:00-21:30"));
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

    private String findNextEligibleMainNodeId(RoadmapResponse roadmap, List<RoadmapResponse.RoadmapNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        for (RoadmapResponse.RoadmapNode node : nodes) {
            if (node == null || node.getId() == null || node.getId().isBlank()) {
                continue;
            }
            // Only return MAIN nodes for enforcement. SIDE nodes are optional.
            if (node.getType() == RoadmapResponse.RoadmapNode.NodeType.MAIN
                    && !isRoadmapNodeCompleted(roadmap, node.getId())) {
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

    private RoadmapResponse.RoadmapNode findNodeById(List<RoadmapResponse.RoadmapNode> nodes, String nodeId) {
        if (nodeId == null || nodeId.isBlank() || nodes == null) {
            return null;
        }
        for (RoadmapResponse.RoadmapNode node : nodes) {
            if (node.getId() != null && node.getId().equals(nodeId)) {
                return node;
            }
        }
        return null;
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

    private void syncCompletedJourneySkillToPortfolio(Journey journey) {
        if (journey == null || journey.getUser() == null || journey.getSkillName() == null
                || journey.getSkillName().isBlank()) {
            return;
        }

        String skillName = SkillNameUtils.normalize(journey.getSkillName());
        if (skillName == null || skillName.isBlank()) {
            return;
        }

        try {
            PortfolioExtendedProfile profile = portfolioExtendedProfileRepository
                    .findByUserId(journey.getUser().getId())
                    .orElseGet(() -> PortfolioExtendedProfile.builder()
                            .user(journey.getUser())
                            .fullName(journey.getUser().getFullName())
                            .build());

            List<String> skills = readStringList(profile.getTopSkills());
            boolean exists = skills.stream().anyMatch(existing -> {
                String normalizedExisting = SkillNameUtils.normalize(existing);
                return skillName.equals(normalizedExisting);
            });
            if (!exists) {
                skills.add(skillName);
                profile.setTopSkills(objectMapper.writeValueAsString(skills));
                portfolioExtendedProfileRepository.save(profile);
            }
        } catch (Exception e) {
            log.warn("Failed to sync completed journey skill {} to portfolio for user {}",
                    skillName, journey.getUser().getId(), e);
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<?> raw = objectMapper.readValue(json, List.class);
            List<String> result = new ArrayList<>();
            for (Object item : raw) {
                if (item instanceof String value && !value.isBlank()) {
                    result.add(value.trim());
                }
            }
            return result;
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

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
                StartJourneyRequest assessmentData = readAssessmentData(journey);
                EvaluationSnapshot snapshot = buildSnapshotFromStoredResult(latestResult, null);
                boolean challengeRequired = shouldRecommendChallengeUp(latestResult, snapshot, assessmentData);
                testResultSummary = JourneySummaryResponse.TestResultSummaryResponse.builder()
                        .resultId(latestResult.getId())
                        .scorePercentage(latestResult.getScorePercentage())
                        .evaluatedLevel(latestResult.getEvaluatedLevel())
                        .baseLevel(resolveBaseLevel(assessmentData))
                        .testedLevel(resolveTestedLevel(latestResult.getAssessmentTest(), assessmentData))
                        .provisional(challengeRequired)
                        .challengeRequired(challengeRequired)
                        .challengeAvailable(canGenerateChallengeUp(latestResult, snapshot, assessmentData))
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
                .industry(journey.getIndustry())
                .subCategory(journey.getSubCategory())
                .jobRole(journey.getJobRole())
                .jobPositionTrackId(journey.getJobPositionTrackId())
                .targetLevel(journey.getTargetLevel())
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
                .skillName(journey.getSkillName())
                .finalVerificationRequired(journey.getFinalVerificationRequired())
                .hasActiveMentorBooking(bookingRepository.hasActiveBookingsForJourney(journey.getId()))
                .build();
    }

    private TestResultResponse mapToTestResultResponse(TestResult result, Integer totalQuestions) {
        EvaluationSnapshot snapshot = buildSnapshotFromStoredResult(result, totalQuestions);
        StartJourneyRequest assessmentData = readAssessmentData(result.getJourney());
        boolean challengeRequired = shouldRecommendChallengeUp(result, snapshot, assessmentData);
        Long challengeTestId = resolvePendingChallengeTestId(result);
        boolean challengeAvailable = challengeTestId != null || canGenerateChallengeUp(result, snapshot, assessmentData);
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
                .assessmentPhase(result.getAssessmentTest().getAssessmentPhase())
                .baseLevel(resolveBaseLevel(assessmentData))
                .testedLevel(resolveTestedLevel(result.getAssessmentTest(), assessmentData))
                .provisional(challengeRequired)
                .challengeRequired(challengeRequired)
                .challengeAvailable(challengeAvailable)
                .challengeTestId(challengeTestId)
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
        return createEvaluationSnapshot(totalQuestions, answeredQuestions, correctAnswers, scorePercentage, null);
    }

    private EvaluationSnapshot createEvaluationSnapshot(
            int totalQuestions,
            int answeredQuestions,
            int correctAnswers,
            int scorePercentage,
            Journey.SkillLevel evaluatedLevel) {
        int safeTotal = Math.max(0, totalQuestions);
        int safeAnswered = Math.max(0, Math.min(answeredQuestions, safeTotal));
        int safeCorrect = Math.max(0, Math.min(correctAnswers, safeTotal));
        int safeIncorrect = Math.max(0, safeTotal - safeCorrect);
        int safeScore = clampScore(scorePercentage);

        String scoreBand = determineScoreBand(safeScore);
        String recommendationMode = capRecommendationModeForRecognizedLevel(
                determineRecommendationMode(safeScore, safeTotal, safeCorrect),
                evaluatedLevel);
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
            return createEvaluationSnapshot(total, answered, correct, score, result.getEvaluatedLevel());
        }

        int total = totalQuestionsOverride != null && totalQuestionsOverride > 0
                ? totalQuestionsOverride
                : storedAnswers.size();
        int score = clampScore(result.getScorePercentage());
        int correct = total > 0 ? (int) Math.round((score / 100.0) * total) : 0;
        int answered = Math.min(total, storedAnswers.size());
        return createEvaluationSnapshot(total, answered, correct, score, result.getEvaluatedLevel());
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

    private String extractScoreRationaleMarkdown(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> rawMap) || rawMap.isEmpty()) {
            return null;
        }

        Map<String, Object> rationale = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            rationale.put(entry.getKey().toString(), entry.getValue());
        }

        String formula = firstNonBlankText(rationale, "formula", "scoringFormula");
        String scoreBand = firstNonBlankText(rationale, "scoreBand", "band");
        String levelBand = firstNonBlankText(rationale, "levelBand", "level");
        String whyThisScore = firstNonBlankText(rationale, "whyThisScore", "explanation");
        String confidenceReason = firstNonBlankText(rationale, "confidenceReason");
        String nextFocus = firstNonBlankText(rationale, "nextFocus", "nextStep");

        if ((formula == null || formula.isBlank())
                && (scoreBand == null || scoreBand.isBlank())
                && (levelBand == null || levelBand.isBlank())
                && (whyThisScore == null || whyThisScore.isBlank())
                && (confidenceReason == null || confidenceReason.isBlank())
                && (nextFocus == null || nextFocus.isBlank())) {
            return null;
        }

        StringBuilder builder = new StringBuilder("### Giải thích thêm từ AI về mức điểm\n");
        if (formula != null && !formula.isBlank()) {
            builder.append("- Công thức AI diễn giải: ").append(formula.trim()).append("\n");
        }
        if (scoreBand != null && !scoreBand.isBlank()) {
            builder.append("- Band AI nhận diện: **").append(scoreBand.trim()).append("**\n");
        }
        if (levelBand != null && !levelBand.isBlank()) {
            builder.append("- Level AI nhận diện: **").append(levelBand.trim()).append("**\n");
        }
        if (whyThisScore != null && !whyThisScore.isBlank()) {
            builder.append("- Vì sao ra mức điểm này: ").append(whyThisScore.trim()).append("\n");
        }
        if (confidenceReason != null && !confidenceReason.isBlank()) {
            builder.append("- Cơ sở độ tin cậy: ").append(confidenceReason.trim()).append("\n");
        }
        if (nextFocus != null && !nextFocus.isBlank()) {
            builder.append("- Trọng tâm nên làm tiếp: ").append(nextFocus.trim()).append("\n");
        }

        return builder.toString().trim();
    }

    private String appendScoreRationaleSection(String feedback,
                                               String aiScoreRationale,
                                               EvaluationSnapshot snapshot,
                                               int scorePercentage,
                                               Journey.SkillLevel evaluatedLevel) {
        String baseFeedback = feedback == null ? "" : feedback.trim();
        if (baseFeedback.contains("## Cơ sở chấm điểm minh bạch")) {
            return baseFeedback;
        }

        int totalQuestions = Math.max(snapshot.totalQuestions, 1);
        StringBuilder section = new StringBuilder();
        section.append("## Cơ sở chấm điểm minh bạch\n");
        section.append(String.format("- Công thức chuẩn: (**%d / %d**) x 100 = **%d%%**.\n",
                snapshot.correctAnswers,
                totalQuestions,
                scorePercentage));
        section.append(String.format("- Band điểm hiện tại: **%s** (%s).\n",
                toScoreBandLabel(snapshot.scoreBand),
                toScoreBandRange(snapshot.scoreBand)));
        section.append(String.format("- Mức năng lực hiện tại: **%s**.\n",
                toSkillLevelLabel(evaluatedLevel)));
        section.append("- Quy chuẩn band điểm: ZERO_BASE 0-20, FOUNDATION 21-45, CORE 46-70, ADVANCED 71-85, EXPERT 86-100.\n");
        section.append("- Quy chuẩn mức năng lực: BEGINNER 0-40, INTERMEDIATE 41-70, ADVANCED 71-85, EXPERT 86-100.\n");

        if (snapshot.reassessmentRecommended) {
            section.append("- Độ phủ câu trả lời chưa đủ chắc chắn, hệ thống khuyến nghị làm lại sau vòng học đầu tiên.\n");
        }

        if (aiScoreRationale != null && !aiScoreRationale.isBlank()) {
            section.append("\n").append(aiScoreRationale.trim());
        }

        if (baseFeedback.isBlank()) {
            return section.toString().trim();
        }
        return baseFeedback + "\n\n" + section.toString().trim();
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

    private String toScoreBandLabel(String scoreBand) {
        if (scoreBand == null || scoreBand.isBlank()) {
            return "Cốt lõi";
        }

        return switch (scoreBand) {
            case "ZERO_BASE" -> "Nền tảng 0";
            case "FOUNDATION" -> "Nền tảng";
            case "CORE" -> "Cốt lõi";
            case "ADVANCED" -> "Nâng cao";
            case "EXPERT" -> "Chuyên sâu";
            default -> scoreBand;
        };
    }

    private String toScoreBandRange(String scoreBand) {
        if (scoreBand == null || scoreBand.isBlank()) {
            return "46-70";
        }

        return switch (scoreBand) {
            case "ZERO_BASE" -> "0-20";
            case "FOUNDATION" -> "21-45";
            case "CORE" -> "46-70";
            case "ADVANCED" -> "71-85";
            case "EXPERT" -> "86-100";
            default -> "46-70";
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

    private Journey.SkillLevel determineEvaluatedLevel(
            int scorePercentage,
            int answeredQuestions,
            int totalQuestions,
            AssessmentTest test,
            StartJourneyRequest assessmentData) {
        Journey.SkillLevel baseLevel = resolveBaseLevel(assessmentData);
        Journey.SkillLevel testedLevel = resolveTestedLevel(test, assessmentData);
        int score = clampScore(scorePercentage);
        boolean enoughAnswersForPromotion = hasPromotionCoverage(answeredQuestions, totalQuestions);

        if (isChallengePhase(test)) {
            int passScore = testedLevel == Journey.SkillLevel.EXPERT
                    ? EXPERT_CHALLENGE_PASS_SCORE
                    : CHALLENGE_PASS_SCORE;
            if (score >= passScore && enoughAnswersForPromotion) {
                return normalizeChallengePromotionLevel(testedLevel);
            }
            return baseLevel;
        }

        if (!enoughAnswersForPromotion || score < 45) {
            return previousLevel(testedLevel).orElse(testedLevel);
        }

        return testedLevel;
    }

    private Journey.SkillLevel normalizeChallengePromotionLevel(Journey.SkillLevel testedLevel) {
        if (testedLevel == Journey.SkillLevel.ELEMENTARY) {
            return Journey.SkillLevel.INTERMEDIATE;
        }
        return testedLevel;
    }

    private boolean shouldRecommendChallengeUp(
            TestResult result,
            EvaluationSnapshot snapshot,
            StartJourneyRequest assessmentData) {
        if (result == null || snapshot == null || result.getAssessmentTest() == null) {
            return false;
        }
        AssessmentTest test = result.getAssessmentTest();
        Journey.SkillLevel testedLevel = resolveTestedLevel(test, assessmentData);
        if (isChallengePhase(test) || testedLevel == Journey.SkillLevel.EXPERT) {
            return false;
        }
        if (!isQuestionBankTest(test)) {
            return false;
        }
        return clampScore(result.getScorePercentage()) >= CHALLENGE_TRIGGER_SCORE
                && hasPromotionCoverage(snapshot.answeredQuestions, snapshot.totalQuestions);
    }

    private boolean canGenerateChallengeUp(
            TestResult result,
            EvaluationSnapshot snapshot,
            StartJourneyRequest assessmentData) {
        Long pendingChallengeTestId = resolvePendingChallengeTestId(result);
        if (pendingChallengeTestId != null) {
            return true;
        }
        if (!shouldRecommendChallengeUp(result, snapshot, assessmentData)) {
            return false;
        }
        if (assessmentTestRepository.countByJourney(result.getJourney()) >= MAX_ASSESSMENT_ATTEMPTS) {
            return false;
        }
        Long bankId = resolveQuestionBankId(result.getAssessmentTest());
        return bankId != null && questionBankService.isBankReadyForAllLevels(bankId);
    }

    private Long resolvePendingChallengeTestId(TestResult result) {
        if (result == null || result.getJourney() == null || result.getAssessmentTest() == null) {
            return null;
        }
        AssessmentTest latestTest = assessmentTestRepository.findTopByJourneyOrderByCreatedAtDesc(result.getJourney()).orElse(null);
        if (latestTest == null || !isChallengePhase(latestTest)) {
            return null;
        }
        if (!Objects.equals(latestTest.getParentTestId(), result.getAssessmentTest().getId())) {
            return null;
        }
        AssessmentTest.TestStatus status = latestTest.getStatus();
        if (status == AssessmentTest.TestStatus.PENDING || status == AssessmentTest.TestStatus.IN_PROGRESS) {
            return latestTest.getId();
        }
        return null;
    }

    private boolean isChallengePhase(AssessmentTest test) {
        return test != null && ASSESSMENT_PHASE_CHALLENGE_UP.equalsIgnoreCase(
                Optional.ofNullable(test.getAssessmentPhase()).orElse(""));
    }

    private boolean isQuestionBankTest(AssessmentTest test) {
        if (test == null) {
            return false;
        }
        if (QUESTION_SOURCE_BANK.equalsIgnoreCase(Optional.ofNullable(test.getQuestionSource()).orElse(""))) {
            return true;
        }
        return test.getQuestionBank() != null
                || Optional.ofNullable(test.getGenerationPrompt()).orElse("").startsWith(FULL_QB_PROMPT_PREFIX);
    }

    private Journey.SkillLevel resolveBaseLevel(StartJourneyRequest assessmentData) {
        return normalizeSkillLevelValue(
                assessmentData != null ? assessmentData.getLevel() : null,
                Journey.SkillLevel.BEGINNER);
    }

    private Journey.SkillLevel resolveTestedLevel(AssessmentTest test, StartJourneyRequest assessmentData) {
        Journey.SkillLevel fallback = resolveBaseLevel(assessmentData);
        if (test == null) {
            return fallback;
        }
        Journey.SkillLevel fromTestedLevel = normalizeSkillLevelValue(test.getTestedLevel(), null);
        if (fromTestedLevel != null) {
            return fromTestedLevel;
        }
        return normalizeSkillLevelValue(test.getDifficultyLevel(), fallback);
    }

    private Journey.SkillLevel normalizeSkillLevelValue(String value, Journey.SkillLevel fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("MIXED".equals(normalized)) {
            return fallback;
        }
        if ("BASIC".equals(normalized)) {
            return Journey.SkillLevel.BEGINNER;
        }
        if ("UPPER_INTERMEDIATE".equals(normalized)) {
            return Journey.SkillLevel.INTERMEDIATE;
        }
        try {
            return Journey.SkillLevel.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private Journey.SkillLevel nextLevel(Journey.SkillLevel level) {
        if (level == null) {
            return Journey.SkillLevel.BEGINNER;
        }
        return switch (level) {
            case BEGINNER, ELEMENTARY -> Journey.SkillLevel.INTERMEDIATE;
            case INTERMEDIATE -> Journey.SkillLevel.ADVANCED;
            case ADVANCED -> Journey.SkillLevel.EXPERT;
            case EXPERT -> null;
        };
    }

    private Optional<Journey.SkillLevel> previousLevel(Journey.SkillLevel level) {
        if (level == null) {
            return Optional.empty();
        }
        return switch (level) {
            case BEGINNER -> Optional.empty();
            case ELEMENTARY -> Optional.of(Journey.SkillLevel.BEGINNER);
            case INTERMEDIATE -> Optional.of(Journey.SkillLevel.ELEMENTARY);
            case ADVANCED -> Optional.of(Journey.SkillLevel.INTERMEDIATE);
            case EXPERT -> Optional.of(Journey.SkillLevel.ADVANCED);
        };
    }

    private boolean hasPromotionCoverage(int answeredQuestions, int totalQuestions) {
        if (totalQuestions <= 0) {
            return false;
        }
        int requiredAnswers = (int) Math.ceil(totalQuestions * MIN_PROMOTION_ANSWER_COVERAGE);
        return answeredQuestions >= requiredAnswers;
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

    private String capRecommendationModeForRecognizedLevel(String recommendationMode, Journey.SkillLevel evaluatedLevel) {
        if (recommendationMode == null || recommendationMode.isBlank() || evaluatedLevel == null) {
            return recommendationMode;
        }

        String maxRecommendation = switch (evaluatedLevel) {
            case BEGINNER, ELEMENTARY, INTERMEDIATE -> "STANDARD";
            case ADVANCED -> "ADVANCED";
            case EXPERT -> "FAST_TRACK";
        };

        return recommendationRank(recommendationMode) > recommendationRank(maxRecommendation)
                ? maxRecommendation
                : recommendationMode;
    }

    private int recommendationRank(String recommendationMode) {
        if (recommendationMode == null) {
            return 0;
        }
        return switch (recommendationMode) {
            case "FROM_ZERO" -> 0;
            case "FOUNDATION" -> 1;
            case "STANDARD" -> 2;
            case "ADVANCED" -> 3;
            case "FAST_TRACK" -> 4;
            default -> 2;
        };
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
        Long roadmapSessionId = journey.getRoadmapSessionId();
        if (roadmapSessionId == null) {
            return 0;
        }
        Long completedCount = userRoadmapProgressRepository.countCompletedBySessionId(roadmapSessionId);
        return completedCount != null ? completedCount.intValue() : 0;
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
                : firstNonBlank(journey.getJobRole(), journey.getSubCategory(), domain + " fundamentals");

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
                .targetEnvironment(safeTruncate(firstNonBlank(journey.getIndustry(), journey.getSubCategory(), journey.getJobRole(), domain), 100, domain))
                .location("Vietnam")
                .priority(safeTruncate(mapPriorityFromRecommendation(snapshot.recommendationMode), 50, "Balanced"))
                .toolPreferences(toolPreferences)
                .toolPreference(toolPreferences)
                .difficultyConcern(difficultyConcern)
                .incomeGoal(inferIncomeGoal(journeyGoal));

        if (careerJourney) {
            builder
                    .targetRole(safeTruncate(target, 120, domain + " role"))
                    .careerTrack(safeTruncate(firstNonBlank(journey.getIndustry(), journey.getSubCategory(), domain), 120, domain))
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
        if (journey.getType() != null && !journey.getType().isBlank()) {
            return "CAREER".equalsIgnoreCase(journey.getType());
        }
        return journey.getJobRole() != null && !journey.getJobRole().isBlank();
    }

    private boolean isSkillJourney(Journey journey) {
        if (journey == null) {
            return false;
        }
        if (journey.getType() != null && !journey.getType().isBlank()) {
            return "SKILL".equalsIgnoreCase(journey.getType());
        }
        return !isCareerJourney(journey);
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
        if (journey.getJobPositionTrackId() != null) {
            return roadmapTemplateService.createRoadmapSessionFromPublishedTemplate(
                    journey,
                    testResult,
                    skillGaps != null ? skillGaps : Collections.emptyList(),
                    strengths != null ? strengths : Collections.emptyList());
        }
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
