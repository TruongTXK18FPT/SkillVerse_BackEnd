package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.study_service.entity.Task;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.entity.StudySessionStatus;
import com.exe.skillverse_backend.study_service.repository.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoadmapCompletionSyncService {

    private static final Pattern ROADMAP_NODE_LINK_PATTERN = Pattern.compile(
            "\\[ROADMAP_NODE_LINK\\](?:\\s+journey=(\\d+))?\\s+roadmap=(\\d+)\\s+node=([^\\s]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> DONE_TASK_STATUSES =
        Set.of("done", "completed", "finished");
    private static final Set<String> IN_PROGRESS_TASK_STATUSES =
        Set.of("inprogress", "doing", "ongoing");
    private static final int IN_PROGRESS_TASK_SIGNAL = 50;
    // Must match JourneyServiceImpl.STUDY_PLAN_LINK_MARKER_PREFIX
    private static final String ROADMAP_NODE_LINK_PREFIX = "[ROADMAP_NODE_LINK]";

    private final UserRoadmapProgressRepository progressRepository;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public Map<String, RoadmapResponse.QuestProgress> overlayDerivedProgress(
            RoadmapSession session,
            List<RoadmapResponse.RoadmapNode> nodes,
            Map<String, RoadmapResponse.QuestProgress> storedProgressMap) {
        return overlayDerivedProgress(session, nodes, storedProgressMap, true);
    }

    @Transactional(readOnly = true)
    public Map<String, RoadmapResponse.QuestProgress> overlayDerivedProgressSnapshot(
            RoadmapSession session,
            List<RoadmapResponse.RoadmapNode> nodes,
            Map<String, RoadmapResponse.QuestProgress> storedProgressMap) {
        return overlayDerivedProgress(session, nodes, storedProgressMap, false);
    }

    private Map<String, RoadmapResponse.QuestProgress> overlayDerivedProgress(
            RoadmapSession session,
            List<RoadmapResponse.RoadmapNode> nodes,
            Map<String, RoadmapResponse.QuestProgress> storedProgressMap,
            boolean persistDerivedCompletions) {

        Map<String, RoadmapResponse.QuestProgress> resolved = new LinkedHashMap<>();
        if (storedProgressMap != null && !storedProgressMap.isEmpty()) {
            resolved.putAll(storedProgressMap);
        }

        if (session == null || session.getUser() == null || session.getUser().getId() == null || nodes == null || nodes.isEmpty()) {
            return resolved;
        }

        Long userId = session.getUser().getId();
        Map<String, List<Task>> tasksByNodeId = loadTasksByNodeId(userId, session.getId());
        List<String> derivedCompletedNodeIds = new ArrayList<>();

        for (RoadmapResponse.RoadmapNode node : nodes) {
            if (node == null || node.getId() == null || node.getId().isBlank()) {
                continue;
            }

            RoadmapResponse.QuestProgress existingProgress = resolved.get(node.getId());
            RoadmapResponse.QuestProgress derivedProgress = deriveSourceDrivenProgress(
                    node,
                    tasksByNodeId,
                    existingProgress);

            if (derivedProgress != null) {
                resolved.put(node.getId(), derivedProgress);
            }

            // BUG-5 FIX: Only mark as COMPLETED via derivation if all prerequisites are also completed.
            // This prevents task-driven derivation from bypassing the sequential locking enforced by updateProgress().
            if (derivedProgress != null
                    && UserRoadmapProgress.ProgressStatus.COMPLETED.name().equals(derivedProgress.getStatus())) {
                List<String> prereqs = node.getPrerequisites();
                boolean allPrereqsMet = (prereqs == null || prereqs.isEmpty())
                        || prereqs.stream().allMatch(prereqId -> {
                            RoadmapResponse.QuestProgress prereqProgress = resolved.get(prereqId);
                            return prereqProgress != null
                                    && UserRoadmapProgress.ProgressStatus.COMPLETED.name().equals(prereqProgress.getStatus());
                        });
                if (allPrereqsMet) {
                    derivedCompletedNodeIds.add(node.getId());
                } else {
                    log.debug("Skipping auto-complete for node '{}' — prerequisites not yet completed: {}",
                            node.getId(), prereqs);
                }
            }
        }

        if (persistDerivedCompletions && !derivedCompletedNodeIds.isEmpty()) {
            persistDerivedCompletions(session, derivedCompletedNodeIds);
        }

        return resolved;
    }

    @Transactional
    public void syncCourseProgress(Long userId, Long courseId) {
        // DEPRECATED: Course enrollment no longer drives roadmap progress directly.
        // Progress is now derived from Study Planner (Task/StudySession) completion.
        // This method is kept for API compatibility but is a no-op.
        log.warn("⚠️ syncCourseProgress(userId={}, courseId={}) is deprecated. "
                + "Course enrollment no longer drives roadmap progress directly. "
                + "Use study plan completion instead.", userId, courseId);
    }

    @Transactional
    public void syncTaskProgress(Task task) {
        if (task == null || task.getUser() == null || task.getUser().getId() == null) {
            return;
        }

        Set<Long> roadmapIds = extractRoadmapIds(task.getUserNotes());
        if (roadmapIds.isEmpty()) {
            return;
        }

        Long userId = task.getUser().getId();
        for (Long roadmapId : roadmapIds) {
            roadmapSessionRepository.findByIdAndUserId(roadmapId, userId).ifPresent(session -> {
                List<RoadmapResponse.RoadmapNode> nodes = extractNodes(session);
                if (nodes.isEmpty()) {
                    return;
                }
                Map<String, RoadmapResponse.QuestProgress> stored = loadStoredProgressMap(session.getId());
                overlayDerivedProgress(session, nodes, stored);
            });
        }
    }

    @Transactional(readOnly = true)
    public String buildCourseMappingAuditReport(Collection<RoadmapSession> sessions) {
        // Course enrollment is no longer used for roadmap progress derivation.
        // This report now only tracks course metadata (suggestedCourseIds) for display purposes.
        int totalNodes = 0;
        int mappedNodes = 0;
        int unmappedNodes = 0;
        int duplicateCourseRefs = 0;

        for (RoadmapSession session : sessions) {
            for (RoadmapResponse.RoadmapNode node : extractNodes(session)) {
                totalNodes++;
                List<String> suggestedCourseIds = node.getSuggestedCourseIds() != null
                        ? node.getSuggestedCourseIds().stream().filter(id -> id != null && !id.isBlank()).toList()
                        : List.of();

                if (suggestedCourseIds.isEmpty()) {
                    unmappedNodes++;
                    continue;
                }

                mappedNodes++;
                Set<String> uniqueIds = new HashSet<>(suggestedCourseIds);
                if (uniqueIds.size() < suggestedCourseIds.size()) {
                    duplicateCourseRefs++;
                }
            }
        }

        return """
                Roadmap Course Mapping Audit
                totalNodes=%d
                mappedNodes=%d
                unmappedNodes=%d
                duplicateCourseRefs=%d
                """.formatted(
                totalNodes,
                mappedNodes,
                unmappedNodes,
                duplicateCourseRefs);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void persistDerivedCompletions(RoadmapSession session, List<String> nodeIds) {
        if (session == null || nodeIds == null || nodeIds.isEmpty()) {
            return;
        }

        List<UserRoadmapProgress> updates = new ArrayList<>();
        Instant now = Instant.now();

        for (String nodeId : nodeIds) {
            UserRoadmapProgress progress = progressRepository
                    .findBySessionIdAndQuestId(session.getId(), nodeId)
                    .orElse(UserRoadmapProgress.builder()
                            .roadmapSession(session)
                            .questId(nodeId)
                            .status(UserRoadmapProgress.ProgressStatus.NOT_STARTED)
                            .progress(0)
                            .build());

            if (progress.getStatus() == UserRoadmapProgress.ProgressStatus.COMPLETED
                    && progress.getProgress() != null
                    && progress.getProgress() == 100) {
                continue;
            }

            progress.setStatus(UserRoadmapProgress.ProgressStatus.COMPLETED);
            progress.setProgress(100);
            if (progress.getCompletedAt() == null) {
                progress.setCompletedAt(now);
            }
            updates.add(progress);
        }

        if (!updates.isEmpty()) {
            progressRepository.saveAll(updates);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, RoadmapResponse.QuestProgress> loadStoredProgressMap(Long sessionId) {
        List<UserRoadmapProgress> progressList = progressRepository.findBySessionId(sessionId);
        if (progressList == null || progressList.isEmpty()) {
            return new LinkedHashMap<>();
        }

        return progressList.stream()
                .collect(Collectors.toMap(
                        UserRoadmapProgress::getQuestId,
                        progress -> RoadmapResponse.QuestProgress.builder()
                                .questId(progress.getQuestId())
                                .status(progress.getStatus().toString())
                                .progress(progress.getStatus() == UserRoadmapProgress.ProgressStatus.COMPLETED ? 100 : 0)
                                .completedAt(progress.getCompletedAt())
                                .build(),
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    @Transactional(readOnly = true)
    public List<RoadmapResponse.RoadmapNode> extractNodes(RoadmapSession session) {
        if (session == null || session.getRoadmapJson() == null || session.getRoadmapJson().isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(session.getRoadmapJson());
            JsonNode roadmapArray = root.path("roadmap");
            if (!roadmapArray.isArray()) {
                return List.of();
            }

            List<RoadmapResponse.RoadmapNode> nodes = new ArrayList<>();
            for (JsonNode nodeJson : roadmapArray) {
                nodes.add(RoadmapResponse.RoadmapNode.builder()
                        .id(nodeJson.path("id").asText(null))
                        .title(nodeJson.path("title").asText(null))
                        .difficulty(nodeJson.path("difficulty").asText(null))
                        .suggestedCourseIds(parseStringArray(nodeJson.path("suggested_course_ids"), nodeJson.path("suggestedCourseIds")))
                        .build());
            }
            return nodes;
        } catch (Exception ex) {
            log.warn("Unable to parse roadmap nodes for session {}: {}", session.getId(), ex.getMessage());
            return List.of();
        }
    }

    private RoadmapResponse.QuestProgress deriveSourceDrivenProgress(
            RoadmapResponse.RoadmapNode node,
            Map<String, List<Task>> tasksByNodeId,
            RoadmapResponse.QuestProgress existingProgress) {

        // Course enrollment is no longer used for roadmap progress derivation.
        // Progress is derived solely from Study Planner (Task/StudySession).
        Integer taskProgress = deriveFallbackNodeProgressPercent(node, tasksByNodeId);

        if (taskProgress == null) {
            return null;
        }

        return buildQuestProgress(node.getId(), taskProgress, existingProgress);
    }

    private Integer deriveFallbackNodeProgressPercent(
            RoadmapResponse.RoadmapNode node,
            Map<String, List<Task>> tasksByNodeId) {
        if (node == null || node.getId() == null || node.getId().isBlank()) {
            return null;
        }

        List<Task> linkedTasks = tasksByNodeId.getOrDefault(node.getId(), List.of());
        if (linkedTasks.isEmpty()) {
            return null; // No tasks linked to this node — signal "no data", not "NOT_STARTED"
        }

        int totalProgress = linkedTasks.stream()
                .map(this::deriveTaskProgressPercent)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        return clampProgress((int) Math.round(totalProgress * 1.0 / linkedTasks.size()));
    }

    private Integer deriveTaskProgressPercent(Task task) {
        if (task == null) {
            return 0;
        }

        if (isCompletedTask(task)) {
            return 100;
        }

        int progress = 0;

        if (task.getUserProgress() != null) {
            progress = Math.max(progress, clampProgress(task.getUserProgress()));
        }

        List<StudySession> linkedSessions = task.getLinkedSessions();
        if (linkedSessions != null && !linkedSessions.isEmpty()) {
            long completedCount = linkedSessions.stream()
                    .filter(Objects::nonNull)
                    .filter(session -> session.getStatus() == StudySessionStatus.COMPLETED)
                    .count();

            if (completedCount > 0) {
                int sessionProgress = (int) Math.round(completedCount * 100.0 / linkedSessions.size());
                progress = Math.max(progress, clampProgress(sessionProgress));
            }
        }

        String normalizedStatus = normalizeTaskStatus(task.getStatus());
        if (IN_PROGRESS_TASK_STATUSES.contains(normalizedStatus)) {
            progress = Math.max(progress, IN_PROGRESS_TASK_SIGNAL);
        }

        return clampProgress(progress);
    }

    private boolean isCompletedTask(Task task) {
        if (task == null) {
            return false;
        }
        List<StudySession> linkedSessions = task.getLinkedSessions();
        if (linkedSessions != null && !linkedSessions.isEmpty()) {
            return linkedSessions.stream().allMatch(session ->
                    session != null && session.getStatus() == StudySessionStatus.COMPLETED);
        }
        if (task.getUserProgress() != null && task.getUserProgress() >= 100) {
            return true;
        }
        return DONE_TASK_STATUSES.contains(normalizeTaskStatus(task.getStatus()));
    }

    private Map<String, List<Task>> loadTasksByNodeId(Long userId, Long roadmapSessionId) {
        // GAP-9: Query only tasks containing the roadmap link marker, instead of loading ALL tasks.
        // The LIKE query narrows results to roadmap-linked tasks, then in-memory grouping applies.
        List<Task> tasks = taskRepository.findByUserIdAndUserNotesContaining(userId, ROADMAP_NODE_LINK_PREFIX);
        if (tasks.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<Task>> tasksByNodeId = new HashMap<>();
        for (Task task : tasks) {
            extractNodeIds(task.getUserNotes(), roadmapSessionId).forEach(nodeId ->
                    tasksByNodeId.computeIfAbsent(nodeId, ignored -> new ArrayList<>()).add(task));
        }
        return tasksByNodeId;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeTaskStatus(String status) {
        return normalize(status).replace("_", "").replace("-", "").replace(" ", "");
    }

    private int clampProgress(Integer progress) {
        if (progress == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, progress));
    }

    private List<String> parseStringArray(JsonNode... candidates) {
        for (JsonNode candidate : candidates) {
            if (candidate != null && candidate.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode item : candidate) {
                    if (item != null && !item.asText("").isBlank()) {
                        values.add(item.asText());
                    }
                }
                return values;
            }
        }
        return List.of();
    }

    private Set<String> extractNodeIds(String notes, Long roadmapSessionId) {
        if (notes == null || notes.isBlank()) {
            return Collections.emptySet();
        }

        Set<String> nodeIds = new HashSet<>();
        Matcher matcher = ROADMAP_NODE_LINK_PATTERN.matcher(notes);
        while (matcher.find()) {
            Long matchedRoadmapId = parseLong(matcher.group(2));
            if (matchedRoadmapId != null && matchedRoadmapId.equals(roadmapSessionId)) {
                String nodeId = matcher.group(3);
                if (nodeId != null && !nodeId.isBlank()) {
                    nodeIds.add(nodeId.trim());
                }
            }
        }
        return nodeIds;
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Set<Long> extractRoadmapIds(String notes) {
        if (notes == null || notes.isBlank()) {
            return Collections.emptySet();
        }
        Set<Long> roadmapIds = new HashSet<>();
        Matcher matcher = ROADMAP_NODE_LINK_PATTERN.matcher(notes);
        while (matcher.find()) {
            Long roadmapId = parseLong(matcher.group(2));
            if (roadmapId != null) {
                roadmapIds.add(roadmapId);
            }
        }
        return roadmapIds;
    }

        private RoadmapResponse.QuestProgress buildQuestProgress(
                String nodeId,
                Integer progressPercent,
                RoadmapResponse.QuestProgress existingProgress) {
            int normalizedProgress = clampProgress(progressPercent);
            String status = normalizedProgress >= 100
                    ? UserRoadmapProgress.ProgressStatus.COMPLETED.name()
                    : normalizedProgress > 0
                            ? UserRoadmapProgress.ProgressStatus.IN_PROGRESS.name()
                            : UserRoadmapProgress.ProgressStatus.NOT_STARTED.name();

            Instant completedAt = null;
            if (UserRoadmapProgress.ProgressStatus.COMPLETED.name().equals(status)) {
                completedAt = existingProgress != null && existingProgress.getCompletedAt() != null
                        ? existingProgress.getCompletedAt()
                        : Instant.now();
            }

            return RoadmapResponse.QuestProgress.builder()
                    .questId(nodeId)
                    .status(status)
                    .progress(normalizedProgress)
                    .completedAt(completedAt)
                    .build();
        }


}
