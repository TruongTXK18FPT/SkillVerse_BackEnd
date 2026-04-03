package com.exe.skillverse_backend.journey_service.service;

import com.exe.skillverse_backend.ai_service.service.AiRoadmapService;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.journey_service.dto.request.StartJourneyRequest;
import com.exe.skillverse_backend.journey_service.dto.response.JourneySummaryResponse;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.entity.JourneyProgress;
import com.exe.skillverse_backend.journey_service.repository.AssessmentTestRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyProgressRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.repository.TestResultRepository;
import com.exe.skillverse_backend.journey_service.service.impl.JourneyServiceImpl;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankService;
import com.exe.skillverse_backend.study_service.service.AiStudySupportService;
import com.exe.skillverse_backend.study_service.service.TaskBoardService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JourneyServiceImplTest {

    @Mock
    private JourneyRepository journeyRepository;

    @Mock
    private AssessmentTestRepository assessmentTestRepository;

    @Mock
    private TestResultRepository testResultRepository;

    @Mock
    private JourneyProgressRepository journeyProgressRepository;

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

    private JourneyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JourneyServiceImpl(
                journeyRepository,
                assessmentTestRepository,
                testResultRepository,
                journeyProgressRepository,
                entityManager,
                generateTestChatModel,
                aiRoadmapService,
                assessmentPromptService,
                taskBoardService,
                aiStudySupportService,
                questionBankService,
                new ObjectMapper());

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

    private User user() {
        return User.builder()
                .id(1L)
                .email("student@skillverse.vn")
                .firstName("Student")
                .lastName("One")
                .build();
    }
}
