package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.entity.TrustScore;
import com.exe.skillverse_backend.business_service.service.TrustScoreService;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trust-scores")
@RequiredArgsConstructor
@Slf4j
public class TrustScoreController {

    private final TrustScoreService trustScoreService;

    @GetMapping("/{userId}")
    public ResponseEntity<TrustScore> getTrustScore(@PathVariable Long userId) {
        log.info("GET /api/trust-scores/{}", userId);
        TrustScore score = trustScoreService.getScore(userId);
        if (score == null) {
            score = trustScoreService.recalculateScore(userId);
        }
        return ResponseEntity.ok(score);
    }

    @PostMapping("/calculate/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TrustScore> calculateScore(@PathVariable Long userId) {
        log.info("POST /api/trust-scores/calculate/{}", userId);
        TrustScore score = trustScoreService.calculateScore(userId);
        return ResponseEntity.ok(score);
    }

    @GetMapping("/me")
    public ResponseEntity<TrustScore> getMyTrustScore(Authentication auth) {
        Long userId = JwtUtils.extractUserId(auth);
        log.info("GET /api/trust-scores/me for user {}", userId);
        TrustScore score = trustScoreService.getScore(userId);
        if (score == null) {
            score = trustScoreService.recalculateScore(userId);
        }
        return ResponseEntity.ok(score);
    }

    @PostMapping("/me/refresh")
    public ResponseEntity<TrustScore> refreshMyScore(Authentication auth) {
        Long userId = JwtUtils.extractUserId(auth);
        log.info("POST /api/trust-scores/me/refresh for user {}", userId);
        TrustScore score = trustScoreService.recalculateScore(userId);
        return ResponseEntity.ok(score);
    }
}
