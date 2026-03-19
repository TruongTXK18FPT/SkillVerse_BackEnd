package com.exe.skillverse_backend.business_service.dto.request;

import com.exe.skillverse_backend.business_service.entity.enums.RecruitmentSessionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO để cập nhật trạng thái recruitment session
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRecruitmentStatusRequest {

    @NotNull(message = "Status is required")
    private RecruitmentSessionStatus status;

    /**
     * Ghi chú thêm (optional)
     */
    private String note;
}
