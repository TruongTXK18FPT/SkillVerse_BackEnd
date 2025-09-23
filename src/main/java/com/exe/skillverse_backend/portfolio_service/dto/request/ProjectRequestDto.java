package com.exe.skillverse_backend.portfolio_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectRequestDto {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @Size(max = 255, message = "Tech stack must not exceed 255 characters")
    private String techStack;

    @Size(max = 255, message = "Project URL must not exceed 255 characters")
    private String projectUrl;

    private Long mediaId;

    private LocalDate completedDate;
}