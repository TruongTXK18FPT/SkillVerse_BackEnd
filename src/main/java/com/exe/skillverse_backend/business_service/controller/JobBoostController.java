package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.CreateJobBoostRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobBoostAnalyticsResponse;
import com.exe.skillverse_backend.business_service.dto.response.JobBoostResponse;
import com.exe.skillverse_backend.business_service.service.JobBoostService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for job boost operations
 */
@RestController
@RequestMapping("/api/v1/recruiter/job-boosts")
@RequiredArgsConstructor
@Slf4j
public class JobBoostController {

    private final JobBoostService jobBoostService;

    /**
     * Create a new job boost
     * POST /api/v1/recruiter/job-boosts
     */
    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> createBoost(
            Authentication authentication,
            @Valid @RequestBody CreateJobBoostRequest request) {

        Long recruiterId = extractUserId(authentication);
        log.info("Recruiter {} creating boost for job {}", recruiterId, request.getJobId());

        JobBoostResponse response = jobBoostService.createBoost(recruiterId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "success", true,
                "message", "Đẩy tin tuyển dụng thành công!",
                "data", response
        ));
    }

    /**
     * Get boost status for a specific job
     * GET /api/v1/recruiter/job-boosts/job/{jobId}
     */
    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> getBoostByJob(Authentication authentication,
                                            @PathVariable Long jobId) {
        extractUserId(authentication); // verify auth is valid
        JobBoostResponse response = jobBoostService.getBoostByJobId(jobId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", response
        ));
    }

    /**
     * Get all boosts for current recruiter
     * GET /api/v1/recruiter/job-boosts
     */
    @GetMapping
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> getMyBoosts(Authentication authentication) {
        Long recruiterId = extractUserId(authentication);

        List<JobBoostResponse> boosts = jobBoostService.getBoostsByRecruiter(recruiterId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Danh sách tin đang đẩy",
                "data", boosts,
                "count", boosts.size()
        ));
    }

    /**
     * Get available boost quota for current recruiter
     * GET /api/v1/recruiter/job-boosts/quota
     */
    @GetMapping("/quota")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> getBoostQuota(Authentication authentication) {
        Long recruiterId = extractUserId(authentication);

        int availableQuota = jobBoostService.getAvailableBoostQuota(recruiterId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("availableQuota", availableQuota);
        response.put("quotaType", availableQuota == Integer.MAX_VALUE ? "UNLIMITED" : "LIMITED");

        return ResponseEntity.ok(response);
    }

    /**
     * Cancel a job boost
     * DELETE /api/v1/recruiter/job-boosts/{boostId}
     */
    @DeleteMapping("/{boostId}")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> cancelBoost(
            Authentication authentication,
            @PathVariable Long boostId) {

        Long recruiterId = extractUserId(authentication);
        log.info("Recruiter {} cancelling boost {}", recruiterId, boostId);

        JobBoostResponse response = jobBoostService.cancelBoost(recruiterId, boostId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã hủy đẩy tin tuyển dụng",
                "data", response
        ));
    }

    /**
     * Extend boost duration
     * POST /api/v1/recruiter/job-boosts/{boostId}/extend
     */
    @PostMapping("/{boostId}/extend")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> extendBoost(
            Authentication authentication,
            @PathVariable Long boostId,
            @RequestParam @Valid @Min(7) @Max(30) int days) {

        Long recruiterId = extractUserId(authentication);
        log.info("Recruiter {} extending boost {} by {} days", recruiterId, boostId, days);

        JobBoostResponse response = jobBoostService.extendBoost(recruiterId, boostId, days);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã gia hạn đẩy tin thành công",
                "data", response
        ));
    }

    /**
     * Get analytics for a specific boost
     * GET /api/v1/recruiter/job-boosts/{boostId}/analytics
     */
    @GetMapping("/{boostId}/analytics")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<?> getBoostAnalytics(
            Authentication authentication,
            @PathVariable Long boostId) {

        Long recruiterId = extractUserId(authentication);
        JobBoostAnalyticsResponse response = jobBoostService.getBoostAnalytics(recruiterId, boostId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Thống kê hiệu quả đẩy tin",
                "data", response
        ));
    }

    /**
     * Extract user ID from Authentication (Jwt principal).
     * Spring Security OAuth2 Resource Server passes Jwt as the principal,
     * NOT UserDetails. This is the correct approach for JWT-based auth.
     */
    private Long extractUserId(Authentication authentication) {
        if (authentication == null) {
            throw new BadRequestException("User not authenticated");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String userIdStr = jwt.getClaimAsString("userId");
            if (userIdStr == null) {
                log.error("JWT does not contain userId claim. Available claims: {}", jwt.getClaims().keySet());
                throw new BadRequestException("Invalid token: missing userId claim");
            }
            try {
                return Long.parseLong(userIdStr);
            } catch (NumberFormatException e) {
                log.error("Failed to parse userId from JWT: {}", userIdStr);
                throw new BadRequestException("Invalid user ID format in token");
            }
        }
        // Fallback: try to get username from principal
        String username = principal.toString();
        try {
            return Long.parseLong(username);
        } catch (NumberFormatException e) {
            log.error("Failed to parse user ID from principal: {}", username);
            throw new BadRequestException("Invalid user ID format");
        }
    }
}
