package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${email.from:skillverseexe@gmail.com}")
    private String fromEmail;

    @Value("${email.from-name:SkillVerse}")
    private String fromName;

    /**
     * Send OTP email for registration
     */
    public void sendOtpEmail(String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Verify Your Email - SkillVerse");
            message.setText(buildOtpEmailContent(otp));

            mailSender.send(message);

            log.info("🔐 EMAIL SERVICE: Registration OTP email sent successfully to {}", email);
            // log.info("📝 OTP Code: {} (expires in 5 minutes)", otp);

        } catch (Exception e) {
            log.error("❌ Failed to send OTP email to {}: {}", email, e.getMessage());
            // Fallback to console logging for development
            log.info("🔐 [FALLBACK] EMAIL SERVICE: Sending registration OTP to {}", email);
            log.info("📧 Subject: Verify Your Email - SkillVerse");
            log.info("📝 Message: Your verification code is: {}", otp);
            log.info("⏰ This code will expire in 5 minutes");
            log.info("✉️  [SIMULATED] Email sent successfully to {}", email);
        }
    }

    /**
     * Send OTP email for password reset
     */
    public void sendPasswordResetOtpEmail(String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("🔐 Password Reset Request - SkillVerse");
            message.setText(buildPasswordResetOtpContent(otp));

            mailSender.send(message);

            log.info("🔑 EMAIL SERVICE: Password reset OTP email sent successfully to {}", email);
            // log.info("📝 OTP Code: {} (expires in 5 minutes)", otp);

        } catch (Exception e) {
            log.error("❌ Failed to send password reset OTP email to {}: {}", email, e.getMessage());
            // Fallback to console logging for development
            log.info("🔑 [FALLBACK] EMAIL SERVICE: Sending password reset OTP to {}", email);
            log.info("📧 Subject: Password Reset Request - SkillVerse");
            log.info("📝 Message: Your password reset code is: {}", otp);
            log.info("⏰ This code will expire in 5 minutes");
            log.info("✉️  [SIMULATED] Password reset email sent successfully to {}", email);
        }
    }

    /**
     * Send welcome email after successful verification
     */
    public void sendWelcomeEmail(String email, String fullName) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Welcome to SkillVerse!");
            message.setText(buildWelcomeEmailContent(fullName != null ? fullName : email));

            mailSender.send(message);

            log.info("🎉 EMAIL SERVICE: Welcome email sent successfully to {}", email);

        } catch (Exception e) {
            log.error("❌ Failed to send welcome email to {}: {}", email, e.getMessage());
            // Fallback to console logging
            log.info("🎉 [FALLBACK] EMAIL SERVICE: Sending welcome email to {}", email);
            log.info("📧 Subject: Welcome to SkillVerse!");
            log.info("📝 Message: Welcome {}! Your email has been verified successfully.",
                    fullName != null ? fullName : email);
            log.info("✉️  [SIMULATED] Welcome email sent successfully to {}", email);
        }
    }

    /**
     * Send approval email for mentor/recruiter applications
     */
    public void sendApprovalEmail(String email, String fullName, String role) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("🎉 Application Approved - SkillVerse");
            message.setText(buildApprovalEmailContent(fullName, role));

            mailSender.send(message);

            log.info("🎉 EMAIL SERVICE: Approval email sent successfully to {} for role: {}", email, role);

        } catch (Exception e) {
            log.error("❌ Failed to send approval email to {}: {}", email, e.getMessage());
            // Fallback to console logging
            log.info("🎉 [FALLBACK] EMAIL SERVICE: Sending approval email to {} for role: {}", email, role);
            log.info("📧 Subject: Application Approved - SkillVerse");
            log.info("📝 Your {} application has been approved!", role.toLowerCase());
            log.info("✉️  [SIMULATED] Approval email sent successfully to {}", email);
        }
    }

    /**
     * Send rejection email for mentor/recruiter applications
     */
    public void sendRejectionEmail(String email, String fullName, String role, String reason) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Application Update - SkillVerse");
            message.setText(buildRejectionEmailContent(fullName, role, reason));

            mailSender.send(message);

            log.info("📧 EMAIL SERVICE: Rejection email sent successfully to {} for role: {}", email, role);

        } catch (Exception e) {
            log.error("❌ Failed to send rejection email to {}: {}", email, e.getMessage());
            // Fallback to console logging
            log.info("📧 [FALLBACK] EMAIL SERVICE: Sending rejection email to {} for role: {}", email, role);
            log.info("📧 Subject: Application Update - SkillVerse");
            log.info("📝 Your {} application status has been updated", role.toLowerCase());
            log.info("✉️  [SIMULATED] Rejection email sent successfully to {}", email);
        }
    }

    private String buildOtpEmailContent(String otp) {
        return """
                Dear User,

                Thank you for registering with SkillVerse!

                Your email verification code is: %s

                This code will expire in 5 minutes. Please enter this code in the verification form to complete your registration.

                If you didn't request this verification, please ignore this email.

                Best regards,
                The SkillVerse Team
                """
                .formatted(otp);
    }

    private String buildPasswordResetOtpContent(String otp) {
        return """
                Dear User,

                We received a request to reset your password for your SkillVerse account.

                Your password reset verification code is: %s

                This code will expire in 5 minutes. Please enter this code to proceed with resetting your password.

                If you didn't request a password reset, please ignore this email and your password will remain unchanged.

                For security reasons, never share this code with anyone.

                Best regards,
                The SkillVerse Team
                """
                .formatted(otp);
    }

    private String buildWelcomeEmailContent(String name) {
        return """
                Dear %s,

                Welcome to SkillVerse!

                Your email has been successfully verified and your account is now active.

                You can now:
                • Complete your profile
                • Browse courses and mentors
                • Apply to become a mentor or recruiter
                • Start your learning journey

                Thank you for joining our community!

                Best regards,
                The SkillVerse Team
                """.formatted(name);
    }

    private String buildApprovalEmailContent(String name, String role) {
        return """
                Dear %s,

                Congratulations! Your %s application has been approved! 🎉

                We're excited to welcome you to our SkillVerse community. Your application has been reviewed and accepted by our admin team.

                What's next:
                • You can now login to your account
                • Explore our features
                • Complete your profile setup
                • Start %s
                • Connect with our community

                Your role-specific features are now activated and you have full access to the platform.

                Thank you for joining SkillVerse!

                Best regards,
                The SkillVerse Team
                """
                .formatted(name, role.toLowerCase(),
                        role.equals("MENTOR") ? "offering mentorship services" : "posting job opportunities");
    }

    private String buildRejectionEmailContent(String name, String role, String reason) {
        String reasonText = reason != null && !reason.trim().isEmpty()
                ? "\n\nReason: " + reason
                : "";

        return """
                Dear %s,

                Thank you for your interest in becoming a %s on SkillVerse.

                After careful review, we regret to inform you that your application has not been approved at this time.%s

                This decision doesn't reflect on your qualifications, and we encourage you to reapply in the future once you've addressed any concerns.

                If you have any questions about this decision, please don't hesitate to contact our support team.

                Thank you for your understanding.

                Best regards,
                The SkillVerse Team
                """
                .formatted(name, role.toLowerCase(), reasonText);
    }

    // ==================== JOB APPLICATION EMAILS ====================

    /**
     * Send email when application status is marked as REVIEWED
     */
    public void sendJobApplicationReviewed(String email, String fullName, String jobTitle) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Your Job Application Has Been Reviewed - SkillVerse");
            message.setText(buildJobApplicationReviewedContent(fullName, jobTitle));

            mailSender.send(message);

            log.info("👀 EMAIL SERVICE: Application reviewed email sent successfully to {} for job: {}", email,
                    jobTitle);

        } catch (Exception e) {
            log.error("❌ Failed to send application reviewed email to {}: {}", email, e.getMessage());
            // Fallback to console logging
            log.info("👀 [FALLBACK] EMAIL SERVICE: Sending application reviewed email to {} for job: {}", email,
                    jobTitle);
            log.info("📧 Subject: Your Job Application Has Been Reviewed - SkillVerse");
            log.info("📝 Your application for '{}' has been reviewed by the recruiter", jobTitle);
            log.info("✉️  [SIMULATED] Application reviewed email sent successfully to {}", email);
        }
    }

    /**
     * Send email when application is ACCEPTED with custom message
     */
    public void sendJobApplicationAccepted(String email, String fullName, String jobTitle, String acceptanceMessage) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("🎉 Congratulations! Your Job Application Has Been Accepted - SkillVerse");
            message.setText(buildJobApplicationAcceptedContent(fullName, jobTitle, acceptanceMessage));

            mailSender.send(message);

            log.info("🎉 EMAIL SERVICE: Application accepted email sent successfully to {} for job: {}", email,
                    jobTitle);

        } catch (Exception e) {
            log.error("❌ Failed to send application accepted email to {}: {}", email, e.getMessage());
            // Fallback to console logging
            log.info("🎉 [FALLBACK] EMAIL SERVICE: Sending application accepted email to {} for job: {}", email,
                    jobTitle);
            log.info("📧 Subject: Congratulations! Your Job Application Has Been Accepted - SkillVerse");
            log.info("📝 Your application for '{}' has been accepted!", jobTitle);
            log.info("💌 Message: {}", acceptanceMessage);
            log.info("✉️  [SIMULATED] Application accepted email sent successfully to {}", email);
        }
    }

    /**
     * Send email when application is REJECTED with reason
     */
    public void sendJobApplicationRejected(String email, String fullName, String jobTitle, String rejectionReason) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Job Application Update - SkillVerse");
            message.setText(buildJobApplicationRejectedContent(fullName, jobTitle, rejectionReason));

            mailSender.send(message);

            log.info("📧 EMAIL SERVICE: Application rejected email sent successfully to {} for job: {}", email,
                    jobTitle);

        } catch (Exception e) {
            log.error("❌ Failed to send application rejected email to {}: {}", email, e.getMessage());
            // Fallback to console logging
            log.info("📧 [FALLBACK] EMAIL SERVICE: Sending application rejected email to {} for job: {}", email,
                    jobTitle);
            log.info("📧 Subject: Job Application Update - SkillVerse");
            log.info("📝 Your application for '{}' has been reviewed", jobTitle);
            log.info("✉️  [SIMULATED] Application rejected email sent successfully to {}", email);
        }
    }

    private String buildJobApplicationReviewedContent(String name, String jobTitle) {
        return """
                Dear %s,

                Thank you for your application on SkillVerse!

                We're writing to let you know that the recruiter has reviewed your application for the position:

                📋 Job: %s

                Your application is now under consideration. The recruiter will reach out to you soon with further updates regarding the next steps in the hiring process.

                You can check your application status anytime by logging into your SkillVerse account.

                Thank you for your patience and interest in this opportunity!

                Best regards,
                The SkillVerse Team
                """
                .formatted(name, jobTitle);
    }

    private String buildJobApplicationAcceptedContent(String name, String jobTitle, String acceptanceMessage) {
        return """
                Dear %s,

                Congratulations! 🎉

                We're thrilled to inform you that your application for the following position has been ACCEPTED:

                📋 Job: %s

                The recruiter has sent you the following message:

                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                %s
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

                Please follow the instructions provided by the recruiter to proceed with the next steps.

                If you have any questions, feel free to reply to this email or contact the recruiter directly using the information provided in their message.

                Congratulations once again, and we wish you all the best!

                Best regards,
                The SkillVerse Team
                """
                .formatted(name, jobTitle, acceptanceMessage);
    }

    private String buildJobApplicationRejectedContent(String name, String jobTitle, String rejectionReason) {
        String reasonText = rejectionReason != null && !rejectionReason.trim().isEmpty()
                ? "\n\nFeedback from recruiter:\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" + rejectionReason
                        + "\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
                : "";

        return """
                Dear %s,

                Thank you for your interest and for applying to the following position on SkillVerse:

                📋 Job: %s

                After careful consideration, we regret to inform you that the recruiter has decided not to move forward with your application at this time.%s

                This decision doesn't reflect on your qualifications or skills. We encourage you to:
                • Continue building your profile on SkillVerse
                • Apply to other job opportunities that match your expertise
                • Connect with mentors to enhance your skills

                We appreciate your interest and wish you the best of luck in your job search!

                Best regards,
                The SkillVerse Team
                """
                .formatted(name, jobTitle, reasonText);
    }

    // ==================== HTML EMAIL SUPPORT ====================

    /**
     * Send HTML email with rich formatting
     * Reusable method for premium emails, admin notifications, etc.
     * 
     * @param to          Recipient email address
     * @param subject     Email subject
     * @param htmlContent HTML content of the email
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            try {
                helper.setFrom(fromEmail, fromName);
            } catch (UnsupportedEncodingException e) {
                helper.setFrom(fromEmail);
            }

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("✅ HTML email sent successfully to {}", to);

        } catch (Exception e) {
            log.error("❌ Failed to send HTML email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send HTML email: " + e.getMessage(), e);
        }
    }

    /**
     * Send HTML email asynchronously
     * Used for non-critical emails that don't need immediate confirmation
     * 
     * @param to          Recipient email address
     * @param subject     Email subject
     * @param htmlContent HTML content of the email
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<Boolean> sendHtmlEmailAsync(String to, String subject, String htmlContent) {
        try {
            sendHtmlEmail(to, subject, htmlContent);
            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("❌ Async HTML email failed for {}: {}", to, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    // ==================== BULK EMAIL SUPPORT (ADMIN) ====================

    /**
     * Send bulk emails with batch processing and rate limiting
     * Prevents server overload by processing emails in batches
     * 
     * @param recipients            List of recipient email addresses
     * @param subject               Email subject
     * @param htmlContent           HTML content of the email
     * @param batchSize             Number of emails per batch (default: 50)
     * @param delayBetweenBatchesMs Delay between batches in milliseconds (default:
     *                              2000)
     * @return EmailSendingResult with success/failure counts
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailSendingResult> sendBulkEmailAsync(
            List<String> recipients,
            String subject,
            String htmlContent,
            int batchSize,
            long delayBetweenBatchesMs) {

        log.info("📧 Starting bulk email send to {} recipients", recipients.size());

        AtomicInteger successCount = new AtomicInteger(0);
        List<String> failedEmails = new ArrayList<>();

        // Split recipients into batches
        int totalBatches = (int) Math.ceil((double) recipients.size() / batchSize);

        for (int i = 0; i < recipients.size(); i += batchSize) {
            int batchNumber = (i / batchSize) + 1;
            int endIndex = Math.min(i + batchSize, recipients.size());
            List<String> batch = recipients.subList(i, endIndex);

            log.info("📨 Processing batch {}/{} ({} emails)", batchNumber, totalBatches, batch.size());

            // Send emails in current batch
            for (String email : batch) {
                try {
                    sendHtmlEmail(email, subject, htmlContent);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    log.error("❌ Failed to send email to {}: {}", email, e.getMessage());
                    failedEmails.add(email);
                }
            }

            // Delay between batches to prevent overload (except for last batch)
            if (endIndex < recipients.size()) {
                try {
                    Thread.sleep(delayBetweenBatchesMs);
                    log.info("⏳ Waiting {}ms before next batch...", delayBetweenBatchesMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("⚠️ Batch delay interrupted");
                }
            }
        }

        EmailSendingResult result = new EmailSendingResult(
                recipients.size(),
                successCount.get(),
                failedEmails.size(),
                failedEmails);

        log.info("✅ Bulk email completed: {}/{} successful, {} failed",
                successCount.get(), recipients.size(), failedEmails.size());

        return CompletableFuture.completedFuture(result);
    }

    /**
     * Send bulk emails to users with batch processing
     * Extracts email addresses from User objects
     * 
     * @param users       List of users to send emails to
     * @param subject     Email subject
     * @param htmlContent HTML content of the email
     * @return EmailSendingResult with success/failure counts
     */
    @Async("emailTaskExecutor")
    public CompletableFuture<EmailSendingResult> sendBulkEmailToUsersAsync(
            List<User> users,
            String subject,
            String htmlContent) {

        List<String> emails = users.stream()
                .map(User::getEmail)
                .filter(email -> email != null && !email.isEmpty())
                .toList();

        log.info("📧 Sending bulk email to {} users", emails.size());

        // Use default batch size of 50 and 2 second delay
        return sendBulkEmailAsync(emails, subject, htmlContent, 50, 2000);
    }

    /**
     * Result object for bulk email operations
     */
    public record EmailSendingResult(
            int totalRecipients,
            int successCount,
            int failedCount,
            List<String> failedEmails) {
        public double getSuccessRate() {
            return totalRecipients > 0 ? (double) successCount / totalRecipients * 100 : 0;
        }
    }
}