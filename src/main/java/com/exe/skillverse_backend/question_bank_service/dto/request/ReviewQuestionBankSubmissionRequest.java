package com.exe.skillverse_backend.question_bank_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewQuestionBankSubmissionRequest {

    @NotNull(message = "Approved flag is required")
    private Boolean approved;

    private String reviewNote;
}
