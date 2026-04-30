package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface EmailService {

        void sendOtpEmail(String email, String otp);

        void sendPasswordResetOtpEmail(String email, String otp);

        void sendWelcomeEmail(String email, String fullName);

        void sendApprovalEmail(String email, String fullName, String role);

        void sendRejectionEmail(String email, String fullName, String role, String reason);

        /**
         * Send email to mentor when admin approves their supplemental CCCD identity
         * verification.
         * This is different from the main approveMentor email - it is for existing
         * accounts
         * that needed to submit CCCD post-registration.
         */
        void sendCccdVerificationApprovedEmail(String email, String fullName);

        void sendJobApplicationReviewed(String email, String fullName, String jobTitle);

        void sendJobApplicationAccepted(String email, String fullName, String jobTitle, String acceptanceMessage,
                        String contactEmail);

        void sendJobApplicationRejected(String email, String fullName, String jobTitle, String rejectionReason);

        // Short-term job email notifications
        void sendShortTermApplicationSubmitted(String email, String fullName, String jobTitle, String recruiterName,
                        String deadline, String budget);

        void sendShortTermApplicationAccepted(String email, String fullName, String jobTitle, String recruiterName,
                        String budget, String deadline);

        void sendShortTermApplicationRejected(String email, String fullName, String jobTitle, String recruiterName,
                        String reason);

        void sendShortTermWorkSubmitted(String email, String recruiterName, String jobTitle, String workerName);

        void sendShortTermWorkApproved(String email, String workerName, String jobTitle, String budget);

        // Job approval/rejection notifications (to recruiter)
        void sendJobApprovalNotification(String email, String jobTitle, String message);

        void sendJobRejectionNotification(String email, String jobTitle, String reason);

        // Application auto-rejection notification (to candidate)
        void sendApplicationRejectionNotification(String email, String jobTitle, String reason);

        void sendHtmlEmail(String to, String subject, String htmlContent);

        void sendHtmlEmailWithAttachment(String to, String subject, String htmlContent,
                        String attachmentFilename, byte[] attachmentBytes, String contentType);

        CompletableFuture<Boolean> sendHtmlEmailAsync(String to, String subject, String htmlContent);

        // Interview scheduling email (full-time job pipeline)
        void sendInterviewScheduled(
                        String email,
                        String fullName,
                        String jobTitle,
                        LocalDateTime scheduledAt,
                        Integer durationMinutes,
                        String meetingType,
                        String meetingLink,
                        String skillverseRoomId,
                        String location,
                        String interviewerName);

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
