package com.exe.skillverse_backend.mentor_service.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Mentor profile update request")
public class MentorProfileUpdateRequest {

    @Schema(description = "First name", example = "John")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String firstName;

    @Schema(description = "Last name", example = "Doe")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String lastName;

    @Schema(description = "Email address", example = "john.doe@example.com")
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @Schema(description = "Bio/Personal profile", example = "Experienced software developer...")
    @Size(max = 1000, message = "Bio must not exceed 1000 characters")
    private String bio;

    @Schema(description = "Specialization/Expertise", example = "React, Node.js")
    @Size(max = 200, message = "Specialization must not exceed 200 characters")
    private String specialization;

    @Schema(description = "Years of experience", example = "5")
    private Integer experience;

    @Schema(description = "Avatar URL", example = "https://example.com/avatar.jpg")
    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    private String avatar;

    @Schema(description = "Social links")
    private SocialLinks socialLinks;

    @Schema(description = "Skills list")
    private String[] skills;

    @Schema(description = "Achievements list")
    private String[] achievements;

    @Data
    @Schema(description = "Social links")
    public static class SocialLinks {
        @Schema(description = "LinkedIn profile URL")
        @Size(max = 500, message = "LinkedIn URL must not exceed 500 characters")
        private String linkedin;

        @Schema(description = "GitHub profile URL")
        @Size(max = 500, message = "GitHub URL must not exceed 500 characters")
        private String github;

        @Schema(description = "Personal website URL")
        @Size(max = 500, message = "Website URL must not exceed 500 characters")
        private String website;
    }
}
