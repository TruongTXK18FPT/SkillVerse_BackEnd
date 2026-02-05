package com.exe.skillverse_backend.premium_service.constants;

import java.math.BigDecimal;
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
     * Days before expiry to process auto-renewal
     */
    public static final int AUTO_RENEWAL_WINDOW_DAYS = 3;
    
    /**
     * Batch size for processing subscriptions in scheduler
     */
    public static final int BATCH_SIZE = 500;

    // ==================== Refund Policy ====================
    /**
     * Hours within which 100% refund is available
     */
    public static final int FULL_REFUND_HOURS = 24;
    
    /**
     * Days within which 50% refund is available
     */
    public static final int PARTIAL_REFUND_DAYS = 3;
    
    /**
     * Refund percentage for partial refund window
     */
    public static final int PARTIAL_REFUND_PERCENTAGE = 50;
    
    /**
     * Maximum cancellations allowed per month
     */
    public static final int MAX_CANCELLATIONS_PER_MONTH = 1;

    // ==================== Student Discount ====================
    /**
     * Student discount multiplier (0.8 = 20% off)
     */
    public static final BigDecimal STUDENT_DISCOUNT_MULTIPLIER = new BigDecimal("0.80");
    
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

    // Prevent instantiation
    private PremiumConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
