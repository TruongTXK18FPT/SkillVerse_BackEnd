package com.exe.skillverse_backend.user_service.controller;

import com.exe.skillverse_backend.user_service.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "User Avatar", description = "User avatar upload endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class UserAvatarController {

    private final UserProfileService userProfileService;

    @PostMapping("/avatar")
    @Operation(summary = "Upload user avatar", description = "Upload avatar image for current logged-in user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avatar uploaded successfully", 
                content = @Content(schema = @Schema(implementation = Map.class))),
            @ApiResponse(responseCode = "400", description = "Invalid file or upload failed"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    public ResponseEntity<Map<String, String>> uploadAvatar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file) {
        try {
            log.info("Uploading avatar for user: {}", jwt.getSubject());
            
            Long userId = Long.parseLong(jwt.getSubject());
            String avatarUrl = userProfileService.uploadAvatar(userId, file);
            
            log.info("Avatar uploaded successfully: {}", avatarUrl);
            return ResponseEntity.ok(Map.of("avatarUrl", avatarUrl));
        } catch (Exception e) {
            log.error("Failed to upload avatar", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
