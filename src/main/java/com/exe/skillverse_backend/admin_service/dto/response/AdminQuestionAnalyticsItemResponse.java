package com.exe.skillverse_backend.admin_service.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminQuestionAnalyticsItemResponse {

    private Long questionId;
    private Long questionBankId;
    private String questionBankTitle;
    private String domain;
    private String industry;
    private String jobRole;
    private String questionText;
    private String difficulty;
    private String skillArea;
    private String category;
    private String source;
    private Integer usedCount;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
