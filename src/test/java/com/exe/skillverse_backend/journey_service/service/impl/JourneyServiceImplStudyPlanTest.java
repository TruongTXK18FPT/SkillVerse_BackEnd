package com.exe.skillverse_backend.journey_service.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.ai_service.service.AiRoadmapService;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.node_mentoring.service.FinalVerificationGateService;
import com.exe.skillverse_backend.journey_service.repository.AssessmentTestRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyProgressRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.repository.TestResultRepository;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankQuestionService;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.study_service.dto.request.GenerateScheduleRequest;
import com.exe.skillverse_backend.study_service.dto.request.CreateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskColumnResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskResponse;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.entity.TaskPriority;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.service.AiStudySupportService;
import com.exe.skillverse_backend.study_service.service.TaskBoardService;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

@ExtendWith(MockitoExtension.class)
class JourneyServiceImplStudyPlanTest {

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
    private UserRoadmapProgressRepository userRoadmapProgressRepository;
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
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;

    @InjectMocks
    private JourneyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new JourneyServiceImpl(
                journeyRepository,
                roadmapSessionRepository,
                assessmentTestRepository,
                testResultRepository,
                journeyProgressRepository,
                userRoadmapProgressRepository,
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
                bookingRepository,
                portfolioExtendedProfileRepository,
                new ObjectMapper());

