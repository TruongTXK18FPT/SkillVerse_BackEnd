package com.exe.skillverse_backend.business_service.entity.enums;

/**
 * Status cho application của ứng viên trong Short-term Job
 */
public enum ShortTermApplicationStatus {
    PENDING,           // Chờ duyệt
    ACCEPTED,          // Được chọn làm
    REJECTED,          // Không được chọn
    WORKING,           // Đang làm việc
    SUBMITTED,         // Đã nộp deliverables
    REVISION_REQUIRED, // Cần sửa lại
    APPROVED,          // Công việc được approve
    COMPLETED,         // Hoàn thành
    CANCELLED,         // Bị hủy
    WITHDRAWN          // Rút đơn
}
