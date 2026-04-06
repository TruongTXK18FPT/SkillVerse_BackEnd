package com.exe.skillverse_backend.study_service.service.impl;

import com.exe.skillverse_backend.ai_service.service.RoadmapCompletionSyncService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.study_service.dto.request.CreateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.request.UpdateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.response.TaskColumnResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskResponse;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.entity.Task;
import com.exe.skillverse_backend.study_service.entity.TaskColumn;
import com.exe.skillverse_backend.study_service.repository.TaskColumnRepository;
import com.exe.skillverse_backend.study_service.repository.TaskRepository;
import com.exe.skillverse_backend.study_service.service.TaskBoardService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskBoardServiceImpl implements TaskBoardService {

    private final TaskRepository taskRepository;
    private final TaskColumnRepository taskColumnRepository;
    private final UserRepository userRepository;
    private final StudySessionRepository studySessionRepository;
    private final NotificationService notificationService;
    private final RoadmapCompletionSyncService roadmapCompletionSyncService;

    private static final String DEFAULT_COLUMN_TODO = "To Do";

    @Override
    @Transactional
    public List<TaskColumnResponse> getBoard(Long userId) {
        return getBoard(userId, null);
    }

    @Override
    @Transactional
    public List<TaskColumnResponse> getBoard(Long userId, Long roadmapSessionId) {
        List<TaskColumn> columns = taskColumnRepository.findByUserIdOrderByOrderIndexAsc(userId);
        if (columns.isEmpty()) {
            initializeDefaultColumns(userId);
            columns = taskColumnRepository.findByUserIdOrderByOrderIndexAsc(userId);
        }
        // Load all tasks for the user and filter archived in-memory.
        // Safe for DBs where the archived column hasn't been migrated yet.
        // When roadmapSessionId is provided, also filter by roadmap in userNotes.
        List<Task> allUserTasks = taskRepository.findByUserId(userId);
        final Set<UUID> archivedTaskIds = allUserTasks.stream()
                .filter(t -> Boolean.TRUE.equals(t.getArchived()))
                .map(Task::getId)
                .collect(Collectors.toSet());
        final String roadmapMarker = roadmapSessionId != null ? "roadmap=" + roadmapSessionId : null;

        return columns.stream()
                .map(col -> {
                    List<TaskResponse> tasks = (col.getTasks() == null ? List.<Task>of() : col.getTasks()).stream()
                            .filter(t -> !archivedTaskIds.contains(t.getId()))
                            .filter(t -> roadmapMarker == null || (t.getUserNotes() != null && t.getUserNotes().contains(roadmapMarker)))
                            .map(this::mapToTaskResponse)
                            .collect(Collectors.toList());
                    return TaskColumnResponse.builder()
                            .id(col.getId())
                            .name(col.getName())
                            .orderIndex(col.getOrderIndex())
                            .color(col.getColor())
                            .tasks(tasks)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Archive all tasks linked to a roadmap session.
     * Tasks are soft-deleted (archived=true) and hidden from the board,
     * but preserved in DB for audit/debug. Called when a roadmap is paused/cancelled.
     *
     * DEFENSIVE: wrapped in try-catch so missing columns / query errors
     * do NOT crash the roadmap pause/delete flow. Task archival is optional.
     */
    @Override
    @Transactional
    public int archiveTasksByRoadmapSession(Long userId, Long roadmapSessionId) {
        try {
            String marker = "roadmap=" + roadmapSessionId;
            return taskRepository.archiveByUserNotesContaining(userId, marker);
        } catch (Exception ex) {
            log.warn("⚠️ Failed to archive tasks for roadmap {} (user {}): {}. "
                    + "This is non-fatal — continuing roadmap pause/delete without task archival.",
                    roadmapSessionId, userId, ex.getMessage());
            return 0;
        }
    }

    /**
     * Unarchive all archived tasks linked to a roadmap session.
     * Called when a roadmap is resumed so tasks reappear on the board.
     *
     * DEFENSIVE: wrapped in try-catch so missing columns / query errors
     * do NOT crash the resume flow.
     */
    @Override
    @Transactional
    public int unarchiveTasksByRoadmapSession(Long userId, Long roadmapSessionId) {
        try {
            String marker = "roadmap=" + roadmapSessionId;
            return taskRepository.unarchiveByUserNotesContaining(userId, marker);
        } catch (Exception ex) {
            log.warn("⚠️ Failed to unarchive tasks for roadmap {} (user {}): {}. "
                    + "This is non-fatal — continuing roadmap resume without task unarchival.",
                    roadmapSessionId, userId, ex.getMessage());
            return 0;
        }
    }

    @Override
    @Transactional
    public void checkUpcomingDeadlines(Long userId) {
        List<Task> tasks = taskRepository.findByUserId(userId).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getArchived())).collect(Collectors.toList());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime warningThreshold = now.plusHours(24); // Notify if deadline is within 24 hours
        
        for (Task task : tasks) {
            // If task is not done and deadline is approaching
            if (!"Done".equals(task.getStatus()) && 
                !"Overdue".equals(task.getStatus()) && 
                task.getDeadline() != null && 
                task.getDeadline().isAfter(now) && 
                task.getDeadline().isBefore(warningThreshold)) {
                
                notificationService.createNotification(
                    userId,
                    "Upcoming Deadline",
                    "Task '" + task.getTitle() + "' is due in less than 24 hours!",
                    NotificationType.TASK_DEADLINE,
                    task.getId().toString()
                );
            }
        }
    }

    private void initializeDefaultColumns(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        createColumnInternal(user, "To Do", 1);
        createColumnInternal(user, "In Progress", 2);
        createColumnInternal(user, "Done", 3);
        createColumnInternal(user, "Overdue", 4);
    }

    private void createColumnInternal(User user, String name, int order) {
        TaskColumn column = TaskColumn.builder()
                .name(name)
                .orderIndex(order)
                .user(user)
                .build();
        taskColumnRepository.save(column);
    }

    @Override
    @Transactional
    public TaskColumnResponse createColumn(Long userId, String name, String color) {
        User user = userRepository.findById(userId).orElseThrow();
        // Find max order
        List<TaskColumn> existing = taskColumnRepository.findByUserIdOrderByOrderIndexAsc(userId);
        int nextOrder = existing.isEmpty() ? 1 : existing.get(existing.size() - 1).getOrderIndex() + 1;
        
        TaskColumn column = TaskColumn.builder()
                .name(name)
                .orderIndex(nextOrder)
                .user(user)
                .color(color)
                .build();
        return mapToColumnResponse(taskColumnRepository.save(column));
    }

    @Override
    @Transactional
    public TaskColumnResponse updateColumn(UUID columnId, String name, String color) {
        TaskColumn column = taskColumnRepository.findById(columnId)
                .orElseThrow(() -> new RuntimeException("Column not found"));
        
        if (name != null) column.setName(name);
        if (color != null) column.setColor(color);
        
        return mapToColumnResponse(taskColumnRepository.save(column));
    }

    @Override
    @Transactional
    public TaskResponse createTask(Long userId, CreateTaskRequest request) {
        User user = userRepository.findById(userId).orElseThrow();
        TaskColumn column = taskColumnRepository.findById(request.getColumnId())
                .orElseThrow(() -> new RuntimeException("Column not found"));

        List<StudySession> sessions = new ArrayList<>();
        if (request.getLinkedSessionIds() != null && !request.getLinkedSessionIds().isEmpty()) {
            sessions = studySessionRepository.findAllById(request.getLinkedSessionIds());
        }

        // Put the new task at the end of the column
        List<Task> existingTasks = taskRepository.findByColumnIdOrderByOrderIndexAsc(column.getId());
        double nextOrder = 1.0;
        if (!existingTasks.isEmpty()) {
            Task lastTask = existingTasks.get(existingTasks.size() - 1);
            Double lastOrder = lastTask.getOrderIndex();
            nextOrder = (lastOrder != null ? lastOrder : existingTasks.size()) + 1.0;
        }

        Task task = Task.builder()
                .title(request.getTitle())
                .fullDescription(request.getDescription())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .deadline(request.getDeadline())
                .priority(request.getPriority())
                .status(column.getName()) // Sync status with column name
                .orderIndex(nextOrder)
                .column(column)
                .user(user)
                .linkedSessions(sessions)
                .userProgress(request.getUserProgress() != null ? request.getUserProgress() : 0)
                .satisfactionLevel(request.getSatisfactionLevel())
                .userNotes(request.getUserNotes())
                .build();

        Task savedTask = taskRepository.save(task);
        roadmapCompletionSyncService.syncTaskProgress(savedTask);
        return mapToTaskResponse(savedTask);
    }

    @Override
    @Transactional
    public TaskResponse updateTask(UUID taskId, UpdateTaskRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        if (request.getTitle() != null) task.setTitle(request.getTitle());
        if (request.getDescription() != null) task.setFullDescription(request.getDescription());
        if (request.getStartDate() != null) task.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) task.setEndDate(request.getEndDate());
        if (request.getDeadline() != null) task.setDeadline(request.getDeadline());
        if (request.getPriority() != null) task.setPriority(request.getPriority());
        if (request.getStatus() != null) task.setStatus(request.getStatus());
        if (request.getUserProgress() != null) task.setUserProgress(request.getUserProgress());
        if (request.getSatisfactionLevel() != null) task.setSatisfactionLevel(request.getSatisfactionLevel());
        if (request.getUserNotes() != null) task.setUserNotes(request.getUserNotes());

        if (request.getColumnId() != null && !request.getColumnId().equals(task.getColumn().getId())) {
             TaskColumn newColumn = taskColumnRepository.findById(request.getColumnId())
                     .orElseThrow(() -> new RuntimeException("Column not found"));
             task.setColumn(newColumn);
             task.setStatus(newColumn.getName());
        }

        if (request.getLinkedSessionIds() != null) {
            List<StudySession> sessions = studySessionRepository.findAllById(request.getLinkedSessionIds());
            task.setLinkedSessions(sessions);
        }

        Task savedTask = taskRepository.save(task);
        roadmapCompletionSyncService.syncTaskProgress(savedTask);
        return mapToTaskResponse(savedTask);
    }

    @Override
    @Transactional
    public void deleteTask(UUID taskId) {
        taskRepository.deleteById(taskId);
    }

    @Override
    @Transactional
    public void moveTask(UUID taskId, UUID targetColumnId) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        TaskColumn column = taskColumnRepository.findById(targetColumnId).orElseThrow();
        List<Task> existingTasks = taskRepository.findByColumnIdOrderByOrderIndexAsc(column.getId());
        double nextOrder = 1.0;
        if (!existingTasks.isEmpty()) {
            Task lastTask = existingTasks.get(existingTasks.size() - 1);
            Double lastOrder = lastTask.getOrderIndex();
            nextOrder = (lastOrder != null ? lastOrder : existingTasks.size()) + 1.0;
        }

        task.setColumn(column);
        task.setStatus(column.getName());
        task.setOrderIndex(nextOrder);
        Task savedTask = taskRepository.save(task);
        roadmapCompletionSyncService.syncTaskProgress(savedTask);
    }

    @Override
    @Transactional
    public TaskResponse reorderTask(UUID taskId, UUID targetColumnId, Double previousOrderIndex, Double nextOrderIndex) {
        Task task = taskRepository.findById(taskId).orElseThrow();
        TaskColumn column = taskColumnRepository.findById(targetColumnId).orElseThrow();

        double newOrderIndex;
        if (previousOrderIndex == null && nextOrderIndex == null) {
            newOrderIndex = 1.0;
        } else if (previousOrderIndex == null) {
            newOrderIndex = nextOrderIndex / 2.0;
        } else if (nextOrderIndex == null) {
            newOrderIndex = previousOrderIndex + 1.0;
        } else {
            newOrderIndex = previousOrderIndex + (nextOrderIndex - previousOrderIndex) / 2.0;
        }

        task.setColumn(column);
        task.setStatus(column.getName());
        task.setOrderIndex(newOrderIndex);
        
        Task savedTask = taskRepository.save(task);
        roadmapCompletionSyncService.syncTaskProgress(savedTask);
        return mapToTaskResponse(savedTask);
    }

    @Override
    @Transactional
    public int clearOverdueTasks(Long userId, int overdueDays, UUID columnId) {
        int safeOverdueDays = Math.max(1, overdueDays);
        LocalDateTime cutoffDateTime = LocalDateTime.now().minusDays(safeOverdueDays);

        List<UUID> taskIdsToDelete = taskRepository.findByUserId(userId).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getArchived()))
                .filter(task -> columnId == null || (task.getColumn() != null && columnId.equals(task.getColumn().getId())))
                .filter(task -> task.getDeadline() != null && !task.getDeadline().isAfter(cutoffDateTime))
                .filter(task -> task.getUserProgress() == null || task.getUserProgress() < 100)
                .filter(task -> task.getStatus() == null || !"Done".equalsIgnoreCase(task.getStatus()))
                .map(Task::getId)
                .collect(Collectors.toList());

        if (taskIdsToDelete.isEmpty()) {
            return 0;
        }

        taskRepository.deleteAllByIdInBatch(taskIdsToDelete);
        return taskIdsToDelete.size();
    }

    @Override
    @Transactional
    public void checkOverdueTasks(Long userId) {
        List<Task> tasks = taskRepository.findByUserId(userId).stream()
                .filter(t -> !Boolean.TRUE.equals(t.getArchived())).collect(Collectors.toList());
        // Find or create Overdue column
        List<TaskColumn> columns = taskColumnRepository.findByUserIdOrderByOrderIndexAsc(userId);
        TaskColumn overdueColumn = columns.stream()
                .filter(c -> "Overdue".equalsIgnoreCase(c.getName()))
                .findFirst()
                .orElseGet(() -> {
                    User user = userRepository.findById(userId).orElseThrow();
                    TaskColumn c = TaskColumn.builder().name("Overdue").orderIndex(99).user(user).build();
                    return taskColumnRepository.save(c);
                });

        LocalDateTime now = LocalDateTime.now();
        for (Task task : tasks) {
            if (task.getDeadline() != null && task.getDeadline().isBefore(now) && 
                !"Done".equalsIgnoreCase(task.getStatus()) && 
                !"Overdue".equalsIgnoreCase(task.getStatus())) {
                
                task.setColumn(overdueColumn);
                task.setStatus("Overdue");
                taskRepository.save(task);

                notificationService.createNotification(
                    userId,
                    "Task Overdue",
                    "Task '" + task.getTitle() + "' is overdue!",
                    NotificationType.TASK_OVERDUE,
                    task.getId().toString()
                );
            }
        }
    }

    private TaskColumnResponse mapToColumnResponse(TaskColumn column) {
        return TaskColumnResponse.builder()
                .id(column.getId())
                .name(column.getName())
                .orderIndex(column.getOrderIndex())
                .color(column.getColor())
                .tasks(List.of())
                .build();
    }

    private TaskResponse mapToTaskResponse(Task task) {
        List<UUID> linkedSessionIds = task.getLinkedSessions() == null ? new ArrayList<>() :
                task.getLinkedSessions().stream().map(StudySession::getId).collect(Collectors.toList());

        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getFullDescription())
                .startDate(task.getStartDate())
                .endDate(task.getEndDate())
                .deadline(task.getDeadline())
                .priority(task.getPriority())
                .status(task.getStatus())
                .orderIndex(task.getOrderIndex())
                .columnId(task.getColumn().getId())
                .userProgress(task.getUserProgress())
                .satisfactionLevel(task.getSatisfactionLevel())
                .userNotes(task.getUserNotes())
                .linkedSessionIds(linkedSessionIds)
                .build();
    }
}
