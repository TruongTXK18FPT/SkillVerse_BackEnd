package com.exe.skillverse_backend.user_service.controller;

import com.exe.skillverse_backend.user_service.dto.request.AddSkillRequest;
import com.exe.skillverse_backend.user_service.dto.request.CreateProfileRequest;
import com.exe.skillverse_backend.user_service.dto.request.UpdateProfileRequest;
import com.exe.skillverse_backend.user_service.dto.request.UpdateSkillRequest;
import com.exe.skillverse_backend.user_service.dto.response.UserProfileResponse;
import com.exe.skillverse_backend.user_service.dto.response.UserSkillResponse;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "User profile and skills management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class UserProfileController {

    private final UserProfileService userProfileService;

    // Profile Management

    @PostMapping
    @Operation(summary = "Create user profile", description = "Creates a new user profile with the provided information")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Profile created successfully", content = @Content(schema = @Schema(implementation = UserProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data", content = @Content),
            @ApiResponse(responseCode = "409", description = "Profile already exists for this user", content = @Content)
    })
    public ResponseEntity<UserProfileResponse> createProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateProfileRequest request) {
        try {
            Long userId = Long.parseLong(jwt.getSubject());
            UserProfileResponse response = userProfileService.createProfile(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest request) {
        try {
            Long userId = Long.parseLong(jwt.getSubject());
            UserProfileResponse response = userProfileService.updateProfile(userId, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public ResponseEntity<UserProfileResponse> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        try {
            Long userId = Long.parseLong(jwt.getSubject());
            UserProfileResponse response = userProfileService.getProfile(userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserProfileResponse> getProfile(@PathVariable Long userId) {
        try {
            UserProfileResponse response = userProfileService.getProfile(userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<UserProfileResponse> getProfileByEmail(@PathVariable String email) {
        try {
            UserProfileResponse response = userProfileService.getProfileByEmail(email);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/region/{region}")
    public ResponseEntity<List<UserProfileResponse>> getProfilesByRegion(@PathVariable String region) {
        List<UserProfileResponse> profiles = userProfileService.getProfilesByRegion(region);
        return ResponseEntity.ok(profiles);
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserProfileResponse>> searchProfiles(@RequestParam String name) {
        List<UserProfileResponse> profiles = userProfileService.searchProfilesByName(name);
        return ResponseEntity.ok(profiles);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteProfile(@AuthenticationPrincipal Jwt jwt) {
        try {
            Long userId = Long.parseLong(jwt.getSubject());
            userProfileService.deleteProfile(userId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Skills Management

    @PostMapping("/skills")
    public ResponseEntity<UserSkillResponse> addSkill(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddSkillRequest request) {
        try {
            Long userId = Long.parseLong(jwt.getSubject());
            UserSkillResponse response = userProfileService.addSkill(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/skills/{skillId}")
    public ResponseEntity<UserSkillResponse> updateSkill(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long skillId,
            @Valid @RequestBody UpdateSkillRequest request) {
        try {
            Long userId = Long.parseLong(jwt.getSubject());
            UserSkillResponse response = userProfileService.updateSkill(userId, skillId, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/skills/{skillId}")
    public ResponseEntity<Void> removeSkill(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long skillId) {
        try {
            Long userId = Long.parseLong(jwt.getSubject());
            userProfileService.removeSkill(userId, skillId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/skills")
    public ResponseEntity<List<UserSkillResponse>> getMySkills(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        List<UserSkillResponse> skills = userProfileService.getUserSkills(userId);
        return ResponseEntity.ok(skills);
    }

    @GetMapping("/{userId}/skills")
    public ResponseEntity<List<UserSkillResponse>> getUserSkills(@PathVariable Long userId) {
        List<UserSkillResponse> skills = userProfileService.getUserSkills(userId);
        return ResponseEntity.ok(skills);
    }

    @GetMapping("/skills/category/{category}")
    public ResponseEntity<List<UserSkillResponse>> getMySkillsByCategory(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String category) {
        Long userId = Long.parseLong(jwt.getSubject());
        List<UserSkillResponse> skills = userProfileService.getUserSkillsByCategory(userId, category);
        return ResponseEntity.ok(skills);
    }

    @GetMapping("/skills/proficiency/{minProficiency}")
    public ResponseEntity<List<UserSkillResponse>> getMySkillsByMinProficiency(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Integer minProficiency) {
        Long userId = Long.parseLong(jwt.getSubject());
        List<UserSkillResponse> skills = userProfileService.getUserSkillsByMinProficiency(userId, minProficiency);
        return ResponseEntity.ok(skills);
    }
}