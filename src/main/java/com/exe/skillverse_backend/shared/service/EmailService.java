package com.exe.skillverse_backend.shared.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ClassPathResource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${email.from:noreply@skillverse.vn}")
    private String fromEmail;

    @Value("${email.from-name:SkillVerse}")
    private String fromName;

    private static final String LOGO_PATH = "c:/WorkSpace/EXE201/SkillVerse_BackEnd/src/assets/skillverse.png";

    /**
     * Send OTP email for registration
     */
    public void sendOtpEmail(String email, String otp) {
        try {
            String subject = "Xác thực email - SkillVerse";
            String htmlContent = buildOtpEmailHtmlContent(otp);
            sendHtmlEmail(email, subject, htmlContent);

            log.info("🔐 EMAIL SERVICE: Đã gửi email OTP xác thực tới {}", email);

        } catch (Exception e) {
            log.error("❌ Gửi email OTP xác thực thất bại tới {}", email, e);
            // Fallback (dev): log ra console
            log.info("🔐 [FALLBACK] EMAIL SERVICE: Gửi OTP xác thực tới {}", email);
            log.info("📧 Tiêu đề: Xác thực email - SkillVerse");
            log.info("📝 Mã xác thực của bạn: {}", otp);
            log.info("⏰ Mã sẽ hết hạn sau 5 phút");
            log.info("✉️  [MÔ PHỎNG] Đã gửi email tới {}", email);
        }
    }

    /**
     * Send OTP email for password reset
     */
    public void sendPasswordResetOtpEmail(String email, String otp) {
        try {
            String subject = "Mã xác thực đặt lại mật khẩu - SkillVerse";
            String htmlContent = buildPasswordResetOtpHtmlContent(otp);
            sendHtmlEmail(email, subject, htmlContent);

            log.info("🔑 EMAIL SERVICE: Đã gửi email OTP đặt lại mật khẩu tới {}", email);

        } catch (Exception e) {
            log.error("❌ Gửi email OTP đặt lại mật khẩu thất bại tới {}", email, e);
            // Fallback (dev): log ra console
            log.info("🔑 [FALLBACK] EMAIL SERVICE: Gửi OTP đặt lại mật khẩu tới {}", email);
            log.info("📧 Tiêu đề: Mã xác thực đặt lại mật khẩu - SkillVerse");
            log.info("📝 Mã xác thực của bạn: {}", otp);
            log.info("⏰ Mã sẽ hết hạn sau 5 phút");
            log.info("✉️  [MÔ PHỎNG] Đã gửi email tới {}", email);
        }
    }

    /**
     * Send welcome email after successful verification
     */
    public void sendWelcomeEmail(String email, String fullName) {
        try {
            String subject = "🎉 Chào mừng đến với SkillVerse";
            String htmlContent = buildWelcomeEmailHtmlContent(fullName != null ? fullName : email);
            sendHtmlEmail(email, subject, htmlContent);

            log.info("🎉 EMAIL SERVICE: Welcome HTML email sent successfully to {}", email);

        } catch (Exception e) {
            log.error("❌ Failed to send welcome email to {}", email, e);
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
            String subject = "🎉 Phê duyệt thành công - SkillVerse";
            String htmlContent = buildApprovalEmailHtmlContent(fullName != null ? fullName : email, role);
            sendHtmlEmail(email, subject, htmlContent);

            log.info("🎉 EMAIL SERVICE: Approval HTML email sent successfully to {} for role: {}", email, role);

        } catch (Exception e) {
            log.error("❌ Failed to send approval email to {}: {}", email, e.getMessage());
            log.info("🎉 [FALLBACK] EMAIL SERVICE: Sending approval email to {} for role: {}", email, role);
            log.info("📧 Subject: Phê duyệt thành công - SkillVerse");
            log.info("📝 {} đã được phê duyệt!", role);
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

    private String buildOtpEmailHtmlContent(String otp) {
        return """
                <!doctype html>
                <html lang=\"vi\">
                <head>
                  <meta charset=\"UTF-8\" />
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                  <title>Xác thực email - SkillVerse</title>
                  <style>
                    body { margin:0; padding:0; background:#f5f7fb; font-family:Inter, Roboto, Helvetica, Arial, sans-serif; color:#1f2937; }
                    .container { max-width:600px; margin:24px auto; padding:0 16px; }
                    .card { background:#ffffff; border-radius:12px; box-shadow:0 6px 20px rgba(31,41,55,0.08); overflow:hidden; }
                    .header { background:linear-gradient(90deg,#4f46e5,#6366f1); color:#fff; padding:20px 24px; }
                    .brand { font-size:18px; font-weight:600; letter-spacing:0.3px; }
                    .chip { display:inline-block; margin-top:6px; padding:4px 10px; background:rgba(255,255,255,0.18); border:1px solid rgba(255,255,255,0.35); border-radius:999px; font-size:12px; }
                    .content { padding:24px; }
                    h1 { margin:0 0 8px 0; font-size:20px; color:#111827; }
                    p { margin:8px 0; line-height:1.6; }
                    .otp-block { margin:18px 0 12px; padding:18px; background:#f9fafb; border:1px dashed #d1d5db; border-radius:10px; text-align:center; }
                    .otp { font-size:32px; font-weight:700; letter-spacing:6px; color:#111827; }
                    .muted { color:#6b7280; font-size:13px; }
                    .footer { padding:16px 24px 22px; border-top:1px solid #eef2f7; background:#fafafa; }
                    .note { font-size:12px; color:#6b7280; }
                  </style>
                </head>
                <body>
                  <div class=\"container\">
                    <div class=\"card\">
                      <div class=\"header\">
                        <div class=\"brand\">SkillVerse</div>
                        <div class=\"chip\">Mã xác thực email</div>
                      </div>
                      <div class=\"content\">
                        <h1>Xin chào,</h1>
                        <p>Cảm ơn bạn đã đăng ký tài khoản tại SkillVerse.</p>
                        <p>Để hoàn tất xác thực email, vui lòng nhập mã OTP dưới đây:</p>
                        <div class=\"otp-block\">
                          <div class=\"otp\">%s</div>
                        </div>
                        <p class=\"muted\">Mã sẽ hết hạn sau <strong>5 phút</strong>. Vui lòng không chia sẻ mã này cho bất kỳ ai.</p>
                        <p>Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.</p>
                      </div>
                      <div class=\"footer\">
                        <div class=\"note\">© SkillVerse — Hành trình học tập và nghề nghiệp của bạn.</div>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(otp);
    }

    private String buildPasswordResetOtpHtmlContent(String otp) {
        return """
                <!doctype html>
                <html lang=\"vi\">
                <head>
                  <meta charset=\"UTF-8\" />
                  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                  <title>Mã xác thực đặt lại mật khẩu - SkillVerse</title>
                  <style>
                    body { margin:0; padding:0; background:#f5f7fb; font-family:Inter, Roboto, Helvetica, Arial, sans-serif; color:#1f2937; }
                    .container { max-width:600px; margin:24px auto; padding:0 16px; }
                    .card { background:#ffffff; border-radius:12px; box-shadow:0 6px 20px rgba(31,41,55,0.08); overflow:hidden; }
                    .header { background:linear-gradient(90deg,#ef4444,#f59e0b); color:#fff; padding:20px 24px; }
                    .brand { font-size:18px; font-weight:600; letter-spacing:0.3px; }
                    .chip { display:inline-block; margin-top:6px; padding:4px 10px; background:rgba(255,255,255,0.18); border:1px solid rgba(255,255,255,0.35); border-radius:999px; font-size:12px; }
                    .content { padding:24px; }
                    h1 { margin:0 0 8px 0; font-size:20px; color:#111827; }
                    p { margin:8px 0; line-height:1.6; }
                    .otp-block { margin:18px 0 12px; padding:18px; background:#fff7ed; border:1px dashed #fdba74; border-radius:10px; text-align:center; }
                    .otp { font-size:32px; font-weight:700; letter-spacing:6px; color:#111827; }
                    .muted { color:#6b7280; font-size:13px; }
                    .footer { padding:16px 24px 22px; border-top:1px solid #eef2f7; background:#fafafa; }
                    .note { font-size:12px; color:#6b7280; }
                  </style>
                </head>
                <body>
                  <div class=\"container\">
                    <div class=\"card\">
                      <div class=\"header\">
                        <div class=\"brand\">SkillVerse</div>
                        <div class=\"chip\">Mã xác thực đặt lại mật khẩu</div>
                      </div>
                      <div class=\"content\">
                        <h1>Xin chào,</h1>
                        <p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản SkillVerse của bạn.</p>
                        <p>Vui lòng dùng mã OTP dưới đây để tiếp tục:</p>
                        <div class=\"otp-block\">
                          <div class=\"otp\">%s</div>
                        </div>
                        <p class=\"muted\">Mã sẽ hết hạn sau <strong>5 phút</strong>. Tuyệt đối không chia sẻ mã này với bất kỳ ai.</p>
                        <p>Nếu bạn không gửi yêu cầu này, vui lòng bỏ qua email và mật khẩu của bạn vẫn giữ nguyên.</p>
                      </div>
                      <div class=\"footer\">
                        <div class=\"note\">© SkillVerse — Bảo mật tài khoản của bạn là ưu tiên hàng đầu.</div>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(otp);
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

    /**
     * Build modern Vietnamese HTML for Welcome email
     */
    private String buildWelcomeEmailHtmlContent(String name) {
        return """
                <!DOCTYPE html>
                <html lang=\"vi\">
                <head>
                    <meta charset=\"UTF-8\" />
                    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                    <title>Chào mừng đến với SkillVerse</title>
                    <style>
                        body { font-family: 'Inter', 'Roboto', 'Arial', sans-serif; background-color: #f5f5f7; margin: 0; padding: 20px; color:#111827; }
                        .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 8px 24px rgba(17,24,39,0.08); }
                        .header { background: linear-gradient(135deg, #4f46e5 0%%, #6366f1 100%%); padding: 36px 30px; color: #ffffff; text-align: center; }
                        .header h1 { margin: 0; font-size: 28px; }
                        .brand { font-weight: 600; opacity: 0.92; margin-top: 6px; }
                        .content { padding: 26px 30px; }
                        p { line-height: 1.7; margin: 10px 0; color:#1f2937; }
                        .highlight { background: #eef2ff; border-left: 4px solid #4f46e5; padding: 14px; border-radius: 8px; margin: 16px 0; }
                        .features { background: #f9fafb; border: 1px solid #e5e7eb; border-radius: 10px; padding: 18px; }
                        .features ul { margin: 0; padding-left: 18px; }
                        .cta { text-align: center; margin: 24px 0; }
                        .button { display: inline-block; background: #4f46e5; color: #ffffff; padding: 12px 28px; text-decoration: none; border-radius: 8px; font-weight: 600; }
                        .footer { background: #f9fafb; padding: 18px 22px; text-align: center; color: #6b7280; font-size: 13px; }
                    </style>
                </head>
                <body>
                    <div class=\"container\">
                        <div class=\"header\">
                            <img src=\"cid:skillverse-logo\" alt=\"SkillVerse\" style=\"height:40px; display:block; margin:0 auto 10px;\" />
                            <h1>🎉 Chào mừng, %s!</h1>
                        </div>
                        <div class=\"content\">
                            <p>Cảm ơn bạn đã xác thực email thành công. Tài khoản của bạn đã sẵn sàng để bắt đầu hành trình học tập và phát triển sự nghiệp.</p>
                            <div class=\"highlight\">
                                <strong>Bạn có thể:</strong>
                                <div class=\"features\">
                                    <ul>
                                        <li>Hoàn thiện hồ sơ cá nhân</li>
                                        <li>Khám phá khóa học và mentor</li>
                                        <li>Ứng tuyển trở thành Mentor hoặc Recruiter</li>
                                        <li>Bắt đầu lộ trình học tập phù hợp</li>
                                    </ul>
                                </div>
                            </div>
                            <div class=\"cta\">
                                <a class=\"button\" href=\"https://skillverse.vn\">Khám phá SkillVerse</a>
                            </div>
                            <p style=\"font-size:13px; color:#6b7280\">Nếu bạn không thực hiện hành động này, hãy bỏ qua email.</p>
                        </div>
                        <div class=\"footer\">
                            © SkillVerse — Cộng đồng học tập và nghề nghiệp.
                        </div>
                    </div>
                </body>
                </html>
        """.formatted(name);
    }

    private String buildApprovalEmailHtmlContent(String name, String role) {
        String roleTitle = switch (role == null ? "" : role.toUpperCase()) {
            case "MENTOR" -> "Mentor";
            case "BUSINESS" -> "Business";
            default -> role != null ? role : "Role";
        };
        String intro = switch (role == null ? "" : role.toUpperCase()) {
            case "MENTOR" -> "Tài khoản của bạn đã được phê duyệt trở thành Mentor trên SkillVerse.";
            case "BUSINESS" -> "Tài khoản của bạn đã được phê duyệt trở thành Business/Recruiter trên SkillVerse.";
            default -> "Tài khoản của bạn đã được phê duyệt.";
        };
        String nextSteps = switch (role == null ? "" : role.toUpperCase()) {
            case "MENTOR" -> "Bạn có thể cập nhật hồ sơ mentor, tạo buổi mentoring và kết nối với học viên.";
            case "BUSINESS" -> "Bạn có thể đăng bài tuyển dụng, quản lý ứng viên và kết nối với cộng đồng.";
            default -> "Bạn có thể đăng nhập và khám phá các tính năng phù hợp.";
        };
        return String.format(
                """
                <!DOCTYPE html>
                <html lang=\"vi\">
                <head>
                    <meta charset=\"UTF-8\" />
                    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                    <title>Phê duyệt thành công</title>
                    <style>
                        body { font-family: 'Inter', 'Roboto', 'Arial', sans-serif; background-color: #f5f5f7; margin: 0; padding: 20px; color:#111827; }
                        .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 8px 24px rgba(17,24,39,0.08); }
                        .header { background: linear-gradient(135deg, #10b981 0%%, #22c55e 100%%); padding: 32px 30px; color: #ffffff; text-align: center; }
                        .header h1 { margin: 0; font-size: 26px; }
                        .badge { display:inline-block; margin-top:10px; background: rgba(255,255,255,0.18); border:1px solid rgba(255,255,255,0.35); border-radius:999px; padding:6px 12px; font-size:13px; }
                        .content { padding: 26px 30px; }
                        p { line-height: 1.7; margin: 10px 0; color:#1f2937; }
                        .highlight { background: #ecfeff; border-left: 4px solid #06b6d4; padding: 14px; border-radius: 8px; margin: 16px 0; }
                        .cta { text-align: center; margin: 24px 0; }
                        .button { display: inline-block; background: #10b981; color: #ffffff; padding: 12px 28px; text-decoration: none; border-radius: 8px; font-weight: 600; }
                        .footer { background: #f9fafb; padding: 18px 22px; text-align: center; color: #6b7280; font-size: 13px; }
                    </style>
                </head>
                <body>
                    <div class=\"container\">
                        <div class=\"header\">
                            <img src=\"cid:skillverse-logo\" alt=\"SkillVerse\" style=\"height:40px; display:block; margin:0 auto 12px;\" />
                            <h1>🎉 Chúc mừng, %s!</h1>
                            <div class=\"badge\">Phê duyệt %s thành công</div>
                        </div>
                        <div class=\"content\">
                            <p>%s</p>
                            <div class=\"highlight\">
                                %s
                            </div>
                            <div class=\"cta\">
                                <a class=\"button\" href=\"https://skillverse.vn\">Đăng nhập và bắt đầu</a>
                            </div>
                            <p style=\"font-size:13px; color:#6b7280\">Nếu bạn không yêu cầu, hãy bỏ qua email này.</p>
                        </div>
                        <div class=\"footer\">© SkillVerse — Cộng đồng học tập và nghề nghiệp.</div>
                    </div>
                </body>
                </html>
                """,
                name, roleTitle, intro, nextSteps);
    }

    private String buildApprovalEmailContent(String name, String role) {
        return """
                Dear %s,

                Congratulations! Your %s application has been approved! 🎉

                We're excited to welcome you to our SkillVerse community. Your application has been reviewed and accepted by our admin team.

                What's next:
                • You can now login to your account (using your email/password or Google Login with this email)
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

            try {
                if (htmlContent != null && htmlContent.contains("cid:skillverse-logo")) {
                    ClassPathResource classpathLogo = new ClassPathResource("assets/skillverse.png");
                    if (classpathLogo.exists()) {
                        helper.addInline("skillverse-logo", classpathLogo);
                    } else {
                        File file = new File(LOGO_PATH);
                        FileSystemResource fsLogo = file.exists() ? new FileSystemResource(file)
                                : new FileSystemResource(new File("src/assets/skillverse.png"));
                        if (fsLogo.exists()) {
                            helper.addInline("skillverse-logo", fsLogo);
                        } else {
                            log.warn("⚠️ Logo not found at classpath:assets/skillverse.png or {}", LOGO_PATH);
                        }
                    }
                }
            } catch (Exception inlineEx) {
                log.warn("⚠️ Inline logo attachment failed: {}", inlineEx.getMessage());
            }

            mailSender.send(message);
            log.info("✅ HTML email sent successfully to {}", to);

        } catch (Exception e) {
            log.error("❌ Failed to send HTML email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send HTML email: " + e.getMessage(), e);
        }
    }

    public void sendHtmlEmailWithAttachment(String to, String subject, String htmlContent,
                                            String attachmentFilename, byte[] attachmentBytes, String contentType) {
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

            if (attachmentBytes != null && attachmentBytes.length > 0 && attachmentFilename != null) {
                helper.addAttachment(attachmentFilename, new org.springframework.core.io.ByteArrayResource(attachmentBytes) {
                    @Override
                    public String getFilename() {
                        return attachmentFilename;
                    }
                    @Override
                    public String getDescription() {
                        return contentType != null ? contentType : "application/octet-stream";
                    }
                });
            }

            try {
                if (htmlContent != null && htmlContent.contains("cid:skillverse-logo")) {
                    ClassPathResource classpathLogo = new ClassPathResource("assets/skillverse.png");
                    if (classpathLogo.exists()) {
                        helper.addInline("skillverse-logo", classpathLogo);
                    } else {
                        File file = new File(LOGO_PATH);
                        FileSystemResource fsLogo = file.exists() ? new FileSystemResource(file)
                                : new FileSystemResource(new File("src/assets/skillverse.png"));
                        if (fsLogo.exists()) {
                            helper.addInline("skillverse-logo", fsLogo);
                        }
                    }
                }
            } catch (Exception inlineEx) {
            }

            mailSender.send(message);
            log.info("✅ HTML email with attachment sent to {}: {}", to, attachmentFilename);

        } catch (Exception e) {
            log.error("❌ Failed to send HTML email with attachment to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send HTML email with attachment: " + e.getMessage(), e);
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
