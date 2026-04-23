package com.exe.skillverse_backend.mentor_verification_service.controller;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.CreateMentorVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.ReviewMentorVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.response.MentorVerificationResponse;
import com.exe.skillverse_backend.mentor_verification_service.service.MentorVerificationService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * [Nghiệp vụ] API cho luồng xác thực skill mentor.
 * 
 * Mentor endpoints: gửi request, xem trạng thái, xem skill verified.
 * Admin endpoints: xem queue, duyệt/reject request.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Mentor Skill Verification", description = "Mentor skill verification request and admin review")
public class MentorVerificationController {

    private final MentorVerificationService verificationService;
    private final UserRepository userRepository;

    // ==================== Mentor Endpoints ====================

    @PostMapping("/mentor/verifications")
    @Operation(summary = "Mentor: gửi yêu cầu xác thực skill kèm chứng chỉ/bằng chứng")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<MentorVerificationResponse> submitVerification(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateMentorVerificationRequest request) {

        User mentor = resolveUser(jwt);
        log.info("Mentor {} submitting skill verification for '{}'", mentor.getId(), request.getSkillName());
        MentorVerificationResponse response = verificationService.submitVerification(mentor, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/mentor/verifications")
    @Operation(summary = "Mentor: xem danh sách yêu cầu xác thực của mình")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<List<MentorVerificationResponse>> getMyVerifications(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

        User mentor = resolveUser(jwt);
        return ResponseEntity.ok(verificationService.getMyVerifications(mentor));
    }

    @GetMapping("/mentor/verifications/verified-skills")
    @Operation(summary = "Mentor: xem danh sách skill đã được verified")
    @PreAuthorize("hasRole('MENTOR')")
    public ResponseEntity<List<String>> getMyVerifiedSkills(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

        User mentor = resolveUser(jwt);
        return ResponseEntity.ok(verificationService.getMyVerifiedSkills(mentor));
    }

    // ==================== Public Endpoints ====================

    @GetMapping("/public/mentors/{mentorId}/verified-skills/details")
    @Operation(summary = "Public: lấy danh sách skill đã APPROVED kèm evidence của mentor (dùng trang public)")
    public ResponseEntity<List<MentorVerificationResponse>> getApprovedVerificationsByMentorId(
            @PathVariable Long mentorId) {
        return ResponseEntity.ok(verificationService.getApprovedVerificationsByMentorId(mentorId));
    }

    // ==================== Admin Endpoints ====================

    @GetMapping("/admin/mentor-verifications/pending")
    @Operation(summary = "Admin: lấy danh sách yêu cầu chờ duyệt")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<MentorVerificationResponse>> getPendingVerifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(verificationService.getPendingVerifications(pageable));
    }

    @GetMapping("/admin/mentor-verifications")
    @Operation(summary = "Admin: lấy tất cả yêu cầu (có filter status)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<MentorVerificationResponse>> getAllVerifications(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(verificationService.getAllVerifications(statuses, pageable));
    }

    @GetMapping("/admin/mentor-verifications/{requestId}")
    @Operation(summary = "Admin: xem chi tiết 1 yêu cầu")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MentorVerificationResponse> getVerificationById(
            @PathVariable Long requestId) {

        return ResponseEntity.ok(verificationService.getVerificationById(requestId));
    }

    @PostMapping("/admin/mentor-verifications/{requestId}/review")
    @Operation(summary = "Admin: duyệt hoặc reject yêu cầu xác thực skill")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MentorVerificationResponse> reviewVerification(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long requestId,
            @Valid @RequestBody ReviewMentorVerificationRequest request) {

        User admin = resolveUser(jwt);
        log.info("Admin {} reviewing verification request {}", admin.getId(), requestId);
        MentorVerificationResponse response = verificationService.reviewVerification(requestId, admin, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/mentor-verifications/count-pending")
    @Operation(summary = "Admin: đếm số request pending (cho badge)")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> countPending() {
        return ResponseEntity.ok(Map.of("count", verificationService.countPending()));
    }

    // ─── Helper ────────────────────────────────────────────────────────────────

    private User resolveUser(Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found: " + userId));
    }
}
