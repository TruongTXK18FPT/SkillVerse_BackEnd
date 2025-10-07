package com.exe.skillverse_backend.ai_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for generating a personalized AI roadmap
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenerateRoadmapRequest {

    /**
     * User's learning goal (e.g., "Learn Spring Boot", "Become a Data Scientist")
     */
    @NotBlank(message = "Goal is required")
    @Size(min = 5, max = 500, message = "Goal must be between 5 and 500 characters")
    private String goal;

    /**
     * Expected duration (e.g., "3 months", "6 weeks", "1 year")
     */
    @NotBlank(message = "Duration is required")
    @Size(max = 50, message = "Duration must be less than 50 characters")
    private String duration;

    /**
     * User's experience level: "beginner", "intermediate", "advanced"
     */
    @NotBlank(message = "Experience level is required")
    @Size(max = 50, message = "Experience level must be less than 50 characters")
    private String experience;

    /**
     * Learning style preference (e.g., "project-based", "theoretical",
     * "video-based", "hands-on")
     */
    @NotBlank(message = "Learning style is required")
    @Size(max = 50, message = "Learning style must be less than 50 characters")
    private String style;
}
