package com.exe.skillverse_backend.shared.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.service.EmailService.EmailSendingResult;
import com.exe.skillverse_backend.shared.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ByteArrayResource;

@Service
@Profile("!ci")
@Slf4j
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

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
                """
                .formatted(otp);
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
                """
                .formatted(name);
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
            String htmlContent = buildJobApplicationReviewedHtmlContent(fullName, jobTitle);
            sendHtmlEmail(email, "Your Job Application Has Been Reviewed — SkillVerse", htmlContent);
            log.info("👀 EMAIL SERVICE: Application reviewed HTML email sent successfully to {} for job: {}", email, jobTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send application reviewed email to {}: {}", email, e.getMessage());
            log.info("👀 [FALLBACK] EMAIL SERVICE: Application reviewed email to {}", email);
        }
    }

    /**
     * Send email when application is ACCEPTED with custom message
     */
    public void sendJobApplicationAccepted(String email, String fullName, String jobTitle, String acceptanceMessage, String contactEmail) {
        try {
            String htmlContent = buildJobApplicationAcceptedHtmlContent(fullName, jobTitle, acceptanceMessage, contactEmail);
            sendHtmlEmail(email, "🎉 Congratulations! Your Job Application Has Been Accepted — SkillVerse", htmlContent);
            log.info("🎉 EMAIL SERVICE: Application accepted HTML email sent successfully to {} for job: {}", email, jobTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send application accepted email to {}: {}", email, e.getMessage());
            log.info("🎉 [FALLBACK] EMAIL SERVICE: Application accepted email to {}", email);
        }
    }

    /**
     * Send email when application is REJECTED with reason
     */
    public void sendJobApplicationRejected(String email, String fullName, String jobTitle, String rejectionReason) {
        try {
            String htmlContent = buildJobApplicationRejectedHtmlContent(fullName, jobTitle, rejectionReason);
            sendHtmlEmail(email, "Job Application Update — SkillVerse", htmlContent);
            log.info("📧 EMAIL SERVICE: Application rejected HTML email sent successfully to {} for job: {}", email, jobTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send application rejected email to {}: {}", email, e.getMessage());
            log.info("📧 [FALLBACK] EMAIL SERVICE: Application rejected email to {}", email);
        }
    }

    // ==================== SHORT-TERM JOB EMAIL NOTIFICATIONS ====================

    @Override
    public void sendShortTermApplicationSubmitted(String email, String fullName, String jobTitle, String recruiterName, String deadline, String budget) {
        try {
            String htmlContent = buildShortTermApplicationSubmittedHtmlContent(fullName, jobTitle, recruiterName, deadline, budget);
            sendHtmlEmail(email, "Đơn ứng tuyển đã được gửi thành công — SkillVerse", htmlContent);
            log.info("📋 EMAIL SERVICE: Short-term application submitted email sent to {} for job: {}", email, jobTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send short-term application submitted email to {}: {}", email, e.getMessage());
            log.info("📋 [FALLBACK] EMAIL SERVICE: Short-term application submitted email to {}", email);
        }
    }

    @Override
    public void sendShortTermApplicationAccepted(String email, String fullName, String jobTitle, String recruiterName, String budget, String deadline) {
        try {
            String htmlContent = buildShortTermApplicationAcceptedHtmlContent(fullName, jobTitle, recruiterName, budget, deadline);
            sendHtmlEmail(email, "🎉 Bạn đã được nhận! Ứng tuyển thành công — SkillVerse", htmlContent);
            log.info("🎉 EMAIL SERVICE: Short-term application accepted email sent to {} for job: {}", email, jobTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send short-term application accepted email to {}: {}", email, e.getMessage());
            log.info("🎉 [FALLBACK] EMAIL SERVICE: Short-term application accepted email to {}", email);
        }
    }

    @Override
    public void sendShortTermApplicationRejected(String email, String fullName, String jobTitle, String recruiterName, String reason) {
        try {
            String htmlContent = buildShortTermApplicationRejectedHtmlContent(fullName, jobTitle, recruiterName, reason);
            sendHtmlEmail(email, "Cập nhật trạng thái ứng tuyển — SkillVerse", htmlContent);
            log.info("📧 EMAIL SERVICE: Short-term application rejected email sent to {} for job: {}", email, jobTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send short-term application rejected email to {}: {}", email, e.getMessage());
            log.info("📧 [FALLBACK] EMAIL SERVICE: Short-term application rejected email to {}", email);
        }
    }

    @Override
    public void sendShortTermWorkSubmitted(String email, String recruiterName, String jobTitle, String workerName) {
        try {
            String htmlContent = buildShortTermWorkSubmittedHtmlContent(recruiterName, jobTitle, workerName);
            sendHtmlEmail(email, "📦 Công việc đã được nộp — SkillVerse", htmlContent);
            log.info("📦 EMAIL SERVICE: Short-term work submitted email sent to {}", email);
        } catch (Exception e) {
            log.error("❌ Failed to send short-term work submitted email to {}: {}", email, e.getMessage());
            log.info("📦 [FALLBACK] EMAIL SERVICE: Short-term work submitted email to {}", email);
        }
    }

    @Override
    public void sendShortTermWorkApproved(String email, String workerName, String jobTitle, String budget) {
        try {
            String htmlContent = buildShortTermWorkApprovedHtmlContent(workerName, jobTitle, budget);
            sendHtmlEmail(email, "✅ Công việc đã được nghiệm thu — SkillVerse", htmlContent);
            log.info("✅ EMAIL SERVICE: Short-term work approved email sent to {}", email);
        } catch (Exception e) {
            log.error("❌ Failed to send short-term work approved email to {}: {}", email, e.getMessage());
            log.info("✅ [FALLBACK] EMAIL SERVICE: Short-term work approved email to {}", email);
        }
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
                helper.addAttachment(attachmentFilename,
                        new ByteArrayResource(attachmentBytes) {
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

    // ==================== JOB APPROVAL/REJECTION NOTIFICATIONS ====================

    @Override
    public void sendJobApprovalNotification(String email, String jobTitle, String message) {
        try {
            String content = buildJobApprovalNotificationContent(jobTitle, message);
            sendHtmlEmail(email, "Your Job Has Been Approved - SkillVerse", content);
            log.info("📧 EMAIL SERVICE: Job approval notification sent to {} for job: {}", email, jobTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send job approval notification to {}: {}", email, e.getMessage());
            // Fallback to console logging
            log.info("📧 [FALLBACK] EMAIL SERVICE: Sending job approval notification to {} for job: {}", email, jobTitle);
            log.info("📧 Subject: Your Job Has Been Approved - SkillVerse");
            log.info("📝 Message: {}", message);
            log.info("✉️  [SIMULATED] Job approval notification sent successfully to {}", email);
        }
    }

    @Override
    public void sendJobRejectionNotification(String email, String jobTitle, String reason) {
        try {
            String content = buildJobRejectionNotificationContent(jobTitle, reason);
            sendHtmlEmail(email, "Your Job Has Been Rejected - SkillVerse", content);
            log.info("📧 EMAIL SERVICE: Job rejection notification sent to {} for job: {}", email, jobTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send job rejection notification to {}: {}", email, e.getMessage());
            // Fallback to console logging
            log.info("📧 [FALLBACK] EMAIL SERVICE: Sending job rejection notification to {} for job: {}", email, jobTitle);
            log.info("📧 Subject: Your Job Has Been Rejected - SkillVerse");
            log.info("📝 Reason: {}", reason);
            log.info("✉️  [SIMULATED] Job rejection notification sent successfully to {}", email);
        }
    }

    @Override
    public void sendApplicationRejectionNotification(String email, String jobTitle, String reason) {
        try {
            String content = buildApplicationAutoRejectionContent(jobTitle, reason);
            sendHtmlEmail(email, "Application Status Update - SkillVerse", content);
            log.info("📧 EMAIL SERVICE: Application rejection notification sent to {} for job: {}", email, jobTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send application rejection notification to {}: {}", email, e.getMessage());
            // Fallback to console logging
            log.info("📧 [FALLBACK] EMAIL SERVICE: Sending application rejection notification to {} for job: {}", email, jobTitle);
            log.info("📧 Subject: Application Status Update - SkillVerse");
            log.info("📝 Reason: {}", reason);
            log.info("✉️  [SIMULATED] Application rejection notification sent successfully to {}", email);
        }
    }

    private String buildJobApprovalNotificationContent(String jobTitle, String message) {
        return """
                Dear Recruiter,

                Great news! 🎉

                Your job posting has been APPROVED and is now live on SkillVerse.

                📋 Job Title: %s

                %s

                Your job is now visible to all job seekers. You can start receiving applications immediately.

                To manage your job posting, log in to your SkillVerse recruiter dashboard.

                Best regards,
                The SkillVerse Team
                """.formatted(jobTitle, message != null && !message.isEmpty() ? "Message: " + message : "");
    }

    private String buildJobRejectionNotificationContent(String jobTitle, String reason) {
        return """
                Dear Recruiter,

                We're sorry to inform you that your job posting has been rejected.

                📋 Job Title: %s

                Reason: %s

                If you believe this is a mistake or would like to appeal this decision, please contact our support team.

                Best regards,
                The SkillVerse Team
                """.formatted(jobTitle, reason);
    }

    private String buildApplicationAutoRejectionContent(String jobTitle, String reason) {
        return """
                Dear Candidate,

                We're writing to inform you that your application has been automatically rejected.

                📋 Job Title: %s

                Reason: %s

                This typically happens when:
                - The job posting deadline has passed
                - The job has been cancelled by the recruiter

                We encourage you to apply for other available positions on SkillVerse that match your skills and interests.

                Best regards,
                The SkillVerse Team
                """.formatted(jobTitle, reason);
    }

    // ==================== HTML EMAIL BUILDERS FOR JOB APPLICATIONS ====================

    private String buildJobApplicationReviewedHtmlContent(String name, String jobTitle) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Đơn ứng tuyển đã được xem xét — SkillVerse</title>
                  <style>
                    body{margin:0;padding:0;background:#f5f7fb;font-family:Inter,Roboto,Helvetica,Arial,sans-serif;color:#1f2937}
                    .container{max-width:600px;margin:24px auto;padding:0 16px}
                    .card{background:#ffffff;border-radius:16px;box-shadow:0 8px 24px rgba(31,41,55,0.08);overflow:hidden}
                    .header{background:linear-gradient(135deg,#4f46e5,#6366f1);padding:32px 28px;text-align:center;color:#fff}
                    .header img{display:block;margin:0 auto 12px;height:44px}
                    .header h1{font-size:22px;font-weight:700;margin:0 0 6px}
                    .badge{display:inline-block;margin-top:8px;padding:5px 14px;background:rgba(255,255,255,0.18);border:1px solid rgba(255,255,255,0.35);border-radius:999px;font-size:13px}
                    .body{padding:28px 28px}
                    .greeting{font-size:16px;font-weight:600;color:#111827;margin:0 0 12px}
                    p{line-height:1.7;margin:10px 0;color:#374151;font-size:14px}
                    .job-card{background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:18px;margin:18px 0}
                    .job-card .job-title{font-size:15px;font-weight:600;color:#111827;margin:0 0 4px}
                    .job-card .job-meta{font-size:13px;color:#6b7280;margin:4px 0 0}
                    .divider{border:none;border-top:1px solid #e5e7eb;margin:20px 0}
                    .hint{background:#eef2ff;border-left:4px solid #4f46e5;padding:12px 14px;border-radius:0 8px 8px 0;margin:16px 0;font-size:13px;color:#3730a3}
                    .footer{padding:18px 28px 22px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center}
                    .footer-text{font-size:12px;color:#6b7280;margin:0}
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="card">
                      <div class="header">
                        <img src="cid:skillverse-logo" alt="SkillVerse" style="height:44px;display:block;margin:0 auto 12px"/>
                        <h1>Đơn ứng tuyển đã được xem xét</h1>
                        <div class="badge">Cập nhật trạng thái</div>
                      </div>
                      <div class="body">
                        <p class="greeting">Xin chào %s,</p>
                        <p>Cảm ơn bạn đã ứng tuyển trên SkillVerse!</p>
                        <p>Nhà tuyển dụng đã xem xét đơn ứng tuyển của bạn cho vị trí:</p>
                        <div class="job-card">
                          <div class="job-title">%s</div>
                          <div class="job-meta">Trạng thái: Đang xem xét</div>
                        </div>
                        <p>Đơn của bạn đang được xem xét. Nhà tuyển dụng sẽ liên hệ với bạn sớm với các bước tiếp theo.</p>
                        <div class="hint">
                          <strong>💡 Mẹo:</strong> Cập nhật hồ sơ và portfolio thường xuyên để tăng cơ hội được nhận!
                        </div>
                        <p>Bạn có thể kiểm tra trạng thái đơn ứng tuyển bất kỳ lúc nào trên tài khoản SkillVerse của mình.</p>
                        <hr class="divider"/>
                        <p style="font-size:13px;color:#6b7280;text-align:center;margin:0">Cảm ơn sự quan tâm của bạn và chúc bạn may mắn!</p>
                      </div>
                      <div class="footer">
                        <p class="footer-text">© SkillVerse — Cộng đồng học tập và nghề nghiệp.</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(name, jobTitle);
    }

    private String buildJobApplicationAcceptedHtmlContent(String name, String jobTitle, String acceptanceMessage, String contactEmail) {
        String messageBlock = acceptanceMessage != null && !acceptanceMessage.trim().isEmpty()
                ? "<div class=\"msg-box\"><strong>Tin nhắn từ nhà tuyển dụng:</strong><br/>" + acceptanceMessage.replace("\n", "<br/>") + "</div>"
                : "<div class=\"msg-box\" style=\"color:#6b7280;font-style:italic\">Không có tin nhắn kèm theo.</div>";
        String contactBlock = contactEmail != null && !contactEmail.trim().isEmpty()
                ? "<div class=\"contact\"><strong>Liên hệ:</strong> <a href=\"mailto:" + contactEmail + "\" style=\"color:#4f46e5\">" + contactEmail + "</a></div>"
                : "";
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Chúc mừng bạn đã được nhận! — SkillVerse</title>
                  <style>
                    body{margin:0;padding:0;background:#f5f7fb;font-family:Inter,Roboto,Helvetica,Arial,sans-serif;color:#1f2937}
                    .container{max-width:600px;margin:24px auto;padding:0 16px}
                    .card{background:#ffffff;border-radius:16px;box-shadow:0 8px 24px rgba(31,41,55,0.08);overflow:hidden}
                    .header{background:linear-gradient(135deg,#059669,#10b981);padding:32px 28px;text-align:center;color:#fff}
                    .header img{display:block;margin:0 auto 12px;height:44px}
                    .header h1{font-size:22px;font-weight:700;margin:0 0 6px}
                    .emoji{font-size:48px;margin-bottom:8px}
                    .badge{display:inline-block;margin-top:8px;padding:5px 14px;background:rgba(255,255,255,0.18);border:1px solid rgba(255,255,255,0.35);border-radius:999px;font-size:13px}
                    .body{padding:28px 28px}
                    .greeting{font-size:16px;font-weight:600;color:#111827;margin:0 0 12px}
                    p{line-height:1.7;margin:10px 0;color:#374151;font-size:14px}
                    .job-card{background:#f0fdf4;border:1px solid #bbf7d0;border-radius:10px;padding:18px;margin:18px 0}
                    .job-card .job-title{font-size:15px;font-weight:600;color:#166534;margin:0 0 4px}
                    .job-card .job-meta{font-size:13px;color:#16a34a;margin:4px 0 0}
                    .job-card .job-meta span{background:#dcfce7;padding:2px 8px;border-radius:4px;font-size:12px;font-weight:600}
                    .msg-box{background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:16px;margin:14px 0;font-size:14px;line-height:1.6}
                    .contact{background:#eef2ff;border-left:4px solid #4f46e5;padding:10px 14px;border-radius:0 8px 8px 0;margin:14px 0;font-size:14px}
                    .divider{border:none;border-top:1px solid #e5e7eb;margin:20px 0}
                    .footer{padding:18px 28px 22px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center}
                    .footer-text{font-size:12px;color:#6b7280;margin:0}
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="card">
                      <div class="header">
                        <img src="cid:skillverse-logo" alt="SkillVerse" style="height:44px;display:block;margin:0 auto 12px"/>
                        <div class="emoji">🎉</div>
                        <h1>Chúc mừng bạn đã được nhận!</h1>
                        <div class="badge">Ứng tuyển được chấp nhận</div>
                      </div>
                      <div class="body">
                        <p class="greeting">Xin chào %s,</p>
                        <p>Chúng tôi rất vui mừng thông báo rằng đơn ứng tuyển của bạn đã được <strong>CHẤP NHẬN</strong> cho vị trí:</p>
                        <div class="job-card">
                          <div class="job-title">%s</div>
                          <div class="job-meta"><span>✓ Đã được nhận</span></div>
                        </div>
                        %s
                        %s
                        <div class="divider"></div>
                        <p style="text-align:center;font-size:14px;margin:0">Hãy làm theo hướng dẫn của nhà tuyển dụng để tiến hành các bước tiếp theo. Nếu có thắc mắc, hãy liên hệ trực tiếp với nhà tuyển dụng.</p>
                        <p style="text-align:center;margin:16px 0 0">Chúc mừng bạn một lần nữa và chúc bạn thành công!</p>
                      </div>
                      <div class="footer">
                        <p class="footer-text">© SkillVerse — Cộng đồng học tập và nghề nghiệp.</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(name, jobTitle, messageBlock, contactBlock);
    }

    private String buildJobApplicationRejectedHtmlContent(String name, String jobTitle, String rejectionReason) {
        String reasonBlock = rejectionReason != null && !rejectionReason.trim().isEmpty()
                ? "<div class=\"reason-box\"><strong>Phản hồi từ nhà tuyển dụng:</strong><br/>" + rejectionReason.replace("\n", "<br/>") + "</div>"
                : "<p style=\"font-size:13px;color:#6b7280;font-style:italic\">Không có phản hồi kèm theo.</p>";
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Cập nhật trạng thái ứng tuyển — SkillVerse</title>
                  <style>
                    body{margin:0;padding:0;background:#f5f7fb;font-family:Inter,Roboto,Helvetica,Arial,sans-serif;color:#1f2937}
                    .container{max-width:600px;margin:24px auto;padding:0 16px}
                    .card{background:#ffffff;border-radius:16px;box-shadow:0 8px 24px rgba(31,41,55,0.08);overflow:hidden}
                    .header{background:linear-gradient(135deg,#dc2626,#ef4444);padding:32px 28px;text-align:center;color:#fff}
                    .header img{display:block;margin:0 auto 12px;height:44px}
                    .header h1{font-size:22px;font-weight:700;margin:0 0 6px}
                    .emoji{font-size:48px;margin-bottom:8px}
                    .badge{display:inline-block;margin-top:8px;padding:5px 14px;background:rgba(255,255,255,0.18);border:1px solid rgba(255,255,255,0.35);border-radius:999px;font-size:13px}
                    .body{padding:28px 28px}
                    .greeting{font-size:16px;font-weight:600;color:#111827;margin:0 0 12px}
                    p{line-height:1.7;margin:10px 0;color:#374151;font-size:14px}
                    .job-card{background:#fef2f2;border:1px solid #fecaca;border-radius:10px;padding:18px;margin:18px 0}
                    .job-card .job-title{font-size:15px;font-weight:600;color:#991b1b;margin:0 0 4px}
                    .job-card .job-meta{font-size:13px;color:#dc2626;margin:4px 0 0}
                    .reason-box{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:16px;margin:14px 0;font-size:14px;line-height:1.6}
                    .tips{background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:16px;margin:16px 0}
                    .tips-title{font-size:14px;font-weight:600;color:#111827;margin:0 0 10px}
                    .tips ul{margin:0;padding-left:18px;font-size:14px;line-height:1.8;color:#374151}
                    .divider{border:none;border-top:1px solid #e5e7eb;margin:20px 0}
                    .footer{padding:18px 28px 22px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center}
                    .footer-text{font-size:12px;color:#6b7280;margin:0}
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="card">
                      <div class="header">
                        <img src="cid:skillverse-logo" alt="SkillVerse" style="height:44px;display:block;margin:0 auto 12px"/>
                        <div class="emoji">📋</div>
                        <h1>Cập nhật trạng thái ứng tuyển</h1>
                        <div class="badge">Không tiến triển</div>
                      </div>
                      <div class="body">
                        <p class="greeting">Xin chào %s,</p>
                        <p>Cảm ơn bạn đã quan tâm và ứng tuyển vị trí trên SkillVerse:</p>
                        <div class="job-card">
                          <div class="job-title">%s</div>
                          <div class="job-meta">Trạng thái: Không được chọn</div>
                        </div>
                        <p>Sau khi xem xét kỹ lưỡng, nhà tuyển dụng quyết định không tiếp tục với đơn ứng tuyển của bạn vào lúc này.</p>
                        %s
                        <div class="tips">
                          <div class="tips-title">💡 Đừng nản lòng! Bạn có thể:</div>
                          <ul>
                            <li>Tiếp tục cập nhật hồ sơ và portfolio trên SkillVerse</li>
                            <li>Ứng tuyển các cơ hội khác phù hợp với chuyên môn</li>
                            <li>Kết nối với mentor để nâng cao kỹ năng</li>
                          </ul>
                        </div>
                        <p>Chúng tôi trân trọng sự quan tâm của bạn và chúc bạn may mắn!</p>
                        <hr class="divider"/>
                        <p style="text-align:center;font-size:13px;color:#6b7280;margin:0">Quyết định này không phản ánh năng lực của bạn. Hãy tiếp tục cố gắng!</p>
                      </div>
                      <div class="footer">
                        <p class="footer-text">© SkillVerse — Cộng đồng học tập và nghề nghiệp.</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(name, jobTitle, reasonBlock);
    }

    // ==================== HTML EMAIL BUILDERS FOR SHORT-TERM JOBS ====================

    private String buildShortTermApplicationSubmittedHtmlContent(String name, String jobTitle, String recruiterName, String deadline, String budget) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Đơn ứng tuyển đã được gửi — SkillVerse</title>
                  <style>
                    body{margin:0;padding:0;background:#f5f7fb;font-family:Inter,Roboto,Helvetica,Arial,sans-serif;color:#1f2937}
                    .container{max-width:600px;margin:24px auto;padding:0 16px}
                    .card{background:#ffffff;border-radius:16px;box-shadow:0 8px 24px rgba(31,41,55,0.08);overflow:hidden}
                    .header{background:linear-gradient(135deg,#4f46e5,#6366f1);padding:32px 28px;text-align:center;color:#fff}
                    .header img{display:block;margin:0 auto 12px;height:44px}
                    .header h1{font-size:22px;font-weight:700;margin:0 0 6px}
                    .emoji{font-size:48px;margin-bottom:8px}
                    .badge{display:inline-block;margin-top:8px;padding:5px 14px;background:rgba(255,255,255,0.18);border:1px solid rgba(255,255,255,0.35);border-radius:999px;font-size:13px}
                    .body{padding:28px 28px}
                    .greeting{font-size:16px;font-weight:600;color:#111827;margin:0 0 12px}
                    p{line-height:1.7;margin:10px 0;color:#374151;font-size:14px}
                    .job-card{background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:18px;margin:18px 0}
                    .job-card .job-title{font-size:15px;font-weight:600;color:#111827;margin:0 0 8px}
                    .job-card .meta-row{display:flex;gap:12px;margin-top:8px}
                    .job-card .meta-item{flex:1;background:#f3f4f6;border-radius:6px;padding:8px 10px}
                    .job-card .meta-item .label{font-size:11px;color:#6b7280;text-transform:uppercase;letter-spacing:0.5px}
                    .job-card .meta-item .value{font-size:14px;font-weight:600;color:#111827;margin-top:2px}
                    .divider{border:none;border-top:1px solid #e5e7eb;margin:20px 0}
                    .footer{padding:18px 28px 22px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center}
                    .footer-text{font-size:12px;color:#6b7280;margin:0}
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="card">
                      <div class="header">
                        <img src="cid:skillverse-logo" alt="SkillVerse" style="height:44px;display:block;margin:0 auto 12px"/>
                        <div class="emoji">✉️</div>
                        <h1>Đơn ứng tuyển đã được gửi!</h1>
                        <div class="badge">Chờ nhà tuyển dụng xem xét</div>
                      </div>
                      <div class="body">
                        <p class="greeting">Xin chào %s,</p>
                        <p>Đơn ứng tuyển của bạn đã được gửi thành công! Dưới đây là thông tin công việc bạn đã ứng tuyển:</p>
                        <div class="job-card">
                          <div class="job-title">%s</div>
                          <div style="font-size:13px;color:#6b7280;margin-top:4px">Nhà tuyển dụng: <strong>%s</strong></div>
                          <div class="meta-row">
                            <div class="meta-item">
                              <div class="label">Ngân sách</div>
                              <div class="value">%s</div>
                            </div>
                            <div class="meta-item">
                              <div class="label">Hạn nộp</div>
                              <div class="value">%s</div>
                            </div>
                          </div>
                        </div>
                        <p>Đơn của bạn đang ở trạng thái <strong>CHỜ XỬ LÝ</strong>. Nhà tuyển dụng sẽ xem xét và liên hệ với bạn sớm.</p>
                        <hr class="divider"/>
                        <p style="text-align:center;font-size:13px;color:#6b7280;margin:0">Cảm ơn bạn đã tin tưởng SkillVerse. Chúc bạn may mắn!</p>
                      </div>
                      <div class="footer">
                        <p class="footer-text">© SkillVerse — Cộng đồng học tập và nghề nghiệp.</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(name, jobTitle, recruiterName, budget != null ? budget : "Thỏa thuận", deadline != null ? deadline : "N/A");
    }

    private String buildShortTermApplicationAcceptedHtmlContent(String name, String jobTitle, String recruiterName, String budget, String deadline) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Bạn đã được nhận! — SkillVerse</title>
                  <style>
                    body{margin:0;padding:0;background:#f5f7fb;font-family:Inter,Roboto,Helvetica,Arial,sans-serif;color:#1f2937}
                    .container{max-width:600px;margin:24px auto;padding:0 16px}
                    .card{background:#ffffff;border-radius:16px;box-shadow:0 8px 24px rgba(31,41,55,0.08);overflow:hidden}
                    .header{background:linear-gradient(135deg,#059669,#10b981);padding:32px 28px;text-align:center;color:#fff}
                    .header img{display:block;margin:0 auto 12px;height:44px}
                    .header h1{font-size:22px;font-weight:700;margin:0 0 6px}
                    .emoji{font-size:56px;margin-bottom:8px}
                    .badge{display:inline-block;margin-top:8px;padding:5px 14px;background:rgba(255,255,255,0.18);border:1px solid rgba(255,255,255,0.35);border-radius:999px;font-size:13px}
                    .body{padding:28px 28px}
                    .greeting{font-size:16px;font-weight:600;color:#111827;margin:0 0 12px}
                    p{line-height:1.7;margin:10px 0;color:#374151;font-size:14px}
                    .job-card{background:#f0fdf4;border:1px solid #bbf7d0;border-radius:10px;padding:18px;margin:18px 0}
                    .job-card .job-title{font-size:15px;font-weight:600;color:#166534;margin:0 0 8px}
                    .job-card .meta-row{display:flex;gap:12px;margin-top:8px}
                    .job-card .meta-item{flex:1;background:#dcfce7;border-radius:6px;padding:8px 10px}
                    .job-card .meta-item .label{font-size:11px;color:#15803d;text-transform:uppercase;letter-spacing:0.5px}
                    .job-card .meta-item .value{font-size:14px;font-weight:600;color:#166534;margin-top:2px}
                    .status-box{background:#fefce8;border:1px solid #fef08a;border-radius:10px;padding:14px 16px;margin:16px 0;text-align:center}
                    .status-box .status-text{font-size:16px;font-weight:700;color:#854d0e;margin:0}
                    .status-box .status-sub{font-size:13px;color:#a16207;margin:4px 0 0}
                    .next-steps{background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:16px;margin:16px 0}
                    .next-steps .title{font-size:14px;font-weight:600;color:#111827;margin:0 0 10px}
                    .next-steps ul{margin:0;padding-left:18px;font-size:14px;line-height:1.8;color:#374151}
                    .divider{border:none;border-top:1px solid #e5e7eb;margin:20px 0}
                    .footer{padding:18px 28px 22px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center}
                    .footer-text{font-size:12px;color:#6b7280;margin:0}
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="card">
                      <div class="header">
                        <img src="cid:skillverse-logo" alt="SkillVerse" style="height:44px;display:block;margin:0 auto 12px"/>
                        <div class="emoji">🎉</div>
                        <h1>Bạn đã được nhận!</h1>
                        <div class="badge">Chúc mừng — Ứng viên được chọn</div>
                      </div>
                      <div class="body">
                        <p class="greeting">Xin chào %s,</p>
                        <p>Chúc mừng bạn! Nhà tuyển dụng <strong>%s</strong> đã chọn bạn cho công việc:</p>
                        <div class="job-card">
                          <div class="job-title">%s</div>
                          <div class="meta-row">
                            <div class="meta-item">
                              <div class="label">Ngân sách</div>
                              <div class="value">%s</div>
                            </div>
                            <div class="meta-item">
                              <div class="label">Hạn hoàn thành</div>
                              <div class="value">%s</div>
                            </div>
                          </div>
                        </div>
                        <div class="status-box">
                          <div class="status-text">✓ Đơn được chấp nhận</div>
                          <div class="status-sub">Bạn đã sẵn sàng để bắt đầu công việc!</div>
                        </div>
                        <div class="next-steps">
                          <div class="title">📋 Các bước tiếp theo:</div>
                          <ul>
                            <li>Kiểm tra chi tiết công việc trên SkillVerse</li>
                            <li>Liên hệ nhà tuyển dụng để xác nhận công việc</li>
                            <li>Bắt đầu thực hiện và nộp sản phẩm đúng hạn</li>
                          </ul>
                        </div>
                        <p style="text-align:center;margin:0">Chúc bạn hoàn thành công việc xuất sắc!</p>
                      </div>
                      <div class="footer">
                        <p class="footer-text">© SkillVerse — Cộng đồng học tập và nghề nghiệp.</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(name, recruiterName, jobTitle, budget != null ? budget : "Thỏa thuận", deadline != null ? deadline : "N/A");
    }

    private String buildShortTermApplicationRejectedHtmlContent(String name, String jobTitle, String recruiterName, String reason) {
        String reasonBlock = reason != null && !reason.trim().isEmpty()
                ? "<div class=\"reason-box\"><strong>Phản hồi:</strong><br/>" + reason.replace("\n", "<br/>") + "</div>"
                : "<p style=\"font-size:13px;color:#6b7280;font-style:italic;margin:12px 0\">Không có phản hồi kèm theo từ nhà tuyển dụng.</p>";
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Cập nhật trạng thái ứng tuyển — SkillVerse</title>
                  <style>
                    body{margin:0;padding:0;background:#f5f7fb;font-family:Inter,Roboto,Helvetica,Arial,sans-serif;color:#1f2937}
                    .container{max-width:600px;margin:24px auto;padding:0 16px}
                    .card{background:#ffffff;border-radius:16px;box-shadow:0 8px 24px rgba(31,41,55,0.08);overflow:hidden}
                    .header{background:linear-gradient(135deg,#dc2626,#ef4444);padding:32px 28px;text-align:center;color:#fff}
                    .header img{display:block;margin:0 auto 12px;height:44px}
                    .header h1{font-size:22px;font-weight:700;margin:0 0 6px}
                    .emoji{font-size:48px;margin-bottom:8px}
                    .badge{display:inline-block;margin-top:8px;padding:5px 14px;background:rgba(255,255,255,0.18);border:1px solid rgba(255,255,255,0.35);border-radius:999px;font-size:13px}
                    .body{padding:28px 28px}
                    .greeting{font-size:16px;font-weight:600;color:#111827;margin:0 0 12px}
                    p{line-height:1.7;margin:10px 0;color:#374151;font-size:14px}
                    .job-card{background:#fef2f2;border:1px solid #fecaca;border-radius:10px;padding:18px;margin:18px 0}
                    .job-card .job-title{font-size:15px;font-weight:600;color:#991b1b;margin:0 0 4px}
                    .job-card .recruiter{font-size:13px;color:#dc2626;margin:0}
                    .reason-box{background:#fff;border:1px solid #e5e7eb;border-radius:10px;padding:14px 16px;margin:14px 0;font-size:14px;line-height:1.6}
                    .tips{background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:16px;margin:16px 0}
                    .tips-title{font-size:14px;font-weight:600;color:#111827;margin:0 0 10px}
                    .tips ul{margin:0;padding-left:18px;font-size:14px;line-height:1.8;color:#374151}
                    .divider{border:none;border-top:1px solid #e5e7eb;margin:20px 0}
                    .footer{padding:18px 28px 22px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center}
                    .footer-text{font-size:12px;color:#6b7280;margin:0}
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="card">
                      <div class="header">
                        <img src="cid:skillverse-logo" alt="SkillVerse" style="height:44px;display:block;margin:0 auto 12px"/>
                        <div class="emoji">📋</div>
                        <h1>Cập nhật trạng thái ứng tuyển</h1>
                        <div class="badge">Không được chọn</div>
                      </div>
                      <div class="body">
                        <p class="greeting">Xin chào %s,</p>
                        <p>Rất tiếc, nhà tuyển dụng <strong>%s</strong> đã chọn ứng viên khác cho công việc:</p>
                        <div class="job-card">
                          <div class="job-title">%s</div>
                          <div class="recruiter">Nhà tuyển dụng: %s</div>
                        </div>
                        %s
                        <div class="tips">
                          <div class="tips-title">💡 Đừng nản lòng!</div>
                          <ul>
                            <li>Cập nhật portfolio để tăng sức hút</li>
                            <li>Tiếp tục ứng tuyển các công việc phù hợp</li>
                            <li>Kết nối với mentor để học hỏi thêm</li>
                          </ul>
                        </div>
                        <p>Chúc bạn sớm tìm được công việc phù hợp!</p>
                        <hr class="divider"/>
                        <p style="text-align:center;font-size:13px;color:#6b7280;margin:0">Quyết định này không phản ánh năng lực của bạn.</p>
                      </div>
                      <div class="footer">
                        <p class="footer-text">© SkillVerse — Cộng đồng học tập và nghề nghiệp.</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(name, recruiterName, jobTitle, recruiterName, reasonBlock);
    }

    private String buildShortTermWorkSubmittedHtmlContent(String recruiterName, String jobTitle, String workerName) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Công việc đã được nộp — SkillVerse</title>
                  <style>
                    body{margin:0;padding:0;background:#f5f7fb;font-family:Inter,Roboto,Helvetica,Arial,sans-serif;color:#1f2937}
                    .container{max-width:600px;margin:24px auto;padding:0 16px}
                    .card{background:#ffffff;border-radius:16px;box-shadow:0 8px 24px rgba(31,41,55,0.08);overflow:hidden}
                    .header{background:linear-gradient(135deg,#2563eb,#3b82f6);padding:32px 28px;text-align:center;color:#fff}
                    .header img{display:block;margin:0 auto 12px;height:44px}
                    .header h1{font-size:22px;font-weight:700;margin:0 0 6px}
                    .emoji{font-size:48px;margin-bottom:8px}
                    .badge{display:inline-block;margin-top:8px;padding:5px 14px;background:rgba(255,255,255,0.18);border:1px solid rgba(255,255,255,0.35);border-radius:999px;font-size:13px}
                    .body{padding:28px 28px}
                    p{line-height:1.7;margin:10px 0;color:#374151;font-size:14px}
                    .job-card{background:#eff6ff;border:1px solid #bfdbfe;border-radius:10px;padding:18px;margin:18px 0}
                    .job-card .job-title{font-size:15px;font-weight:600;color:#1e40af;margin:0 0 4px}
                    .job-card .worker{font-size:13px;color:#2563eb;margin:0}
                    .action-box{background:#f9fafb;border:1px solid #e5e7eb;border-radius:10px;padding:16px;margin:16px 0;text-align:center}
                    .action-box p{margin:0;font-size:14px}
                    .note{background:#fffbeb;border-left:4px solid #f59e0b;padding:12px 14px;border-radius:0 8px 8px 0;margin:16px 0;font-size:13px;color:#92400e}
                    .divider{border:none;border-top:1px solid #e5e7eb;margin:20px 0}
                    .footer{padding:18px 28px 22px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center}
                    .footer-text{font-size:12px;color:#6b7280;margin:0}
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="card">
                      <div class="header">
                        <img src="cid:skillverse-logo" alt="SkillVerse" style="height:44px;display:block;margin:0 auto 12px"/>
                        <div class="emoji">📦</div>
                        <h1>Công việc đã được nộp!</h1>
                        <div class="badge">Chờ bạn nghiệm thu</div>
                      </div>
                      <div class="body">
                        <p>Xin chào <strong>%s</strong>,</p>
                        <p><strong>%s</strong> đã nộp sản phẩm cho công việc:</p>
                        <div class="job-card">
                          <div class="job-title">%s</div>
                          <div class="worker">Người thực hiện: %s</div>
                        </div>
                        <div class="action-box">
                          <p>Vui lòng đăng nhập SkillVerse để kiểm tra và nghiệm thu sản phẩm.</p>
                        </div>
                        <div class="note">
                          <strong>⏰ Lưu ý:</strong> Bạn có 72 giờ để nghiệm thu hoặc yêu cầu chỉnh sửa. Sau 72 giờ, hệ thống sẽ tự động nghiệm thu.
                        </div>
                      </div>
                      <div class="footer">
                        <p class="footer-text">© SkillVerse — Cộng đồng học tập và nghề nghiệp.</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(recruiterName, workerName, jobTitle, workerName);
    }

    private String buildShortTermWorkApprovedHtmlContent(String workerName, String jobTitle, String budget) {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Công việc đã được nghiệm thu — SkillVerse</title>
                  <style>
                    body{margin:0;padding:0;background:#f5f7fb;font-family:Inter,Roboto,Helvetica,Arial,sans-serif;color:#1f2937}
                    .container{max-width:600px;margin:24px auto;padding:0 16px}
                    .card{background:#ffffff;border-radius:16px;box-shadow:0 8px 24px rgba(31,41,55,0.08);overflow:hidden}
                    .header{background:linear-gradient(135deg,#059669,#10b981);padding:32px 28px;text-align:center;color:#fff}
                    .header img{display:block;margin:0 auto 12px;height:44px}
                    .header h1{font-size:22px;font-weight:700;margin:0 0 6px}
                    .emoji{font-size:56px;margin-bottom:8px}
                    .badge{display:inline-block;margin-top:8px;padding:5px 14px;background:rgba(255,255,255,0.18);border:1px solid rgba(255,255,255,0.35);border-radius:999px;font-size:13px}
                    .body{padding:28px 28px}
                    .greeting{font-size:16px;font-weight:600;color:#111827;margin:0 0 12px}
                    p{line-height:1.7;margin:10px 0;color:#374151;font-size:14px}
                    .job-card{background:#f0fdf4;border:1px solid #bbf7d0;border-radius:10px;padding:18px;margin:18px 0}
                    .job-card .job-title{font-size:15px;font-weight:600;color:#166534;margin:0 0 8px}
                    .job-card .budget-row{display:flex;gap:12px;margin-top:8px}
                    .job-card .budget-item{flex:1;background:#dcfce7;border-radius:6px;padding:8px 10px}
                    .job-card .budget-item .label{font-size:11px;color:#15803d;text-transform:uppercase;letter-spacing:0.5px}
                    .job-card .budget-item .value{font-size:16px;font-weight:700;color:#15803d;margin-top:2px}
                    .success-box{background:#fefce8;border:1px solid #fef08a;border-radius:10px;padding:14px 16px;margin:16px 0;text-align:center}
                    .success-box .success-text{font-size:16px;font-weight:700;color:#854d0e;margin:0}
                    .success-box .success-sub{font-size:13px;color:#a16207;margin:4px 0 0}
                    .thanks{text-align:center;margin:16px 0;font-size:14px}
                    .footer{padding:18px 28px 22px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center}
                    .footer-text{font-size:12px;color:#6b7280;margin:0}
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="card">
                      <div class="header">
                        <img src="cid:skillverse-logo" alt="SkillVerse" style="height:44px;display:block;margin:0 auto 12px"/>
                        <div class="emoji">✅</div>
                        <h1>Công việc đã được nghiệm thu!</h1>
                        <div class="badge">Hoàn thành xuất sắc</div>
                      </div>
                      <div class="body">
                        <p class="greeting">Xin chào <strong>%s</strong>,</p>
                        <p>Tuyệt vời! Nhà tuyển dụng đã nghiệm thu và chấp nhận sản phẩm của bạn cho công việc:</p>
                        <div class="job-card">
                          <div class="job-title">%s</div>
                          <div class="budget-row">
                            <div class="budget-item">
                              <div class="label">Thanh toán</div>
                              <div class="value">%s</div>
                            </div>
                            <div class="budget-item">
                              <div class="label">Trạng thái</div>
                              <div class="value">✓ Hoàn thành</div>
                            </div>
                          </div>
                        </div>
                        <div class="success-box">
                          <div class="success-text">🎉 Thanh toán sẽ được giải ngân sớm!</div>
                          <div class="success-sub">Cảm ơn bạn đã hoàn thành công việc xuất sắc.</div>
                        </div>
                        <p class="thanks">Hãy để lại đánh giá cho nhà tuyển dụng để xây dựng uy tín trên SkillVerse nhé!</p>
                      </div>
                      <div class="footer">
                        <p class="footer-text">© SkillVerse — Cộng đồng học tập và nghề nghiệp.</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(workerName, jobTitle, budget != null ? budget : "Thỏa thuận");
    }

    @Override
    public void sendInterviewScheduled(
            String email,
            String fullName,
            String jobTitle,
            java.time.LocalDateTime scheduledAt,
            Integer durationMinutes,
            String meetingType,
            String meetingLink,
            String skillverseRoomId,
            String location,
            String interviewerName) {
        try {
            String htmlContent = buildInterviewScheduledHtmlContent(
                    fullName, jobTitle, scheduledAt, durationMinutes,
                    meetingType, meetingLink, skillverseRoomId, location, interviewerName);
            sendHtmlEmail(email, "📅 Lịch phỏng vấn đã được xếp — SkillVerse", htmlContent);
            log.info("📅 EMAIL SERVICE: Interview scheduled email sent to {} for job: {}", email, jobTitle);
        } catch (Exception e) {
            log.error("❌ Failed to send interview scheduled email to {}: {}", email, e.getMessage());
            log.info("📅 [FALLBACK] EMAIL SERVICE: Interview scheduled email to {}", email);
        }
    }

    private String buildInterviewScheduledHtmlContent(
            String fullName,
            String jobTitle,
            java.time.LocalDateTime scheduledAt,
            Integer durationMinutes,
            String meetingType,
            String meetingLink,
            String skillverseRoomId,
            String location,
            String interviewerName) {
        String dateTimeStr = scheduledAt != null
                ? scheduledAt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm, 'ngày' dd/MM/yyyy"))
                : "Chưa xác định";
        String durationStr = durationMinutes != null ? durationMinutes + " phút" : "60 phút";
        String meetingLinkBlock = meetingLink != null && !meetingLink.isBlank()
                ? "<div class=\"meeting-link\"><a href=\"" + meetingLink + "\" class=\"btn-meet\">Tham gia cuộc họp</a></div>"
                : "";
        String roomBlock = skillverseRoomId != null && !skillverseRoomId.isBlank()
                ? "<div class=\"room-info\"><strong>SkillVerse Room:</strong> <code>" + skillverseRoomId + "</code></div>"
                : "";
        String interviewerBlock = interviewerName != null && !interviewerName.isBlank()
                ? "<div class=\"interviewer-info\"><strong>Người phỏng vấn:</strong> " + interviewerName + "</div>"
                : "";
        String locationBlock = location != null && !location.isBlank()
                ? "<div class=\"location-info\"><strong>Địa điểm:</strong> " + location + "</div>"
                : "";
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>Lịch phỏng vấn đã được xếp — SkillVerse</title>
                  <style>
                    body{margin:0;padding:0;background:#f0f4ff;font-family:Inter,Roboto,Helvetica,Arial,sans-serif;color:#1f2937}
                    .container{max-width:600px;margin:24px auto;padding:0 16px}
                    .card{background:#ffffff;border-radius:16px;box-shadow:0 8px 24px rgba(31,41,55,0.08);overflow:hidden}
                    .header{background:linear-gradient(135deg,#0066ff,#00c6ff);padding:32px 28px;text-align:center;color:#fff}
                    .header img{display:block;margin:0 auto 12px;height:44px}
                    .header h1{font-size:22px;font-weight:700;margin:0 0 6px}
                    .emoji{font-size:48px;margin-bottom:8px}
                    .badge{display:inline-block;margin-top:8px;padding:5px 14px;background:rgba(255,255,255,0.18);border:1px solid rgba(255,255,255,0.35);border-radius:999px;font-size:13px}
                    .body{padding:28px 28px}
                    .greeting{font-size:16px;font-weight:600;color:#111827;margin:0 0 12px}
                    p{line-height:1.7;margin:10px 0;color:#374151;font-size:14px}
                    .info-grid{background:#f0f7ff;border:1px solid #cce4ff;border-radius:12px;padding:18px;margin:18px 0}
                    .info-row{display:flex;align-items:center;margin:8px 0;font-size:14px;color:#1e40af}
                    .info-row .icon{margin-right:10px;font-size:18px}
                    .info-row strong{min-width:140px;color:#1e3a8a}
                    .meeting-link{text-align:center;margin:20px 0}
                    .btn-meet{display:inline-block;background:linear-gradient(135deg,#0066ff,#00c6ff);color:#fff;padding:14px 32px;border-radius:10px;text-decoration:none;font-weight:700;font-size:15px}
                    .room-info{background:#f5f0ff;border:1px solid #e0c8ff;border-radius:8px;padding:12px 16px;margin:12px 0;font-size:14px;text-align:center}
                    .room-info code{background:#f5f0ff;color:#7c3aed;font-weight:700;font-size:15px;padding:4px 12px;border-radius:6px;font-family:monospace}
                    .interviewer-info,.location-info{background:#f9fafb;border:1px solid #e5e7eb;border-radius:8px;padding:10px 14px;margin:8px 0;font-size:14px}
                    .divider{border:none;border-top:1px solid #e5e7eb;margin:20px 0}
                    .footer{padding:18px 28px 22px;background:#f9fafb;border-top:1px solid #e5e7eb;text-align:center}
                    .footer-text{font-size:12px;color:#6b7280;margin:0}
                  </style>
                </head>
                <body>
                  <div class="container">
                    <div class="card">
                      <div class="header">
                        <img src="cid:skillverse-logo" alt="SkillVerse" style="height:44px;display:block;margin:0 auto 12px"/>
                        <div class="emoji">📅</div>
                        <h1>Lịch phỏng vấn đã được xếp!</h1>
                        <div class="badge">Xác nhận lịch hẹn</div>
                      </div>
                      <div class="body">
                        <p class="greeting">Xin chào <strong>%s</strong>,</p>
                        <p>Chúc mừng bạn! Nhà tuyển dụng đã xếp lịch phỏng vấn cho vị trí <strong>%s</strong>.</p>
                        <div class="info-grid">
                          <div class="info-row"><span class="icon">🗓</span><strong>Thời gian:</strong> %s</div>
                          <div class="info-row"><span class="icon">⏱</span><strong>Thời lượng:</strong> %s</div>
                          <div class="info-row"><span class="icon">💻</span><strong>Hình thức:</strong> %s</div>
                          %s
                          %s
                          %s
                        </div>
                        %s
                        <div class="divider"></div>
                        <p style="text-align:center;font-size:14px;margin:0">Vui lòng đăng nhập đúng giờ và chuẩn bị sẵn các câu hỏi của bạn. Chúc bạn phỏng vấn thành công!</p>
                      </div>
                      <div class="footer">
                        <p class="footer-text">© SkillVerse — Cộng đồng học tập và nghề nghiệp.</p>
                      </div>
                    </div>
                  </div>
                </body>
                </html>
                """.formatted(fullName, jobTitle, dateTimeStr, durationStr,
                        meetingType != null ? meetingType.replace("_", " ") : "Chưa xác định",
                        interviewerBlock, roomBlock, locationBlock, meetingLinkBlock);
    }
}
