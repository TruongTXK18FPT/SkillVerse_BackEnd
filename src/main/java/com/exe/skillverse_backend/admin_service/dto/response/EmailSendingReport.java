package com.exe.skillverse_backend.admin_service.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for email sending operations
 * Provides detailed report of bulk email sending results
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Email sending report with success/failure statistics")
public class EmailSendingReport {

    @Schema(description = "Total number of recipients", example = "150")
    private Integer totalRecipients;

    @Schema(description = "Number of successfully sent emails", example = "148")
    private Integer successCount;

    @Schema(description = "Number of failed emails", example = "2")
    private Integer failedCount;

    @Schema(description = "List of email addresses that failed to receive the email")
    private List<String> failedEmails;

    @Schema(description = "Timestamp when emails were sent")
    @Builder.Default
    private LocalDateTime sentAt = LocalDateTime.now();

    @Schema(description = "Overall status of the email sending operation", example = "COMPLETED")
    @Builder.Default
    private EmailSendingStatus status = EmailSendingStatus.COMPLETED;

    @Schema(description = "Success rate percentage", example = "98.67")
    private Double successRate;

    /**
     * Email sending status
     */
    public enum EmailSendingStatus {
        COMPLETED, // All emails processed (some may have failed)
        PARTIAL_FAILURE, // Some emails failed
        FAILED, // All emails failed
        IN_PROGRESS // Still sending
    }

    /**
     * Calculate success rate percentage
     */
    public void calculateSuccessRate() {
        if (totalRecipients != null && totalRecipients > 0) {
            this.successRate = (double) successCount / totalRecipients * 100;
            this.successRate = Math.round(successRate * 100.0) / 100.0; // Round to 2 decimal places
        } else {
            this.successRate = 0.0;
        }
    }

    /**
     * Determine status based on success/failure counts
     */
    public void determineStatus() {
        if (totalRecipients == null || totalRecipients == 0) {
            this.status = EmailSendingStatus.FAILED;
        } else if (failedCount == 0) {
            this.status = EmailSendingStatus.COMPLETED;
        } else if (successCount == 0) {
            this.status = EmailSendingStatus.FAILED;
        } else {
            this.status = EmailSendingStatus.PARTIAL_FAILURE;
        }
    }

    /**
     * Factory method to create report from EmailService.EmailSendingResult
     */
    public static EmailSendingReport fromEmailSendingResult(
            com.exe.skillverse_backend.shared.service.EmailService.EmailSendingResult result) {

        EmailSendingReport report = EmailSendingReport.builder()
                .totalRecipients(result.totalRecipients())
                .successCount(result.successCount())
                .failedCount(result.failedCount())
                .failedEmails(result.failedEmails())
                .sentAt(LocalDateTime.now())
                .build();

        report.calculateSuccessRate();
        report.determineStatus();

        return report;
    }
}
