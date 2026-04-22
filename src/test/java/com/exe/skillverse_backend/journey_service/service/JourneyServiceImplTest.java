package com.exe.skillverse_backend.journey_service.service;

import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.service.AiRoadmapService;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.QuestionInfo;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.journey_service.dto.request.StartJourneyRequest;
import com.exe.skillverse_backend.journey_service.dto.response.JourneySummaryResponse;
import com.exe.skillverse_backend.journey_service.entity.AssessmentTest;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.entity.JourneyProgress;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.FinalVerificationGateService;
import com.exe.skillverse_backend.journey_service.repository.AssessmentTestRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyProgressRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.repository.TestResultRepository;
import com.exe.skillverse_backend.journey_service.service.impl.JourneyServiceImpl;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankResponse;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBank;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankQuestionService;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankService;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.service.AiStudySupportService;
import com.exe.skillverse_backend.study_service.service.TaskBoardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JourneyServiceImplTest {

    @Mock
    private JourneyRepository journeyRepository;

    @Mock
    private RoadmapSessionRepository roadmapSessionRepository;

    @Mock
    private AssessmentTestRepository assessmentTestRepository;

    @Mock
    private TestResultRepository testResultRepository;

    @Mock
    private JourneyProgressRepository journeyProgressRepository;

    @Mock
    private FinalVerificationGateService finalVerificationGateService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private ChatModel generateTestChatModel;

    @Mock
    private AiRoadmapService aiRoadmapService;

    @Mock
    private AssessmentPromptService assessmentPromptService;

    @Mock
    private TaskBoardService taskBoardService;

    @Mock
    private AiStudySupportService aiStudySupportService;

    @Mock
    private QuestionBankService questionBankService;
    @Mock
    private QuestionBankQuestionService questionBankQuestionService;
    @Mock
    private StudySessionRepository studySessionRepository;

    private JourneyServiceImpl service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new JourneyServiceImpl(
                journeyRepository,
                roadmapSessionRepository,
                assessmentTestRepository,
                testResultRepository,
                journeyProgressRepository,
                finalVerificationGateService,
                entityManager,
                generateTestChatModel,
                aiRoadmapService,
                assessmentPromptService,
                taskBoardService,
                aiStudySupportService,
                questionBankService,
                questionBankQuestionService,
                studySessionRepository,
                objectMapper);

        lenient().when(journeyRepository.save(any(Journey.class))).thenAnswer(invocation -> {
            Journey journey = invocation.getArgument(0);
            if (journey.getId() == null) {
                journey.setId(10L);
            }
            return journey;
        });
        lenient().when(journeyRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(journeyProgressRepository.save(any(JourneyProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(journeyProgressRepository.findByJourney(any(Journey.class))).thenReturn(List.of());
        lenient().when(testResultRepository.findTopByJourneyOrderByCreatedAtDesc(any(Journey.class))).thenReturn(Optional.empty());
        lenient().when(assessmentTestRepository.findTopByJourneyOrderByCreatedAtDesc(any(Journey.class))).thenReturn(Optional.empty());
        lenient().when(assessmentTestRepository.countByJourney(any(Journey.class))).thenReturn(0L);
        lenient().when(assessmentTestRepository.save(any(AssessmentTest.class))).thenAnswer(invocation -> {
            AssessmentTest test = invocation.getArgument(0);
            if (test.getId() == null) {
                test.setId(101L);
            }
            return test;
        });
        lenient().when(entityManager.getReference(eq(QuestionBank.class), any(Long.class))).thenAnswer(invocation ->
                QuestionBank.builder().id(invocation.getArgument(1)).build());
    }

    @Test
    @DisplayName("startJourney should create an assessment-pending journey")
    void startJourney_ShouldCreateAnAssessmentPendingJourney() {
        User user = user();

        JourneySummaryResponse response = service.startJourney(user, StartJourneyRequest.builder()
                .type("CAREER")
                .domain("IT")
                .goal("Backend developer")
                .level("BEGINNER")
                .jobRole("JAVA")
                .build());

        assertEquals(10L, response.getId());
        assertEquals(Journey.JourneyStatus.ASSESSMENT_PENDING, response.getStatus());
        assertEquals("IT", response.getDomain());
        assertEquals(0, response.getProgressPercentage());
    }

    @Test
    @DisplayName("resumeJourney should pause other active journeys and restore roadmap status")
    void resumeJourney_ShouldPauseOtherActiveJourneysAndRestoreRoadmapStatus() {
        User user = user();
        Journey pausedJourney = Journey.builder()
                .id(11L)
                .user(user)
                .domain("IT")
                .goal("Backend")
                .status(Journey.JourneyStatus.PAUSED)
                .roadmapSessionId(55L)
                .progressPercentage(20)
                .build();
        Journey otherJourney = Journey.builder()
                .id(12L)
                .user(user)
                .domain("Design")
                .goal("UX")
                .status(Journey.JourneyStatus.ACTIVE)
                .progressPercentage(40)
                .build();

        when(journeyRepository.findByIdAndUser(11L, user)).thenReturn(Optional.of(pausedJourney));
        when(journeyRepository.findActiveJourneysByUser(user)).thenReturn(List.of(otherJourney));

        JourneySummaryResponse response = service.resumeJourney(user, 11L);

        assertEquals(Journey.JourneyStatus.ROADMAP_GENERATED, response.getStatus());
        assertEquals(Journey.JourneyStatus.PAUSED, otherJourney.getStatus());
    }

    @Test
    @DisplayName("resumeJourney should reject journeys that are not paused")
    void resumeJourney_ShouldRejectJourneysThatAreNotPaused() {
        User user = user();
        Journey activeJourney = Journey.builder()
                .id(11L)
                .user(user)
                .domain("IT")
                .goal("Backend")
                .status(Journey.JourneyStatus.ACTIVE)
                .build();

        when(journeyRepository.findByIdAndUser(11L, user)).thenReturn(Optional.of(activeJourney));

        assertThrows(RuntimeException.class, () -> service.resumeJourney(user, 11L));
    }

    @Test
    @DisplayName("completeJourney should mark the journey complete and create a completion milestone")
    void completeJourney_ShouldMarkTheJourneyCompleteAndCreateACompletionMilestone() {
        User user = user();
        Journey journey = Journey.builder()
                .id(13L)
                .user(user)
                .domain("IT")
                .goal("Backend")
                .status(Journey.JourneyStatus.ACTIVE)
                .progressPercentage(60)
                .build();
        JourneyProgress completion = JourneyProgress.builder()
                .milestone(JourneyProgress.Milestone.JOURNEY_COMPLETED)
                .isCompleted(true)
                .completedAt(Instant.now())
                .build();

        when(journeyRepository.findByIdAndUser(13L, user)).thenReturn(Optional.of(journey));
        when(journeyProgressRepository.findByJourney(journey)).thenReturn(List.of(completion));

        JourneySummaryResponse response = service.completeJourney(user, 13L);

        assertEquals(Journey.JourneyStatus.COMPLETED, response.getStatus());
        assertEquals(100, response.getProgressPercentage());
        assertEquals(1, response.getMilestones().size());
        assertEquals("JOURNEY_COMPLETED", response.getMilestones().get(0).getMilestone());
    }

    @Test
    @DisplayName("startJourney should retain industry and job role for skill journeys")
    void startJourney_ShouldRetainIndustryAndJobRoleForSkillJourneys() {
        User user = user();

        JourneySummaryResponse response = service.startJourney(user, StartJourneyRequest.builder()
                .type("SKILL")
                .domain("BUSINESS")
                .industry("CUSTOMER_SERVICE")
                .subCategory("CUSTOMER_SERVICE")
                .jobRole("CUSTOMER_SERVICE")
                .goal("Improve support workflow")
                .level("BEGINNER")
                .skills(List.of("Communication Skills", "Complaint Resolution"))
                .build());

        assertEquals("BUSINESS", response.getDomain());
        assertEquals("CUSTOMER_SERVICE", response.getIndustry());
        assertEquals("CUSTOMER_SERVICE", response.getJobRole());
        assertEquals(Journey.JourneyStatus.ASSESSMENT_PENDING, response.getStatus());
    }

    @Test
    @DisplayName("generateAssessmentTest should use domain industry job role lookup for skill journeys")
    void generateAssessmentTest_ShouldUseScopedQuestionBankForSkillJourneys() throws Exception {
        User user = user();
        StartJourneyRequest request = StartJourneyRequest.builder()
                .type("SKILL")
                .domain("SERVICE")
                .industry("CUSTOMER_SERVICE")
                .subCategory("CUSTOMER_SERVICE")
                .jobRole("CUSTOMER_SERVICE")
                .goal("Improve support workflow")
                .level("BEGINNER")
                .skills(List.of("Communication Skills", "Complaint Resolution"))
                .questionCount(15)
                .build();
        Journey journey = Journey.builder()
                .id(21L)
                .user(user)
                .type("SKILL")
                .domain("SERVICE")
                .industry("CUSTOMER_SERVICE")
                .subCategory("CUSTOMER_SERVICE")
                .jobRole("CUSTOMER_SERVICE")
                .goal("Improve support workflow")
                .assessmentData(objectMapper.writeValueAsString(request))
                .status(Journey.JourneyStatus.ASSESSMENT_PENDING)
                .build();

        QuestionBankResponse bank = QuestionBankResponse.builder()
                .id(200L)
                .domain("SERVICE")
                .industry("CUSTOMER_SERVICE")
                .jobRole("CUSTOMER_SERVICE")
                .difficultyDistribution("{\"BEGINNER\":1.0}")
                .difficultyBreakdown(new LinkedHashMap<>())
                .build();

        when(journeyRepository.findByIdAndUser(21L, user)).thenReturn(Optional.of(journey));
        when(questionBankService.findActiveBank("SERVICE", "CUSTOMER_SERVICE", "CUSTOMER_SERVICE", null))
                .thenReturn(Optional.of(bank));
        when(questionBankService.countBySkillAreaAndDifficulty(200L)).thenReturn(List.of(
                new Object[]{"Communication Skills", "BEGINNER", 8L},
                new Object[]{"Complaint Resolution", "BEGINNER", 8L}
        ));
        when(questionBankService.selectRandomQuestionsBySkillAreaAndDifficulty(200L, "Communication Skills", "BEGINNER", 8))
                .thenReturn(buildQuestions(1, 8, "Communication Skills", "BEGINNER"));
        when(questionBankService.selectRandomQuestionsBySkillAreaAndDifficulty(200L, "Complaint Resolution", "BEGINNER", 8))
                .thenReturn(buildQuestions(50, 8, "Complaint Resolution", "BEGINNER"));
        when(questionBankService.selectRandomQuestionsByLevel(200L, 15, "BEGINNER"))
                .thenReturn(buildQuestions(100, 15, "Customer Support", "BEGINNER"));

        var response = service.generateAssessmentTest(user, 21L);

        assertEquals(101L, response.getTestId());
        assertEquals(15, response.getQuestionCount());
        verify(questionBankService).findActiveBank("SERVICE", "CUSTOMER_SERVICE", "CUSTOMER_SERVICE", null);
        verify(questionBankService, never()).findActiveBank("SERVICE", "CUSTOMER_SERVICE");
    }

    @Test
    @DisplayName("generateAssessmentTest should keep legacy domain job role lookup for career journeys")
    void generateAssessmentTest_ShouldKeepLegacyLookupForCareerJourneys() throws Exception {
        User user = user();
        StartJourneyRequest request = StartJourneyRequest.builder()
                .type("CAREER")
                .domain("IT")
                .industry("WEB_DEV")
                .subCategory("WEB_DEV")
                .jobRole("BACKEND")
                .goal("Backend developer")
                .level("BEGINNER")
                .questionCount(15)
                .build();
        Journey journey = Journey.builder()
                .id(22L)
                .user(user)
                .type("CAREER")
                .domain("IT")
                .industry("WEB_DEV")
                .subCategory("WEB_DEV")
                .jobRole("BACKEND")
                .goal("Backend developer")
                .assessmentData(objectMapper.writeValueAsString(request))
                .status(Journey.JourneyStatus.ASSESSMENT_PENDING)
                .build();

        QuestionBankResponse bank = QuestionBankResponse.builder()
                .id(300L)
                .domain("IT")
                .jobRole("BACKEND")
                .difficultyDistribution("{\"BEGINNER\":1.0}")
                .difficultyBreakdown(new LinkedHashMap<>())
                .build();

        when(journeyRepository.findByIdAndUser(22L, user)).thenReturn(Optional.of(journey));
        when(questionBankService.findActiveBank("IT", "BACKEND")).thenReturn(Optional.of(bank));
        when(questionBankService.isBankReadyForAllLevels(300L)).thenReturn(true);
        when(questionBankService.selectRandomQuestionsByLevel(300L, 15, "BEGINNER"))
                .thenReturn(buildQuestions(200, 15, "Spring Boot", "BEGINNER"));

        var response = service.generateAssessmentTest(user, 22L);

        assertEquals(101L, response.getTestId());
        assertEquals(15, response.getQuestionCount());
        verify(questionBankService).findActiveBank("IT", "BACKEND");
        verify(questionBankService, never()).findActiveBank("IT", "WEB_DEV", "BACKEND");
    }

    private User user() {
        return User.builder()
                .id(1L)
                .email("student@skillverse.vn")
                .firstName("Student")
                .lastName("One")
                .build();
    }

    private List<QuestionInfo> buildQuestions(long startId, int count, String skillArea, String difficulty) {
        List<QuestionInfo> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long questionId = startId + i;
            questions.add(new QuestionInfo(
                    questionId,
                    "Question " + questionId,
                    List.of("A. One", "B. Two", "C. Three", "D. Four"),
                    "A",
                    "Explanation " + questionId,
                    difficulty,
                    skillArea
            ));
        }
        return questions;
    }
}
