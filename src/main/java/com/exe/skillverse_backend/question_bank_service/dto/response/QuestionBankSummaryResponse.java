package com.exe.skillverse_backend.question_bank_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionBankSummaryResponse {

    private Long id;
    private Long domainId;
    private Long jobPositionId;
    private Long skillId;
    private String domain;
    private String domainName;
    private String jobPositionName;
    private String skillName;
    private String title;
    private Integer activeQuestionCount;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
