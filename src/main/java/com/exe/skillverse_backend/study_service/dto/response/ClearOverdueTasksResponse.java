package com.exe.skillverse_backend.study_service.dto.response;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ClearOverdueTasksResponse {
    private int deletedCount;
    private int overdueDays;
    private UUID columnId;
    private String message;
}
