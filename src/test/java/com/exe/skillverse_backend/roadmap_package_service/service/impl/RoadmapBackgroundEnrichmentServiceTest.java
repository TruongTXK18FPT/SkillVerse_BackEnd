package com.exe.skillverse_backend.roadmap_package_service.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.roadmap_package_service.service.RoadmapNodeAiEnrichmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

@ExtendWith(MockitoExtension.class)
class RoadmapBackgroundEnrichmentServiceTest {

    @Mock
    private RoadmapNodeAiEnrichmentService nodeAiEnrichmentService;

    @Mock
    private RoadmapSessionRepository roadmapSessionRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private ObjectMapper objectMapper;
    private RoadmapBackgroundEnrichmentService service;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        service = new RoadmapBackgroundEnrichmentService(
                nodeAiEnrichmentService,
                roadmapSessionRepository,
                objectMapper,
                transactionManager
        );
        Field gapField = RoadmapBackgroundEnrichmentService.class.getDeclaredField("backgroundGapMs");
        gapField.setAccessible(true);
        gapField.set(service, 0L);
    }

    @Test
    void enrichRemainingNodesAsyncUpdatesPendingNodeAndMarksCompleted() throws Exception {
        String initialJson = """
                {
                  "roadmap": [
                    {
                      "id": "node-1",
                      "title": "Node 1",
                      "description": "Synced",
                      "enrichment_status": "COMPLETED"
                    },
                    {
                      "id": "node-3",
                      "title": "Node 3",
                      "description": "Blueprint desc",
                      "enrichment_status": "PENDING"
                    }
                  ]
                }
                """;
        RoadmapSession session = RoadmapSession.builder()
                .id(42L)
                .status(RoadmapSession.RoadmapStatus.ACTIVE)
                .roadmapJson(initialJson)
                .build();

        when(roadmapSessionRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(session));
        when(nodeAiEnrichmentService.enrichNode(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyBoolean(), anyBoolean(), any(), any()))
                .thenReturn(new RoadmapNodeAiEnrichmentService.EnrichedNode(
                        "Personalized node 3 description",
                        List.of("Obj 1"),
                        List.of("Ex 1"),
                        List.of("Crit 1"),
                        "Expected output markdown",
                        "| Criterion | Weight | Pass | Fail |",
                        List.of(new RoadmapNodeAiEnrichmentService.EnrichedLesson("Lesson 1", "Desc 1", "Obj 1", 45))
                ));

        var task = new RoadmapBackgroundEnrichmentService.PendingNodeEnrichmentTask(
                "node-3",
                "Node 3",
                "Blueprint desc",
                "Blueprint output",
                "Blueprint rubric",
                "Spring Boot",
                "INTERMEDIATE",
                "PREPARE_JOB",
                true,
                false,
                null,
                null
        );

        service.enrichRemainingNodesAsync(42L, List.of(task));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(roadmapSessionRepository).updateRoadmapJson(eq(42L), jsonCaptor.capture());

        JsonNode updatedRoot = objectMapper.readTree(jsonCaptor.getValue());
        JsonNode node1 = updatedRoot.path("roadmap").get(0);
        JsonNode node3 = updatedRoot.path("roadmap").get(1);

        assertThat(node1.path("description").asText()).isEqualTo("Synced");
        assertThat(node1.path("enrichment_status").asText()).isEqualTo("COMPLETED");

        assertThat(node3.path("enrichment_status").asText()).isEqualTo("COMPLETED");
        assertThat(node3.path("description").asText()).isEqualTo("Personalized node 3 description");
        assertThat(node3.path("expected_output").asText()).isEqualTo("Expected output markdown");
        assertThat(node3.path("rubric").asText()).isEqualTo("| Criterion | Weight | Pass | Fail |");
        assertThat(node3.path("learning_objectives").get(0).asText()).isEqualTo("Obj 1");
        assertThat(node3.path("practical_exercises").get(0).asText()).isEqualTo("Ex 1");
        assertThat(node3.path("success_criteria").get(0).asText()).isEqualTo("Crit 1");
        assertThat(node3.path("lessons").get(0).path("title").asText()).isEqualTo("Lesson 1");
    }

    @Test
    void enrichRemainingNodesAsyncSkipsUpdateWhenSessionDeleted() {
        RoadmapSession deletedSession = RoadmapSession.builder()
                .id(77L)
                .status(RoadmapSession.RoadmapStatus.DELETED)
                .roadmapJson("{\"roadmap\":[]}")
                .build();

        when(roadmapSessionRepository.findByIdForUpdate(77L)).thenReturn(Optional.of(deletedSession));
        when(nodeAiEnrichmentService.enrichNode(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyBoolean(), anyBoolean(), any(), any()))
                .thenReturn(new RoadmapNodeAiEnrichmentService.EnrichedNode(
                        "Desc", List.of(), List.of(), List.of(), "Out", "Rubric"));

        var task = new RoadmapBackgroundEnrichmentService.PendingNodeEnrichmentTask(
                "node-3", "Node 3", "Desc", "Out", "Rubric", "Java", "BEGINNER", "EXPLORE",
                false, false, null, null);

        service.enrichRemainingNodesAsync(77L, List.of(task));

        verify(roadmapSessionRepository, never()).updateRoadmapJson(any(), any());
    }
}
