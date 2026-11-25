package com.exe.skillverse_backend.roadmap_package_service.service.impl;

import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.roadmap_package_service.service.RoadmapNodeAiEnrichmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Background sequential AI enrichment for roadmap template nodes marked as PENDING.
 * Spaces requests using app.roadmap.enrichment.background-gap-ms to respect Mistral RPM limits.
 */
@Service
@Slf4j
public class RoadmapBackgroundEnrichmentService {

    public record PendingNodeEnrichmentTask(
            String nodeId,
            String title,
            String description,
            String expectedOutput,
            String rubric,
            String skillName,
            String studentLevel,
            String studentGoal,
            boolean isGap,
            boolean isStrength,
            String pinnedDocumentIds,
            String lessonsJson
    ) {
    }

    private final RoadmapNodeAiEnrichmentService nodeAiEnrichmentService;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.roadmap.enrichment.background-gap-ms:32000}")
    private long backgroundGapMs = 32000L;

    @Autowired
    public RoadmapBackgroundEnrichmentService(
            RoadmapNodeAiEnrichmentService nodeAiEnrichmentService,
            RoadmapSessionRepository roadmapSessionRepository,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.nodeAiEnrichmentService = nodeAiEnrichmentService;
        this.roadmapSessionRepository = roadmapSessionRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async("roadmapEnrichmentTaskExecutor")
    public void enrichRemainingNodesAsync(Long sessionId, List<PendingNodeEnrichmentTask> pendingTasks) {
        if (sessionId == null || pendingTasks == null || pendingTasks.isEmpty()) {
            return;
        }

        log.info("🚀 Starting background AI enrichment for session {} ({} pending nodes, gap={}ms)",
                sessionId, pendingTasks.size(), backgroundGapMs);

        for (PendingNodeEnrichmentTask task : pendingTasks) {
            if (task == null || task.nodeId() == null) {
                continue;
            }

            if (backgroundGapMs > 0) {
                try {
                    Thread.sleep(backgroundGapMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Background AI enrichment interrupted for session {} at node '{}'",
                            sessionId, task.nodeId());
                    break;
                }
            }

            try {
                RoadmapNodeAiEnrichmentService.EnrichedNode enriched = nodeAiEnrichmentService.enrichNode(
                        task.title(),
                        task.description(),
                        task.expectedOutput(),
                        task.rubric(),
                        task.skillName(),
                        task.studentLevel(),
                        task.studentGoal(),
                        task.isGap(),
                        task.isStrength(),
                        task.pinnedDocumentIds(),
                        task.lessonsJson()
                );

                persistEnrichedNode(sessionId, task.nodeId(), enriched);
            } catch (Exception e) {
                log.error("Background enrichment failed for session {} node '{}': {}",
                        sessionId, task.nodeId(), e.getMessage(), e);
            }
        }

        log.info("🏁 Finished background AI enrichment batch for session {}", sessionId);
    }

    protected void persistEnrichedNode(
            Long sessionId,
            String nodeId,
            RoadmapNodeAiEnrichmentService.EnrichedNode enriched) {
        if (enriched == null) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            Optional<RoadmapSession> sessionOpt = roadmapSessionRepository.findByIdForUpdate(sessionId);
            if (sessionOpt.isEmpty()) {
                log.warn("RoadmapSession {} not found while persisting background enrichment for node '{}'",
                        sessionId, nodeId);
                return;
            }

            RoadmapSession session = sessionOpt.get();
            if (session.getStatus() == RoadmapSession.RoadmapStatus.DELETED) {
                log.info("RoadmapSession {} is DELETED; skipping background enrichment update for node '{}'",
                        sessionId, nodeId);
                return;
            }

            String currentJson = session.getRoadmapJson();
            if (currentJson == null || currentJson.isBlank()) {
                return;
            }

            try {
                JsonNode root = objectMapper.readTree(currentJson);
                JsonNode roadmapArray = root.path("roadmap");
                if (!roadmapArray.isArray()) {
                    return;
                }

                boolean updated = false;
                for (JsonNode item : roadmapArray) {
                    if (item.isObject() && nodeId.equals(item.path("id").asText())) {
                        applyEnrichedFields((ObjectNode) item, enriched);
                        updated = true;
                        break;
                    }
                }

                if (updated) {
                    String updatedJson = objectMapper.writeValueAsString(root);
                    roadmapSessionRepository.updateRoadmapJson(sessionId, updatedJson);
                    log.info("✅ Background enriched and saved node '{}' in session {}", nodeId, sessionId);
                } else {
                    log.warn("Node '{}' not found in roadmapJson for session {}", nodeId, sessionId);
                }
            } catch (Exception e) {
                log.error("Failed to update roadmapJson in session {} for node '{}': {}",
                        sessionId, nodeId, e.getMessage(), e);
            }
        });
    }

    private void applyEnrichedFields(ObjectNode nodeObj, RoadmapNodeAiEnrichmentService.EnrichedNode enriched) {
        if (enriched.getDescription() != null && !enriched.getDescription().isBlank()) {
            nodeObj.put("description", enriched.getDescription());
        }
        if (enriched.getExpectedOutput() != null && !enriched.getExpectedOutput().isBlank()) {
            nodeObj.put("expected_output", enriched.getExpectedOutput());
        }
        if (enriched.getRubric() != null && !enriched.getRubric().isBlank()) {
            nodeObj.put("rubric", enriched.getRubric());
        }
        if (enriched.getLearningObjectives() != null && !enriched.getLearningObjectives().isEmpty()) {
            ArrayNode arr = nodeObj.putArray("learning_objectives");
            for (String item : enriched.getLearningObjectives()) {
                arr.add(item);
            }
        }
        if (enriched.getPracticalExercises() != null && !enriched.getPracticalExercises().isEmpty()) {
            ArrayNode arr = nodeObj.putArray("practical_exercises");
            for (String item : enriched.getPracticalExercises()) {
                arr.add(item);
            }
        }
        if (enriched.getSuccessCriteria() != null && !enriched.getSuccessCriteria().isEmpty()) {
            ArrayNode arr = nodeObj.putArray("success_criteria");
            for (String item : enriched.getSuccessCriteria()) {
                arr.add(item);
            }
        }
        if (enriched.getLessons() != null && !enriched.getLessons().isEmpty()) {
            ArrayNode lessonsArr = nodeObj.putArray("lessons");
            for (RoadmapNodeAiEnrichmentService.EnrichedLesson lesson : enriched.getLessons()) {
                ObjectNode les = lessonsArr.addObject();
                les.put("title", lesson.getTitle());
                les.put("description", lesson.getDescription());
                les.put("learningObjective", lesson.getLearningObjective());
                if (lesson.getEstimatedMinutes() != null) {
                    les.put("estimatedMinutes", lesson.getEstimatedMinutes());
                } else {
                    les.putNull("estimatedMinutes");
                }
            }
        }
        nodeObj.put("enrichment_status", "COMPLETED");
    }
}
