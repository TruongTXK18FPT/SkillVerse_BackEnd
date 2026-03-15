package com.exe.skillverse_backend.journey_service.controller;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.journey_service.dto.request.StartJourneyRequest;
import com.exe.skillverse_backend.journey_service.dto.request.SubmitTestRequest;
import com.exe.skillverse_backend.journey_service.dto.response.*;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.service.JourneyService;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Journey management.
 * Handles guided learning journey flows from assessment to roadmap to study plans.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/journey")
@RequiredArgsConstructor
public class JourneyController {

    private final JourneyService journeyService;
    private final UserRepository userRepository;

    /**
     * Start a new guided journey.
     * POST /api/v1/journey
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneySummaryResponse> startJourney(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody StartJourneyRequest request) {
        User user = getUserFromAuth(userDetails);
        log.info("User {} starting new journey: {}", user.getEmail(), request.getDomain());
        return ResponseEntity.ok(journeyService.startJourney(user, request));
    }

    /**
     * Get journey by ID.
     * GET /api/v1/journey/{journeyId}
     */
    @GetMapping("/{journeyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneySummaryResponse> getJourney(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId) {
        User user = getUserFromAuth(userDetails);
        return ResponseEntity.ok(journeyService.getJourneyById(user, journeyId));
    }

    /**
     * Get all journeys for current user.
     * GET /api/v1/journey
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<JourneySummaryResponse>> getUserJourneys(
            @AuthenticationPrincipal UserDetails userDetails,
            @PageableDefault(size = 10) Pageable pageable) {
        User user = getUserFromAuth(userDetails);
        return ResponseEntity.ok(journeyService.getUserJourneys(user, pageable));
    }

    /**
     * Get active journeys for current user.
     * GET /api/v1/journey/active
     */
    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<JourneySummaryResponse>> getActiveJourneys(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUserFromAuth(userDetails);
        return ResponseEntity.ok(journeyService.getActiveJourneys(user));
    }

    /**
     * Get current journey progress for dashboard.
     * GET /api/v1/journey/current
     */
    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneySummaryResponse> getCurrentJourneyProgress(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = getUserFromAuth(userDetails);
        JourneySummaryResponse progress = journeyService.getCurrentJourneyProgress(user);
        if (progress == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(progress);
    }

    /**
     * Generate AI assessment test for a journey.
     * POST /api/v1/journey/{journeyId}/generate-test
     */
    @PostMapping("/{journeyId}/generate-test")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GenerateTestResponse> generateAssessmentTest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId) {
        User user = getUserFromAuth(userDetails);
        log.info("Generating assessment test for journey: {}", journeyId);
        return ResponseEntity.ok(journeyService.generateAssessmentTest(user, journeyId));
    }

    /**
     * Get assessment test details.
     * GET /api/v1/journey/{journeyId}/test/{testId}
     */
    @GetMapping("/{journeyId}/test/{testId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AssessmentTestResponse> getAssessmentTest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId,
            @PathVariable Long testId) {
        User user = getUserFromAuth(userDetails);
        return ResponseEntity.ok(journeyService.getAssessmentTest(user, journeyId, testId));
    }

