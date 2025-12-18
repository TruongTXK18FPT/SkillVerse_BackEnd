package com.exe.skillverse_backend.study_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.study_service.dto.request.CreateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.request.UpdateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.response.TaskColumnResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskResponse;
import com.exe.skillverse_backend.study_service.entity.Task;
import com.exe.skillverse_backend.study_service.entity.TaskColumn;
import com.exe.skillverse_backend.study_service.entity.StudySession;
import com.exe.skillverse_backend.study_service.repository.TaskColumnRepository;
import com.exe.skillverse_backend.study_service.repository.TaskRepository;
import com.exe.skillverse_backend.study_service.service.TaskBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskBoardServiceImpl implements TaskBoardService {

    private final TaskRepository taskRepository;
    private final TaskColumnRepository taskColumnRepository;
    private final UserRepository userRepository;
    private final com.exe.skillverse_backend.study_service.repository.StudySessionRepository studySessionRepository;
    private final NotificationService notificationService;

    private static final String DEFAULT_COLUMN_TODO = "To Do";

    @Override
    @Transactional
    public List<TaskColumnResponse> getBoard(Long userId) {
        List<TaskColumn> columns = taskColumnRepository.findByUserIdOrderByOrderIndexAsc(userId);
        if (columns.isEmpty()) {
            // Initialize default columns if none exist
            initializeDefaultColumns(userId);
            columns = taskColumnRepository.findByUserIdOrderByOrderIndexAsc(userId);
        }
        return columns.stream().map(this::mapToColumnResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void checkUpcomingDeadlines(Long userId) {
        List<Task> tasks = taskRepository.findByUserId(userId);
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

        Task task = Task.builder()
                .title(request.getTitle())
                .fullDescription(request.getDescription())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .deadline(request.getDeadline())
                .priority(request.getPriority())
                .status(column.getName()) // Sync status with column name
                .column(column)
                .user(user)
                .linkedSessions(sessions)
                .userProgress(request.getUserProgress() != null ? request.getUserProgress() : 0)
                .satisfactionLevel(request.getSatisfactionLevel())
                .userNotes(request.getUserNotes())
                .build();

        return mapToTaskResponse(taskRepository.save(task));
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

        return mapToTaskResponse(taskRepository.save(task));
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
        task.setColumn(column);
        task.setStatus(column.getName());
        taskRepository.save(task);
    }

    @Override
    @Transactional
    public void checkOverdueTasks(Long userId) {
        List<Task> tasks = taskRepository.findByUserId(userId);
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
        List<TaskResponse> taskResponses = column.getTasks() == null ? new ArrayList<>() : 
                column.getTasks().stream().map(this::mapToTaskResponse).collect(Collectors.toList());
        
        return TaskColumnResponse.builder()
                .id(column.getId())
                .name(column.getName())
                .orderIndex(column.getOrderIndex())
                .color(column.getColor())
                .tasks(taskResponses)
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
                .columnId(task.getColumn().getId())
                .userProgress(task.getUserProgress())
                .satisfactionLevel(task.getSatisfactionLevel())
                .userNotes(task.getUserNotes())
                .linkedSessionIds(linkedSessionIds)
                .build();
    }
}
