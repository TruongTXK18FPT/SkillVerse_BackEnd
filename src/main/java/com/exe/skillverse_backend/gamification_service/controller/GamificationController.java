package com.exe.skillverse_backend.gamification_service.controller;

import com.exe.skillverse_backend.gamification_service.dto.request.CompleteGameSessionRequest;
import com.exe.skillverse_backend.gamification_service.dto.request.LogActivityRequest;
import com.exe.skillverse_backend.gamification_service.dto.request.StartGameSessionRequest;
import com.exe.skillverse_backend.gamification_service.dto.response.*;
import com.exe.skillverse_backend.gamification_service.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/gamification")
@RequiredArgsConstructor
@Tag(name = "Gamification", description = "User gamification endpoints")
public class GamificationController {

    private final GamificationBadgeService badgeService;
    private final GamificationMiniGameService miniGameService;
    private final GamificationLeaderboardService leaderboardService;
    private final GamificationDashboardService dashboardService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get gamification dashboard")
    public ResponseEntity<GamificationDashboardResponse> getDashboard(Authentication auth) {
        Long userId = getUserIdFromAuth(auth);
        String userPlan = "free"; // TODO: Get from user service
        return ResponseEntity.ok(dashboardService.getUserDashboard(userId, userPlan));
    }

    @GetMapping("/badges")
    @Operation(summary = "Get user badges")
    public ResponseEntity<List<UserBadgeResponse>> getBadges(
            Authentication auth,
            @RequestParam(required = false) String category) {
        Long userId = getUserIdFromAuth(auth);
        return ResponseEntity.ok(badgeService.getUserBadges(userId, category));
    }

    @GetMapping("/badges/definitions")
    @Operation(summary = "Get all badge definitions")
    public ResponseEntity<List<BadgeDefinitionResponse>> getBadgeDefinitions(
            @RequestParam(required = false) String category) {
        if (category != null) {
            return ResponseEntity.ok(badgeService.getBadgesByCategory(category));
        }
        return ResponseEntity.ok(badgeService.getActiveBadgeDefinitions());
    }

    @GetMapping("/games")
    @Operation(summary = "Get available mini-games")
    public ResponseEntity<List<MiniGameDefinitionResponse>> getGames(Authentication auth) {
        Long userId = getUserIdFromAuth(auth);
        String userPlan = "free"; // TODO: Get from user service
        return ResponseEntity.ok(miniGameService.getAvailableGames(userId, userPlan));
    }

    @GetMapping("/games/{gameKey}")
    @Operation(summary = "Get game definition")
    public ResponseEntity<MiniGameDefinitionResponse> getGame(
            @PathVariable String gameKey, Authentication auth) {
        Long userId = getUserIdFromAuth(auth);
        return ResponseEntity.ok(miniGameService.getGameDefinition(gameKey, userId));
    }

    @PostMapping("/games/start")
    @Operation(summary = "Start game session")
    public ResponseEntity<GameSessionResponse> startGame(
            @Valid @RequestBody StartGameSessionRequest request, Authentication auth) {
        Long userId = getUserIdFromAuth(auth);
        return ResponseEntity.ok(miniGameService.startGameSession(userId, request));
    }

    @PostMapping("/games/complete")
    @Operation(summary = "Complete game session")
    public ResponseEntity<GameSessionResponse> completeGame(
            @Valid @RequestBody CompleteGameSessionRequest request, Authentication auth) {
        Long userId = getUserIdFromAuth(auth);
        return ResponseEntity.ok(miniGameService.completeGameSession(userId, request));
    }

    @GetMapping("/games/history")
    @Operation(summary = "Get game history")
    public ResponseEntity<Page<GameSessionResponse>> getGameHistory(
            Authentication auth, Pageable pageable) {
        Long userId = getUserIdFromAuth(auth);
        return ResponseEntity.ok(miniGameService.getUserGameHistory(userId, pageable));
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Get leaderboard (public access)")
    public ResponseEntity<LeaderboardResponse> getLeaderboard(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "coins") String type,
            Authentication auth,
            Pageable pageable) {
        // Optional authentication - if authenticated, include user position
        Long userId = auth != null ? getUserIdFromAuth(auth) : null;
        return ResponseEntity.ok(leaderboardService.getLeaderboard(period, type, userId, pageable));
    }

    @GetMapping("/leaderboard/position")
    @Operation(summary = "Get user leaderboard position")
    public ResponseEntity<LeaderboardEntryResponse> getUserPosition(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "coins") String type,
            Authentication auth) {
        Long userId = getUserIdFromAuth(auth);
        return ResponseEntity.ok(leaderboardService.getUserLeaderboardPosition(userId, period, type));
    }

    private Long getUserIdFromAuth(Authentication auth) {
        if (auth == null) return 0L;
        if (auth.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) auth.getPrincipal();
            try {
                return Long.parseLong(jwt.getSubject());
            } catch (NumberFormatException e) {
                // Handle case where subject is not a long (shouldn't happen with our token setup)
                return 0L;
            }
        }
        return 0L;
    }
}
