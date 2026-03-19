package com.exe.skillverse_backend.study_service.dto.response;

import java.util.List;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaskColumnResponse {
    private UUID id;
    private String name;
    private String color;
    private Integer orderIndex;
    private List<TaskResponse> tasks;
}
