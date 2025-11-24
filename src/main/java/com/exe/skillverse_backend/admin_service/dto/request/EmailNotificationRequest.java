package com.exe.skillverse_backend.admin_service.dto.request;

import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for admin to send bulk email notifications
 * Follows OOP principles with proper validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Email notification request for admin bulk sending")
public class EmailNotificationRequest {

    @NotBlank(message = "Email subject is required")
    @Schema(description = "Email subject line", example = "Khuyến mãi đặc biệt cho Mentor")
    private String subject;

    @NotBlank(message = "Email content is required")
    @Schema(description = "HTML content of the email", example = "<h1>Chào mừng!</h1><p>Nội dung email...</p>")
    private String htmlContent;

    @NotNull(message = "Target role is required")
    @Schema(description = "Target user role to send emails to", example = "MENTOR")
    private TargetRole targetRole;

    @NotNull(message = "Email type is required")
    @Schema(description = "Type of email notification", example = "PROMOTIONAL")
    private EmailType emailType;

    @Schema(description = "Whether this is an urgent notification", example = "false")
    @Builder.Default
    private Boolean isUrgent = false;

    /**
     * Target role enum for filtering recipients
     */
    public enum TargetRole {
        ALL, // All users
        USER, // Regular users (students)
        MENTOR, // Mentors only
        RECRUITER, // Recruiters/Business only
        ADMIN // Admins only
    }

    /**
     * Email notification type
     */
    public enum EmailType {
        PROMOTIONAL, // Promotional/marketing emails
        ANNOUNCEMENT, // System announcements
        UPDATE, // Feature updates
        MAINTENANCE // Maintenance notifications
    }

    /**
     * Convert TargetRole to PrimaryRole for database query
     * Returns null for ALL (no filter)
     */
    public PrimaryRole toPrimaryRole() {
        if (targetRole == null || targetRole == TargetRole.ALL) {
            return null;
        }
        return switch (targetRole) {
            case USER -> PrimaryRole.USER;
            case MENTOR -> PrimaryRole.MENTOR;
            case RECRUITER -> PrimaryRole.RECRUITER;
            case ADMIN -> PrimaryRole.ADMIN;
            default -> null;
        };
    }
}
