package com.exe.skillverse_backend.study_service.dto.response;

import com.exe.skillverse_backend.study_service.entity.TaskPriority;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskResponse {
    private UUID id;
    private String title;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private LocalDateTime deadline;
    private TaskPriority priority;
    private String status;
    private Integer userProgress;
    private String satisfactionLevel;
    private String userNotes;
    private UUID columnId;
    private List<UUID> linkedSessionIds;
}
