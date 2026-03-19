package com.exe.skillverse_backend.study_service.dto.request;

import com.exe.skillverse_backend.study_service.entity.TaskPriority;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Data;

@Data
public class UpdateTaskRequest {
    private String title;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime deadline;
    private TaskPriority priority;
    private UUID columnId;
    private String status;
    private Integer userProgress;
    private String satisfactionLevel;
    private String userNotes;
    private List<UUID> linkedSessionIds;
}
