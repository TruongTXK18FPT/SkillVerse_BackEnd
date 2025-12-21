package com.exe.skillverse_backend.wallet_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminGiftRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @PositiveOrZero(message = "Cash amount must be positive or zero")
    private BigDecimal cashAmount = BigDecimal.ZERO;

    @PositiveOrZero(message = "Coin amount must be positive or zero")
    private Long coinAmount = 0L;

    private String reason;
}
