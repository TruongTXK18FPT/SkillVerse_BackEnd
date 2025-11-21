package com.exe.skillverse_backend.premium_service.entity;

/**
 * Enum defining all feature types that can be limited by premium plans
 * Each feature type represents a service or capability that can be tracked and
 * limited
 * 
 * CLEANED UP: Removed 7 unused features (2024-11-21)
 * - MENTOR_SESSION_DURATION
 * - COURSE_ENROLLMENT_LIMIT
 * - PORTFOLIO_PROJECTS_LIMIT
 * - AD_FREE_EXPERIENCE
 * - CUSTOM_FEATURE_1/2/3
 */
public enum FeatureType {

    // ==================== AI Services ====================

    /**
     * AI Chatbot career counseling requests
     * Tracks number of chat messages sent to AI career advisor
     */
    AI_CHATBOT_REQUESTS("AI Chatbot Requests", "Số lượng request chat với AI career advisor"),

    /**
     * AI Roadmap generation
     * Tracks number of personalized learning roadmaps generated
     */
    AI_ROADMAP_GENERATION("AI Roadmap Generation", "Số lần tạo roadmap học tập cá nhân hóa"),

    // ==================== Mentor Services ====================

    /**
     * Mentor booking limit per month
     * Tracks number of mentor sessions booked in current month
     */
    MENTOR_BOOKING_MONTHLY("Mentor Booking (Monthly)", "Số lần đặt lịch mentor mỗi tháng"),

    // ==================== Bonus Features ====================

    /**
     * Coin earning multiplier
     * Multiplier applied to all coin earnings (1.0 = normal, 2.0 = double)
     * This is stored as bonusMultiplier, not a count limit
     */
    COIN_EARNING_MULTIPLIER("Coin Earning Multiplier", "Hệ số nhân xu kiếm được"),

    /**
     * Priority support access
     * Boolean feature - user has access to priority support
     */
    PRIORITY_SUPPORT("Priority Support", "Hỗ trợ ưu tiên");

    private final String displayName;
    private final String displayNameVi;

    FeatureType(String displayName, String displayNameVi) {
        this.displayName = displayName;
        this.displayNameVi = displayNameVi;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDisplayNameVi() {
        return displayNameVi;
    }

    /**
     * Check if this feature type is a boolean feature (on/off) rather than a count
     * limit
     */
    public boolean isBooleanFeature() {
        return this == PRIORITY_SUPPORT;
    }

    /**
     * Check if this feature type uses bonus multiplier instead of count limit
     */
    public boolean isMultiplierFeature() {
        return this == COIN_EARNING_MULTIPLIER;
    }
}
