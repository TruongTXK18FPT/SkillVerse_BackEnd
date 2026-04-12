package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.request.UpdateProgressRequest;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.exception.UsageLimitExceededException;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.study_service.repository.TaskRepository;
import com.exe.skillverse_backend.study_service.service.TaskBoardService;
import com.exe.skillverse_backend.ai_service.service.AiCourseCatalogService;
import com.exe.skillverse_backend.ai_service.service.impl.MultiLevelCourseMatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiRoadmapServiceImplTest {

    @Mock
    private RoadmapSessionRepository roadmapSessionRepository;

    @Mock
    private UserRoadmapProgressRepository progressRepository;

    @Mock
    private InputValidationService inputValidationService;

    @Mock
    private UsageLimitService usageLimitService;

    @Mock
    private ExpertPromptService expertPromptService;

    @Mock
    private TaxonomyService taxonomyService;

    @Mock
    private PremiumService premiumService;

    @Mock
    private ChatModel mistralChatModel;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private JourneyRepository journeyRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private RoadmapCompletionSyncService roadmapCompletionSyncService;

    @Mock
    private TaskBoardService taskBoardService;

    @Mock
    private AiCourseCatalogService aiCourseCatalogService;

    @Mock
    private MultiLevelCourseMatcher multiLevelCourseMatcher;

    private AiRoadmapServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiRoadmapServiceImpl(
                roadmapSessionRepository,
                progressRepository,
                new ObjectMapper(),
                inputValidationService,
                usageLimitService,
                expertPromptService,
                taxonomyService,
                premiumService,
                mistralChatModel,
                courseRepository,
                journeyRepository,
                taskRepository,
                roadmapCompletionSyncService,
                taskBoardService,
                aiCourseCatalogService,
                multiLevelCourseMatcher);
    }

    @Test
    @DisplayName("generateRoadmap should enforce roadmap storage limits before generation")
    void generateRoadmap_ShouldEnforceRoadmapStorageLimitsBeforeGeneration() {
        User user = User.builder().id(1L).email("learner@skillverse.vn").build();
        GenerateRoadmapRequest request = request();
        when(usageLimitService.getUserUsage(user.getId(), FeatureType.AI_ROADMAP_GENERATION))
                .thenReturn(FeatureLimitInfo.builder()
                        .featureType(FeatureType.AI_ROADMAP_GENERATION)
                        .isUnlimited(false)
                        .limit(1)
                        .build());
        when(roadmapSessionRepository.countByUserId(user.getId())).thenReturn(1L);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.generateRoadmap(request, user));

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("gi"));
    }

    @Test
    @DisplayName("generateRoadmap should block deep research for non-premium users")
    void generateRoadmap_ShouldBlockDeepResearchForNonPremiumUsers() {
        User user = User.builder().id(1L).email("learner@skillverse.vn").build();
        GenerateRoadmapRequest request = request();
        request.setAiAgentMode("deep-research-pro-preview-12-2025");

        when(usageLimitService.getUserUsage(user.getId(), FeatureType.AI_ROADMAP_GENERATION))
                .thenReturn(FeatureLimitInfo.builder()
                        .featureType(FeatureType.AI_ROADMAP_GENERATION)
                        .isUnlimited(true)
                        .build());
        when(premiumService.hasActivePremiumSubscription(user.getId())).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class, () -> service.generateRoadmap(request, user));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    @DisplayName("getRoadmapById should only return sessions owned by the user")
    void getRoadmapById_ShouldOnlyReturnSessionsOwnedByTheUser() {
        when(roadmapSessionRepository.findByIdAndUserId(55L, 1L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.getRoadmapById(55L, 1L));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("updateProgress should reject updates for roadmaps owned by another user")
    void updateProgress_ShouldRejectUpdatesForRoadmapsOwnedByAnotherUser() {
        when(roadmapSessionRepository.findByIdAndUserId(55L, 1L)).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.updateProgress(
                55L,
                1L,
                UpdateProgressRequest.builder().questId("quest_1").completed(true).build()));

        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("parseMetadata should handle missing mode metadata without NPE")
    void parseMetadata_ShouldHandleMissingModeMetadataWithoutNpe() throws Exception {
        String metadataJson = """
                        {
                            "title": "Backend roadmap",
                            "original_goal": "Learn backend",
                            "validated_goal": "Become backend developer",
                            "duration": "3 months",
                            "desired_duration": "3 months",
                            "experience_level": "beginner",
                            "learning_style": "project-based",
                            "difficulty_level": "medium",
                            "roadmap_mode": "CAREER_BASED"
                        }
                        """;

        RoadmapResponse.RoadmapMetadata metadata = invokeParseMetadata(metadataJson);

        assertNotNull(metadata);
        assertEquals("CAREER_BASED", metadata.getRoadmapMode());
        assertNull(metadata.getSkillMode());
        assertNull(metadata.getCareerMode());
    }

    @Test
    @DisplayName("parseMetadata should still parse skill mode when provided")
    void parseMetadata_ShouldStillParseSkillModeWhenProvided() throws Exception {
        String metadataJson = """
                        {
                            "roadmapMode": "SKILL_BASED",
                            "skillMode": {
                                "skillName": "Spring Boot",
                                "desiredDepth": "intermediate",
                                "dailyLearningTime": "2h/day"
                            }
                        }
                        """;

        RoadmapResponse.RoadmapMetadata metadata = invokeParseMetadata(metadataJson);

        assertNotNull(metadata);
        assertNotNull(metadata.getSkillMode());
        assertEquals("Spring Boot", metadata.getSkillMode().getSkillName());
        assertNull(metadata.getCareerMode());
    }

    @Test
    @DisplayName("getAllRoadmaps should fall back to totalNodes when JSON parsing fails")
    void getAllRoadmaps_ShouldFallBackToTotalNodesWhenJsonParsingFails() {
        RoadmapSession session = RoadmapSession.builder()
                .id(10L)
                .title("Backend roadmap")
                .schemaVersion(2)
                .roadmapMode("SKILL_BASED")
                .originalGoal("Learn backend")
                .duration("3 months")
                .experienceLevel("beginner")
                .learningStyle("project-based")
                .difficultyLevel("medium")
                .totalNodes(4)
                .roadmapJson("{invalid")
                .createdAt(Instant.now())
                .build();
        when(roadmapSessionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(java.util.List.of(session));
        when(progressRepository.countCompletedBySessionId(session.getId())).thenReturn(1L);

        assertEquals(4, service.getAllRoadmaps().get(0).getTotalQuests());
        assertEquals(25, service.getAllRoadmaps().get(0).getProgressPercentage());
    }

    @Test
    @DisplayName("getUserRoadmaps should compute summary progress from derived partial node progress")
    void getUserRoadmaps_ShouldUseDerivedPartialProgressForSummaryPercentage() {
        User user = User.builder().id(42L).build();
        RoadmapSession session = RoadmapSession.builder()
            .id(11L)
            .user(user)
            .title("Node progress roadmap")
            .roadmapJson("{\"roadmap\":[{\"id\":\"n1\"},{\"id\":\"n2\"}]}")
            .createdAt(Instant.now())
            .build();

        RoadmapResponse.RoadmapNode node1 = RoadmapResponse.RoadmapNode.builder().id("n1").build();
        RoadmapResponse.RoadmapNode node2 = RoadmapResponse.RoadmapNode.builder().id("n2").build();

        Map<String, RoadmapResponse.QuestProgress> derivedProgress = Map.of(
            "n1",
            RoadmapResponse.QuestProgress.builder()
                .questId("n1")
                .status("COMPLETED")
                .progress(100)
                .build(),
            "n2",
            RoadmapResponse.QuestProgress.builder()
                .questId("n2")
                .status("IN_PROGRESS")
                .progress(50)
                .build());

        when(roadmapSessionRepository.findByUserIdAndStatusNotDeleted(42L)).thenReturn(List.of(session));
        when(roadmapCompletionSyncService.extractNodes(session)).thenReturn(List.of(node1, node2));
        when(roadmapCompletionSyncService.loadStoredProgressMap(11L)).thenReturn(Map.of());
        when(roadmapCompletionSyncService.overlayDerivedProgressSnapshot(session, List.of(node1, node2), Map.of()))
            .thenReturn(derivedProgress);

        RoadmapSessionSummary summary = service.getUserRoadmaps(42L, false).get(0);

        assertEquals(2, summary.getTotalQuests());
        assertEquals(1, summary.getCompletedQuests());
        assertEquals(75, summary.getProgressPercentage());
    }

    @Test
    @DisplayName("classifyAiFailure should classify 5xx failures as server failures")
    void classifyAiFailure_ShouldClassifyServerFailure() throws Exception {
        org.springframework.web.client.HttpServerErrorException exception =
                new org.springframework.web.client.HttpServerErrorException(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                        "upstream failed");

        assertEquals("http-500-server", invokeClassifyAiFailure(exception));
        assertTrue(invokeIsRetryableAiFailure(exception));
    }

    @Test
    @DisplayName("classifyAiFailure should classify 4xx failures as client failures")
    void classifyAiFailure_ShouldClassifyClientFailure() throws Exception {
        org.springframework.web.client.HttpClientErrorException exception =
                new org.springframework.web.client.HttpClientErrorException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "not found");

        assertEquals("http-404-client", invokeClassifyAiFailure(exception));
        assertFalse(invokeIsRetryableAiFailure(exception));
    }

    @Test
    @DisplayName("isTruncatedJsonParseFailure should detect truncated parser signatures")
    void isTruncatedJsonParseFailure_ShouldDetectTruncatedSignature() throws Exception {
        ApiException truncated = new ApiException(
                ErrorCode.BAD_REQUEST,
                "Unexpected end-of-input while parsing root object");
        ApiException nonTruncated = new ApiException(
                ErrorCode.BAD_REQUEST,
                "Invalid field type for roadmap node");

        assertTrue(invokeIsTruncatedJsonParseFailure(truncated));
        assertFalse(invokeIsTruncatedJsonParseFailure(nonTruncated));
    }

    @Test
    @DisplayName("computeRetryBackoffMs should grow exponentially")
    void computeRetryBackoffMs_ShouldGrowExponentially() throws Exception {
        assertEquals(1_000L, invokeComputeRetryBackoffMs(0));
        assertEquals(2_000L, invokeComputeRetryBackoffMs(1));
        assertEquals(4_000L, invokeComputeRetryBackoffMs(2));
    }

    private GenerateRoadmapRequest request() {
        return GenerateRoadmapRequest.builder()
                .goal("Become a backend developer")
                .duration("3 months")
                .experience("beginner")
                .style("project-based")
                .build();
    }

    private RoadmapResponse.RoadmapMetadata invokeParseMetadata(String metadataJson) throws Exception {
        Method method = AiRoadmapServiceImpl.class.getDeclaredMethod("parseMetadata", JsonNode.class);
        method.setAccessible(true);
        JsonNode metadataNode = new ObjectMapper().readTree(metadataJson);
        return (RoadmapResponse.RoadmapMetadata) method.invoke(service, metadataNode);
    }

    private String invokeClassifyAiFailure(Throwable throwable) throws Exception {
        Method method = AiRoadmapServiceImpl.class.getDeclaredMethod("classifyAiFailure", Throwable.class);
        method.setAccessible(true);
        return (String) method.invoke(service, throwable);
    }

    private boolean invokeIsRetryableAiFailure(Throwable throwable) throws Exception {
        Method method = AiRoadmapServiceImpl.class.getDeclaredMethod("isRetryableAiFailure", Throwable.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, throwable);
    }

    private boolean invokeIsTruncatedJsonParseFailure(ApiException ex) throws Exception {
        Method method = AiRoadmapServiceImpl.class.getDeclaredMethod("isTruncatedJsonParseFailure", ApiException.class);
        method.setAccessible(true);
        return (boolean) method.invoke(service, ex);
    }

    private long invokeComputeRetryBackoffMs(int attemptIndex) throws Exception {
        Method method = AiRoadmapServiceImpl.class.getDeclaredMethod("computeRetryBackoffMs", int.class);
        method.setAccessible(true);
        return (long) method.invoke(service, attemptIndex);
    }
}
