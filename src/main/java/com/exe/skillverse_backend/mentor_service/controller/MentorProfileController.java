package com.exe.skillverse_backend.mentor_service.controller;

import com.exe.skillverse_backend.mentor_service.dto.request.MentorProfileUpdateRequest;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorProfileResponse;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/mentors")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mentor Profile", description = "Mentor profile management endpoints")
public class MentorProfileController {

    private final MentorProfileService mentorProfileService;

    @GetMapping("/{mentorId}/profile")
    @Operation(summary = "Get mentor profile by ID")
    public ResponseEntity<MentorProfileResponse> getMentorProfile(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId) {
        
        log.info("Getting mentor profile for ID: {}", mentorId);
        MentorProfileResponse profile = mentorProfileService.getMentorProfile(mentorId);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/{mentorId}/profile")
    @Operation(summary = "Update mentor profile")
    public ResponseEntity<MentorProfileResponse> updateMentorProfile(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId,
            @Parameter(description = "Profile update data") @Valid @RequestBody MentorProfileUpdateRequest request) {
        
        log.info("Updating mentor profile for ID: {}", mentorId);
        MentorProfileResponse updatedProfile = mentorProfileService.updateMentorProfile(mentorId, request);
        return ResponseEntity.ok(updatedProfile);
    }

    @PostMapping(value = "/{mentorId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload mentor avatar")
    public ResponseEntity<AvatarUploadResponse> uploadMentorAvatar(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId,
            @Parameter(description = "Avatar file") @RequestParam("file") MultipartFile file) {
        
        log.info("Uploading avatar for mentor ID: {}", mentorId);
        
        try {
            String avatarUrl = mentorProfileService.uploadMentorAvatar(
                    mentorId, 
                    file.getBytes(), 
                    file.getOriginalFilename()
            );
            
            AvatarUploadResponse response = new AvatarUploadResponse(avatarUrl);
            return ResponseEntity.ok(response);
            
        } catch (IOException e) {
            log.error("Error reading file for mentor ID: {}", mentorId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
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
