package com.exe.skillverse_backend.study_service.dto.request;

import com.exe.skillverse_backend.study_service.entity.TaskPriority;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class CreateTaskRequest {
    private String title;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime deadline;
    private TaskPriority priority;
    private UUID columnId;
    private java.util.List<UUID> linkedSessionIds;
}
