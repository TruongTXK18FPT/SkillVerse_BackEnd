package com.exe.skillverse_backend.mentor_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Mentor profile response")
public class MentorProfileResponse {

    @Schema(description = "User ID", example = "123")
    private Long id;

    @Schema(description = "First name", example = "John")
    private String firstName;

    @Schema(description = "Last name", example = "Doe")
    private String lastName;

    @Schema(description = "Email address", example = "john.doe@example.com")
    private String email;

    @Schema(description = "Bio/Personal profile", example = "Experienced software developer...")
    private String bio;

    @Schema(description = "Specialization/Expertise", example = "React, Node.js")
    private String specialization;

    @Schema(description = "Years of experience", example = "5")
    private Integer experience;

    @Schema(description = "Avatar URL", example = "https://example.com/avatar.jpg")
    private String avatar;

    @Schema(description = "Social links")
    private SocialLinks socialLinks;

    @Schema(description = "Skills list")
    private String[] skills;

    @Schema(description = "Achievements list")
    private String[] achievements;

    @Schema(description = "Creation timestamp")
    private LocalDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SocialLinks {
        @Schema(description = "LinkedIn profile URL")
        private String linkedin;

        @Schema(description = "GitHub profile URL")
        private String github;

        @Schema(description = "Personal website URL")
        private String website;
    }
}
