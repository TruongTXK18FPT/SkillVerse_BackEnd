package com.exe.skillverse_backend.premium_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.premium_service.entity.UserSubscription;
import com.exe.skillverse_backend.premium_service.service.PremiumEmailService;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.shared.util.EmailThemeStyles;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

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
    @Override
    @Async("emailTaskExecutor")
    public void sendPremiumPurchaseSuccessEmail(
            User user,
            UserSubscription subscription,
            BigDecimal paidAmount,
            String paymentMethod) {

        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Skipping premium purchase email because recipient email is missing");
            return;
        }
        if (subscription == null || subscription.getPlan() == null) {
            log.warn("Skipping premium purchase email for {} because subscription or plan is missing", user.getEmail());
            return;
        }

        try {
            String userName = getUserDisplayName(user);
            PremiumPlan plan = subscription.getPlan();

            String htmlContent = buildPremiumPurchaseSuccessHtml(
                    userName,
                    plan.getDisplayName(),
                    plan.getPlanType().name(),
                    formatCurrency(paidAmount),
                    resolvePaymentMethodLabel(paymentMethod),
                    subscription.getStartDate().format(DATE_FORMATTER),
                    subscription.getEndDate().format(DATE_FORMATTER),
                    subscription.getIsStudentSubscription(),
                    getPlanFeatures(plan.getPlanType()));

            String subject = "🎉 Mua Premium Thành Công - " + plan.getDisplayName();
            emailService.sendHtmlEmail(user.getEmail(), subject, htmlContent);

            log.info("✅ Premium purchase email sent to {} for plan: {}", user.getEmail(), plan.getDisplayName());

        } catch (Exception e) {
            log.error("❌ Failed to send premium purchase email to {}", user.getEmail(), e);
        }
    }

    /**
     * Send auto-renewal success email
     */
    @Override
    @Async("emailTaskExecutor")
    public void sendAutoRenewalSuccessEmail(
            User user,
            UserSubscription subscription,
            BigDecimal renewalAmount) {

        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Skipping auto-renewal success email because recipient email is missing");
            return;
        }
        if (subscription == null || subscription.getPlan() == null) {
            log.warn("Skipping auto-renewal success email for {} because subscription or plan is missing", user.getEmail());
            return;
        }

        try {
            String userName = getUserDisplayName(user);
            PremiumPlan plan = subscription.getPlan();

            String htmlContent = buildAutoRenewalSuccessHtml(
                    userName,
                    plan.getDisplayName(),
                    plan.getPlanType().name(),
                    formatCurrency(renewalAmount),
                    subscription.getStartDate().format(DATE_FORMATTER),
                    subscription.getEndDate().format(DATE_FORMATTER),
                    subscription.getIsStudentSubscription(),
                    getPlanFeatures(plan.getPlanType()));

            String subject = "♻️ Gia Hạn Premium Thành Công - " + plan.getDisplayName();
            emailService.sendHtmlEmail(user.getEmail(), subject, htmlContent);

            log.info("✅ Auto-renewal email sent to {} for plan: {}", user.getEmail(), plan.getDisplayName());

        } catch (Exception e) {
            log.error("❌ Failed to send auto-renewal email to {}", user.getEmail(), e);
        }
    }

    /**
     * Send auto-renewal failed email (insufficient balance)
     */
    @Override
    @Async("emailTaskExecutor")
    public void sendAutoRenewalFailedEmail(
            User user,
            UserSubscription subscription,
            BigDecimal renewalAmount) {

        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Skipping auto-renewal failed email because recipient email is missing");
            return;
        }
        if (subscription == null || subscription.getPlan() == null) {
            log.warn("Skipping auto-renewal failed email for {} because subscription or plan is missing", user.getEmail());
            return;
        }

        try {
            String userName = getUserDisplayName(user);
            PremiumPlan plan = subscription.getPlan();

            String htmlContent = buildAutoRenewalFailedHtml(
                    userName,
                    plan.getDisplayName(),
                    formatCurrency(renewalAmount),
                    subscription.getEndDate().format(DATE_FORMATTER));

            String subject = "❌ Gia Hạn Premium Thất Bại - " + plan.getDisplayName();
            emailService.sendHtmlEmail(user.getEmail(), subject, htmlContent);

            log.info("⚠️ Auto-renewal failed email sent to {} for plan: {}", user.getEmail(), plan.getDisplayName());

        } catch (Exception e) {
            log.error("❌ Failed to send auto-renewal failed email to {}", user.getEmail(), e);
        }
    }

    /**
     * Build HTML content for auto-renewal success email
     */
    private String buildAutoRenewalSuccessHtml(
            String userName,
            String planName,
            String planType,
            String renewalAmount,
            String startDate,
            String endDate,
            boolean isStudentDiscount,
            String features) {

        String discountBadge = isStudentDiscount
                ? "<div class=\"discount-badge\">🎓 Giảm giá sinh viên đã áp dụng</div>"
                : "";

        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <style>%s</style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <img src="cid:skillverse-logo" alt="SkillVerse" style="height:40px; display:block; margin:0 auto 12px;" />
                                    <h1>♻️ Gia Hạn Thành Công!</h1>
                                    <div class="plan-name">%s</div>
                                </div>
                                <div class="content">
                                    <div class="success-icon">✅</div>
                                    <p style="text-align: center; font-size: 18px;">Xin chào <strong>%s</strong>,</p>
                                    <p style="text-align: center;">Gói Premium của bạn đã được gia hạn tự động thành công.</p>

                                    %s

                                    <div class="price">%s</div>

                                    <div class="info-box">
                                        <div class="info-row">
                                            <span class="info-label">Gói gia hạn:</span>
                                            <span class="info-value"><strong>%s</strong></span>
                                        </div>
                                        <div class="info-row">
                                            <span class="info-label">Phương thức:</span>
                                            <span class="info-value">Ví SkillVerse (Tự động)</span>
                                        </div>
                                        <div class="info-row">
                                            <span class="info-label">Chu kỳ mới:</span>
                                            <span class="info-value">%s</span>
                                        </div>
                                        <div class="info-row">
                                            <span class="info-label">Hết hạn:</span>
                                            <span class="info-value">%s</span>
                                        </div>
                                    </div>

                                    <div class="features-box">
                                        <h3>🌟 Tiếp Tục Tận Hưởng</h3>
                                        %s
                                    </div>

                                    <p style="text-align: center;">
                                        <a href="https://skillverse.vn/premium" class="button">Vào Học Ngay</a>
                                    </p>

                                    <p style="color: #6b7280; font-size: 14px; margin-top: 30px; text-align: center;">
                                        💡 <strong>Mẹo:</strong> Bạn có thể quản lý cài đặt gia hạn trong phần "Tài khoản" → "Premium"
                                    </p>
                                </div>
                                <div class="footer">
                                    <p>Cảm ơn bạn đã đồng hành cùng SkillVerse! 🚀</p>
                                    <p>Nếu có thắc mắc, vui lòng liên hệ support@skillverse.vn</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                EmailThemeStyles.CSS_BLOCK,
                planName, userName, discountBadge, renewalAmount,
                planName, startDate, endDate, features);
    }

    /**
     * Build HTML content for auto-renewal failed email
     */
    private String buildAutoRenewalFailedHtml(
            String userName,
            String planName,
            String amount,
            String expiryDate) {

        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <style>%s</style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <img src="cid:skillverse-logo" alt="SkillVerse" style="height:40px; display:block; margin:0 auto 12px;" />
                                    <h1>⚠️ Gia Hạn Thất Bại</h1>
                                    <div class="plan-name">%s</div>
                                </div>
                                <div class="content">
                                    <div class="fail-icon">❌</div>
                                    <p style="text-align: center; font-size: 18px;">Xin chào <strong>%s</strong>,</p>
                                    <p style="text-align: center;">Chúng tôi không thể gia hạn gói Premium của bạn do số dư ví không đủ.</p>

                                    <div class="info-box">
                                        <div class="info-row">
                                            <span class="info-label">Số tiền cần thanh toán:</span>
                                            <span class="info-value"><strong>%s</strong></span>
                                        </div>
                                        <div class="info-row">
                                            <span class="info-label">Lý do:</span>
                                            <span class="info-value">Số dư ví không đủ</span>
                                        </div>
                                        <div class="info-row">
                                            <span class="info-label">Hết hạn vào:</span>
                                            <span class="info-value">%s</span>
                                        </div>
                                    </div>

                                    <p style="text-align: center; margin-top: 20px;">
                                        Vui lòng nạp thêm tiền vào ví để tiếp tục sử dụng dịch vụ Premium không gián đoạn.
                                    </p>

                                    <p style="text-align: center;">
                                        <a href="https://skillverse.vn/wallet" class="button">Nạp Tiền Ngay</a>
                                    </p>
                                </div>
                                <div class="footer">
                                    <p>Nếu bạn cần hỗ trợ, vui lòng liên hệ support@skillverse.vn</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                EmailThemeStyles.CSS_BLOCK,
                planName, userName, amount, expiryDate);
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

        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <style>%s</style>
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
                    EmailThemeStyles.CSS_BLOCK,
                    planName,
                    userName,
                    discountBadge,
                    paidAmount,
                    planName,
                    paymentMethod,
                    startDate,
                    endDate,
                    features);
    }

    /**
     * Convert technical payment method to user-friendly Vietnamese label.
     */
    private String resolvePaymentMethodLabel(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return "Không xác định";
        }

        return switch (paymentMethod.toUpperCase()) {
            case "WALLET" -> "Ví SkillVerse";
            case "PAYOS" -> "PayOS";
            case "CASH" -> "Tiền mặt";
            default -> paymentMethod;
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
