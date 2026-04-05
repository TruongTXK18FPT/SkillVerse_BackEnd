package com.exe.skillverse_backend.study_service.service;

import com.exe.skillverse_backend.study_service.dto.request.CreateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.request.UpdateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.response.TaskColumnResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskResponse;
import java.util.List;
import java.util.UUID;

public interface TaskBoardService {
    List<TaskColumnResponse> getBoard(Long userId);
    List<TaskColumnResponse> getBoard(Long userId, Long roadmapSessionId);
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
}
