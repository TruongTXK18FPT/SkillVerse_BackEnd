package com.exe.skillverse_backend.business_service.entity.enums;

/**
 * Status workflow cho Short-term Job
 * Flow: DRAFT → PENDING_APPROVAL → PUBLISHED → APPLIED → IN_PROGRESS → SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED → COMPLETED → PAID
 */
public enum ShortTermJobStatus {
    DRAFT,              // Recruiter đang soạn
    PENDING_APPROVAL,   // Đã gửi duyệt, chờ admin phê duyệt
    PUBLISHED,          // Đã đăng, chờ ứng viên apply
    APPLIED,         // Có ứng viên đã apply
    IN_PROGRESS,     // Ứng viên đang làm việc
    SUBMITTED,       // Ứng viên đã nộp bài (bàn giao)
    UNDER_REVIEW,    // Recruiter đang review
    APPROVED,        // Công việc được approve
    REJECTED,        // Công việc bị reject, cần làm lại
    COMPLETED,       // Hoàn thành
    PAID,            // Đã thanh toán
    CANCELLED,       // Đã hủy
    DISPUTED,        // Đang tranh chấp
    CLOSED           // Đã đóng (recruiter chủ động đóng job)
}
