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
    private String domain;
    private String industry;
    private String jobRole;
    private String title;
    private Integer activeQuestionCount;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
