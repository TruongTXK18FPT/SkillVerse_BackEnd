package com.exe.skillverse_backend.business_service.entity.enums;

/**
 * Trạng thái phiên chat tuyển dụng
 * Phản ánh pipeline recruitment từ lúc recruiter tiếp cận candidate
 */
public enum RecruitmentSessionStatus {
    /**
     * Recruiter đã tiếp cận/liên hệ candidate
     */
    CONTACTED,

    /**
     * Candidate đã phản hồi và quan tâm
     */
    INTERESTED,

    /**
     * Recruiter đã gửi lời mời ứng tuyển (có thể có job cụ thể)
     */
    INVITED,

    /**
     * Candidate đã apply vào job của recruiter (có thể từ invite hoặc tự apply)
     */
    APPLICATION_RECEIVED,

    /**
     * Candidate đang trong quá trình screening/interview
     */
    SCREENING,

    /**
     * Offer đã được gửi cho candidate
     */
    OFFER_SENT,

    /**
     * Candidate đã accept offer
     */
    HIRED,

    /**
     * Candidate từ chối hoặc không quan tâm
     */
    NOT_INTERESTED,

    /**
     * Session chat đã bị ẩn/archived
     */
    ARCHIVED
}
