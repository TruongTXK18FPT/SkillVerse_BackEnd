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
    private final com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository bookingRepository;
    private final com.exe.skillverse_backend.mentor_booking_service.repository.BookingReviewRepository bookingReviewRepository;
    private final com.exe.skillverse_backend.course_service.repository.CoursePurchaseRepository coursePurchaseRepository;
    private final com.exe.skillverse_backend.auth_service.repository.UserRepository userRepository;

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
        return ResponseEntity.ok(buildSkillTab(mentorId));
    }

    @GetMapping("/{mentorId}/skilltab")
    @Operation(summary = "Get mentor skill tab by ID")
    public ResponseEntity<SkillTabResponse> getSkillTab(
            @Parameter(description = "Mentor user ID") @PathVariable Long mentorId) {
        return ResponseEntity.ok(buildSkillTab(mentorId));
    }

    private SkillTabResponse buildSkillTab(Long mentorId) {
        MentorProfileResponse profile = mentorProfileService.getMentorProfile(mentorId);
        var badgesEarned = new java.util.HashSet<String>();
        if (profile.getBadges() != null) {
            for (String b : profile.getBadges()) if (b != null) badgesEarned.add(b);
        }
        com.exe.skillverse_backend.auth_service.entity.User mentorUser = userRepository.findById(mentorId)
                .orElseThrow(() -> new IllegalArgumentException("Mentor không tồn tại"));

        long sessionsCompleted = bookingRepository.countByMentorAndStatus(mentorUser,
                com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus.COMPLETED);
        long fiveStar = bookingReviewRepository.findByMentorIdOrderByCreatedAtDesc(mentorId)
                .stream().filter(r -> r.getRating() != null && r.getRating() == 5).count();
        long sales = coursePurchaseRepository.countSuccessfulPurchasesByMentorId(mentorId);
        java.math.BigDecimal revenue = coursePurchaseRepository.sumCapturedByMentor(mentorId)
                .orElse(java.math.BigDecimal.ZERO);

        java.util.List<BadgeInfo> catalog = java.util.List.of(
                new BadgeInfo("FIRST_SESSION", "Buổi đầu tiên", "Hoàn thành buổi mentoring đầu tiên", sessionsCompleted, 1, badgesEarned.contains("FIRST_SESSION")),
                new BadgeInfo("TEN_SESSIONS", "10 buổi mentoring", "Hoàn thành 10 buổi mentoring", sessionsCompleted, 10, badgesEarned.contains("TEN_SESSIONS")),
                new BadgeInfo("HUNDRED_SESSIONS", "100 buổi mentoring", "Hoàn thành 100 buổi mentoring", sessionsCompleted, 100, badgesEarned.contains("HUNDRED_SESSIONS")),
                new BadgeInfo("FIRST_FIVE_STAR", "Đánh giá 5⭐ đầu tiên", "Nhận đánh giá 5 sao đầu tiên", fiveStar, 1, badgesEarned.contains("FIRST_FIVE_STAR")),
                new BadgeInfo("TEN_FIVE_STAR", "10 đánh giá 5⭐", "Nhận 10 đánh giá 5 sao", fiveStar, 10, badgesEarned.contains("TEN_FIVE_STAR")),
                new BadgeInfo("HUNDRED_FIVE_STAR", "100 đánh giá 5⭐", "Nhận 100 đánh giá 5 sao", fiveStar, 100, badgesEarned.contains("HUNDRED_FIVE_STAR")),
                new BadgeInfo("FIRST_COURSE_SALE", "Bán khóa học đầu tiên", "Bán được khóa học đầu tiên", sales, 1, badgesEarned.contains("FIRST_COURSE_SALE")),
                new BadgeInfo("TEN_COURSE_SALES", "Bán 10 khóa học", "Bán được 10 khóa học", sales, 10, badgesEarned.contains("TEN_COURSE_SALES")),
                new BadgeInfo("HUNDRED_COURSE_SALES", "Bán 100 khóa học", "Bán được 100 khóa học", sales, 100, badgesEarned.contains("HUNDRED_COURSE_SALES"))
        );

        String levelTitle = getLevelTitle(profile.getCurrentLevel() != null ? profile.getCurrentLevel() : 0);
        int nextLevelPoints = computeNextLevelPoints(profile.getSkillPoints() != null ? profile.getSkillPoints() : 0);

        SkillTabResponse resp = new SkillTabResponse();
        resp.skillPoints = profile.getSkillPoints() != null ? profile.getSkillPoints() : 0;
        resp.currentLevel = profile.getCurrentLevel() != null ? profile.getCurrentLevel() : 0;
        resp.levelTitle = levelTitle;
        resp.sessionsCompleted = (int) sessionsCompleted;
        resp.fiveStarCount = (int) fiveStar;
        resp.courseSales = (int) sales;
        resp.revenueVnd = revenue;
        resp.nextLevelPoints = nextLevelPoints;
        resp.badges = catalog;
        return resp;
    }

    private String getLevelTitle(int level) {
        if (level == 1) return "Mentor mới nổi";
        if (level == 5) return "Mentor ngôi sao";
        if (level == 10) return "Mentor kỳ cựu";
        if (level == 15) return "Mentor cao thủ";
        if (level == 20) return "Mentor siêu cấp";
        return null;
    }

    private int computeNextLevelPoints(int points) {
        int remainder = points % 100;
        return 100 - remainder;
    }

    public static class SkillTabResponse {
        public int skillPoints;
        public int currentLevel;
        public String levelTitle;
        public int nextLevelPoints;
        public int sessionsCompleted;
        public int fiveStarCount;
        public int courseSales;
        public java.math.BigDecimal revenueVnd;
        public java.util.List<BadgeInfo> badges;
    }

    public static class BadgeInfo {
        public String code;
        public String name;
        public String description;
        public int progressCurrent;
        public int progressTarget;
        public boolean earned;

        public BadgeInfo(String code, String name, String description,
                         long progressCurrent, int progressTarget, boolean earned) {
            this.code = code;
            this.name = name;
            this.description = description;
            this.progressCurrent = (int) progressCurrent;
            this.progressTarget = progressTarget;
            this.earned = earned;
        }
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
