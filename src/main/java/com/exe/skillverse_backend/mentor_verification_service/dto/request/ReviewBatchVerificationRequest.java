package com.exe.skillverse_backend.mentor_verification_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewBatchVerificationRequest {

    @Size(max = 2000, message = "General review note must be at most 2000 characters")
    private String generalReviewNote;

    @Valid
    @NotEmpty(message = "Skill review list is required")
    @Builder.Default
    private List<SkillReviewItem> skillsReview = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkillReviewItem {
        @NotNull(message = "Skill verification id is required")
        private Long skillVerificationId;

        @NotNull(message = "Approved status is required")
        private Boolean approved;

        @Size(max = 2000, message = "Review note must be at most 2000 characters")
        private String reviewNote;
    }
}
