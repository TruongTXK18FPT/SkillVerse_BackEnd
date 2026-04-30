package com.exe.skillverse_backend.shared.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.service.EmailService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("ci")
@Slf4j
public class CiEmailServiceImpl implements EmailService {

    private void logSkip(String method, String to) {
        log.debug("[CI] Email suppressed: {}({})", method, to);
    }

    @Override
    public void sendOtpEmail(String email, String otp) {
        logSkip("sendOtpEmail", email);
    }

    @Override
    public void sendPasswordResetOtpEmail(String email, String otp) {
        logSkip("sendPasswordResetOtpEmail", email);
    }

    @Override
    public void sendWelcomeEmail(String email, String fullName) {
        logSkip("sendWelcomeEmail", email);
    }

    @Override
    public void sendApprovalEmail(String email, String fullName, String role) {
        logSkip("sendApprovalEmail", email);
    }

    @Override
    public void sendRejectionEmail(String email, String fullName, String role, String reason) {
        logSkip("sendRejectionEmail", email);
    }

    @Override
    public void sendCccdVerificationApprovedEmail(String email, String fullName) {
        logSkip("sendCccdVerificationApprovedEmail", email);
    }

    @Override
    public void sendJobApplicationReviewed(String email, String fullName, String jobTitle) {
        logSkip("sendJobApplicationReviewed", email);
    }

    @Override
    public void sendJobApplicationAccepted(String email, String fullName, String jobTitle, String acceptanceMessage, String contactEmail) {
        logSkip("sendJobApplicationAccepted", email);
    }

    @Override
    public void sendJobApplicationRejected(String email, String fullName, String jobTitle, String rejectionReason) {
        logSkip("sendJobApplicationRejected", email);
    }

    @Override
    public void sendShortTermApplicationSubmitted(String email, String fullName, String jobTitle, String recruiterName, String deadline, String budget) {
        logSkip("sendShortTermApplicationSubmitted", email);
    }

    @Override
    public void sendShortTermApplicationAccepted(String email, String fullName, String jobTitle, String recruiterName, String budget, String deadline) {
        logSkip("sendShortTermApplicationAccepted", email);
    }

    @Override
    public void sendShortTermApplicationRejected(String email, String fullName, String jobTitle, String recruiterName, String reason) {
        logSkip("sendShortTermApplicationRejected", email);
    }

    @Override
    public void sendShortTermWorkSubmitted(String email, String recruiterName, String jobTitle, String workerName) {
        logSkip("sendShortTermWorkSubmitted", email);
    }

    @Override
    public void sendShortTermWorkApproved(String email, String workerName, String jobTitle, String budget) {
        logSkip("sendShortTermWorkApproved", email);
    }

    @Override
    public void sendJobApprovalNotification(String email, String jobTitle, String message) {
        logSkip("sendJobApprovalNotification", email);
    }

    @Override
    public void sendJobRejectionNotification(String email, String jobTitle, String reason) {
        logSkip("sendJobRejectionNotification", email);
    }

    @Override
    public void sendApplicationRejectionNotification(String email, String jobTitle, String reason) {
        logSkip("sendApplicationRejectionNotification", email);
    }

    @Override
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        logSkip("sendHtmlEmail", to);
    }

    @Override
    public void sendHtmlEmailWithAttachment(String to, String subject, String htmlContent,
            String attachmentFilename, byte[] attachmentBytes, String contentType) {
        logSkip("sendHtmlEmailWithAttachment", to);
    }

    @Override
    public CompletableFuture<Boolean> sendHtmlEmailAsync(String to, String subject, String htmlContent) {
        logSkip("sendHtmlEmailAsync", to);
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<EmailSendingResult> sendBulkEmailAsync(List<String> recipients,
            String subject, String htmlContent, int batchSize, long delayBetweenBatchesMs) {
        return CompletableFuture.completedFuture(
                new EmailSendingResult(recipients.size(), recipients.size(), 0, List.of()));
    }

    @Override
    public CompletableFuture<EmailSendingResult> sendBulkEmailToUsersAsync(List<User> users,
            String subject, String htmlContent) {
        int n = (int) users.stream()
                .map(User::getEmail)
                .filter(e -> e != null && !e.isBlank())
                .count();
        return CompletableFuture.completedFuture(
                new EmailSendingResult(n, n, 0, List.of()));
    }

    @Override
    public void sendInterviewScheduled(
            String email,
            String fullName,
            String jobTitle,
            LocalDateTime scheduledAt,
            Integer durationMinutes,
            String meetingType,
            String meetingLink,
            String skillverseRoomId,
            String location,
            String interviewerName) {
        logSkip("sendInterviewScheduled", email);
    }
}
