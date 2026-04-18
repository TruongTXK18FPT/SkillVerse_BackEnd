package com.exe.skillverse_backend.ai_service.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CompleteNodeResponse {
    /** Number of tasks successfully marked done. */
    private int doneCount;
    /** Number of tasks that failed to mark done (e.g. DB error). */
    private int failedCount;
    /** Whether the node itself was marked COMPLETED. False if sequential lock prevented it. */
    private boolean nodeCompleted;
    /** Human-readable message for FE toast. */
    private String message;
}
