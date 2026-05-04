package com.exe.skillverse_backend.student_skill_verification.controller;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.student_skill_verification.dto.request.CreateStudentVerificationRequest;
import com.exe.skillverse_backend.student_skill_verification.dto.request.ReviewStudentVerificationRequest;
import com.exe.skillverse_backend.student_skill_verification.dto.response.StudentVerificationResponse;
import com.exe.skillverse_backend.student_skill_verification.service.StudentSkillVerificationService;
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
 * [Nghiệp vụ] API cho luồng xác thực skill student.
 *
 * Student endpoints: gửi request kèm bằng chứng, xem trạng thái, xem skill verified.
 * Admin endpoints: xem queue, duyệt/reject request.
 * Public endpoint: xem skill đã verified của student.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Student Skill Verification", description = "Student skill verification request and admin review")
public class StudentSkillVerificationController {

    private final StudentSkillVerificationService verificationService;
    private final UserRepository userRepository;

    // ==================== Student Endpoints ====================

    @PostMapping("/student/skill-verifications")
    @Operation(summary = "Student: gửi yêu cầu xác thực skill kèm bằng chứng")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<StudentVerificationResponse> submitVerification(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateStudentVerificationRequest request) {

        User student = resolveUser(jwt);
        log.info("Student {} submitting skill verification for '{}'", student.getId(), request.getSkillName());
        StudentVerificationResponse response = verificationService.submitVerification(student, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/student/skill-verifications")
    @Operation(summary = "Student: xem danh sách yêu cầu xác thực của mình")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<StudentVerificationResponse>> getMyVerifications(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

        User student = resolveUser(jwt);
        return ResponseEntity.ok(verificationService.getMyVerifications(student));
    }

    @GetMapping("/student/skill-verifications/verified-skills")
    @Operation(summary = "Student: xem danh sách skill đã được verified")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<String>> getMyVerifiedSkills(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {

        User student = resolveUser(jwt);
        return ResponseEntity.ok(verificationService.getMyVerifiedSkills(student));
    }

    // ==================== Public Endpoints ====================

    @GetMapping("/public/students/{userId}/verified-skills/details")
    @Operation(summary = "Public: lấy danh sách skill đã APPROVED kèm evidence của student")
    public ResponseEntity<List<StudentVerificationResponse>> getApprovedVerificationsByUserId(
            @PathVariable Long userId) {
        return ResponseEntity.ok(verificationService.getApprovedVerificationsByUserId(userId));
    }

    // ==================== Admin Endpoints ====================

    @GetMapping("/admin/student-verifications/pending")
    @Operation(summary = "Admin: lấy danh sách yêu cầu student chờ duyệt")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<Page<StudentVerificationResponse>> getPendingVerifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(verificationService.getPendingVerifications(pageable));
    }

    @GetMapping("/admin/student-verifications")
    @Operation(summary = "Admin: lấy tất cả yêu cầu student (có filter status)")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<Page<StudentVerificationResponse>> getAllVerifications(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(verificationService.getAllVerifications(statuses, pageable));
    }

    @GetMapping("/admin/student-verifications/{requestId}")
    @Operation(summary = "Admin: xem chi tiết 1 yêu cầu student")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<StudentVerificationResponse> getVerificationById(
            @PathVariable Long requestId) {

        return ResponseEntity.ok(verificationService.getVerificationById(requestId));
    }

    @PostMapping("/admin/student-verifications/{requestId}/review")
    @Operation(summary = "Admin: duyệt hoặc reject yêu cầu xác thực skill student")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
    public ResponseEntity<StudentVerificationResponse> reviewVerification(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long requestId,
            @Valid @RequestBody ReviewStudentVerificationRequest request) {

        User admin = resolveUser(jwt);
        log.info("Admin {} reviewing student verification request {}", admin.getId(), requestId);
        StudentVerificationResponse response = verificationService.reviewVerification(requestId, admin, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/admin/student-verifications/count-pending")
    @Operation(summary = "Admin: đếm số request student pending")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER_ADMIN')")
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
