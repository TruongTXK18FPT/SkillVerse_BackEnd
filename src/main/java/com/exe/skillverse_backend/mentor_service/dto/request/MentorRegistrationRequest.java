package com.exe.skillverse_backend.mentor_service.dto.request;

import com.exe.skillverse_backend.shared.dto.request.BaseRegistrationRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Mentor registration request")
public class MentorRegistrationRequest extends BaseRegistrationRequest {

    @NotBlank(message = "Expertise is required")
    @Size(max = 500, message = "Expertise must not exceed 500 characters")
    @Schema(description = "Mentor's area of expertise", example = "Software Engineering, Java, Spring Boot")
    private String expertise;

    @NotBlank(message = "Teaching experience is required")
    @Size(max = 1000, message = "Teaching experience must not exceed 1000 characters")
    @Schema(description = "Mentor's teaching or professional experience", example = "5 years of software development and 2 years of mentoring")
    private String teachingExperience;

    @Schema(description = "List of skills the mentor can teach", example = "[\"Java\", \"Spring Boot\", \"React\", \"Database Design\"]")
    private List<String> skills;

    @Schema(description = "Mentor's hourly rate for sessions", example = "50.0")
    private Double hourlyRate;

    @Size(max = 100, message = "LinkedIn URL must not exceed 100 characters")
    @Schema(description = "Mentor's LinkedIn profile URL", example = "https://linkedin.com/in/mentor")
    private String linkedinUrl;

    @Size(max = 100, message = "GitHub URL must not exceed 100 characters")
    @Schema(description = "Mentor's GitHub profile URL", example = "https://github.com/mentor")
    private String githubUrl;

    @Size(max = 1000, message = "Motivation must not exceed 1000 characters")
    @Schema(description = "Why the person wants to become a mentor", example = "I want to share my knowledge and help others grow in their careers")
    private String motivation;
}