        // Mock session persistence (GAP-2 fix: sessions are now created before tasks)
        lenient().when(studySessionRepository.saveAll(any())).thenAnswer(invocation -> {
            List<StudySession> sessions = invocation.getArgument(0);
            sessions.forEach(s -> {
                if (s.getId() == null) {
                    s.setId(UUID.randomUUID());
                }
            });
            return sessions;
        });
    }

    @Test
    void createStudyPlanForRoadmapNode_allowsStandaloneRoadmapSessionWithoutJourney() {
        User user = User.builder().id(9L).email("standalone@example.com").build();
        RoadmapSession roadmapSession = RoadmapSession.builder()
                .id(55L)
                .user(user)
                .title("Standalone roadmap")
                .originalGoal("Master backend fundamentals")
                .roadmapJson("{\"roadmap\":[]}")
                .build();

        when(roadmapSessionRepository.findByIdAndUserId(55L, 9L)).thenReturn(Optional.of(roadmapSession));
        when(journeyRepository.findByRoadmapSessionId(55L)).thenReturn(Optional.empty());
        when(aiRoadmapService.getRoadmapById(55L, 9L)).thenReturn(buildRoadmapResponse(55L, "node-1"));
        when(taskBoardService.getBoard(9L)).thenReturn(List.of(TaskColumnResponse.builder()
                .id(UUID.randomUUID())
                .name("To Do")
                .tasks(List.of())
                .build()));
        when(aiStudySupportService.generateProposedSchedule(anyLong(), any())).thenReturn(List.of(
                StudySessionResponse.builder()
                        .title("Session 1")
                        .description("Practice node")
                        .startTime(LocalDateTime.of(2026, 4, 3, 19, 0))
                        .endTime(LocalDateTime.of(2026, 4, 3, 20, 30))
                        .build()));
        when(taskBoardService.createTask(anyLong(), any(CreateTaskRequest.class))).thenAnswer(invocation -> {
            CreateTaskRequest request = invocation.getArgument(1);
            return TaskResponse.builder()
                    .id(UUID.randomUUID())
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .userNotes(request.getUserNotes())
                    .columnId(request.getColumnId())
                    .priority(request.getPriority())
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .deadline(request.getDeadline())
                    .status("todo")
                    .userProgress(0)
                    .build();
        });

        Object result = service.createStudyPlanForRoadmapNode(user, 55L, "node-1", new GenerateScheduleRequest());

        Map<?, ?> payload = assertInstanceOf(Map.class, result);
        assertEquals(Boolean.TRUE, payload.get("created"));
        assertEquals(55L, payload.get("roadmapSessionId"));
        assertEquals("node-1", payload.get("nodeId"));
        assertFalse(payload.containsKey("journeyId"));

        ArgumentCaptor<CreateTaskRequest> taskCaptor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(taskBoardService).createTask(anyLong(), taskCaptor.capture());
        assertTrue(taskCaptor.getValue().getUserNotes().contains("[ROADMAP_NODE_LINK] roadmap=55 node=node-1"));
        assertFalse(taskCaptor.getValue().getUserNotes().contains("journey="));
        verify(journeyRepository, never()).save(any());
    }

    @Test
    void createStudyPlanForRoadmapNode_reusesJourneyContextWhenRoadmapBelongsToJourney() {
        User user = User.builder().id(9L).email("journey@example.com").build();
        RoadmapSession roadmapSession = RoadmapSession.builder()
                .id(56L)
                .user(user)
                .title("Journey roadmap")
                .roadmapJson("{\"roadmap\":[]}")
                .build();
        Journey journey = Journey.builder()
                .id(77L)
                .user(user)
                .title("Java journey")
                .roadmapSessionId(56L)
                .status(Journey.JourneyStatus.ROADMAP_GENERATED)
                .progressPercentage(30)
                .lastActivityAt(Instant.now())
                .build();

        when(roadmapSessionRepository.findByIdAndUserId(56L, 9L)).thenReturn(Optional.of(roadmapSession));
        when(journeyRepository.findByRoadmapSessionId(56L)).thenReturn(Optional.of(journey));
        when(aiRoadmapService.getRoadmapById(56L, 9L)).thenReturn(buildRoadmapResponse(56L, "node-1"));
        when(taskBoardService.getBoard(9L)).thenReturn(List.of(TaskColumnResponse.builder()
                .id(UUID.randomUUID())
                .name("To Do")
                .tasks(List.of())
                .build()));
        when(aiStudySupportService.generateProposedSchedule(anyLong(), any())).thenReturn(List.of(
                StudySessionResponse.builder()
                        .title("Session 1")
                        .description("Practice node")
                        .startTime(LocalDateTime.of(2026, 4, 3, 19, 0))
                        .endTime(LocalDateTime.of(2026, 4, 3, 20, 30))
                        .build()));
        when(taskBoardService.createTask(anyLong(), any(CreateTaskRequest.class))).thenAnswer(invocation -> {
            CreateTaskRequest request = invocation.getArgument(1);
            return TaskResponse.builder()
                    .id(UUID.randomUUID())
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .userNotes(request.getUserNotes())
                    .columnId(request.getColumnId())
                    .priority(request.getPriority())
                    .startDate(request.getStartDate())
                    .endDate(request.getEndDate())
                    .deadline(request.getDeadline())
                    .status("todo")
                    .userProgress(0)
                    .build();
        });
        when(journeyRepository.save(any(Journey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Object result = service.createStudyPlanForRoadmapNode(user, 56L, "node-1", new GenerateScheduleRequest());

        Map<?, ?> payload = assertInstanceOf(Map.class, result);
        assertEquals(Boolean.TRUE, payload.get("created"));
        assertEquals(77L, payload.get("journeyId"));
        verify(journeyRepository).save(any(Journey.class));
    }

    @Test
    void createStudyPlanForRoadmapNode_returnsNotFoundForMissingOwnedRoadmapSession() {
        User user = User.builder().id(9L).email("missing@example.com").build();
        when(roadmapSessionRepository.findByIdAndUserId(999L, 9L)).thenReturn(Optional.empty());

        ApiException ex = org.junit.jupiter.api.Assertions.assertThrows(
                ApiException.class,
                () -> service.createStudyPlanForRoadmapNode(user, 999L, "node-1", new GenerateScheduleRequest()));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
        assertEquals("Roadmap not found", ex.getMessage());
    }

    @Test
    void flexibleScheduling_timezoneConversion_UTCtoHCM() {
        User user = User.builder().id(9L).email("test@example.com").build();
        RoadmapSession roadmapSession = RoadmapSession.builder()
                .id(55L)
                .user(user)
                .title("Test roadmap")
                .roadmapJson("{\"roadmap\":[]}")
                .build();

        when(roadmapSessionRepository.findByIdAndUserId(55L, 9L)).thenReturn(Optional.of(roadmapSession));
        when(journeyRepository.findByRoadmapSessionId(55L)).thenReturn(Optional.empty());
        when(aiRoadmapService.getRoadmapById(55L, 9L)).thenReturn(buildRoadmapResponse(55L, "node-1"));
        when(taskBoardService.getBoard(9L)).thenReturn(List.of(TaskColumnResponse.builder()
                .id(UUID.randomUUID())
                .name("To Do")
                .tasks(List.of())
                .build()));

        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setStartDate(LocalDateTime.of(2026, 4, 5, 0, 0).toLocalDate());
        request.setTimezone("Asia/Ho_Chi_Minh");
        request.setStudyPreference("flexible");
        request.setDurationMinutes(90);
        request.setMaxSessionsPerDay(1);

        when(aiStudySupportService.generateProposedSchedule(anyLong(), any())).thenReturn(List.of());
        when(taskBoardService.createTask(anyLong(), any(CreateTaskRequest.class))).thenAnswer(invocation -> {
            CreateTaskRequest req = invocation.getArgument(1);
            return TaskResponse.builder()
                    .id(UUID.randomUUID())
                    .title(req.getTitle())
                    .description(req.getDescription())
                    .userNotes(req.getUserNotes())
                    .columnId(req.getColumnId())
                    .priority(req.getPriority())
                    .startDate(req.getStartDate())
                    .endDate(req.getEndDate())
                    .deadline(req.getDeadline())
                    .status("todo")
                    .userProgress(0)
                    .build();
        });

        service.studyClock = Clock.fixed(Instant.parse("2026-04-05T12:00:00Z"), ZoneOffset.UTC);

        Object result = service.createStudyPlanForRoadmapNode(user, 55L, "node-1", request);

        Map<?, ?> payload = assertInstanceOf(Map.class, result);
        assertEquals(Boolean.TRUE, payload.get("created"));

        ArgumentCaptor<CreateTaskRequest> taskCaptor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(taskBoardService, org.mockito.Mockito.atLeast(1)).createTask(anyLong(), taskCaptor.capture());

        LocalDateTime taskStart = taskCaptor.getValue().getStartDate();
        LocalDateTime expectedMinimum = LocalDateTime.of(2026, 4, 5, 19, 30);
        assertTrue(taskStart.isEqual(expectedMinimum) || taskStart.isAfter(expectedMinimum),
            "Task start should be >= 19:30 HCM (12:00 UTC + 7h + 30min buffer)");
    }

    @Test
    void flexibleScheduling_nearFutureBuffer() {
        User user = User.builder().id(9L).email("test@example.com").build();
        RoadmapSession roadmapSession = RoadmapSession.builder()
                .id(55L)
                .user(user)
                .title("Test roadmap")
                .roadmapJson("{\"roadmap\":[]}")
                .build();

        when(roadmapSessionRepository.findByIdAndUserId(55L, 9L)).thenReturn(Optional.of(roadmapSession));
        when(journeyRepository.findByRoadmapSessionId(55L)).thenReturn(Optional.empty());
        when(aiRoadmapService.getRoadmapById(55L, 9L)).thenReturn(buildRoadmapResponse(55L, "node-1"));
        when(taskBoardService.getBoard(9L)).thenReturn(List.of(TaskColumnResponse.builder()
                .id(UUID.randomUUID())
                .name("To Do")
                .tasks(List.of())
                .build()));

        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setStartDate(LocalDateTime.of(2026, 4, 5, 0, 0).toLocalDate());
        request.setTimezone("Asia/Ho_Chi_Minh");
        request.setStudyPreference("flexible");
        request.setDurationMinutes(90);
        request.setMaxSessionsPerDay(1);

        when(aiStudySupportService.generateProposedSchedule(anyLong(), any())).thenReturn(List.of(
                StudySessionResponse.builder()
                        .title("Session 1")
                        .description("Practice node")
                        .startTime(LocalDateTime.of(2026, 4, 5, 19, 10))
                        .endTime(LocalDateTime.of(2026, 4, 5, 20, 40))
                        .build()));
        when(taskBoardService.createTask(anyLong(), any(CreateTaskRequest.class))).thenAnswer(invocation -> {
            CreateTaskRequest req = invocation.getArgument(1);
            return TaskResponse.builder()
                    .id(UUID.randomUUID())
                    .title(req.getTitle())
                    .description(req.getDescription())
                    .userNotes(req.getUserNotes())
                    .columnId(req.getColumnId())
                    .priority(req.getPriority())
                    .startDate(req.getStartDate())
                    .endDate(req.getEndDate())
                    .deadline(req.getDeadline())
                    .status("todo")
                    .userProgress(0)
                    .build();
        });

        service.studyClock = Clock.fixed(Instant.parse("2026-04-05T12:00:00Z"), ZoneOffset.UTC);

        Object result = service.createStudyPlanForRoadmapNode(user, 55L, "node-1", request);

        Map<?, ?> payload = assertInstanceOf(Map.class, result);
        assertEquals(Boolean.TRUE, payload.get("created"));

        ArgumentCaptor<CreateTaskRequest> taskCaptor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(taskBoardService).createTask(anyLong(), taskCaptor.capture());

        LocalDateTime taskStart = taskCaptor.getValue().getStartDate();
        LocalDateTime expectedMinimum = LocalDateTime.of(2026, 4, 5, 19, 30);
        assertTrue(taskStart.isEqual(expectedMinimum) || taskStart.isAfter(expectedMinimum),
            "Task start should be >= 19:30 (12:00 UTC + 7h + 30min buffer), ignoring AI's 19:10");
    }

    @Test
    void flexibleScheduling_quarterHourRounding() {
        User user = User.builder().id(9L).email("test@example.com").build();
        RoadmapSession roadmapSession = RoadmapSession.builder()
                .id(55L)
                .user(user)
                .title("Test roadmap")
                .roadmapJson("{\"roadmap\":[]}")
                .build();

        when(roadmapSessionRepository.findByIdAndUserId(55L, 9L)).thenReturn(Optional.of(roadmapSession));
        when(journeyRepository.findByRoadmapSessionId(55L)).thenReturn(Optional.empty());
        when(aiRoadmapService.getRoadmapById(55L, 9L)).thenReturn(buildRoadmapResponse(55L, "node-1"));
        when(taskBoardService.getBoard(9L)).thenReturn(List.of(TaskColumnResponse.builder()
                .id(UUID.randomUUID())
                .name("To Do")
                .tasks(List.of())
                .build()));

        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setStartDate(LocalDateTime.of(2026, 4, 5, 0, 0).toLocalDate());
        request.setTimezone("Asia/Ho_Chi_Minh");
        request.setStudyPreference("flexible");
        request.setDurationMinutes(90);
        request.setMaxSessionsPerDay(1);

        when(aiStudySupportService.generateProposedSchedule(anyLong(), any())).thenReturn(List.of());
        when(taskBoardService.createTask(anyLong(), any(CreateTaskRequest.class))).thenAnswer(invocation -> {
            CreateTaskRequest req = invocation.getArgument(1);
            return TaskResponse.builder()
                    .id(UUID.randomUUID())
                    .title(req.getTitle())
                    .description(req.getDescription())
                    .userNotes(req.getUserNotes())
                    .columnId(req.getColumnId())
                    .priority(req.getPriority())
                    .startDate(req.getStartDate())
                    .endDate(req.getEndDate())
                    .deadline(req.getDeadline())
                    .status("todo")
                    .userProgress(0)
                    .build();
        });

        service.studyClock = Clock.fixed(Instant.parse("2026-04-05T12:01:00Z"), ZoneOffset.UTC);

        Object result = service.createStudyPlanForRoadmapNode(user, 55L, "node-1", request);

        Map<?, ?> payload = assertInstanceOf(Map.class, result);
        assertEquals(Boolean.TRUE, payload.get("created"));

        ArgumentCaptor<CreateTaskRequest> taskCaptor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(taskBoardService, org.mockito.Mockito.atLeast(1)).createTask(anyLong(), taskCaptor.capture());

        LocalDateTime taskStart = taskCaptor.getValue().getStartDate();
        int minute = taskStart.getMinute();
        assertTrue(minute == 0 || minute == 15 || minute == 30 || minute == 45,
            "Task start minute should be rounded to quarter-hour boundary (0, 15, 30, 45)");
        LocalDateTime expectedMinimum = LocalDateTime.of(2026, 4, 5, 19, 45);
        assertTrue(taskStart.isEqual(expectedMinimum) || taskStart.isAfter(expectedMinimum),
            "Task start should be >= 19:45 (12:01 UTC + 7h + 30min buffer + rounded to 45)");
    }

    @Test
    void flexibleScheduling_multipleWindowsDistribution() {
        User user = User.builder().id(9L).email("test@example.com").build();
        RoadmapSession roadmapSession = RoadmapSession.builder()
                .id(55L)
                .user(user)
                .title("Test roadmap")
                .roadmapJson("{\"roadmap\":[]}")
                .build();

        when(roadmapSessionRepository.findByIdAndUserId(55L, 9L)).thenReturn(Optional.of(roadmapSession));
        when(journeyRepository.findByRoadmapSessionId(55L)).thenReturn(Optional.empty());
        when(aiRoadmapService.getRoadmapById(55L, 9L)).thenReturn(buildRoadmapResponse(55L, "node-1"));
        when(taskBoardService.getBoard(9L)).thenReturn(List.of(TaskColumnResponse.builder()
                .id(UUID.randomUUID())
                .name("To Do")
                .tasks(List.of())
                .build()));

        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setStartDate(LocalDateTime.of(2026, 4, 5, 0, 0).toLocalDate());
        request.setTimezone("Asia/Ho_Chi_Minh");
        request.setStudyPreference("flexible");
        request.setDurationMinutes(90);
        request.setMaxSessionsPerDay(3);

        when(aiStudySupportService.generateProposedSchedule(anyLong(), any())).thenReturn(List.of());
        when(taskBoardService.createTask(anyLong(), any(CreateTaskRequest.class))).thenAnswer(invocation -> {
            CreateTaskRequest req = invocation.getArgument(1);
            return TaskResponse.builder()
                    .id(UUID.randomUUID())
                    .title(req.getTitle())
                    .description(req.getDescription())
                    .userNotes(req.getUserNotes())
                    .columnId(req.getColumnId())
                    .priority(req.getPriority())
                    .startDate(req.getStartDate())
                    .endDate(req.getEndDate())
                    .deadline(req.getDeadline())
                    .status("todo")
                    .userProgress(0)
                    .build();
        });

        service.studyClock = Clock.fixed(Instant.parse("2026-04-05T00:00:00Z"), ZoneOffset.UTC);
        
        Object result = service.createStudyPlanForRoadmapNode(user, 55L, "node-1", request);
        
        Map<?, ?> payload = assertInstanceOf(Map.class, result);
        assertEquals(Boolean.TRUE, payload.get("created"));
        
        ArgumentCaptor<CreateTaskRequest> taskCaptor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(taskBoardService, org.mockito.Mockito.atLeast(3)).createTask(anyLong(), taskCaptor.capture());
        
        List<CreateTaskRequest> capturedTasks = taskCaptor.getAllValues();
        if (capturedTasks.size() >= 3) {
            List<LocalTime> startTimes = capturedTasks.stream()
                    .map(t -> t.getStartDate().toLocalTime())
                    .toList();
            
            boolean usesMorning = startTimes.stream().anyMatch(t -> t.getHour() >= 8 && t.getHour() < 11);
            boolean usesAfternoon = startTimes.stream().anyMatch(t -> t.getHour() >= 13 && t.getHour() < 18);
            boolean usesEvening = startTimes.stream().anyMatch(t -> t.getHour() >= 19 && t.getHour() < 22);
            
            int windowsUsed = (usesMorning ? 1 : 0) + (usesAfternoon ? 1 : 0) + (usesEvening ? 1 : 0);
            assertTrue(windowsUsed >= 2,
                "Flexible scheduling should use at least 2 different time windows (morning/afternoon/evening)");
        }
    }

    private RoadmapResponse buildRoadmapResponse(Long sessionId, String nodeId) {
        return RoadmapResponse.builder()
                .sessionId(sessionId)
                .roadmap(List.of(RoadmapResponse.RoadmapNode.builder()
                        .id(nodeId)
                        .title("Node title")
                        .description("Node description")
                        .type(RoadmapResponse.RoadmapNode.NodeType.MAIN)
                        .children(List.of())
                        .learningObjectives(List.of("Objective"))
                        .keyConcepts(List.of("Concept"))
                        .practicalExercises(List.of("Exercise"))
                        .successCriteria(List.of("Done"))
                        .build()))
                .progress(Map.of())
                .build();
    }
}
