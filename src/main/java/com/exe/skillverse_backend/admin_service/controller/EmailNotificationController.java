package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.dto.request.EmailNotificationRequest;
import com.exe.skillverse_backend.admin_service.dto.response.EmailSendingReport;
import com.exe.skillverse_backend.auth_service.entity.PrimaryRole;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for admin email notification operations
 * Follows OOP principles with proper dependency injection and security
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/email-notifications")
@RequiredArgsConstructor
@Tag(name = "Admin Email Notifications", description = "Admin endpoints for sending bulk email notifications")
@SecurityRequirement(name = "bearerAuth")
public class EmailNotificationController {

    private final EmailService emailService;
    private final UserRepository userRepository;

    /**
     * Send bulk email notification to users based on role filter
     * 
     * @param request Email notification request with subject, content, and target
     *                role
     * @return Email sending report with success/failure statistics
     */
    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Send bulk email notification", description = "Send HTML email to multiple users filtered by role. Supports batch processing to prevent server overload.")
    public ResponseEntity<EmailSendingReport> sendBulkEmail(@Valid @RequestBody EmailNotificationRequest request) {
        log.info("📧 Admin sending bulk email: subject='{}', targetRole={}, type={}",
                request.getSubject(), request.getTargetRole(), request.getEmailType());

        try {
            // Get users based on target role
            List<User> targetUsers = getUsersByRole(request.toPrimaryRole());

            if (targetUsers.isEmpty()) {
                log.warn("⚠️ No users found for role: {}", request.getTargetRole());
                return ResponseEntity.ok(EmailSendingReport.builder()
                        .totalRecipients(0)
                        .successCount(0)
                        .failedCount(0)
                        .failedEmails(List.of())
                        .build());
            }

            log.info("📨 Sending email to {} users", targetUsers.size());

            // Send emails asynchronously with batch processing
            CompletableFuture<EmailService.EmailSendingResult> future = emailService.sendBulkEmailToUsersAsync(
                    targetUsers,
                    request.getSubject(),
                    request.getHtmlContent());

            // Wait for completion and convert to report
            EmailService.EmailSendingResult result = future.join();
            EmailSendingReport report = EmailSendingReport.fromEmailSendingResult(result);

            log.info("✅ Bulk email completed: {}/{} successful",
                    report.getSuccessCount(), report.getTotalRecipients());

            return ResponseEntity.ok(report);

        } catch (Exception e) {
            log.error("❌ Failed to send bulk email: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(EmailSendingReport.builder()
                            .totalRecipients(0)
                            .successCount(0)
                            .failedCount(0)
                            .status(EmailSendingReport.EmailSendingStatus.FAILED)
                            .build());
        }
    }

    /**
     * Preview recipients count before sending
     * 
     * @param targetRole Target role to filter users
     * @return Map with recipient count and sample emails
     */
    @GetMapping("/preview-recipients")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Preview email recipients", description = "Get count and sample of users who will receive the email based on role filter")
    public ResponseEntity<Map<String, Object>> previewRecipients(
            @RequestParam(required = false) EmailNotificationRequest.TargetRole targetRole) {

        log.info("👀 Previewing recipients for role: {}", targetRole);

        PrimaryRole primaryRole = targetRole != null && targetRole != EmailNotificationRequest.TargetRole.ALL
                ? convertTargetRoleToPrimaryRole(targetRole)
                : null;

        List<User> users = getUsersByRole(primaryRole);

        // Get sample of first 10 emails
        List<String> sampleEmails = users.stream()
                .limit(10)
                .map(User::getEmail)
                .toList();

        Map<String, Object> response = new HashMap<>();
        response.put("totalCount", users.size());
        response.put("sampleEmails", sampleEmails);
        response.put("targetRole", targetRole != null ? targetRole.name() : "ALL");

        return ResponseEntity.ok(response);
    }

    /**
     * Get email sending statistics
     * 
     * @return Statistics about email sending operations
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get email statistics", description = "Get statistics about user counts by role for email targeting")
    public ResponseEntity<Map<String, Object>> getEmailStatistics() {
        log.info("📊 Fetching email statistics");

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("userCount", userRepository.countByPrimaryRole(PrimaryRole.USER));
        stats.put("mentorCount", userRepository.countByPrimaryRole(PrimaryRole.MENTOR));
        stats.put("recruiterCount", userRepository.countByPrimaryRole(PrimaryRole.RECRUITER));
        stats.put("adminCount", userRepository.countByPrimaryRole(PrimaryRole.ADMIN));

        return ResponseEntity.ok(stats);
    }

    /**
     * Get users by role filter
     * Returns all users if role is null
     */
    private List<User> getUsersByRole(PrimaryRole role) {
        if (role == null) {
            return userRepository.findAll();
        }
        return userRepository.findByPrimaryRole(role);
    }

    /**
     * Convert TargetRole to PrimaryRole
     */
    private PrimaryRole convertTargetRoleToPrimaryRole(EmailNotificationRequest.TargetRole targetRole) {
        return switch (targetRole) {
            case USER -> PrimaryRole.USER;
            case MENTOR -> PrimaryRole.MENTOR;
            case RECRUITER -> PrimaryRole.RECRUITER;
            case ADMIN -> PrimaryRole.ADMIN;
            default -> null;
        };
    }
}
