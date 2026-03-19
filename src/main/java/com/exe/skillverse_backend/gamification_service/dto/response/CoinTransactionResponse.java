package com.exe.skillverse_backend.gamification_service.dto.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for coin transaction history
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoinTransactionResponse {

    private Long transactionId;
    private Long userId;
    private Integer coinAmount;
    private Integer xpAmount;
    private String transactionType;
    private String sourceType;
    private Long sourceId;
    private String description;
    private Boolean isVerified;
    private Integer balanceAfter;
    private LocalDateTime transactionDate;
}
