package com.exe.skillverse_backend.business_service.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request để tạo review/rating
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateJobReviewRequest {

    @NotNull(message = "Application ID is required")
    private Long applicationId;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating must not exceed 5")
    private Integer rating;

    @Size(max = 5000, message = "Comment must not exceed 5000 characters")
    private String comment;

    @Size(max = 2000, message = "Strengths must not exceed 2000 characters")
    private String strengths;

    @Size(max = 2000, message = "Improvements must not exceed 2000 characters")
    private String improvements;

    @Size(max = 2000, message = "Recommendations must not exceed 2000 characters")
    private String recommendations;

    // Specific ratings (optional)
    @Min(value = 1, message = "Communication rating must be at least 1")
    @Max(value = 5, message = "Communication rating must not exceed 5")
    private Integer communicationRating;

    @Min(value = 1, message = "Quality rating must be at least 1")
    @Max(value = 5, message = "Quality rating must not exceed 5")
    private Integer qualityRating;

    @Min(value = 1, message = "Timeliness rating must be at least 1")
    @Max(value = 5, message = "Timeliness rating must not exceed 5")
    private Integer timelinessRating;

    @Min(value = 1, message = "Professionalism rating must be at least 1")
    @Max(value = 5, message = "Professionalism rating must not exceed 5")
    private Integer professionalismRating;

    @Builder.Default
    private Boolean isPublic = true;
}
