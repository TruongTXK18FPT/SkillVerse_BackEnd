package com.exe.skillverse_backend.gamification_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for user wallet information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserWalletResponse {

    private Long walletId;
    private Long userId;
    private Integer totalCoins;
    private Integer earnedCoins;
    private Integer spentCoins;
    private Integer totalXp;
    private Integer streakDays;
    private LocalDateTime lastActivityDate;
    private LocalDateTime updatedAt;
}
