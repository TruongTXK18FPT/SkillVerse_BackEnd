package com.exe.skillverse_backend.gamification_service.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for gamification dashboard summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GamificationDashboardResponse {

    private UserWalletResponse wallet;
    private Integer totalBadges;
    private Integer unlockedBadges;
    private Integer currentRank;
    private List<UserBadgeResponse> recentBadges;
    private List<CoinTransactionResponse> recentTransactions;
    private List<MiniGameDefinitionResponse> availableGames;
}
