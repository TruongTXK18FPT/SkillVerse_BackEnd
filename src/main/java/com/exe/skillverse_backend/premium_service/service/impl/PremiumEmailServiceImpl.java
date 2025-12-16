package com.exe.skillverse_backend.premium_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.service.PremiumEmailService;
import com.exe.skillverse_backend.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Email notification service for premium subscription operations
 * Follows OOP principles and reuses EmailService for HTML email sending
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PremiumEmailServiceImpl implements PremiumEmailService {

    private final EmailService emailService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale VI_VN_LOCALE = new Locale.Builder().setLanguage("vi").setRegion("VN").build();
    private static final NumberFormat VND_FORMAT = NumberFormat.getCurrencyInstance(VI_VN_LOCALE);

    /**
     * Send premium purchase success email
     * Called after successful premium subscription activation
     * 
     * @param user          User who purchased premium
     * @param subscription  The activated subscription
     * @param paidAmount    Amount paid for the subscription
     * @param paymentMethod Payment method used (WALLET, PAYOS, etc.)
     */
    @Async("emailTaskExecutor")
    public void sendPremiumPurchaseSuccessEmail(
            User user,
            UserSubscription subscription,
            BigDecimal paidAmount,
            String paymentMethod) {

        try {
            String userName = getUserDisplayName(user);
            PremiumPlan plan = subscription.getPlan();

            String htmlContent = buildPremiumPurchaseSuccessHtml(
                    userName,
                    plan.getDisplayName(),
                    plan.getPlanType().name(),
                    formatCurrency(paidAmount),
                    paymentMethod,
                    subscription.getStartDate().format(DATE_FORMATTER),
                    subscription.getEndDate().format(DATE_FORMATTER),
                    subscription.getIsStudentSubscription(),
                    getPlanFeatures(plan.getPlanType()));

            String subject = "🎉 Mua Premium Thành Công - " + plan.getDisplayName();
            emailService.sendHtmlEmail(user.getEmail(), subject, htmlContent);

            log.info("✅ Premium purchase email sent to {} for plan: {}", user.getEmail(), plan.getDisplayName());

        } catch (Exception e) {
            log.error("❌ Failed to send premium purchase email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    /**
     * Build HTML content for premium purchase success email
     */
    private String buildPremiumPurchaseSuccessHtml(
            String userName,
            String planName,
            String planType,
            String paidAmount,
            String paymentMethod,
            String startDate,
            String endDate,
            boolean isStudentDiscount,
            String features) {

        String discountBadge = isStudentDiscount
                ? "<div class=\"discount-badge\">🎓 Giảm giá sinh viên đã áp dụng</div>"
                : "";

        String brandGradient = "linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)";
        String brandColor = "#4f46e5";
        String brandSoft = "#eef2ff";

        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <style>
                                body { font-family: 'Inter', 'Roboto', 'Arial', sans-serif; background-color: #f5f5f7; margin: 0; padding: 20px; }
                                .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; box-shadow: 0 8px 24px rgba(17,24,39,0.08); }
                                .header { background: %s; padding: 32px 30px; text-align: center; color: white; }
                                .header h1 { margin: 0; font-size: 32px; font-weight: bold; }
                                .header .plan-name { font-size: 24px; margin-top: 10px; opacity: 0.95; }
                                .content { padding: 30px; }
                                .success-icon { font-size: 64px; text-align: center; margin: 20px 0; }
                                .discount-badge { background: %s; color: white; padding: 8px 16px; border-radius: 20px; display: inline-block; margin: 15px 0; font-weight: bold; }
                                .info-box { background: #f9fafb; border-left: 4px solid %s; padding: 20px; margin: 20px 0; border-radius: 8px; }
                                .info-row { display: flex; justify-content: space-between; margin: 10px 0; }
                                .info-label { font-weight: 600; color: #374151; }
                                .info-value { color: #6b7280; }
                                .features-box { background: %s; color: white; padding: 25px; border-radius: 8px; margin: 25px 0; }
                                .features-box h3 { margin-top: 0; font-size: 20px; }
                                .features-list { list-style: none; padding: 0; margin: 15px 0; }
                                .features-list li { padding: 8px 0; padding-left: 25px; position: relative; }
                                .features-list li:before { content: "✓"; position: absolute; left: 0; font-weight: bold; color: #a5b4fc; }
                                .button { display: inline-block; background: %s; color: white; padding: 14px 32px; text-decoration: none; border-radius: 8px; margin: 20px 0; font-weight: 600; }
                                .footer { background: #f9fafb; padding: 20px; text-align: center; color: #6b7280; font-size: 14px; }
                                .price { font-size: 36px; color: %s; font-weight: bold; text-align: center; margin: 20px 0; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <img src="cid:skillverse-logo" alt="SkillVerse" style="height:40px; display:block; margin:0 auto 12px;" />
                                    <h1>🎉 Chúc Mừng!</h1>
                                    <div class="plan-name">%s</div>
                                </div>
                                <div class="content">
                                    <div class="success-icon">✅</div>
                                    <p style="text-align: center; font-size: 18px;">Xin chào <strong>%s</strong>,</p>
                                    <p style="text-align: center;">Bạn đã mua gói Premium thành công!</p>

                                    %s

                                    <div class="price">%s</div>

                                    <div class="info-box">
                                        <div class="info-row">
                                            <span class="info-label">Gói đăng ký:</span>
                                            <span class="info-value"><strong>%s</strong></span>
                                        </div>
                                        <div class="info-row">
                                            <span class="info-label">Phương thức thanh toán:</span>
                                            <span class="info-value">%s</span>
                                        </div>
                                        <div class="info-row">
                                            <span class="info-label">Ngày bắt đầu:</span>
                                            <span class="info-value">%s</span>
                                        </div>
                                        <div class="info-row">
                                            <span class="info-label">Ngày hết hạn:</span>
                                            <span class="info-value">%s</span>
                                        </div>
                                    </div>

                                    <div class="features-box">
                                        <h3>🌟 Tính Năng Đã Mở Khóa</h3>
                                        %s
                                    </div>

                                    <p style="text-align: center;">
                                        <a href="https://skillverse.vn/premium" class="button">Khám Phá Tính Năng Premium</a>
                                    </p>

                                    <p style="color: #6b7280; font-size: 14px; margin-top: 30px; text-align: center;">
                                        💡 <strong>Mẹo:</strong> Bạn có thể quản lý gói đăng ký của mình trong phần "Tài khoản" → "Premium"
                                    </p>
                                </div>
                                <div class="footer">
                                    <p>Cảm ơn bạn đã tin tưởng SkillVerse! 🚀</p>
                                    <p>Nếu bạn có bất kỳ câu hỏi nào, vui lòng liên hệ support@skillverse.vn</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                brandGradient, planName, userName, discountBadge, brandColor,
                brandColor, brandGradient, brandColor, brandColor,
                planName, paymentMethod, startDate, endDate, features);
    }

    /**
     * Get plan-specific gradient color
     */
    private String getPlanGradient(String planType) {
        return switch (planType) {
            case "PREMIUM_BASIC" -> "linear-gradient(135deg, #667eea 0%, #764ba2 100%)";
            case "PREMIUM_PLUS" -> "linear-gradient(135deg, #f093fb 0%, #f5576c 100%)";
            case "STUDENT_PACK" -> "linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)";
            default -> "linear-gradient(135deg, #667eea 0%, #764ba2 100%)";
        };
    }

    /**
     * Get plan-specific features HTML
     */
    private String getPlanFeatures(PremiumPlan.PlanType planType) {
        String features = switch (planType) {
            case PREMIUM_BASIC -> """
                    <ul class="features-list">
                        <li>Truy cập không giới hạn tất cả khóa học</li>
                        <li>Tải xuống tài liệu học tập</li>
                        <li>Hỗ trợ ưu tiên từ mentor</li>
                        <li>Tham gia các buổi workshop độc quyền</li>
                        <li>Không có quảng cáo</li>
                    </ul>
                    """;
            case PREMIUM_PLUS -> """
                    <ul class="features-list">
                        <li>Tất cả tính năng Premium Basic</li>
                        <li>1-on-1 mentoring sessions (2 buổi/tháng)</li>
                        <li>Chứng chỉ hoàn thành khóa học</li>
                        <li>Truy cập sớm các khóa học mới</li>
                        <li>Tham gia cộng đồng Premium Plus riêng</li>
                        <li>Giảm giá 20% cho các khóa học trả phí</li>
                    </ul>
                    """;
            case STUDENT_PACK -> """
                    <ul class="features-list">
                        <li>Truy cập không giới hạn tất cả khóa học</li>
                        <li>Tải xuống tài liệu học tập</li>
                        <li>Hỗ trợ từ mentor</li>
                        <li>Tham gia workshop dành cho sinh viên</li>
                        <li>Giảm giá đặc biệt cho sinh viên</li>
                        <li>Kết nối với cộng đồng sinh viên</li>
                    </ul>
                    """;
            default -> """
                    <ul class="features-list">
                        <li>Truy cập các tính năng premium</li>
                        <li>Hỗ trợ ưu tiên</li>
                    </ul>
                    """;
        };
        return features;
    }

    /**
     * Format currency to VND
     */
    private String formatCurrency(BigDecimal amount) {
        return VND_FORMAT.format(amount);
    }

    /**
     * Get user display name (firstName or email)
     */
    private String getUserDisplayName(User user) {
        if (user.getFirstName() != null && !user.getFirstName().isEmpty()) {
            return user.getFirstName();
        }
        return user.getEmail();
    }
}
