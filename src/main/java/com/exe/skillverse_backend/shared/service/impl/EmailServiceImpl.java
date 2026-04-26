package com.exe.skillverse_backend.shared.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.service.EmailService.EmailSendingResult;
import com.exe.skillverse_backend.shared.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

        @Override
        public void sendOtpEmail(String email, String otp) {
                try {
                        String subject = "Xác thực email - SkillVerse";
                        String htmlContent = buildOtpEmailHtmlContent(otp);
                        sendHtmlEmail(email, subject, htmlContent);

                        log.info("🔐 EMAIL SERVICE: Đã gửi email OTP xác thực tới {}", email);

                } catch (Exception e) {
                        log.error("❌ Gửi email OTP xác thực thất bại tới {}", email, e);
                        log.info("🔐 [FALLBACK] EMAIL SERVICE: Gửi OTP xác thực tới {}", email);
                        log.info("📧 Tiêu đề: Xác thực email - SkillVerse");
                        log.info("📝 Mã xác thực của bạn: {}", otp);
                        log.info("⏰ Mã sẽ hết hạn sau 5 phút");
                        log.info("✉️  [MÔ PHỎNG] Đã gửi email tới {}", email);
                }
        }

        @Override
        public void sendPasswordResetOtpEmail(String email, String otp) {
                try {
                        String subject = "Mã xác thực đặt lại mật khẩu - SkillVerse";
                        String htmlContent = buildPasswordResetOtpHtmlContent(otp);
                        sendHtmlEmail(email, subject, htmlContent);

                        log.info("🔑 EMAIL SERVICE: Đã gửi email OTP đặt lại mật khẩu tới {}", email);

                } catch (Exception e) {
                        log.error("❌ Gửi email OTP đặt lại mật khẩu thất bại tới {}", email, e);
                        log.info("🔑 [FALLBACK] EMAIL SERVICE: Gửi OTP đặt lại mật khẩu tới {}", email);
                        log.info("📧 Tiêu đề: Mã xác thực đặt lại mật khẩu - SkillVerse");
                        log.info("📝 Mã xác thực của bạn: {}", otp);
                        log.info("⏰ Mã sẽ hết hạn sau 5 phút");
                        log.info("✉️  [MÔ PHỎNG] Đã gửi email tới {}", email);
                }
        }

        @Override
        public void sendWelcomeEmail(String email, String fullName) {
                try {
                        String subject = "🎉 Chào mừng đến với SkillVerse";
                        String htmlContent = buildWelcomeEmailHtmlContent(fullName != null ? fullName : email);
                        sendHtmlEmail(email, subject, htmlContent);

                        log.info("🎉 EMAIL SERVICE: Welcome HTML email sent successfully to {}", email);

                } catch (Exception e) {
                        log.error("❌ Failed to send welcome email to {}", email, e);
                        log.info("🎉 [FALLBACK] EMAIL SERVICE: Sending welcome email to {}", email);
                        log.info("📧 Subject: Welcome to SkillVerse!");
                        log.info("📝 Message: Welcome {}! Your email has been verified successfully.",
                                        fullName != null ? fullName : email);
                        log.info("✉️  [SIMULATED] Welcome email sent successfully to {}", email);
                }
        }

        @Override
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

        @Override
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
                        log.info("📧 [FALLBACK] EMAIL SERVICE: Sending rejection email to {} for role: {}", email, role);
                        log.info("📧 Subject: Application Update - SkillVerse");
                        log.info("📝 Your {} application status has been updated", role.toLowerCase());
                        log.info("✉️  [SIMULATED] Rejection email sent successfully to {}", email);
                }
        }

        // [Nghiệp vụ] Dùng khung email table-based thống nhất để logo và màu thương hiệu hiển thị ổn định trên các email client.
        private String buildCorporateEmailLayout(String badge, String title, String contentHtml, String footerNote) {
                String safeFooter = (footerNote == null || footerNote.isBlank())
                                ? "© 2026 SkillVerse. Email được gửi tự động từ hệ thống."
                                : footerNote;

                return """
                                <!doctype html>
                                <html lang=\"vi\">
                                <head>
                                    <meta charset=\"UTF-8\" />
                                    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
                                    <title>SkillVerse Notification</title>
                                    <style>
                                        body { margin:0; padding:0; background:#f3f6fb; font-family:Arial, Helvetica, sans-serif; color:#132238; }
                                        .wrapper { width:100%%; background:#f3f6fb; }
                                        .container { width:640px; max-width:640px; border:1px solid #d9e4f1; border-radius:16px; overflow:hidden; background:#ffffff; }
                                        .header { padding:22px 18px; background:#1f9ed7; background-image:linear-gradient(90deg,#f5a623 0%%,#1f9ed7 100%%); text-align:center; }
                                        .logo { width:138px; max-width:138px; height:auto; display:block; margin:0 auto; }
                                        .badge { display:inline-block; margin-top:12px; padding:6px 12px; border-radius:999px; background:#ffffff; color:#0f75bc; font-size:11px; font-weight:700; letter-spacing:0.4px; }
                                        .content { padding:24px; }
                                        h1 { margin:0 0 12px 0; font-size:24px; line-height:1.3; color:#10263f; }
                                        p { margin:0 0 10px 0; font-size:14px; line-height:1.7; color:#344a63; }
                                        .muted { color:#6c8098; font-size:13px; }
                                        .otp-wrap { margin:16px 0 14px; border:1px dashed #c8d9ec; border-radius:12px; background:#f8fbff; text-align:center; padding:16px 12px; }
                                        .otp-label { font-size:12px; color:#5d7692; letter-spacing:0.4px; margin-bottom:4px; }
                                        .otp-code { font-size:34px; letter-spacing:6px; line-height:1.2; font-weight:700; color:#0f75bc; }
                                        .section-card { margin:14px 0; border:1px solid #dbe6f3; border-radius:12px; background:#f9fcff; padding:14px; }
                                        .info-table { width:100%%; border-collapse:separate; border-spacing:0; border:1px solid #dbe6f3; border-radius:12px; overflow:hidden; margin:12px 0; }
                                        .info-table td { padding:12px 14px; font-size:14px; }
                                        .info-table tr + tr td { border-top:1px solid #e8eff8; }
                                        .info-table .label { color:#617991; width:42%%; }
                                        .info-table .value { color:#163352; text-align:right; font-weight:700; }
                                        .cta { margin-top:16px; }
                                        .button { display:inline-block; background:#0f75bc; color:#ffffff !important; text-decoration:none; padding:12px 18px; border-radius:10px; font-size:14px; font-weight:700; }
                                        .footer { padding:14px 20px 20px; border-top:1px solid #e6eef8; text-align:center; font-size:12px; color:#6c8098; background:#fbfdff; }
                                    </style>
                                </head>
                                <body>
                                    <table role=\"presentation\" class=\"wrapper\" cellpadding=\"0\" cellspacing=\"0\">
                                        <tr>
                                            <td align=\"center\" style=\"padding:24px 12px;\">
                                                <table role=\"presentation\" class=\"container\" cellpadding=\"0\" cellspacing=\"0\">
                                                    <tr>
                                                        <td class=\"header\">
                                                            <img class=\"logo\" src=\"cid:skillverse-logo\" alt=\"SkillVerse\" />
                                                            <div class=\"badge\">%s</div>
                                                        </td>
                                                    </tr>
                                                    <tr>
                                                        <td class=\"content\">
                                                            <h1>%s</h1>
                                                            %s
                                                        </td>
                                                    </tr>
                                                    <tr>
                                                        <td class=\"footer\">%s</td>
                                                    </tr>
                                                </table>
                                            </td>
                                        </tr>
                                    </table>
                                </body>
                                </html>
                                """.formatted(badge, title, contentHtml, safeFooter);
        }

        // [Nghiệp vụ] Chuẩn hóa dòng thông tin hai cột để toàn bộ email ứng tuyển dễ đọc và đồng nhất.
        private String buildCorporateInfoRow(String label, String value) {
                String normalizedValue = (value == null || value.isBlank()) ? "-" : value;
                return """
                                <tr>
                                    <td class=\"label\">%s</td>
                                    <td class=\"value\">%s</td>
                                </tr>
                                """.formatted(label, normalizedValue);
        }

        // [Nghiệp vụ] Tạo bảng thông tin dùng chung cho các thông báo ứng tuyển và OTP nghiệp vụ.
        private String buildCorporateInfoTable(String rowsHtml) {
                return """
                                <table role=\"presentation\" class=\"info-table\" cellpadding=\"0\" cellspacing=\"0\">
                                    %s
                                </table>
                                """.formatted(rowsHtml);
        }

        private String buildOtpEmailHtmlContent(String otp) {
                String content = "<p>Kính gửi bạn,</p>"
                                + "<p>Cảm ơn bạn đã đăng ký tài khoản tại SkillVerse. Vui lòng nhập mã OTP dưới đây để hoàn tất xác thực email.</p>"
                                + "<div class=\"otp-wrap\"><div class=\"otp-label\">MÃ OTP CỦA BẠN</div><div class=\"otp-code\">" + otp + "</div></div>"
                                + "<p class=\"muted\">Mã có hiệu lực trong <strong>5 phút</strong>. Vui lòng không chia sẻ mã này cho bất kỳ ai.</p>"
                                + "<p class=\"muted\">Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.</p>";

                return buildCorporateEmailLayout(
                                "XÁC THỰC EMAIL",
                                "Mã OTP kích hoạt tài khoản",
                                content,
                                "© 2026 SkillVerse. Bảo mật tài khoản là ưu tiên hàng đầu.");
        }

        private String buildPasswordResetOtpHtmlContent(String otp) {
                String content = "<p>Kính gửi bạn,</p>"
                                + "<p>Hệ thống đã nhận được yêu cầu đặt lại mật khẩu cho tài khoản SkillVerse của bạn.</p>"
                                + "<div class=\"otp-wrap\"><div class=\"otp-label\">MÃ OTP ĐẶT LẠI MẬT KHẨU</div><div class=\"otp-code\">" + otp + "</div></div>"
                                + "<p class=\"muted\">Mã OTP có hiệu lực trong <strong>5 phút</strong>. Tuyệt đối không chia sẻ mã cho người khác.</p>"
                                + "<p class=\"muted\">Nếu bạn không gửi yêu cầu này, vui lòng bỏ qua email và kiểm tra lại bảo mật tài khoản.</p>";

                return buildCorporateEmailLayout(
                                "BẢO MẬT TÀI KHOẢN",
                                "Mã OTP đặt lại mật khẩu",
                                content,
                                "© 2026 SkillVerse. Email thông báo bảo mật tự động.");
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
        String infoTable = buildCorporateInfoTable(
                buildCorporateInfoRow("Vị trí ứng tuyển", jobTitle)
                        + buildCorporateInfoRow("Trạng thái", "Đang được nhà tuyển dụng xem xét"));

        String content = "<p>Kính gửi <strong>" + name + "</strong>,</p>"
                + "<p>Nhà tuyển dụng đã bắt đầu xem xét đơn ứng tuyển của bạn trên SkillVerse.</p>"
                + infoTable
                + "<div class=\"section-card\"><p style=\"margin:0\">Bạn vui lòng theo dõi email và thông báo trong hệ thống để không bỏ lỡ bước tiếp theo.</p></div>"
                + "<div class=\"cta\"><a class=\"button\" href=\"https://skillverse.vn/job-applications\">Theo dõi đơn ứng tuyển</a></div>";

        return buildCorporateEmailLayout(
                "CẬP NHẬT ỨNG TUYỂN",
                "Đơn ứng tuyển đang được xem xét",
                content,
                "© 2026 SkillVerse. Cảm ơn bạn đã đồng hành cùng cộng đồng nghề nghiệp SkillVerse.");
    }

    private String buildJobApplicationAcceptedHtmlContent(String name, String jobTitle, String acceptanceMessage, String contactEmail) {
        String infoTable = buildCorporateInfoTable(
                buildCorporateInfoRow("Vị trí ứng tuyển", jobTitle)
                        + buildCorporateInfoRow("Kết quả", "Được chấp nhận"));

        String messageBlock = (acceptanceMessage != null && !acceptanceMessage.trim().isEmpty())
                ? "<div class=\"section-card\"><p style=\"margin:0 0 6px 0;font-weight:700;color:#163352\">Tin nhắn từ nhà tuyển dụng:</p><p style=\"margin:0\">"
                        + acceptanceMessage.replace("\n", "<br/>")
                        + "</p></div>"
                : "";

        String contactBlock = (contactEmail != null && !contactEmail.trim().isEmpty())
                ? "<p class=\"muted\">Email liên hệ: <a href=\"mailto:" + contactEmail
                        + "\" style=\"color:#0f75bc;text-decoration:none;font-weight:700\">"
                        + contactEmail + "</a></p>"
                : "";

        String content = "<p>Kính gửi <strong>" + name + "</strong>,</p>"
                + "<p>Chúc mừng bạn! Đơn ứng tuyển của bạn đã được nhà tuyển dụng chấp thuận.</p>"
                + infoTable
                + messageBlock
                + contactBlock
                + "<div class=\"section-card\"><p style=\"margin:0\">Vui lòng phản hồi sớm để xác nhận lịch làm việc và các thủ tục tiếp theo.</p></div>"
                + "<div class=\"cta\"><a class=\"button\" href=\"https://skillverse.vn/job-applications\">Xem chi tiết kết quả</a></div>";

        return buildCorporateEmailLayout(
                "ỨNG TUYỂN THÀNH CÔNG",
                "Chúc mừng! Bạn đã được chọn",
                content,
                "© 2026 SkillVerse. Chúc bạn có một khởi đầu công việc thuận lợi.");
    }

    private String buildJobApplicationRejectedHtmlContent(String name, String jobTitle, String rejectionReason) {
        String infoTable = buildCorporateInfoTable(
                buildCorporateInfoRow("Vị trí ứng tuyển", jobTitle)
                        + buildCorporateInfoRow("Kết quả", "Chưa phù hợp ở thời điểm hiện tại"));

        String reasonBlock = (rejectionReason != null && !rejectionReason.trim().isEmpty())
                ? "<div class=\"section-card\"><p style=\"margin:0 0 6px 0;font-weight:700;color:#163352\">Phản hồi từ nhà tuyển dụng:</p><p style=\"margin:0\">"
                        + rejectionReason.replace("\n", "<br/>")
                        + "</p></div>"
                : "";

        String content = "<p>Kính gửi <strong>" + name + "</strong>,</p>"
                + "<p>Cảm ơn bạn đã quan tâm và ứng tuyển vị trí tại SkillVerse.</p>"
                + infoTable
                + reasonBlock
                + "<div class=\"section-card\"><p style=\"margin:0 0 8px 0;font-weight:700;color:#163352\">Gợi ý để tăng tỷ lệ đậu:</p>"
                + "<ul style=\"margin:0;padding-left:18px;color:#344a63;line-height:1.7\">"
                + "<li>Cập nhật CV và hồ sơ năng lực theo vị trí ứng tuyển.</li>"
                + "<li>Bổ sung thêm dự án hoặc thành tích gần nhất.</li>"
                + "<li>Tiếp tục ứng tuyển các vị trí phù hợp trên SkillVerse.</li>"
                + "</ul></div>"
                + "<div class=\"cta\"><a class=\"button\" href=\"https://skillverse.vn/jobs\">Khám phá cơ hội khác</a></div>";

        return buildCorporateEmailLayout(
                "CẬP NHẬT ỨNG TUYỂN",
                "Thông báo kết quả ứng tuyển",
                content,
                "© 2026 SkillVerse. Quyết định tuyển dụng không phản ánh toàn bộ năng lực của bạn.");
    }

    // ==================== HTML EMAIL BUILDERS FOR SHORT-TERM JOBS ====================

    private String buildShortTermApplicationSubmittedHtmlContent(String name, String jobTitle, String recruiterName, String deadline, String budget) {
        String infoTable = buildCorporateInfoTable(
                buildCorporateInfoRow("Công việc", jobTitle)
                        + buildCorporateInfoRow("Nhà tuyển dụng", recruiterName)
                        + buildCorporateInfoRow("Ngân sách", budget != null ? budget : "Thỏa thuận")
                        + buildCorporateInfoRow("Hạn nộp", deadline != null ? deadline : "Theo thông báo tuyển dụng"));

        String content = "<p>Kính gửi <strong>" + name + "</strong>,</p>"
                + "<p>Đơn ứng tuyển công việc ngắn hạn của bạn đã được gửi thành công.</p>"
                + infoTable
                + "<div class=\"section-card\"><p style=\"margin:0\">Nhà tuyển dụng sẽ xem xét hồ sơ và phản hồi trong thời gian sớm nhất.</p></div>"
                + "<div class=\"cta\"><a class=\"button\" href=\"https://skillverse.vn/short-term-jobs/my-applications\">Theo dõi trạng thái</a></div>";

        return buildCorporateEmailLayout(
                "ĐƠN ĐÃ GỬI",
                "Ứng tuyển công việc ngắn hạn thành công",
                content,
                "© 2026 SkillVerse. Chúc bạn sớm nhận được phản hồi tích cực.");
    }

    private String buildShortTermApplicationAcceptedHtmlContent(String name, String jobTitle, String recruiterName, String budget, String deadline) {
        String infoTable = buildCorporateInfoTable(
                buildCorporateInfoRow("Công việc", jobTitle)
                        + buildCorporateInfoRow("Nhà tuyển dụng", recruiterName)
                        + buildCorporateInfoRow("Ngân sách", budget != null ? budget : "Thỏa thuận")
                        + buildCorporateInfoRow("Hạn hoàn thành", deadline != null ? deadline : "Theo thỏa thuận"));

        String content = "<p>Kính gửi <strong>" + name + "</strong>,</p>"
                + "<p>Chúc mừng! Bạn đã được chọn cho công việc ngắn hạn trên SkillVerse.</p>"
                + infoTable
                + "<div class=\"section-card\"><p style=\"margin:0 0 8px 0;font-weight:700;color:#163352\">Bước tiếp theo:</p>"
                + "<ul style=\"margin:0;padding-left:18px;color:#344a63;line-height:1.7\">"
                + "<li>Đọc kỹ mô tả và tiêu chí bàn giao.</li>"
                + "<li>Chủ động liên hệ nhà tuyển dụng để xác nhận phạm vi công việc.</li>"
                + "<li>Nộp sản phẩm đúng hạn để đảm bảo tiến độ thanh toán.</li>"
                + "</ul></div>"
                + "<div class=\"cta\"><a class=\"button\" href=\"https://skillverse.vn/short-term-jobs/my-applications\">Bắt đầu công việc</a></div>";

        return buildCorporateEmailLayout(
                "ỨNG TUYỂN THÀNH CÔNG",
                "Bạn đã được nhận vào công việc",
                content,
                "© 2026 SkillVerse. Chúc bạn hoàn thành công việc xuất sắc.");
    }

    private String buildShortTermApplicationRejectedHtmlContent(String name, String jobTitle, String recruiterName, String reason) {
        String infoTable = buildCorporateInfoTable(
                buildCorporateInfoRow("Công việc", jobTitle)
                        + buildCorporateInfoRow("Nhà tuyển dụng", recruiterName)
                        + buildCorporateInfoRow("Kết quả", "Chưa phù hợp"));

        String reasonBlock = (reason != null && !reason.trim().isEmpty())
                ? "<div class=\"section-card\"><p style=\"margin:0 0 6px 0;font-weight:700;color:#163352\">Phản hồi từ nhà tuyển dụng:</p><p style=\"margin:0\">"
                        + reason.replace("\n", "<br/>")
                        + "</p></div>"
                : "";

        String content = "<p>Kính gửi <strong>" + name + "</strong>,</p>"
                + "<p>Nhà tuyển dụng đã hoàn tất vòng xem xét hồ sơ cho công việc bạn ứng tuyển.</p>"
                + infoTable
                + reasonBlock
                + "<div class=\"section-card\"><p style=\"margin:0\">Bạn có thể tiếp tục ứng tuyển các công việc khác phù hợp hơn để tăng cơ hội trúng tuyển.</p></div>"
                + "<div class=\"cta\"><a class=\"button\" href=\"https://skillverse.vn/short-term-jobs\">Xem công việc khác</a></div>";

        return buildCorporateEmailLayout(
                "CẬP NHẬT ỨNG TUYỂN",
                "Thông báo kết quả công việc ngắn hạn",
                content,
                "© 2026 SkillVerse. Cảm ơn bạn đã tiếp tục đồng hành cùng nền tảng.");
    }

    private String buildShortTermWorkSubmittedHtmlContent(String recruiterName, String jobTitle, String workerName) {
        String infoTable = buildCorporateInfoTable(
                buildCorporateInfoRow("Công việc", jobTitle)
                        + buildCorporateInfoRow("Người thực hiện", workerName)
                        + buildCorporateInfoRow("Trạng thái", "Đã nộp sản phẩm, chờ nghiệm thu"));

        String content = "<p>Kính gửi <strong>" + recruiterName + "</strong>,</p>"
                + "<p>Ứng viên đã hoàn tất và nộp sản phẩm cho công việc của bạn.</p>"
                + infoTable
                + "<div class=\"section-card\"><p style=\"margin:0\">Vui lòng kiểm tra, phản hồi và nghiệm thu trong thời gian quy định để hệ thống xử lý thanh toán đúng hạn.</p></div>"
                + "<div class=\"cta\"><a class=\"button\" href=\"https://skillverse.vn/short-term-jobs/manage\">Kiểm tra sản phẩm</a></div>";

        return buildCorporateEmailLayout(
                "YÊU CẦU NGHIỆM THU",
                "Sản phẩm công việc đã được nộp",
                content,
                "© 2026 SkillVerse. Bạn có thể quản lý tiến độ trực tiếp trên hệ thống.");
    }

    private String buildShortTermWorkApprovedHtmlContent(String workerName, String jobTitle, String budget) {
        String infoTable = buildCorporateInfoTable(
                buildCorporateInfoRow("Công việc", jobTitle)
                        + buildCorporateInfoRow("Thanh toán", budget != null ? budget : "Thỏa thuận")
                        + buildCorporateInfoRow("Kết quả", "Đã nghiệm thu thành công"));

        String content = "<p>Kính gửi <strong>" + workerName + "</strong>,</p>"
                + "<p>Nhà tuyển dụng đã nghiệm thu sản phẩm và xác nhận hoàn thành công việc.</p>"
                + infoTable
                + "<div class=\"section-card\"><p style=\"margin:0\">Thanh toán sẽ được hệ thống xử lý theo chính sách giải ngân hiện hành của SkillVerse.</p></div>"
                + "<div class=\"cta\"><a class=\"button\" href=\"https://skillverse.vn/wallet\">Xem trạng thái thanh toán</a></div>";

        return buildCorporateEmailLayout(
                "HOÀN THÀNH CÔNG VIỆC",
                "Công việc đã được nghiệm thu",
                content,
                "© 2026 SkillVerse. Cảm ơn bạn vì chất lượng công việc chuyên nghiệp.");
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
            LocalDateTime scheduledAt,
            Integer durationMinutes,
            String meetingType,
            String meetingLink,
            String skillverseRoomId,
            String location,
            String interviewerName) {
        String dateTimeStr = scheduledAt != null
                ? scheduledAt.format(DateTimeFormatter.ofPattern("HH:mm, 'ngày' dd/MM/yyyy"))
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
