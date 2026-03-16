package com.exe.skillverse_backend.study_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class ClearOverdueTasksResponse {
    private int deletedCount;
    private int overdueDays;
    private UUID columnId;
    private String message;
}
