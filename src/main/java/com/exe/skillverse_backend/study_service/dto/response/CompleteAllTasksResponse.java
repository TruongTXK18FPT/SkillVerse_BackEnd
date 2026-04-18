package com.exe.skillverse_backend.study_service.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompleteAllTasksResponse {
    private int doneCount;
    private int failedCount;
}
