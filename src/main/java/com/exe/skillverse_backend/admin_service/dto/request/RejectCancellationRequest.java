package com.exe.skillverse_backend.admin_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * [Nghiệp vụ] Admin từ chối yêu cầu hủy job từ recruiter.
 * Khi admin từ chối, job quay về IN_PROGRESS và worker có thể tiếp tục làm việc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectCancellationRequest {
    private String reason; // Lý do admin từ chối (sẽ gửi notification cho cả hai bên)
}