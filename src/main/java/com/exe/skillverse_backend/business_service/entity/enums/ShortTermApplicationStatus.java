package com.exe.skillverse_backend.business_service.entity.enums;

/**
 * Status cho application của ứng viên trong Short-term Job
 */
public enum ShortTermApplicationStatus {
    PENDING,                // Chờ duyệt
    ACCEPTED,               // Được chọn làm
    REJECTED,               // Không được chọn
    WORKING,                // Đang làm việc
    SUBMITTED,              // Đã nộp deliverables
    SUBMITTED_OVERDUE,      // Đã nộp, recruiter quá hạn review 48h
    REVISION_REQUIRED,      // Cần sửa lại
    REVISION_RESPONSE_OVERDUE, // User quá hạn phản hồi revision
    CANCELLATION_REQUESTED,  // Recruiter yêu cầu hủy (≥5 revision)
    AUTO_CANCELLED,          // System auto-cancel (user không phản hồi 72h)
    APPROVED,               // Công việc được approve
    COMPLETED,              // Hoàn thành
    DISPUTE_OPENED,         // Đang dispute
    CANCELLED,              // Bị hủy
    WITHDRAWN               // Rút đơn
}
