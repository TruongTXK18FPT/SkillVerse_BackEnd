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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "User profile and skills management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class UserProfileController {

    private final UserProfileService userProfileService;

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

    @GetMapping("/public/{id}")
    public ResponseEntity<UserProfileResponse> getPublicProfile(@PathVariable Long id) {
        try {
            UserProfileResponse response = userProfileService.getProfile(id);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}