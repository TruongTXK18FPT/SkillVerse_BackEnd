package com.exe.skillverse_backend.business_service.entity.enums;

/**
 * Nguồn tạo phiên chat tuyển dụng
 */
public enum RecruitmentSessionSource {
    /**
     * Recruiter tìm kiếm thủ công
     */
    MANUAL,

    /**
     * Từ AI Candidate Search
     */
    AI_SEARCH,

    /**
     * Từ tính năng gợi ý của hệ thống
     */
    RECOMMENDATION,

    /**
     * Recruiter xem profile candidate và chủ động liên hệ
     */
    PROFILE_VIEW,

    /**
     * Từ shortlist
     */
    SHORTLIST
}
