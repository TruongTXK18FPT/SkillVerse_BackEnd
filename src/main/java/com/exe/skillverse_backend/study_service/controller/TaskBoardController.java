package com.exe.skillverse_backend.study_service.controller;

import com.exe.skillverse_backend.study_service.dto.request.CreateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.request.UpdateTaskRequest;
import com.exe.skillverse_backend.study_service.dto.response.ClearOverdueTasksResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskColumnResponse;
import com.exe.skillverse_backend.study_service.dto.response.TaskResponse;
import com.exe.skillverse_backend.study_service.service.TaskBoardService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.exe.skillverse_backend.study_service.entity.DashboardNote;
import com.exe.skillverse_backend.study_service.service.DashboardService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-board")
@RequiredArgsConstructor
public class TaskBoardController {

    private final TaskBoardService taskBoardService;
    private final DashboardService dashboardService;

    private Long getUserId(Authentication authentication) {
        return Long.parseLong(authentication.getName());
    }

    @GetMapping
    public ResponseEntity<List<TaskColumnResponse>> getBoard(
            @RequestParam(required = false) Long roadmapSessionId,
            Authentication authentication) {
        if (roadmapSessionId != null) {
            return ResponseEntity.ok(taskBoardService.getBoard(getUserId(authentication), roadmapSessionId));
        }
        return ResponseEntity.ok(taskBoardService.getBoard(getUserId(authentication)));
    }

    @GetMapping("/archived")
    public ResponseEntity<PageResponse<TaskResponse>> getArchivedTasks(
            @RequestParam(required = false) Long roadmapSessionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(taskBoardService.getArchivedTasks(getUserId(authentication), roadmapSessionId, pageable));
    }

    @PatchMapping("/tasks/{taskId}/unarchive")
    public ResponseEntity<TaskResponse> unarchiveTask(@PathVariable UUID taskId) {
        return ResponseEntity.ok(taskBoardService.unarchiveTask(taskId));
    }

    @PostMapping("/archive-roadmap/{roadmapSessionId}")
    public ResponseEntity<java.util.Map<String, Object>> archiveRoadmapTasks(
            @PathVariable Long roadmapSessionId,
            Authentication authentication) {
        int archived = taskBoardService.archiveTasksByRoadmapSession(getUserId(authentication), roadmapSessionId);
        return ResponseEntity.ok(java.util.Map.of(
                "archivedCount", archived,
                "roadmapSessionId", roadmapSessionId,
                "message", "Đã ẩn " + archived + " task của roadmap."
        ));
    }

    @PostMapping("/columns")
    public ResponseEntity<TaskColumnResponse> createColumn(@RequestParam String name, @RequestParam(required = false) String color, Authentication authentication) {
        return ResponseEntity.ok(taskBoardService.createColumn(getUserId(authentication), name, color));
    }

    @PatchMapping("/columns/{columnId}")
    public ResponseEntity<TaskColumnResponse> updateColumn(@PathVariable UUID columnId, @RequestParam(required = false) String name, @RequestParam(required = false) String color) {
        return ResponseEntity.ok(taskBoardService.updateColumn(columnId, name, color));
    }

    @DeleteMapping("/columns/{columnId}")
    public ResponseEntity<Void> deleteColumn(
            @PathVariable UUID columnId,
            @RequestParam(required = false) UUID targetColumnId) {
        taskBoardService.deleteColumn(columnId, targetColumnId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/tasks")
    public ResponseEntity<TaskResponse> createTask(@RequestBody CreateTaskRequest request, Authentication authentication) {
        return ResponseEntity.ok(taskBoardService.createTask(getUserId(authentication), request));
    }

    @PatchMapping("/tasks/{taskId}")
    public ResponseEntity<TaskResponse> updateTask(@PathVariable UUID taskId, @RequestBody UpdateTaskRequest request) {
        return ResponseEntity.ok(taskBoardService.updateTask(taskId, request));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID taskId) {
        taskBoardService.deleteTask(taskId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/tasks/clear-overdue")
    public ResponseEntity<ClearOverdueTasksResponse> clearOverdueTasks(
            @RequestParam(defaultValue = "30") Integer overdueDays,
            @RequestParam(required = false) UUID columnId,
            Authentication authentication
    ) {
        int safeOverdueDays = overdueDays == null ? 30 : Math.max(1, overdueDays);
        int deletedCount = taskBoardService.clearOverdueTasks(
                getUserId(authentication),
                safeOverdueDays,
                columnId
        );

        String message = "Đã xóa " + deletedCount + " task quá hạn hơn " + safeOverdueDays + " ngày.";
        return ResponseEntity.ok(ClearOverdueTasksResponse.builder()
                .deletedCount(deletedCount)
                .overdueDays(safeOverdueDays)
                .columnId(columnId)
                .message(message)
                .build());
    }

    @PatchMapping("/tasks/{taskId}/move")
    public ResponseEntity<Void> moveTask(@PathVariable UUID taskId, @RequestParam UUID targetColumnId) {
        taskBoardService.moveTask(taskId, targetColumnId);
        return ResponseEntity.ok().build();
    }
    @PutMapping("/tasks/{taskId}/reorder")
    public ResponseEntity<TaskResponse> reorderTask(
            @PathVariable UUID taskId,
            @RequestParam UUID targetColumnId,
            @RequestParam(required = false) Double previousOrderIndex,
            @RequestParam(required = false) Double nextOrderIndex) {
        TaskResponse response = taskBoardService.reorderTask(taskId, targetColumnId, previousOrderIndex, nextOrderIndex);
        return ResponseEntity.ok(response);
    }
    @PostMapping("/check-overdue")
    public ResponseEntity<Void> checkOverdueTasks(Authentication authentication) {
        taskBoardService.checkOverdueTasks(getUserId(authentication));
        taskBoardService.checkUpcomingDeadlines(getUserId(authentication));
        return ResponseEntity.ok().build();
    }

    // Dashboard Notes Endpoints

    @GetMapping("/notes")
    public ResponseEntity<List<DashboardNote>> getNotes(Authentication authentication) {
        return ResponseEntity.ok(dashboardService.getUserNotes(getUserId(authentication)));
    }

    @PostMapping("/notes")
    public ResponseEntity<DashboardNote> createNote(@RequestBody String content, Authentication authentication) {
        return ResponseEntity.ok(dashboardService.createNote(getUserId(authentication), content));
    }

    @PatchMapping("/notes/{noteId}")
    public ResponseEntity<DashboardNote> updateNote(@PathVariable UUID noteId, @RequestBody String content) {
        return ResponseEntity.ok(dashboardService.updateNote(noteId, content));
    }

    @DeleteMapping("/notes/{noteId}")
    public ResponseEntity<Void> deleteNote(@PathVariable UUID noteId) {
        dashboardService.deleteNote(noteId);
        return ResponseEntity.noContent().build();
    }
}
