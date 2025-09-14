package com.exe.skillverse_backend.shared.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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
     * Send OTP email
     */
    public void sendOtpEmail(String email, String otp) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("Verify Your Email - SkillVerse");
            message.setText(buildOtpEmailContent(otp));

            mailSender.send(message);

            log.info("🔐 EMAIL SERVICE: OTP email sent successfully to {}", email);
            log.info("📝 OTP Code: {} (expires in 10 minutes)", otp);

        } catch (Exception e) {
            log.error("❌ Failed to send OTP email to {}: {}", email, e.getMessage());
            // Fallback to console logging for development
            log.info("🔐 [FALLBACK] EMAIL SERVICE: Sending OTP to {}", email);
            log.info("📧 Subject: Verify Your Email - SkillVerse");
            log.info("📝 Message: Your verification code is: {}", otp);
            log.info("⏰ This code will expire in 10 minutes");
            log.info("✉️  [SIMULATED] Email sent successfully to {}", email);
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

                This code will expire in 10 minutes. Please enter this code in the verification form to complete your registration.

                If you didn't request this verification, please ignore this email.

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
}