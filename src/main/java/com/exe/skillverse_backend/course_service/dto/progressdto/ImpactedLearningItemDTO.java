package com.exe.skillverse_backend.course_service.dto.progressdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImpactedLearningItemDTO {
    private Long itemId;
    private String itemType;
    private String title;
    private Boolean isBreakingChanged;
    private String breakingReason;
    private String reasonCode;
    private String reason;
    private Long sourceRevisionId;
    private Long targetRevisionId;
    private Boolean requiresRetake;
}
