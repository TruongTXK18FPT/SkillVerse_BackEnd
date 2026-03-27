package com.exe.skillverse_backend.premium_service.constants;

import java.util.List;

/**
 * Constants for Premium Subscription system.
 * Centralizes all magic numbers and configurable values.
 */
public final class PremiumConstants {

    // ==================== Subscription Duration ====================
    /**
     * Default subscription duration when upgrading via standard flow
     */
    public static final int DEFAULT_SUBSCRIPTION_MONTHS = 1;
    
    /**
     * FREE_TIER validity in years (effectively permanent)
     */
    public static final int FREE_TIER_VALIDITY_YEARS = 100;

    // ==================== Notifications & Scheduling ====================
    /**
     * Days before expiry to send notification
     */
    public static final int EXPIRY_NOTIFICATION_DAYS = 3;
    
    /**
     * Days before expiry to send low-balance reminder for auto-renewal
     */
    public static final int AUTO_RENEWAL_WINDOW_DAYS = 3;
    
    /**
     * Batch size for processing subscriptions in scheduler
     */
    public static final int BATCH_SIZE = 500;

    /**
     * Frequency for wallet auto-renewal attempts, in minutes.
     */
    public static final int AUTO_RENEWAL_INTERVAL_MINUTES = 1;

    // ==================== Refund Policy ====================
    /**
     * Hours within which 100% refund is available
     */
    public static final int FULL_REFUND_HOURS = 24;
    
    /**
     * Hours within which 50% refund is still available after the full-refund window
     */
    public static final int PARTIAL_REFUND_HOURS = 72;

    /**
     * Hours within which learner subscriptions can be upgraded immediately
     * using the grace-window fixed-delta pricing policy.
     */
    public static final int UPGRADE_GRACE_WINDOW_HOURS = 72;
    
    /**
     * Refund percentage for partial refund window
     */
    public static final int PARTIAL_REFUND_PERCENTAGE = 50;
    
    /**
     * Maximum cancellations allowed per month
     */
    public static final int MAX_CANCELLATIONS_PER_MONTH = 1;

    /**
     * Valid student email domain patterns
     */
    public static final List<String> STUDENT_EMAIL_DOMAINS = List.of(
            ".edu", ".edu.vn", ".ac.uk", "university.", "student.", ".edu.au",
            ".ac.jp", ".edu.br", ".edu.mx", ".edu.sg", ".edu.my", ".ac.nz"
    );

    // ==================== Error Messages (Vietnamese) ====================
    public static final String MSG_USER_NOT_FOUND = "Không tìm thấy người dùng";
    public static final String MSG_PLAN_NOT_FOUND = "Không tìm thấy gói Premium";
    public static final String MSG_SUBSCRIPTION_NOT_FOUND = "Không tìm thấy gói đăng ký";
    public static final String MSG_ALREADY_HAS_PREMIUM = "Người dùng đã có gói Premium đang hoạt động";
    public static final String MSG_INSUFFICIENT_BALANCE = "Số dư ví không đủ";
    public static final String MSG_FREE_TIER_NOT_CONFIGURED = "Gói miễn phí chưa được cấu hình. Vui lòng liên hệ hỗ trợ.";
    public static final String MSG_CANCELLATION_LIMIT = "Bạn đã hủy gói Premium trong tháng này. Chỉ được phép hủy 1 lần/tháng.";
    public static final String MSG_NO_ACTIVE_SUBSCRIPTION = "Bạn hiện không có gói Premium đang hoạt động.";
    public static final String MSG_FREE_TIER_NO_REFUND = "Gói miễn phí không hỗ trợ hoàn tiền.";
    public static final String MSG_FREE_TIER_NO_AUTO_RENEW = "Gói miễn phí không hỗ trợ gia hạn tự động.";
    public static final String MSG_AUTO_RENEW_ALREADY_ENABLED = "Gia hạn tự động đã được bật trước đó.";
    public static final String MSG_REFUND_FULL = "Bạn đủ điều kiện hoàn 100% trong 24 giờ đầu.";
    public static final String MSG_REFUND_PARTIAL = "Bạn đủ điều kiện hoàn 50% trong vòng 72 giờ kể từ khi mua gói.";
    public static final String MSG_REFUND_EXPIRED = "Đã quá 72 giờ. Bạn chỉ có thể hủy gia hạn tự động, không được hoàn tiền.";

    // Prevent instantiation
    private PremiumConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
