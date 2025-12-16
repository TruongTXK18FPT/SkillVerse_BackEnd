package com.exe.skillverse_backend.mentor_service.controller;

import com.exe.skillverse_backend.mentor_service.dto.request.MentorProfileUpdateRequest;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorProfileResponse;
import com.exe.skillverse_backend.mentor_service.dto.response.SkillTabResponse;
import com.exe.skillverse_backend.mentor_service.service.MentorProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/mentors")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mentor Profile", description = "Mentor profile management endpoints")
public class MentorProfileController {

    private final MentorProfileService mentorProfileService;

    @GetMapping
    @Operation(summary = "Get all approved mentors")
    public ResponseEntity<List<MentorProfileResponse>> getAllMentors() {
        log.info("Getting all approved mentors");
        List<MentorProfileResponse> mentors = mentorProfileService.getAllMentors();
        return ResponseEntity.ok(mentors);
    }

    @GetMapping("/skills")
    @Operation(summary = "Get all unique skills from approved mentors")
    public ResponseEntity<List<String>> getAllSkills() {
        log.info("Getting all unique skills");
        return ResponseEntity.ok(mentorProfileService.getAllSkills());
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Get mentor leaderboard")
    public ResponseEntity<List<MentorProfileResponse>> getLeaderboard(
            @Parameter(description = "Number of mentors to return") @RequestParam(name = "size", defaultValue = "10") int size) {
        log.info("Getting mentor leaderboard, size {}", size);
        return ResponseEntity.ok(mentorProfileService.getLeaderboard(size));
    }

    @GetMapping("/skilltab")
    @Operation(summary = "Get current mentor skill tab")
    public ResponseEntity<SkillTabResponse> getMySkillTab(
            @Parameter(hidden = true) @AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {
        Long mentorId = Long.parseLong(jwt.getSubject());
        return ResponseEntity.ok(mentorProfileService.getSkillTab(mentorId));
    }

    @GetMapping("/{mentorId}/skilltab")
    @Operation(summary = "Get mentor skill tab by ID")
    public ResponseEntity<SkillTabResponse> getSkillTab(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId) {
        return ResponseEntity.ok(mentorProfileService.getSkillTab(mentorId));
    }

    @GetMapping("/profile")
    @Operation(summary = "Get current mentor profile")
    public ResponseEntity<MentorProfileResponse> getMyMentorProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt) {

        Long mentorId = Long.parseLong(jwt.getSubject());
        log.info("Getting current mentor profile for ID: {}", mentorId);
        MentorProfileResponse profile = mentorProfileService.getMentorProfile(mentorId);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/{mentorId}/profile")
    @Operation(summary = "Get mentor profile by ID")
    public ResponseEntity<MentorProfileResponse> getMentorProfile(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId) {

        log.info("Getting mentor profile for ID: {}", mentorId);
        MentorProfileResponse profile = mentorProfileService.getMentorProfile(mentorId);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    @Operation(summary = "Update current mentor profile")
    public ResponseEntity<MentorProfileResponse> updateMyMentorProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt,
            @Parameter(description = "Profile update data") @Valid @RequestBody MentorProfileUpdateRequest request) {

        Long mentorId = Long.parseLong(jwt.getSubject());
        log.info("Updating current mentor profile for ID: {}", mentorId);
        MentorProfileResponse updatedProfile = mentorProfileService.updateMentorProfile(mentorId, request);
        return ResponseEntity.ok(updatedProfile);
    }

    @PutMapping("/{mentorId}/profile")
    @Operation(summary = "Update mentor profile by ID (Admin)")
    public ResponseEntity<MentorProfileResponse> updateMentorProfile(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId,
            @Parameter(description = "Profile update data") @Valid @RequestBody MentorProfileUpdateRequest request) {

        log.info("Updating mentor profile for ID: {}", mentorId);
        MentorProfileResponse updatedProfile = mentorProfileService.updateMentorProfile(mentorId, request);
        return ResponseEntity.ok(updatedProfile);
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload current mentor avatar")
    public ResponseEntity<AvatarUploadResponse> uploadMyMentorAvatar(
            @Parameter(hidden = true) @AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt,
            @Parameter(description = "Avatar file") @RequestParam("file") MultipartFile file) {

        Long mentorId = Long.parseLong(jwt.getSubject());
        log.info("Uploading avatar for current mentor ID: {}", mentorId);

        try {
            String avatarUrl = mentorProfileService.uploadMentorAvatar(
                    mentorId,
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType());

            AvatarUploadResponse response = new AvatarUploadResponse(avatarUrl);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error reading file for mentor ID: {}", mentorId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping(value = "/{mentorId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload mentor avatar by ID (Admin)")
    public ResponseEntity<AvatarUploadResponse> uploadMentorAvatar(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId,
            @Parameter(description = "Avatar file") @RequestParam("file") MultipartFile file) {

        log.info("Uploading avatar for mentor ID: {}", mentorId);

        try {
            String avatarUrl = mentorProfileService.uploadMentorAvatar(
                    mentorId,
                    file.getBytes(),
                    file.getOriginalFilename(),
                    file.getContentType());

            AvatarUploadResponse response = new AvatarUploadResponse(avatarUrl);
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error reading file for mentor ID: {}", mentorId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/prechat-enabled")
    @Operation(summary = "Bật/tắt pre-chat cho mentor hiện tại")
    public ResponseEntity<Void> setPreChatEnabled(
            @Parameter(hidden = true) @AuthenticationPrincipal org.springframework.security.oauth2.jwt.Jwt jwt,
            @RequestParam("enabled") boolean enabled) {

        Long mentorId = Long.parseLong(jwt.getSubject());
        log.info("Setting preChatEnabled={} for mentor ID: {}", enabled, mentorId);
        mentorProfileService.setPreChatEnabled(mentorId, enabled);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{mentorId}/prechat-enabled")
    @Operation(summary = "Bật/tắt pre-chat cho mentor (Admin)")
    public ResponseEntity<Void> setPreChatEnabledAdmin(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId,
            @RequestParam("enabled") boolean enabled) {

        log.info("[Admin] Setting preChatEnabled={} for mentor ID: {}", enabled, mentorId);
        mentorProfileService.setPreChatEnabled(mentorId, enabled);
        return ResponseEntity.ok().build();
    }

    // Response DTO for avatar upload
    public static class AvatarUploadResponse {
        private String avatarUrl;

        public AvatarUploadResponse(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }
    }
}
