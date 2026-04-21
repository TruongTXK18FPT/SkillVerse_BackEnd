package com.exe.skillverse_backend.mentor_verification_service.entity;

/**
 * [Nghiệp vụ] Trạng thái của một yêu cầu xác thực skill mentor.
 * PENDING: chờ admin duyệt
 * APPROVED: admin đã duyệt, mentor được công nhận skill
 * REJECTED: admin từ chối, mentor cần bổ sung bằng chứng
 */
public enum VerificationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
