package com.exe.skillverse_backend.wallet_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.wallet_service.entity.WithdrawalRequest;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Email notification service for wallet operations
 * Uses same email configuration as OTP service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletEmailServiceImpl {

    private final JavaMailSender mailSender;

    @Value("${email.from}")
    private String fromEmail;

    @Value("${email.from-name}")
    private String fromName;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Locale VI_VN_LOCALE = new Locale.Builder().setLanguage("vi").setRegion("VN").build();
    private static final NumberFormat VND_FORMAT = NumberFormat.getCurrencyInstance(VI_VN_LOCALE);

    /**
     * Send deposit success notification
     */
    @Async
    public void sendDepositSuccessEmail(User user, BigDecimal amount, String transactionId, BigDecimal currentBalance) {
        try {
            String userName = (user.getFirstName() != null ? user.getFirstName() : user.getEmail());
            String html = buildDepositSuccessHtml(
                    userName,
                    formatCurrency(amount),
                    transactionId,
                    formatCurrency(currentBalance));
            sendHtmlEmail(user.getEmail(), "✅ Nạp Tiền Thành Công", html);
            log.info("✅ Sent deposit success email to {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Failed to send deposit email to {}: {}", user.getEmail(), e.getMessage());
        }
    }

    /**
     * Send coin purchase success notification
     */
    @Async
    public void sendCoinPurchaseEmail(User user, Long totalCoins, Long bonusCoins, BigDecimal paidAmount,
            String paymentMethod) {
        try {
            String userName = (user.getFirstName() != null ? user.getFirstName() : user.getEmail());
            String html = buildCoinPurchaseHtml(
                    userName,
                    totalCoins.toString(),
                    bonusCoins > 0 ? "<span class=\"bonus-badge\">+ " + bonusCoins + " Bonus</span>" : "",
                    formatCurrency(paidAmount),
                    paymentMethod);
            sendHtmlEmail(user.getEmail(), "🪙 Mua SkillCoin Thành Công", html);
            log.info("✅ Sent coin purchase email to {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Failed to send coin purchase email: {}", e.getMessage());
        }
    }

    /**
     * Send withdrawal request created notification (to user)
     */
    @Async
    public void sendWithdrawalRequestCreatedEmail(User user, WithdrawalRequest request) {
        try {
            String userName = (user.getFirstName() != null ? user.getFirstName() : user.getEmail());
            String html = buildWithdrawalRequestHtml(
                    userName,
                    request.getRequestCode(),
                    formatCurrency(request.getAmount()),
                    formatCurrency(request.getFee()),
                    formatCurrency(request.getNetAmount()),
                    request.getBankName(),
                    maskAccountNumber(request.getBankAccountNumber()),
                    request.getCreatedAt().format(DATE_FORMATTER));
            sendHtmlEmail(user.getEmail(), "💸 Yêu Cầu Rút Tiền", html);
            log.info("✅ Sent withdrawal request email to {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Failed to send withdrawal request email: {}", e.getMessage());
        }
    }

    /**
     * Send withdrawal approved notification (to user)
     */
    @Async
    public void sendWithdrawalApprovedEmail(User user, WithdrawalRequest request) {
        try {
            String userName = (user.getFirstName() != null ? user.getFirstName() : user.getEmail());
            String html = buildWithdrawalApprovedHtml(
                    userName,
                    request.getRequestCode(),
                    formatCurrency(request.getNetAmount()),
                    request.getBankName(),
                    maskAccountNumber(request.getBankAccountNumber()),
                    request.getAdminNotes() != null ? request.getAdminNotes() : "Không có ghi chú");
            sendHtmlEmail(user.getEmail(), "✅ Yêu Cầu Đã Được Duyệt", html);
            log.info("✅ Sent withdrawal approved email to {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Failed to send withdrawal approved email: {}", e.getMessage());
        }
    }

    /**
     * Send withdrawal rejected notification (to user)
     */
    @Async
    public void sendWithdrawalRejectedEmail(User user, WithdrawalRequest request) {
        try {
            String userName = (user.getFirstName() != null ? user.getFirstName() : user.getEmail());
            String html = buildWithdrawalRejectedHtml(
                    userName,
                    request.getRequestCode(),
                    formatCurrency(request.getAmount()),
                    request.getRejectionReason());
            sendHtmlEmail(user.getEmail(), "❌ Yêu Cầu Bị Từ Chối", html);
            log.info("✅ Sent withdrawal rejected email to {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Failed to send withdrawal rejected email: {}", e.getMessage());
        }
    }

    /**
     * Send withdrawal completed notification (to user)
     */
    @Async
    public void sendWithdrawalCompletedEmail(User user, WithdrawalRequest request) {
        try {
            String userName = (user.getFirstName() != null ? user.getFirstName() : user.getEmail());
            String html = buildWithdrawalCompletedHtml(
                    userName,
                    request.getRequestCode(),
                    formatCurrency(request.getNetAmount()),
                    request.getBankName(),
                    maskAccountNumber(request.getBankAccountNumber()),
                    request.getBankTransactionId() != null ? request.getBankTransactionId() : "Đang cập nhật");
            sendHtmlEmail(user.getEmail(), "🎉 Rút Tiền Hoàn Tất", html);
            log.info("✅ Sent withdrawal completed email to {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Failed to send withdrawal completed email: {}", e.getMessage());
        }
    }

    /**
     * Send new withdrawal request notification (to admin)
     */
    @Async
    public void sendAdminWithdrawalNotification(String adminEmail, User user, WithdrawalRequest request) {
        try {
            String userName = (user.getFirstName() != null && user.getLastName() != null)
                    ? user.getFirstName() + " " + user.getLastName()
                    : user.getEmail();
            String html = buildAdminWithdrawalNotificationHtml(
                    request.getRequestCode(),
                    userName,
                    user.getEmail(),
                    formatCurrency(request.getAmount()),
                    formatCurrency(request.getNetAmount()),
                    request.getBankName(),
                    request.getBankAccountNumber(),
                    request.getBankAccountName(),
                    getPriorityLabel(request.getPriority()));
            sendHtmlEmail(adminEmail, "🔔 Yêu Cầu Rút Tiền Mới", html);
            log.info("✅ Sent admin notification for withdrawal {}", request.getRequestCode());
        } catch (Exception e) {
            log.error("❌ Failed to send admin notification: {}", e.getMessage());
        }
    }

    /**
     * Send Admin Gift notification (to user)
     */
    @Async
    public void sendAdminGiftEmail(User user, BigDecimal cashAmount, Long coinAmount, String reason) {
        try {
            String userName = (user.getFirstName() != null ? user.getFirstName() : user.getEmail());
            String html = buildAdminGiftHtml(
                    userName,
                    cashAmount != null && cashAmount.compareTo(BigDecimal.ZERO) > 0 ? formatCurrency(cashAmount) : null,
                    coinAmount != null && coinAmount > 0 ? coinAmount + " Coins" : null,
                    reason != null && !reason.isEmpty() ? reason : "Quà tặng từ Admin");
            sendHtmlEmail(user.getEmail(), "🎁 Bạn Nhận Được Quà Tặng Từ SkillVerse", html);
            log.info("✅ Sent admin gift email to {}", user.getEmail());
        } catch (Exception e) {
            log.error("❌ Failed to send admin gift email: {}", e.getMessage());
        }
    }

    // ==================== HTML EMAIL BUILDERS ====================

    private String buildAdminGiftHtml(String userName, String cashAmount, String coinAmount, String reason) {
        StringBuilder giftContent = new StringBuilder();
        if (cashAmount != null) {
            giftContent.append(String.format("<div class=\"gift-item cash\">💰 %s</div>", cashAmount));
        }
        if (coinAmount != null) {
            giftContent.append(String.format("<div class=\"gift-item coin\">🪙 %s</div>", coinAmount));
        }

        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <meta name="viewport" content="width=device-width, initial-scale=1.0">
                            <style>
                                body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f3f4f6; margin: 0; padding: 0; line-height: 1.6; }
                                .container { max-width: 600px; margin: 20px auto; background: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 25px rgba(0,0,0,0.05); }
                                
                                /* Header */
                                .header { background: linear-gradient(135deg, #8b5cf6 0%%, #3b82f6 100%%); padding: 40px 20px; text-align: center; color: white; }
                                .header-icon { font-size: 48px; margin-bottom: 10px; display: block; }
                                .header h1 { margin: 0; font-size: 24px; font-weight: 700; letter-spacing: 0.5px; }
                                
                                /* Content */
                                .content { padding: 40px 30px; color: #374151; }
                                .greeting { font-size: 18px; margin-bottom: 15px; }
                                .message { color: #6b7280; font-size: 16px; margin-bottom: 30px; }
                                
                                /* Gift Card */
                                .gift-card { background: #fdf2f8; border: 2px dashed #ec4899; border-radius: 16px; padding: 25px; text-align: center; margin: 0 auto 30px; position: relative; }
                                .gift-title { color: #db2777; font-size: 14px; text-transform: uppercase; font-weight: 700; letter-spacing: 1px; margin-bottom: 15px; }
                                .gift-items { display: flex; flex-direction: column; gap: 10px; align-items: center; }
                                .gift-item { font-size: 28px; font-weight: 800; color: #111827; display: flex; align-items: center; gap: 10px; }
                                .gift-item.cash { color: #059669; }
                                .gift-item.coin { color: #d97706; }
                                
                                /* Reason */
                                .reason-container { background-color: #f9fafb; border-left: 4px solid #8b5cf6; padding: 15px 20px; border-radius: 0 8px 8px 0; margin-bottom: 30px; }
                                .reason-label { font-size: 12px; color: #6b7280; text-transform: uppercase; font-weight: 600; display: block; margin-bottom: 5px; }
                                .reason-text { color: #1f2937; font-style: italic; font-weight: 500; }
                                
                                /* Button */
                                .btn-container { text-align: center; margin-top: 10px; }
                                .btn { display: inline-block; background: linear-gradient(135deg, #8b5cf6 0%%, #6366f1 100%%); color: white; text-decoration: none; padding: 16px 40px; border-radius: 50px; font-weight: 600; font-size: 16px; box-shadow: 0 4px 15px rgba(99, 102, 241, 0.3); transition: all 0.3s ease; }
                                .btn:hover { transform: translateY(-2px); box-shadow: 0 8px 20px rgba(99, 102, 241, 0.4); }
                                
                                /* Footer */
                                .footer { background-color: #f9fafb; padding: 25px; text-align: center; border-top: 1px solid #e5e7eb; }
                                .footer p { margin: 5px 0; font-size: 13px; color: #9ca3af; }
                                .social-links { margin-top: 15px; }
                                .social-link { color: #6b7280; text-decoration: none; margin: 0 10px; font-size: 12px; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <span class="header-icon">🎁</span>
                                    <h1>Bạn Nhận Được Quà Tặng Mới!</h1>
                                </div>
                                
                                <div class="content">
                                    <p class="greeting">Xin chào <strong>%s</strong>,</p>
                                    <p class="message">SkillVerse xin trân trọng gửi tặng bạn một phần quà đặc biệt. Hy vọng món quà này sẽ tiếp thêm động lực cho hành trình học tập và phát triển của bạn!</p>
                                    
                                    <div class="gift-card">
                                        <div class="gift-title">Nội Dung Quà Tặng</div>
                                        <div class="gift-items">
                                            %s
                                        </div>
                                    </div>
                                    
                                    <div class="reason-container">
                                        <span class="reason-label">Lời nhắn:</span>
                                        <span class="reason-text">"%s"</span>
                                    </div>
                                    
                                    <div class="btn-container">
                                        <a href="https://skillverse.vn/my-wallet" class="btn">Kiểm Tra Ví Ngay</a>
                                    </div>
                                </div>
                                
                                <div class="footer">
                                    <p>© 2025 SkillVerse. All rights reserved.</p>
                                    <p>Lô E2a-7, Đường D1, Đ. D1, Long Thạnh Mỹ, Thành Phố Thủ Đức, TP.HCM</p>
                                    <div class="social-links">
                                        <a href="https://skillverse.vn" class="social-link">Website</a> • 
                                        <a href="#" class="social-link">Điều khoản</a> • 
                                        <a href="#" class="social-link">Hỗ trợ</a>
                                    </div>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                userName, giftContent.toString(), reason);
    }

    private String buildDepositSuccessHtml(String userName, String amount, String transactionId,
            String currentBalance) {
        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <style>
                                body { font-family: 'Inter', 'Roboto', 'Arial', sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }
                                .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                                .header { background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 30px; text-align: center; color: white; }
                                .header h1 { margin: 0; font-size: 28px; }
                                .content { padding: 30px; }
                                .amount { font-size: 36px; color: #10b981; font-weight: bold; text-align: center; margin: 20px 0; }
                                .info-box { background: #f9fafb; border-left: 4px solid #667eea; padding: 15px; margin: 20px 0; border-radius: 4px; }
                                .info-label { font-weight: 600; color: #374151; margin-bottom: 5px; }
                                .info-value { color: #6b7280; }
                                .button { display: inline-block; background: #667eea; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px; margin: 20px 0; }
                                .footer { background: #f9fafb; padding: 20px; text-align: center; color: #6b7280; font-size: 14px; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <h1>✅ Nạp Tiền Thành Công</h1>
                                </div>
                                <div class="content">
                                    <p>Xin chào <strong>%s</strong>,</p>
                                    <p>Giao dịch nạp tiền của bạn đã được xử lý thành công!</p>

                                    <div class="amount">+ %s</div>

                                    <div class="info-box">
                                        <div class="info-label">Mã giao dịch:</div>
                                        <div class="info-value">%s</div>
                                    </div>

                                    <div class="info-box">
                                        <div class="info-label">Số dư hiện tại:</div>
                                        <div class="info-value">%s</div>
                                    </div>

                                    <p style="text-align: center;">
                                        <a href="http://localhost:5173/my-wallet" class="button">Xem Ví Của Tôi</a>
                                    </p>

                                    <p style="color: #6b7280; font-size: 14px; margin-top: 30px;">
                                        💡 <strong>Mẹo:</strong> Bạn có thể sử dụng số dư này để mua SkillCoin hoặc đăng ký các khóa học premium!
                                    </p>
                                </div>
                                <div class="footer">
                                    <p>Email này được gửi tự động từ SkillVerse</p>
                                    <p>Nếu bạn có bất kỳ câu hỏi nào, vui lòng liên hệ support@skillverse.vn</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                userName, amount, transactionId, currentBalance);
    }

    private String buildCoinPurchaseHtml(String userName, String totalCoins, String bonusBadge, String paidAmount,
            String paymentMethod) {
        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <style>
                                body { font-family: 'Inter', 'Roboto', 'Arial', sans-serif; background-color: #f5f5f5; margin: 0; padding: 20px; }
                                .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                                .header { background: linear-gradient(135deg, #f59e0b 0%%, #d97706 100%%); padding: 30px; text-align: center; color: white; }
                                .header h1 { margin: 0; font-size: 28px; }
                                .content { padding: 30px; }
                                .coin-amount { font-size: 48px; text-align: center; margin: 20px 0; }
                                .coin-icon { color: #f59e0b; }
                                .info-box { background: #fef3c7; border-left: 4px solid #f59e0b; padding: 15px; margin: 20px 0; border-radius: 4px; }
                                .bonus-badge { background: #10b981; color: white; padding: 4px 12px; border-radius: 12px; font-size: 14px; font-weight: bold; display: inline-block; margin-left: 10px; }
                                .footer { background: #f9fafb; padding: 20px; text-align: center; color: #6b7280; font-size: 14px; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <h1>🪙 Mua SkillCoin Thành Công</h1>
                                </div>
                                <div class="content">
                                    <p>Xin chào <strong>%s</strong>,</p>
                                    <p>Bạn đã mua SkillCoin thành công!</p>

                                    <div class="coin-amount">
                                        <span class="coin-icon">🪙</span> %s Coins
                                        %s
                                    </div>

                                    <div class="info-box">
                                        <p><strong>Thông tin thanh toán:</strong></p>
                                        <p>Số tiền: <strong>%s</strong></p>
                                        <p>Phương thức: <strong>%s</strong></p>
                                    </div>

                                    <p style="text-align: center; margin-top: 30px;">
                                        <a href="http://localhost:5173/my-wallet" style="display: inline-block; background: #f59e0b; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px;">
                                            Sử Dụng SkillCoin
                                        </a>
                                    </p>
                                </div>
                                <div class="footer">
                                    <p>Cảm ơn bạn đã tin tưởng SkillVerse! 🚀</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                userName, totalCoins, bonusBadge, paidAmount, paymentMethod);
    }

    private String buildWithdrawalRequestHtml(String userName, String requestCode, String amount, String fee,
            String netAmount, String bankName, String accountNumber, String createdAt) {
        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="UTF-8">
                        <style>
                            body { font-family: 'Inter', 'Roboto', 'Arial', sans-serif; background: #f5f5f5; margin: 0; padding: 20px; }
                            .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                            .header { background: linear-gradient(135deg, #3b82f6, #2563eb); padding: 30px; text-align: center; color: white; }
                            .content { padding: 30px; }
                            .status-badge { background: #fbbf24; color: #78350f; padding: 6px 16px; border-radius: 20px; font-weight: bold; display: inline-block; margin: 15px 0; }
                            .info-box { background: #eff6ff; border-left: 4px solid #3b82f6; padding: 15px; margin: 15px 0; border-radius: 4px; }
                        </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header"><h1>💸 Yêu Cầu Rút Tiền</h1></div>
                                <div class="content">
                                    <p>Xin chào <strong>%s</strong>,</p>
                                    <p>Yêu cầu rút tiền của bạn đã được tạo thành công!</p>
                                    <span class="status-badge">⏳ Đang chờ duyệt</span>
                                    <div class="info-box">
                                        <p><strong>Mã yêu cầu:</strong> %s</p>
                                        <p><strong>Số tiền rút:</strong> %s</p>
                                        <p><strong>Phí giao dịch:</strong> %s</p>
                                        <p><strong>Số tiền nhận:</strong> <strong style="color: #10b981;">%s</strong></p>
                                        <p><strong>Ngân hàng:</strong> %s</p>
                                        <p><strong>Số tài khoản:</strong> %s</p>
                                        <p><strong>Thời gian:</strong> %s</p>
                                    </div>
                                    <p style="color: #6b7280; font-size: 14px;">⏰ Yêu cầu sẽ được xử lý trong vòng 24-48 giờ</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                userName, requestCode, amount, fee, netAmount, bankName, accountNumber, createdAt);
    }

    private String buildWithdrawalApprovedHtml(String userName, String requestCode, String netAmount, String bankName,
            String accountNumber, String adminNotes) {
        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="UTF-8">
                        <style>
                            body { font-family: 'Inter', 'Roboto', 'Arial', sans-serif; background: #f5f5f5; margin: 0; padding: 20px; }
                            .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                            .header { background: linear-gradient(135deg, #10b981, #059669); padding: 30px; text-align: center; color: white; }
                            .content { padding: 30px; }
                            .status-badge { background: #10b981; color: white; padding: 6px 16px; border-radius: 20px; font-weight: bold; display: inline-block; }
                            .info-box { background: #f0fdf4; border-left: 4px solid #10b981; padding: 15px; margin: 15px 0; border-radius: 4px; }
                        </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header"><h1>✅ Yêu Cầu Đã Được Duyệt</h1></div>
                                <div class="content">
                                    <p>Xin chào <strong>%s</strong>,</p>
                                    <p>Yêu cầu rút tiền <strong>%s</strong> đã được quản trị viên phê duyệt!</p>
                                    <span class="status-badge">✅ Đã duyệt</span>
                                    <div class="info-box">
                                        <p><strong>Số tiền nhận:</strong> <strong style="color: #10b981; font-size: 24px;">%s</strong></p>
                                        <p><strong>Chuyển đến:</strong> %s - %s</p>
                                        <p><strong>Ghi chú:</strong> %s</p>
                                    </div>
                                    <p style="color: #6b7280;">💰 Tiền sẽ được chuyển vào tài khoản của bạn trong 1-3 ngày làm việc</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                userName, requestCode, netAmount, bankName, accountNumber, adminNotes);
    }

    private String buildWithdrawalRejectedHtml(String userName, String requestCode, String amount, String reason) {
        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="UTF-8">
                        <style>
                            body { font-family: 'Inter', 'Roboto', 'Arial', sans-serif; background: #f5f5f5; margin: 0; padding: 20px; }
                            .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                            .header { background: linear-gradient(135deg, #ef4444, #dc2626); padding: 30px; text-align: center; color: white; }
                            .content { padding: 30px; }
                            .status-badge { background: #ef4444; color: white; padding: 6px 16px; border-radius: 20px; font-weight: bold; display: inline-block; }
                            .info-box { background: #fef2f2; border-left: 4px solid #ef4444; padding: 15px; margin: 15px 0; border-radius: 4px; }
                        </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header"><h1>❌ Yêu Cầu Bị Từ Chối</h1></div>
                                <div class="content">
                                    <p>Xin chào <strong>%s</strong>,</p>
                                    <p>Rất tiếc, yêu cầu rút tiền <strong>%s</strong> của bạn đã bị từ chối.</p>
                                    <span class="status-badge">❌ Từ chối</span>
                                    <div class="info-box">
                                        <p><strong>Số tiền:</strong> %s</p>
                                        <p><strong>Lý do từ chối:</strong> %s</p>
                                    </div>
                                    <p style="color: #6b7280;">💡 Số tiền đã được hoàn trả vào ví của bạn. Vui lòng kiểm tra lại thông tin và tạo yêu cầu mới nếu cần.</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                userName, requestCode, amount, reason);
    }

    private String buildWithdrawalCompletedHtml(String userName, String requestCode, String netAmount, String bankName,
            String accountNumber, String bankTxId) {
        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="UTF-8">
                        <style>
                            body { font-family: 'Inter', 'Roboto', 'Arial', sans-serif; background: #f5f5f5; margin: 0; padding: 20px; }
                            .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                            .header { background: linear-gradient(135deg, #8b5cf6, #7c3aed); padding: 30px; text-align: center; color: white; }
                            .content { padding: 30px; }
                            .success-icon { font-size: 64px; text-align: center; margin: 20px 0; }
                            .info-box { background: #f5f3ff; border-left: 4px solid #8b5cf6; padding: 15px; margin: 15px 0; border-radius: 4px; }
                        </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header"><h1>🎉 Rút Tiền Hoàn Tất</h1></div>
                                <div class="content">
                                    <div class="success-icon">✅</div>
                                    <p style="text-align: center; font-size: 18px;">Xin chào <strong>%s</strong>,</p>
                                    <p style="text-align: center;">Giao dịch rút tiền <strong>%s</strong> đã hoàn tất!</p>
                                    <div class="info-box">
                                        <p><strong>Số tiền:</strong> <strong style="color: #8b5cf6; font-size: 24px;">%s</strong></p>
                                        <p><strong>Chuyển đến:</strong> %s - %s</p>
                                        <p><strong>Mã giao dịch ngân hàng:</strong> <code>%s</code></p>
                                    </div>
                                    <p style="color: #6b7280; text-align: center;">🎊 Tiền đã được chuyển vào tài khoản của bạn. Vui lòng kiểm tra!</p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                userName, requestCode, netAmount, bankName, accountNumber, bankTxId);
    }

    private String buildAdminWithdrawalNotificationHtml(String requestCode, String userName, String userEmail,
            String amount, String netAmount, String bankName, String accountNumber, String accountName,
            String priority) {
        return String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head><meta charset="UTF-8">
                        <style>
                            body { font-family: 'Inter', 'Roboto', 'Arial', sans-serif; background: #f5f5f5; margin: 0; padding: 20px; }
                            .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
                            .header { background: linear-gradient(135deg, #f59e0b, #d97706); padding: 30px; text-align: center; color: white; }
                            .content { padding: 30px; }
                            .priority-badge { background: #ef4444; color: white; padding: 6px 16px; border-radius: 20px; font-weight: bold; display: inline-block; }
                            .info-box { background: #fef3c7; border-left: 4px solid #f59e0b; padding: 15px; margin: 15px 0; border-radius: 4px; }
                        </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header"><h1>🔔 Yêu Cầu Rút Tiền Mới</h1></div>
                                <div class="content">
                                    <p><strong>Admin</strong>,</p>
                                    <p>Có yêu cầu rút tiền mới cần duyệt!</p>
                                    <span class="priority-badge">%s</span>
                                    <div class="info-box">
                                        <p><strong>Mã yêu cầu:</strong> %s</p>
                                        <p><strong>User:</strong> %s (%s)</p>
                                        <p><strong>Số tiền rút:</strong> %s</p>
                                        <p><strong>Số tiền nhận:</strong> <strong>%s</strong></p>
                                        <hr>
                                        <p><strong>Ngân hàng:</strong> %s</p>
                                        <p><strong>Số TK:</strong> %s</p>
                                        <p><strong>Chủ TK:</strong> %s</p>
                                    </div>
                                    <p style="text-align: center;">
                                        <a href="http://localhost:8080/swagger-ui.html" style="display: inline-block; background: #f59e0b; color: white; padding: 12px 30px; text-decoration: none; border-radius: 6px;">
                                            Xem Chi Tiết & Duyệt
                                        </a>
                                    </p>
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                priority, requestCode, userName, userEmail, amount, netAmount, bankName, accountNumber, accountName);
    }

    // ==================== HELPER METHODS ====================

    private void sendHtmlEmail(String to, String subject, String htmlContent) {
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
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }

    private String formatCurrency(BigDecimal amount) {
        return VND_FORMAT.format(amount);
    }

    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return accountNumber;
        }
        int visibleDigits = 4;
        String masked = "*".repeat(accountNumber.length() - visibleDigits);
        return masked + accountNumber.substring(accountNumber.length() - visibleDigits);
    }

    private String getPriorityLabel(Integer priority) {
        if (priority == null)
            return "🔵 Thường";
        return switch (priority) {
            case 3 -> "🔴 Cao";
            case 2 -> "🟠 Trung bình";
            default -> "🔵 Thường";
        };
    }
}
