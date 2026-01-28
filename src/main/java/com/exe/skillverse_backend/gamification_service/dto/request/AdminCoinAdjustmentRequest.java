package com.exe.skillverse_backend.gamification_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin request DTO for manual coin adjustment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCoinAdjustmentRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Coin amount is required")
    private Integer coinAmount; // Can be positive or negative

    private Integer xpAmount;

    @NotNull(message = "Reason is required")
    private String reason;

    private String metadata; // JSON
}