    /**
     * Submit test answers and get evaluation.
     * POST /api/v1/journey/{journeyId}/submit-test
     */
    @PostMapping("/{journeyId}/submit-test")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TestResultResponse> submitTest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId,
            @Valid @RequestBody SubmitTestRequest request) {
        User user = getUserFromAuth(userDetails);
        log.info("Submitting test for journey: {}", journeyId);
        return ResponseEntity.ok(journeyService.submitTest(user, journeyId, request));
    }

    /**
     * Get test result.
     * GET /api/v1/journey/{journeyId}/result/{resultId}
     */
    @GetMapping("/{journeyId}/result/{resultId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<TestResultResponse> getTestResult(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId,
            @PathVariable Long resultId) {
        User user = getUserFromAuth(userDetails);
        return ResponseEntity.ok(journeyService.getTestResult(user, journeyId, resultId));
    }

    /**
     * Generate roadmap for journey based on evaluation results.
     * POST /api/v1/journey/{journeyId}/generate-roadmap
     */
    @PostMapping("/{journeyId}/generate-roadmap")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneySummaryResponse> generateRoadmap(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId) {
        User user = getUserFromAuth(userDetails);
        log.info("Generating roadmap for journey: {}", journeyId);
        return ResponseEntity.ok(journeyService.generateRoadmap(user, journeyId));
    }

    /**
     * Get roadmap for journey.
     * GET /api/v1/journey/{journeyId}/roadmap
     */
    @GetMapping("/{journeyId}/roadmap")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getRoadmap(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId) {
        User user = getUserFromAuth(userDetails);
        return ResponseEntity.ok(journeyService.getRoadmapForJourney(user, journeyId));
    }

    /**
     * Generate study plan suggestions for roadmap nodes.
     * POST /api/v1/journey/{journeyId}/generate-study-plans
     */
    @PostMapping("/{journeyId}/generate-study-plans")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> generateStudyPlans(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId) {
        User user = getUserFromAuth(userDetails);
        log.info("Generating study plans for journey: {}", journeyId);
        return ResponseEntity.ok(journeyService.generateStudyPlans(user, journeyId));
    }

    /**
     * Create study plan for specific roadmap node.
     * POST /api/v1/journey/{journeyId}/study-plan/node/{nodeId}
     */
    @PostMapping("/{journeyId}/study-plan/node/{nodeId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createStudyPlanForNode(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId,
            @PathVariable Long nodeId) {
        User user = getUserFromAuth(userDetails);
        return ResponseEntity.ok(journeyService.createStudyPlanForNode(user, journeyId, nodeId));
    }

    /**
     * Generate AI summary report for journey.
     * POST /api/v1/journey/{journeyId}/generate-report
     */
    @PostMapping("/{journeyId}/generate-report")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneySummaryResponse> generateAiReport(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId) {
        User user = getUserFromAuth(userDetails);
        log.info("Generating AI report for journey: {}", journeyId);
        return ResponseEntity.ok(journeyService.generateAiReport(user, journeyId));
    }

    /**
     * Pause a journey.
     * POST /api/v1/journey/{journeyId}/pause
     */
    @PostMapping("/{journeyId}/pause")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneySummaryResponse> pauseJourney(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId) {
        User user = getUserFromAuth(userDetails);
        return ResponseEntity.ok(journeyService.pauseJourney(user, journeyId));
    }

    /**
     * Resume a paused journey.
     * POST /api/v1/journey/{journeyId}/resume
     */
    @PostMapping("/{journeyId}/resume")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneySummaryResponse> resumeJourney(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId) {
        User user = getUserFromAuth(userDetails);
        return ResponseEntity.ok(journeyService.resumeJourney(user, journeyId));
    }

    /**
     * Cancel a journey.
     * POST /api/v1/journey/{journeyId}/cancel
     */
    @PostMapping("/{journeyId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneySummaryResponse> cancelJourney(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId) {
        User user = getUserFromAuth(userDetails);
        return ResponseEntity.ok(journeyService.cancelJourney(user, journeyId));
    }

    /**
     * Complete a journey.
     * POST /api/v1/journey/{journeyId}/complete
     */
    @PostMapping("/{journeyId}/complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneySummaryResponse> completeJourney(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId) {
        User user = getUserFromAuth(userDetails);
        return ResponseEntity.ok(journeyService.completeJourney(user, journeyId));
    }

    /**
     * Update journey status.
     * PUT /api/v1/journey/{journeyId}/status
     */
    @PutMapping("/{journeyId}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JourneySummaryResponse> updateJourneyStatus(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long journeyId,
            @RequestParam Journey.JourneyStatus status) {
        User user = getUserFromAuth(userDetails);
        return ResponseEntity.ok(journeyService.updateJourneyStatus(user, journeyId, status));
    }

    /**
     * Helper method to get User from authentication.
     */
    private User getUserFromAuth(UserDetails userDetails) {
        // Legacy path: if UserDetails is available, use it first.
        String principalValue = userDetails != null ? userDetails.getUsername() : null;

        // Modern JWT resource-server path: extract from SecurityContext JWT principal.
        if (principalValue == null || principalValue.isBlank()) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                Object principal = authentication.getPrincipal();
                if (principal instanceof Jwt jwt) {
                    Long userId = JwtUtils.extractUserId(jwt);
                    return userRepository.findById(userId)
                            .orElseThrow(() -> new RuntimeException("User not found by id: " + userId));
                }
                principalValue = authentication.getName();
            }
        }

        if (principalValue == null || principalValue.isBlank()) {
            throw new RuntimeException("Invalid authentication. Please login again.");
        }

        // SecurityConfig sets principal claim name to "userId", so principal is usually numeric.
        try {
            Long userId = Long.valueOf(principalValue);
            return userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found by id: " + userId));
        } catch (NumberFormatException ignored) {
            // Fallback for flows where principal is still email/username.
        }

        final String principalEmail = principalValue;
        return userRepository.findByEmail(principalEmail)
                .orElseThrow(() -> new RuntimeException("User not found by email: " + principalEmail));
    }
}
