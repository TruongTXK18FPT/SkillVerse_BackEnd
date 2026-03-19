package com.exe.skillverse_backend.business_service.dto.request;

import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request để update status của short-term application
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShortTermApplicationStatusRequest {

    @NotNull(message = "Status is required")
    private ShortTermApplicationStatus status;

    @Size(max = 2000, message = "Message must not exceed 2000 characters")
    private String message; // Optional message (e.g., acceptance message, rejection reason)

    @Size(max = 2000, message = "Reason must not exceed 2000 characters")
    private String reason; // Reason for status change (for audit log)
}
