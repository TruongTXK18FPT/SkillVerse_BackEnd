package com.exe.skillverse_backend.course_service.dto.progressdto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LearningResultHistoryItemDTO {
    private Long itemId;
    private String itemType;
    private String title;
    private String scoreLabel;
    private Instant completedAt;
    private Boolean isBreakingChanged;
    private String breakingReason;
    private Long sourceRevisionId;
    private String reason;
    private Boolean requiresRetake;
}

