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
    PRIORITY_SUPPORT("Priority Support", "Hỗ trợ ưu tiên"),

    // ==================== Recruiter Services ====================

    /**
     * Monthly job posting limit for recruiters (Full-time jobs)
     * Tracks number of full-time job postings per month
     */
    JOB_POSTING_MONTHLY("Job Posting (Monthly)", "Số lượng tin tuyển dụng dài hạn mỗi tháng"),

    /**
     * Monthly short-term job/gig posting limit for recruiters
     * Tracks number of short-term job/gig postings per month
     */
    SHORT_TERM_JOB_POSTING("Short-term Job Posting", "Số lượng tin công việc ngắn hạn/gig mỗi tháng"),

    /**
     * Highlight job post
     * Boolean feature - recruiter can highlight/feature their job posts
     */
    HIGHLIGHT_JOB_POST("Highlight Job Post", "Đánh dấu nổi bật tin tuyển dụng"),

    /**
     * AI Candidate Suggestion
     * Boolean feature - AI suggests matching candidates for job posts
     */
    AI_CANDIDATE_SUGGESTION("AI Candidate Suggestion", "AI gợi ý ứng viên phù hợp"),

    /**
     * Premium Company Profile
     * Boolean feature - Access to enhanced company profile with logo, banner, video
     */
    COMPANY_PROFILE_PREMIUM("Premium Company Profile", "Hồ sơ công ty nâng cao với logo, banner, video"),

    /**
     * Analytics Dashboard
     * Boolean feature - Access to detailed recruitment analytics
     */
    ANALYTICS_DASHBOARD("Analytics Dashboard", "Bảng phân tích chi tiết tuyển dụng"),

    /**
     * Candidate Database Access
     * Boolean feature - Access to search and view candidate profiles
     */
    CANDIDATE_DATABASE_ACCESS("Candidate Database Access", "Truy cập cơ sở dữ liệu ứng viên"),

    /**
     * Job Boost
     * Number of job boosts per month - push job to top of listings
     */
    JOB_BOOST_MONTHLY("Job Boost (Monthly)", "Đẩy tin tuyển dụng lên đầu danh sách"),

    /**
     * Automated Outreach
     * Boolean feature - Send automated messages to matching candidates
     */
    AUTOMATED_OUTREACH("Automated Outreach", "Tự động tiếp cận ứng viên phù hợp"),

    /**
     * Bulk Import Candidates
     * Number of candidate imports per month
     */
    BULK_IMPORT_CANDIDATES("Bulk Import Candidates", "Nhập khẩu hàng loạt ứng viên"),

    /**
     * API Access
     * Boolean feature - Access to REST API for integration
     */
    API_ACCESS("API Access", "Truy cập API để tích hợp hệ thống"),

    /**
     * Priority Support
     * Boolean feature - Priority support access
     */
    RECRUITER_PRIORITY_SUPPORT("Recruiter Priority Support", "Hỗ trợ ưu tiên dành riêng cho recruiter");

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
        return this == PRIORITY_SUPPORT || this == RECRUITER_PRIORITY_SUPPORT
            || this == HIGHLIGHT_JOB_POST || this == AI_CANDIDATE_SUGGESTION
            || this == COMPANY_PROFILE_PREMIUM || this == ANALYTICS_DASHBOARD
            || this == CANDIDATE_DATABASE_ACCESS || this == AUTOMATED_OUTREACH
            || this == API_ACCESS;
    }

    /**
     * Check if this feature type uses bonus multiplier instead of count limit
     */
    public boolean isMultiplierFeature() {
        return this == COIN_EARNING_MULTIPLIER;
    }
}
