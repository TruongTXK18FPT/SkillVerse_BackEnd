package com.exe.skillverse_backend.question_bank_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuestionBankRequest {

    private String title;
    private String description;
    private Long domainId;
    private Long jobPositionId;
    private Long skillId;
    private String skillName;
    private String difficultyDistribution;
    private Boolean isActive;
}
