package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EmailService {

    void sendOtpEmail(String email, String otp);

    void sendPasswordResetOtpEmail(String email, String otp);

    void sendWelcomeEmail(String email, String fullName);

    void sendApprovalEmail(String email, String fullName, String role);

    void sendRejectionEmail(String email, String fullName, String role, String reason);

    void sendJobApplicationReviewed(String email, String fullName, String jobTitle);

    void sendJobApplicationAccepted(String email, String fullName, String jobTitle, String acceptanceMessage);

    void sendJobApplicationRejected(String email, String fullName, String jobTitle, String rejectionReason);

    void sendHtmlEmail(String to, String subject, String htmlContent);

    void sendHtmlEmailWithAttachment(String to, String subject, String htmlContent,
            String attachmentFilename, byte[] attachmentBytes, String contentType);

    CompletableFuture<Boolean> sendHtmlEmailAsync(String to, String subject, String htmlContent);

    CompletableFuture<EmailSendingResult> sendBulkEmailAsync(
            List<String> recipients,
            String subject,
            String htmlContent,
            int batchSize,
            long delayBetweenBatchesMs);

    CompletableFuture<EmailSendingResult> sendBulkEmailToUsersAsync(
            List<User> users,
            String subject,
            String htmlContent);

    /**
     * Result object for bulk email operations
     */
    record EmailSendingResult(
            int totalRecipients,
            int successCount,
            int failedCount,
            List<String> failedEmails) {
        public double getSuccessRate() {
            return totalRecipients > 0 ? (double) successCount / totalRecipients * 100 : 0;
        }
    }
}
