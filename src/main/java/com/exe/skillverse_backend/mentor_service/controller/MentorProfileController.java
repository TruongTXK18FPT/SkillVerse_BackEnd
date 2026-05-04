package com.exe.skillverse_backend.mentor_service.controller;

import com.exe.skillverse_backend.mentor_service.dto.request.MentorProfileUpdateRequest;
import com.exe.skillverse_backend.mentor_service.dto.request.MentorSignatureDrawRequest;
import com.exe.skillverse_backend.mentor_service.dto.response.MentorProfileResponse;
import com.exe.skillverse_backend.mentor_service.dto.response.SkillTabResponse;
import com.exe.skillverse_backend.mentor_service.service.MentorProfileService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    public ResponseEntity<SkillTabResponse> getMySkillTab(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
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
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    public ResponseEntity<MentorProfileResponse> getMyMentorProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

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
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    public ResponseEntity<MentorProfileResponse> updateMyMentorProfile(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Profile update data") @Valid @RequestBody MentorProfileUpdateRequest request) {

        Long mentorId = Long.parseLong(jwt.getSubject());
        log.info("Updating current mentor profile for ID: {}", mentorId);
        MentorProfileResponse updatedProfile = mentorProfileService.updateMentorProfile(mentorId, request);
        return ResponseEntity.ok(updatedProfile);
    }

    @PutMapping("/{mentorId}/profile")
    @Operation(summary = "Update mentor profile by ID (Admin)")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<MentorProfileResponse> updateMentorProfile(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId,
            @Parameter(description = "Profile update data") @Valid @RequestBody MentorProfileUpdateRequest request) {

        log.info("Updating mentor profile for ID: {}", mentorId);
        MentorProfileResponse updatedProfile = mentorProfileService.updateMentorProfile(mentorId, request);
        return ResponseEntity.ok(updatedProfile);
    }

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload current mentor avatar")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    public ResponseEntity<AvatarUploadResponse> uploadMyMentorAvatar(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
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
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
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

    @PostMapping(value = "/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload current mentor signature")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    public ResponseEntity<SignatureUploadResponse> uploadMyMentorSignature(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Signature image file") @RequestParam("file") MultipartFile file) {
        throw new BadRequestException("SIGNATURE_FILE_UPLOAD_DISABLED_USE_SYSTEM_SIGNING");
    }

    @PostMapping(value = "/{mentorId}/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload mentor signature by ID (Admin)")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<SignatureUploadResponse> uploadMentorSignature(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId,
            @Parameter(description = "Signature image file") @RequestParam("file") MultipartFile file) {
        throw new BadRequestException("SIGNATURE_FILE_UPLOAD_DISABLED_USE_SYSTEM_SIGNING");
    }

    @PostMapping(value = "/signature/system", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create current mentor signature from system drawing strokes")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    public ResponseEntity<SignatureUploadResponse> createMyMentorSignatureFromDrawing(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody MentorSignatureDrawRequest request) {
        Long mentorId = Long.parseLong(jwt.getSubject());
        log.info("Creating system signature for current mentor ID: {}", mentorId);
        String signatureUrl = mentorProfileService.createMentorSignatureFromDrawing(mentorId, request);
        return ResponseEntity.ok(new SignatureUploadResponse(signatureUrl));
    }

    @PostMapping(value = "/{mentorId}/signature/system", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create mentor signature from system drawing strokes by ID (Admin)")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<SignatureUploadResponse> createMentorSignatureFromDrawing(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId,
            @Valid @RequestBody MentorSignatureDrawRequest request) {
        log.info("Creating system signature for mentor ID: {}", mentorId);
        String signatureUrl = mentorProfileService.createMentorSignatureFromDrawing(mentorId, request);
        return ResponseEntity.ok(new SignatureUploadResponse(signatureUrl));
    }

    @DeleteMapping("/signature")
    @Operation(summary = "Remove current mentor signature")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    public ResponseEntity<Void> removeMyMentorSignature(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

        Long mentorId = Long.parseLong(jwt.getSubject());
        log.info("Removing signature for current mentor ID: {}", mentorId);
        mentorProfileService.removeMentorSignature(mentorId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{mentorId}/signature")
    @Operation(summary = "Remove mentor signature by ID (Admin)")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<Void> removeMentorSignature(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId) {

        log.info("Removing signature for mentor ID: {}", mentorId);
        mentorProfileService.removeMentorSignature(mentorId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/prechat-enabled")
    @Operation(summary = "Bật/tắt pre-chat cho mentor hiện tại")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    public ResponseEntity<Void> setPreChatEnabled(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @RequestParam("enabled") boolean enabled) {

        Long mentorId = Long.parseLong(jwt.getSubject());
        log.info("Setting preChatEnabled={} for mentor ID: {}", enabled, mentorId);
        mentorProfileService.setPreChatEnabled(mentorId, enabled);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{mentorId}/prechat-enabled")
    @Operation(summary = "Bật/tắt pre-chat cho mentor (Admin)")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<Void> setPreChatEnabledAdmin(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId,
            @RequestParam("enabled") boolean enabled) {

        log.info("[Admin] Setting preChatEnabled={} for mentor ID: {}", enabled, mentorId);
        mentorProfileService.setPreChatEnabled(mentorId, enabled);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/stats/total-students")
    @Operation(summary = "Get total students count for current mentor across all courses")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    public ResponseEntity<TotalStudentsResponse> getMyTotalStudents(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        Long mentorId = Long.parseLong(jwt.getSubject());
        log.info("Getting total students count for mentor ID: {}", mentorId);
        long totalStudents = mentorProfileService.getTotalStudentsCount(mentorId);
        return ResponseEntity.ok(new TotalStudentsResponse(totalStudents));
    }

    @GetMapping("/{mentorId}/stats/total-students")
    @Operation(summary = "Get total students count for mentor by ID across all courses")
    public ResponseEntity<TotalStudentsResponse> getTotalStudents(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId) {
        log.info("Getting total students count for mentor ID: {}", mentorId);
        long totalStudents = mentorProfileService.getTotalStudentsCount(mentorId);
        return ResponseEntity.ok(new TotalStudentsResponse(totalStudents));
    }

    @GetMapping("/by-skill/{skillName}")
    @Operation(summary = "Get mentors by verified skill (skill must be verified by admin)")
    public ResponseEntity<List<MentorProfileResponse>> getMentorsBySkill(
            @Parameter(description = "Skill name (e.g., 'React', 'Java Core')") @PathVariable String skillName) {
        log.info("Getting mentors by verified skill: {}", skillName);
        List<MentorProfileResponse> mentors = mentorProfileService.findMentorsByVerifiedSkill(skillName);
        return ResponseEntity.ok(mentors);
    }

    @GetMapping("/{mentorId}/verified-skills")
    @Operation(summary = "Get verified skills for a specific mentor")
    public ResponseEntity<List<String>> getVerifiedSkills(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId) {
        log.info("Getting verified skills for mentor ID: {}", mentorId);
        List<String> skills = mentorProfileService.getVerifiedSkillsByMentorId(mentorId);
        return ResponseEntity.ok(skills);
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

    public static class SignatureUploadResponse {
        private String signatureUrl;

        public SignatureUploadResponse(String signatureUrl) {
            this.signatureUrl = signatureUrl;
        }

        public String getSignatureUrl() {
            return signatureUrl;
        }

        public void setSignatureUrl(String signatureUrl) {
            this.signatureUrl = signatureUrl;
        }
    }

    // Response DTO for total students
    public static class TotalStudentsResponse {
        private long totalStudents;

        public TotalStudentsResponse(long totalStudents) {
            this.totalStudents = totalStudents;
        }

        public long getTotalStudents() {
            return totalStudents;
        }

        public void setTotalStudents(long totalStudents) {
            this.totalStudents = totalStudents;
        }
    }
}
