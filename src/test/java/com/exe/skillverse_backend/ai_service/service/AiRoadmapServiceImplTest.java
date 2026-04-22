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
import com.exe.skillverse_backend.ai_service.service.LocalAiGateway;
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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.mockito.Mockito;
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

    @Mock
    private LocalAiGateway localAiGateway;

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
                multiLevelCourseMatcher,
                null);
    }

    @Test
    @DisplayName("generateRoadmap with null localAiGateway does not call local AI")
    void generateRoadmap_WithNullLocalGateway_SkipsLocalPath() {
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
                multiLevelCourseMatcher,
                null);

        Mockito.verifyNoInteractions(localAiGateway);
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
    @DisplayName("validateAndParseRoadmapV2 should repair unescaped quotes inside string values")
    void validateAndParseRoadmapV2_ShouldRepairUnescapedQuotesInsideStringValues() throws Exception {
        String roadmapJson = """
                {
                  "roadmap_metadata": {
                    "title": "Support roadmap",
                    "original_goal": "Learn customer support",
                    "validated_goal": "Build customer support fundamentals",
                    "duration": "8 weeks",
                    "desired_duration": "8 weeks",
                    "experience_level": "beginner",
                    "learning_style": "hands-on",
                    "difficulty_level": "medium",
                    "roadmap_mode": "CAREER_BASED"
                  },
                  "overview": {
                    "purpose": "Learn support operations",
                    "audience": "Beginners",
                    "post_roadmap_state": "Can handle support workflows"
                  },
                  "skill_dependencies": [],
                  "roadmap": [
                    {
                      "id": "quest-1",
                      "title": "Support basics",
                      "description": "Practice "active listening" with sample scenarios.",
                      "estimated_time_minutes": 60,
                      "type": "MAIN",
                      "is_core": true,
                      "parent_id": null,
                      "difficulty": "easy",
                      "learning_objectives": ["Understand the workflow"],
                      "key_concepts": ["Tickets"],
                      "practical_exercises": ["Role play"],
                      "suggested_resources": ["Internal handbook"],
                      "success_criteria": ["Explain the process"],
                      "prerequisites": [],
                      "children": [],
                      "estimated_completion_rate": "90%"
                    }
                  ],
                  "roadmap_statistics": {
                    "total_nodes": 1,
                    "main_nodes": 1,
                    "side_nodes": 0,
                    "total_estimated_hours": 1.0,
                    "difficulty_distribution": {
                      "easy": 1,
                      "medium": 0,
                      "hard": 0
                    }
                  },
                  "learning_tips": ["Stay consistent"]
                }
                """;

        Object parsedRoadmap = invokeValidateAndParseRoadmapV2(roadmapJson);
        RoadmapResponse.RoadmapMetadata metadata = extractMetadata(parsedRoadmap);
        List<RoadmapResponse.RoadmapNode> nodes = extractNodes(parsedRoadmap);

        assertEquals("Support roadmap", metadata.getTitle());
        assertEquals(1, nodes.size());
        assertEquals("Practice \"active listening\" with sample scenarios.", nodes.get(0).getDescription());
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
    @DisplayName("isTruncatedJsonParseFailure should ignore generic invalid-json wrapper messages")
    void isTruncatedJsonParseFailure_ShouldIgnoreGenericInvalidJsonWrapperMessage() throws Exception {
        ApiException malformed = new ApiException(
                ErrorCode.BAD_REQUEST,
                "AI response was incomplete or invalid JSON. Please retry. "
                        + "(Error: Unexpected character (',' (code 44)): "
                        + "was expecting a colon to separate field name and value)");

        assertFalse(invokeIsTruncatedJsonParseFailure(malformed));
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

    private Object invokeValidateAndParseRoadmapV2(String roadmapJson) throws Exception {
        Method method = AiRoadmapServiceImpl.class.getDeclaredMethod("validateAndParseRoadmapV2", String.class);
        method.setAccessible(true);
        return method.invoke(service, roadmapJson);
    }

    private RoadmapResponse.RoadmapMetadata extractMetadata(Object parsedRoadmap) throws Exception {
        Method method = parsedRoadmap.getClass().getDeclaredMethod("metadata");
        method.setAccessible(true);
        return (RoadmapResponse.RoadmapMetadata) method.invoke(parsedRoadmap);
    }

    @SuppressWarnings("unchecked")
    private List<RoadmapResponse.RoadmapNode> extractNodes(Object parsedRoadmap) throws Exception {
        Method method = parsedRoadmap.getClass().getDeclaredMethod("nodes");
        method.setAccessible(true);
        return (List<RoadmapResponse.RoadmapNode>) method.invoke(parsedRoadmap);
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

    // ─── Cascade Refactor Tests (2026-04-15) ───────────────────────────────────

    @Test
    @DisplayName("callGeminiWithRetry method should exist in AiRoadmapServiceImpl")
    void callGeminiWithRetry_MethodShouldExist() throws Exception {
        boolean found = false;
        for (Method m : AiRoadmapServiceImpl.class.getDeclaredMethods()) {
            if ("callGeminiWithRetry".equals(m.getName())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "callGeminiWithRetry method should exist after refactor");
    }

    @Test
    @DisplayName("callMistralWithRetry method should exist (renamed from callMistralWithPrimaryRetry)")
    void callMistralWithRetry_MethodShouldExist() throws Exception {
        boolean foundNewName = false;
        boolean foundOldName = false;
        for (Method m : AiRoadmapServiceImpl.class.getDeclaredMethods()) {
            if ("callMistralWithRetry".equals(m.getName())) {
                foundNewName = true;
            }
            if ("callMistralWithPrimaryRetry".equals(m.getName())) {
                foundOldName = true;
            }
        }
        assertTrue(foundNewName, "callMistralWithRetry method should exist (renamed)");
        assertFalse(foundOldName, "Old name callMistralWithPrimaryRetry should no longer exist");
    }

    @Test
    @DisplayName("isTruncatedJsonParseFailure should detect end-of-input truncated JSON")
    void isTruncatedJsonParseFailure_ShouldDetectEndOfInput() throws Exception {
        ApiException truncatedGemini = new ApiException(
                ErrorCode.BAD_REQUEST,
                "Unexpected end-of-input while parsing root object");
        ApiException nonTruncated = new ApiException(
                ErrorCode.BAD_REQUEST,
                "Invalid field type for roadmap node");

        assertTrue(invokeIsTruncatedJsonParseFailure(truncatedGemini),
                "Should detect 'end-of-input' truncated JSON");
        assertFalse(invokeIsTruncatedJsonParseFailure(nonTruncated),
                "Should NOT detect non-truncated parse errors");
    }

    @Test
    @DisplayName("computeRetryBackoffMs exponential values should match spec (1s, 2s, 4s)")
    void computeRetryBackoffMs_ShouldMatchExponentialSpec() throws Exception {
        assertEquals(1_000L, invokeComputeRetryBackoffMs(0), "Attempt 0 → 1s backoff");
        assertEquals(2_000L, invokeComputeRetryBackoffMs(1), "Attempt 1 → 2s backoff");
        assertEquals(4_000L, invokeComputeRetryBackoffMs(2), "Attempt 2 → 4s backoff");
    }
}
