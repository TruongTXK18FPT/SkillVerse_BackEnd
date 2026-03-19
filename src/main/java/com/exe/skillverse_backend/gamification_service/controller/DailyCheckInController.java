package com.exe.skillverse_backend.gamification_service.controller;

import com.exe.skillverse_backend.gamification_service.dto.response.CheckInResponseDTO;
import com.exe.skillverse_backend.gamification_service.dto.response.StreakInfoDTO;
import com.exe.skillverse_backend.gamification_service.service.DailyCheckInService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/streak")
@RequiredArgsConstructor
@Tag(name = "Daily Check-In & Streak", description = "Daily check-in and streak tracking endpoints")
public class DailyCheckInController {

    private final DailyCheckInService checkInService;

    @PostMapping("/check-in")
    @Operation(summary = "Perform daily check-in", 
               description = "Records daily attendance and awards coins/XP. Returns updated streak info.")
    public ResponseEntity<CheckInResponseDTO> checkIn(Authentication auth) {
        Long userId = getUserIdFromAuth(auth);
        return ResponseEntity.ok(checkInService.checkIn(userId));
    }

    @GetMapping("/info")
    @Operation(summary = "Get streak information", 
               description = "Returns current streak, weekly activity, power level, and check-in status")
    public ResponseEntity<StreakInfoDTO> getStreakInfo(Authentication auth) {
        Long userId = getUserIdFromAuth(auth);
        return ResponseEntity.ok(checkInService.getStreakInfo(userId));
    }

    @GetMapping("/status")
    @Operation(summary = "Check if already checked in today")
    public ResponseEntity<Boolean> hasCheckedInToday(Authentication auth) {
        Long userId = getUserIdFromAuth(auth);
        return ResponseEntity.ok(checkInService.hasCheckedInToday(userId));
    }

    private Long getUserIdFromAuth(Authentication auth) {
        if (auth == null) return 0L;
        if (auth.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) auth.getPrincipal();
            try {
                return Long.parseLong(jwt.getSubject());
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }
}
