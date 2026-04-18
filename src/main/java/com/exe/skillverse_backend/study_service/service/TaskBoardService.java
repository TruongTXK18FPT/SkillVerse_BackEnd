package com.exe.skillverse_backend.study_service.service;

import com.exe.skillverse_backend.study_service.dto.request.CreateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.request.UpdateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.response.CompleteAllTasksResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskColumnResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskResponse;
import java.util.List;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface TaskBoardService {
    List<TaskColumnResponse> getBoard(Long userId);
    List<TaskColumnResponse> getBoard(Long userId, Long roadmapSessionId);
    /**
     * Get all archived tasks for a user, optionally filtered by roadmap session.
     * Used for the "Show archived" toggle in the task board.
     */
    List<TaskResponse> getArchivedTasks(Long userId, Long roadmapSessionId);

    /**
     * Paginated archived tasks for the modal.
     * @return PageResponse with items, page, size, total
     */
    PageResponse<TaskResponse> getArchivedTasks(Long userId, Long roadmapSessionId, Pageable pageable);
    TaskColumnResponse createColumn(Long userId, String name, String color);
    TaskColumnResponse updateColumn(UUID columnId, String name, String color);
    TaskResponse createTask(Long userId, CreateTaskRequest request);
    TaskResponse updateTask(UUID taskId, UpdateTaskRequest request);
    void deleteTask(UUID taskId);
    void moveTask(UUID taskId, UUID targetColumnId);
    TaskResponse reorderTask(UUID taskId, UUID targetColumnId, Double previousOrderIndex, Double nextOrderIndex);
    int clearOverdueTasks(Long userId, int overdueDays, UUID columnId);
    void checkOverdueTasks(Long userId);
    void checkUpcomingDeadlines(Long userId);
    /**
     * Unarchive a single task so it reappears on the board.
     */
    TaskResponse unarchiveTask(UUID taskId);
    /**
     * Archive all tasks linked to a roadmap session.
     * Called automatically when a roadmap is paused or cancelled.
     * @return number of tasks archived
     */
    int archiveTasksByRoadmapSession(Long userId, Long roadmapSessionId);

    /**
     * Unarchive all tasks linked to a roadmap session.
     * Called when a journey is resumed so tasks reappear on the board.
     * @return number of tasks unarchived
     */
    int unarchiveTasksByRoadmapSession(Long userId, Long roadmapSessionId);

    /**
     * Delete a column. Tasks in the column are moved to the fallback target column
     * (To Do -> In Progress -> first available) before deletion.
     *
     * @param columnId        the column to delete
     * @param targetColumnId  optional explicit target; auto-resolved if omitted
     * @throws IllegalArgumentException if column is protected, is the last column,
     *                                  target equals source, or validation fails
     */
    void deleteColumn(UUID columnId, UUID targetColumnId);

    /**
     * Mark all tasks linked to a specific roadmap node as done.
     * Finds tasks via [ROADMAP_NODE_LINK] userNotes marker, updates each to Done column
     * with userProgress=100, then triggers RoadmapCompletionSyncService to derive node completion.
     *
     * @param userId          the user
     * @param roadmapSessionId  the roadmap session ID
     * @param nodeId          the node ID to complete
     * @return CompleteAllTasksResponse with done/failed counts
     */
    CompleteAllTasksResponse completeAllTasksForNode(Long userId, Long roadmapSessionId, String nodeId);
}
