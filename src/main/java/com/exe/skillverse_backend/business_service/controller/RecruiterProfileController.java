package com.exe.skillverse_backend.business_service.controller;

import com.exe.skillverse_backend.business_service.dto.request.RecruiterProfileUpdateRequest;
import com.exe.skillverse_backend.business_service.dto.response.RecruiterProfileResponse;
import com.exe.skillverse_backend.business_service.service.RecruiterProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Recruiter Profile", description = "Recruiter/Business profile management endpoints")
public class RecruiterProfileController {

    private final RecruiterProfileService recruiterProfileService;

    @GetMapping("/profile")
    @PreAuthorize("hasAuthority('RECRUITER')")
    @Operation(summary = "Get current recruiter profile")
    public ResponseEntity<RecruiterProfileResponse> getMyRecruiterProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        try {
            Long userId = Long.parseLong(jwt.getSubject());
            log.info("Getting current recruiter profile for user ID: {}", userId);
            RecruiterProfileResponse profile = recruiterProfileService.getRecruiterProfile(userId);
            return ResponseEntity.ok(profile);
        } catch (RuntimeException e) {
            log.error("Error getting recruiter profile: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{recruiterId}/profile")
    @Operation(summary = "Get recruiter profile by ID (public)")
    public ResponseEntity<RecruiterProfileResponse> getRecruiterProfile(
            @Parameter(description = "Recruiter user ID") @PathVariable Long recruiterId) {
        try {
            log.info("Getting recruiter profile for ID: {}", recruiterId);
            RecruiterProfileResponse profile = recruiterProfileService.getRecruiterProfile(recruiterId);
            return ResponseEntity.ok(profile);
        } catch (RuntimeException e) {
            log.error("Error getting recruiter profile: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAuthority('RECRUITER')")
    @Operation(summary = "Update current recruiter profile")
    public ResponseEntity<RecruiterProfileResponse> updateMyRecruiterProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Profile update data") @Valid @RequestBody RecruiterProfileUpdateRequest request) {
        try {
            Long userId = Long.parseLong(jwt.getSubject());
            log.info("Updating recruiter profile for user ID: {}", userId);
            RecruiterProfileResponse updatedProfile = recruiterProfileService.updateRecruiterProfile(userId, request);
            return ResponseEntity.ok(updatedProfile);
        } catch (RuntimeException e) {
            log.error("Error updating recruiter profile: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
