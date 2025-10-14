package com.exe.skillverse_backend.ai_service.controller;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.request.UpdateProgressRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ProgressResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.response.ValidationResult;
import com.exe.skillverse_backend.ai_service.service.AiRoadmapService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for AI-powered roadmap generation and management
 */
@RestController
@RequestMapping("/api/v1/ai/roadmap")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Roadmap", description = "AI-powered personalized learning roadmap generation")
@SecurityRequirement(name = "bearerAuth")
public class RoadmapController {

    private final AiRoadmapService aiRoadmapService;
    private final UserRepository userRepository;

    /**
     * Generate a new personalized learning roadmap using AI
     * 
     * @param request        Roadmap generation parameters
     * @param authentication Current authenticated user
     * @return Generated roadmap with session ID
     */
    @PostMapping("/generate")
    @Operation(summary = "Generate AI Roadmap", description = "Generate a personalized learning roadmap using Gemini AI based on user's goal, duration, experience, and learning style")
    public ResponseEntity<RoadmapResponse> generateRoadmap(
            @Valid @RequestBody GenerateRoadmapRequest request,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

        log.info("User {} requesting roadmap generation for goal: {}", userId, request.getGoal());

        RoadmapResponse response = aiRoadmapService.generateRoadmap(request, user);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Pre-validate roadmap generation request without actually generating
     * 
     * @param request Roadmap generation parameters to validate
     * @return List of validation warnings (INFO/WARNING/ERROR severity)
     */
    @PostMapping("/validate")
    @Operation(summary = "Pre-validate Roadmap Request", description = "Validate user inputs before generating roadmap. Returns warnings for deprecated technologies, time feasibility issues, test score validation, etc. ERROR severity blocks generation.")
    public ResponseEntity<List<ValidationResult>> preValidate(
            @Valid @RequestBody GenerateRoadmapRequest request) {

        log.info("Pre-validating roadmap request for goal: {}", request.getGoal());

        List<ValidationResult> warnings = aiRoadmapService.preValidateRequest(request);

        return ResponseEntity.ok(warnings);
    }

    /**
     * Get all roadmap sessions for the current user
     * 
     * @param authentication Current authenticated user
     * @return List of roadmap session summaries
     */
    @GetMapping
    @Operation(summary = "Get User Roadmaps", description = "Retrieve all roadmap sessions for the current user with progress statistics")
    public ResponseEntity<List<RoadmapSessionSummary>> getUserRoadmaps(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("Fetching roadmaps for user {}", userId);

        List<RoadmapSessionSummary> roadmaps = aiRoadmapService.getUserRoadmaps(userId);

        return ResponseEntity.ok(roadmaps);
    }

    /**
     * Get a specific roadmap session by ID
     * 
     * @param sessionId      Roadmap session ID
     * @param authentication Current authenticated user
     * @return Full roadmap details with nodes
     */
    @GetMapping("/{sessionId}")
    @Operation(summary = "Get Roadmap by ID", description = "Retrieve a specific roadmap session with full node details")
    public ResponseEntity<RoadmapResponse> getRoadmapById(
            @PathVariable Long sessionId,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} fetching roadmap {}", userId, sessionId);

        RoadmapResponse response = aiRoadmapService.getRoadmapById(sessionId, userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Update quest/milestone progress in a roadmap
     * 
     * @param sessionId      Roadmap session ID
     * @param request        Progress update details (questId, completed status)
     * @param authentication Current authenticated user
     * @return Updated progress information with statistics
     */
    @PostMapping("/{sessionId}/progress")
    @Operation(summary = "Update Quest Progress", description = "Mark a quest/milestone as completed or incomplete and get updated progress statistics")
    public ResponseEntity<ProgressResponse> updateProgress(
            @PathVariable Long sessionId,
            @Valid @RequestBody UpdateProgressRequest request,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} updating progress for session {} - quest: {}, completed: {}",
                userId, sessionId, request.getQuestId(), request.getCompleted());

        ProgressResponse response = aiRoadmapService.updateProgress(sessionId, userId, request);

        return ResponseEntity.ok(response);
    }
}
