package com.exe.skillverse_backend.study_service.service;

import com.exe.skillverse_backend.study_service.dto.request.CreateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.request.UpdateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.response.TaskColumnResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskResponse;
import java.util.List;
import java.util.UUID;

public interface TaskBoardService {
    List<TaskColumnResponse> getBoard(Long userId);
    TaskColumnResponse createColumn(Long userId, String name, String color);
    TaskColumnResponse updateColumn(UUID columnId, String name, String color);
    TaskResponse createTask(Long userId, CreateTaskRequest request);
    TaskResponse updateTask(UUID taskId, UpdateTaskRequest request);
    void deleteTask(UUID taskId);
    void moveTask(UUID taskId, UUID targetColumnId);
    int clearOverdueTasks(Long userId, int overdueDays, UUID columnId);
    void checkOverdueTasks(Long userId);
    void checkUpcomingDeadlines(Long userId);
}
