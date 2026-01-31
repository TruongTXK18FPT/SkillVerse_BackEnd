package com.exe.skillverse_backend.gamification_service.controller;

import com.exe.skillverse_backend.gamification_service.dto.request.*;
import com.exe.skillverse_backend.gamification_service.dto.response.*;
import com.exe.skillverse_backend.gamification_service.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/gamification")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Gamification", description = "Admin gamification management endpoints")
public class AdminGamificationController {

    private final GamificationBadgeService badgeService;
    private final GamificationMiniGameService miniGameService;
    private final GamificationAdminDashboardService dashboardService;
    private final GamificationLeaderboardService leaderboardService;

    // =============================================
    // DASHBOARD & STATISTICS
    // =============================================

    @GetMapping("/dashboard/stats")
    @Operation(summary = "Get comprehensive dashboard statistics")
    public ResponseEntity<AdminGamificationStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(dashboardService.getDashboardStats());
    }

    // =============================================
    // DATA SEEDING (TESTING ONLY)
    // =============================================

    @PostMapping("/seed/leaderboard")
    @Operation(summary = "Seed leaderboard data (For testing)")
    @PreAuthorize("permitAll()") // For testing purposes only
    public ResponseEntity<String> seedLeaderboardData() {
        try {
            // Seed Week
            leaderboardService.updateLeaderboardSnapshot("week", "coins");
            leaderboardService.updateLeaderboardSnapshot("week", "learning");
            leaderboardService.updateLeaderboardSnapshot("week", "community");
            leaderboardService.updateLeaderboardSnapshot("week", "skins");
            leaderboardService.updateLeaderboardSnapshot("week", "streak");
            
            // Seed Month
            leaderboardService.updateLeaderboardSnapshot("month", "coins");
            leaderboardService.updateLeaderboardSnapshot("month", "learning");
            leaderboardService.updateLeaderboardSnapshot("month", "community");
            leaderboardService.updateLeaderboardSnapshot("month", "skins");
            leaderboardService.updateLeaderboardSnapshot("month", "streak");
            
            // Seed All Time
            leaderboardService.updateLeaderboardSnapshot("all", "coins");
            leaderboardService.updateLeaderboardSnapshot("all", "learning");
            leaderboardService.updateLeaderboardSnapshot("all", "community");
            leaderboardService.updateLeaderboardSnapshot("all", "skins");
            leaderboardService.updateLeaderboardSnapshot("all", "streak");
            
            return ResponseEntity.ok("Leaderboard data seeded successfully");
        } catch (Exception e) {
            log.error("Error seeding leaderboard", e);
            return ResponseEntity.internalServerError().body("Error seeding leaderboard: " + e.getMessage());
        }
    }

    @GetMapping("/dashboard/trends")
    @Operation(summary = "Get activity trends for date range")
    public ResponseEntity<List<AdminGamificationStatsResponse.ActivityTrendData>> getActivityTrends(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(dashboardService.getActivityTrends(startDate, endDate));
    }

    @GetMapping("/dashboard/top-earners")
    @Operation(summary = "Get top coin earners")
    public ResponseEntity<List<AdminGamificationStatsResponse.UserActivitySummary>> getTopCoinEarners(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getTopCoinEarners(limit));
    }

    @GetMapping("/dashboard/active-users")
    @Operation(summary = "Get most active users")
    public ResponseEntity<List<AdminGamificationStatsResponse.UserActivitySummary>> getMostActiveUsers(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(dashboardService.getMostActiveUsers(limit));
    }

    @GetMapping("/dashboard/game-stats")
    @Operation(summary = "Get game statistics")
    public ResponseEntity<List<AdminGamificationStatsResponse.GameStatsSummary>> getGameStats() {
        return ResponseEntity.ok(dashboardService.getGameStats());
    }

    @GetMapping("/dashboard/badge-stats")
    @Operation(summary = "Get badge statistics")
    public ResponseEntity<List<AdminGamificationStatsResponse.BadgeStatsSummary>> getBadgeStats() {
        return ResponseEntity.ok(dashboardService.getBadgeStats());
    }

    // =============================================
    // USER ACTIVITY TRACKING
    // =============================================

    @GetMapping("/users/activities")
    @Operation(summary = "Get all user activities (paginated)")
    public ResponseEntity<Page<AdminGamificationStatsResponse.UserActivitySummary>> getAllUserActivities(
            Pageable pageable) {
        return ResponseEntity.ok(dashboardService.getAllUserActivities(pageable));
    }

    @GetMapping("/users/{userId}/activity")
    @Operation(summary = "Get user activity tracking with achievements")
    public ResponseEntity<UserActivityTrackingResponse> getUserActivityTracking(
            @PathVariable Long userId) {
        return ResponseEntity.ok(dashboardService.getUserActivityTracking(userId));
    }

    // =============================================
    // LEADERBOARD MANAGEMENT
    // =============================================

    @GetMapping("/leaderboard")
    @Operation(summary = "Get leaderboard with admin view")
    public ResponseEntity<LeaderboardResponse> getLeaderboard(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "coins") String type,
            Pageable pageable) {
        return ResponseEntity.ok(leaderboardService.getLeaderboard(period, type, null, pageable));
    }

    @GetMapping("/leaderboard/full")
    @Operation(summary = "Get full leaderboard data for admin dashboard")
    public ResponseEntity<java.util.Map<String, Object>> getFullLeaderboard(
            @RequestParam(defaultValue = "week") String period) {
        LeaderboardResponse coinLeaderboard = leaderboardService.getLeaderboard(period, "coins", null, Pageable.ofSize(100));
        LeaderboardResponse xpLeaderboard = leaderboardService.getLeaderboard(period, "xp", null, Pageable.ofSize(100));
        
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("topCoinEarners", coinLeaderboard.getTopEntries());
        response.put("topXpEarners", xpLeaderboard.getTopEntries());
        response.put("totalParticipants", coinLeaderboard.getTotalParticipants());
        response.put("period", period);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/leaderboard/refresh")
    @Operation(summary = "Refresh leaderboard snapshot")
    public ResponseEntity<Void> refreshLeaderboard() {
        // Refresh both coin and XP leaderboards for weekly period by default
        leaderboardService.updateLeaderboardSnapshot("WEEKLY", "COIN");
        leaderboardService.updateLeaderboardSnapshot("WEEKLY", "XP");
        return ResponseEntity.ok().build();
    }

    // =============================================
    // BADGE MANAGEMENT
    // =============================================

    @PostMapping("/badges")
    @Operation(summary = "Create badge definition")
    public ResponseEntity<BadgeDefinitionResponse> createBadge(
            @Valid @RequestBody BadgeDefinitionRequest request, Authentication auth) {
        Long adminId = getAdminIdFromAuth(auth);
        return ResponseEntity.ok(badgeService.createBadgeDefinition(request, adminId));
    }

    @PutMapping("/badges/{badgeDefId}")
    @Operation(summary = "Update badge definition")
    public ResponseEntity<BadgeDefinitionResponse> updateBadge(
            @PathVariable Long badgeDefId,
            @Valid @RequestBody BadgeDefinitionRequest request) {
        return ResponseEntity.ok(badgeService.updateBadgeDefinition(badgeDefId, request));
    }

    @DeleteMapping("/badges/{badgeDefId}")
    @Operation(summary = "Delete badge definition")
    public ResponseEntity<Void> deleteBadge(@PathVariable Long badgeDefId) {
        badgeService.deleteBadgeDefinition(badgeDefId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/badges")
    @Operation(summary = "Get all badge definitions")
    public ResponseEntity<List<BadgeDefinitionResponse>> getAllBadges() {
        return ResponseEntity.ok(badgeService.getAllBadgeDefinitions());
    }

    @PostMapping("/badges/award/{userId}/{badgeKey}")
    @Operation(summary = "Manually award badge to user")
    public ResponseEntity<UserBadgeResponse> awardBadge(
            @PathVariable Long userId,
            @PathVariable String badgeKey) {
        return ResponseEntity.ok(badgeService.awardBadge(userId, badgeKey));
    }

    @PostMapping("/badges/bulk-award/{badgeKey}")
    @Operation(summary = "Bulk award badge to all users meeting criteria")
    public ResponseEntity<Integer> bulkAwardBadge(@PathVariable String badgeKey) {
        return ResponseEntity.ok(dashboardService.bulkAwardBadges(badgeKey));
    }

    // =============================================
    // GAME MANAGEMENT
    // =============================================

    @PostMapping("/games")
    @Operation(summary = "Create mini-game definition")
    public ResponseEntity<MiniGameDefinitionResponse> createGame(
            @Valid @RequestBody MiniGameDefinitionRequest request, Authentication auth) {
        Long adminId = getAdminIdFromAuth(auth);
        return ResponseEntity.ok(miniGameService.createGameDefinition(request, adminId));
    }

    @PutMapping("/games/{gameDefId}")
    @Operation(summary = "Update mini-game definition")
    public ResponseEntity<MiniGameDefinitionResponse> updateGame(
            @PathVariable Long gameDefId,
            @Valid @RequestBody MiniGameDefinitionRequest request) {
        return ResponseEntity.ok(miniGameService.updateGameDefinition(gameDefId, request));
    }

    @DeleteMapping("/games/{gameDefId}")
    @Operation(summary = "Delete mini-game definition")
    public ResponseEntity<Void> deleteGame(@PathVariable Long gameDefId) {
        miniGameService.deleteGameDefinition(gameDefId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/games")
    @Operation(summary = "Get all mini-game definitions")
    public ResponseEntity<List<MiniGameDefinitionResponse>> getAllGames() {
        return ResponseEntity.ok(miniGameService.getAllGameDefinitions());
    }

    @PutMapping("/games/{gameDefId}/toggle")
    @Operation(summary = "Toggle game active status")
    public ResponseEntity<MiniGameDefinitionResponse> toggleGameStatus(
            @PathVariable Long gameDefId,
            @RequestParam boolean active) {
        return ResponseEntity.ok(miniGameService.toggleGameStatus(gameDefId, active));
    }

    @PutMapping("/games/{gameDefId}/rewards")
    @Operation(summary = "Update game rewards (coins, XP)")
    public ResponseEntity<MiniGameDefinitionResponse> updateGameRewards(
            @PathVariable Long gameDefId,
            @RequestParam Integer baseCoinReward,
            @RequestParam Integer maxCoinReward,
            @RequestParam Integer xpReward) {
        return ResponseEntity.ok(miniGameService.updateGameRewards(gameDefId, baseCoinReward, maxCoinReward, xpReward));
    }

    @PutMapping("/games/{gameDefId}/cooldown")
    @Operation(summary = "Update game cooldown configuration")
    public ResponseEntity<MiniGameDefinitionResponse> updateGameCooldown(
            @PathVariable Long gameDefId,
            @RequestParam Integer cooldownMinutes,
            @RequestParam(required = false) Integer maxPlaysPerDay,
            @RequestParam(required = false) Integer maxCoinsPerDay) {
        return ResponseEntity.ok(miniGameService.updateGameCooldown(gameDefId, cooldownMinutes, maxPlaysPerDay, maxCoinsPerDay));
    }

    // =============================================
    // ACHIEVEMENT MANAGEMENT
    // =============================================

    @PostMapping("/achievements/recalculate")
    @Operation(summary = "Recalculate all user achievements")
    public ResponseEntity<Integer> recalculateAchievements() {
        return ResponseEntity.ok(dashboardService.recalculateAchievements());
    }

    private Long getAdminIdFromAuth(Authentication auth) {
        if (auth != null && auth.getPrincipal() != null) {
            try {
                return Long.parseLong(auth.getName());
            } catch (NumberFormatException e) {
                return 1L;
            }
        }
        return 1L;
    }
}
