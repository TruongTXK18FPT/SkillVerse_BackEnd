package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollRequestDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentDetailDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentStatsDTO;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.course_service.service.EnrollmentService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Enrollment Management", description = "APIs for managing course enrollments and progress")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final CourseRepository courseRepository;

    // ========== Write Operations ==========

    /**
     * Enroll in a free course (authenticated user only).
     * Only works for courses with price == 0.
     * For paid courses, use POST /api/course-purchases/wallet instead.
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Enroll in a free course")
    public ResponseEntity<EnrollmentDetailDTO> enrollUser(
            @Parameter(description = "Enrollment request data") @Valid @RequestBody EnrollRequestDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = JwtUtils.extractUserId(jwt);
        log.info("User {} enrolling in free course {}", userId, dto.getCourseId());

        // Check: course must be FREE (price == 0)
        Course course = courseRepository.findById(dto.getCourseId()).orElse(null);
        if (course == null) {
            throw new BadRequestException("Course not found");
        }
        if (course.getPrice() != null && BigDecimal.ZERO.compareTo(course.getPrice()) < 0) {
            throw new BadRequestException("Khóa học có phí. Vui lòng mua khóa học trước.");
        }

        // The service layer will also check course status == PUBLIC
        EnrollmentDetailDTO enrollment = enrollmentService.enrollUser(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollment);
    }

    /**
     * Unenroll self from a course.
     * Only the enrolled user can unenroll themselves.
     */
    @DeleteMapping("/course/{courseId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Unenroll self from a course")
    public ResponseEntity<Void> unenrollUser(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = JwtUtils.extractUserId(jwt);
        log.info("User {} unenrolling from course {}", userId, courseId);
        enrollmentService.unenrollUser(courseId, userId);
        return ResponseEntity.noContent().build();
    }

    // ========== Read Operations ==========

    /**
     * Get enrollment details for self in a course.
     */
    @GetMapping("/me/course/{courseId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my enrollment details for a course")
    public ResponseEntity<EnrollmentDetailDTO> getMyEnrollment(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = JwtUtils.extractUserId(jwt);
        EnrollmentDetailDTO enrollment = enrollmentService.getEnrollment(courseId, userId);
        return ResponseEntity.ok(enrollment);
    }

    /**
     * Get all my enrollments across all courses.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List my enrollments")
    public ResponseEntity<PageResponse<EnrollmentDetailDTO>> getMyEnrollments(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {

        Long userId = JwtUtils.extractUserId(jwt);
        PageResponse<EnrollmentDetailDTO> enrollments = enrollmentService.getUserEnrollments(userId, pageable);
        return ResponseEntity.ok(enrollments);
    }

    /**
     * Get enrollments for a user across multiple courses.
     * Only the user themselves or an admin can access.
     */
    @GetMapping("/user/{userId}/batch")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get enrollments for a user across multiple courses")
    public ResponseEntity<List<EnrollmentDetailDTO>> getEnrollmentsByCourseIds(
            @Parameter(description = "User ID") @PathVariable @NotNull Long userId,
            @Parameter(description = "Comma-separated course IDs") @RequestParam @NotNull String courseIds,
            @AuthenticationPrincipal Jwt jwt) {

        Long currentUserId = JwtUtils.extractUserId(jwt);
        if (!currentUserId.equals(userId)) {
            throw new AccessDeniedException("Bạn chỉ có thể xem enrollment của chính mình");
        }

        List<Long> ids = Arrays.stream(courseIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .distinct()
                .collect(Collectors.toList());

        List<EnrollmentDetailDTO> enrollments = enrollmentService.getEnrollmentsByCourseIds(userId, ids);
        return ResponseEntity.ok(enrollments);
    }

    /**
     * Check if self is enrolled in a course.
     */
    @GetMapping("/me/course/{courseId}/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check if I am enrolled in a course")
    public ResponseEntity<Map<String, Boolean>> checkMyEnrollmentStatus(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = JwtUtils.extractUserId(jwt);
        boolean enrolled = enrollmentService.isUserEnrolled(courseId, userId);
        return ResponseEntity.ok(Map.of("enrolled", enrolled));
    }

    // ========== Instructor/Admin Operations ==========

    /**
     * List enrollments for a course.
     * Only the course author (mentor) or an admin can access.
     */
    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "List enrollments for a course (mentor/admin only)")
    public ResponseEntity<PageResponse<EnrollmentDetailDTO>> getCourseEnrollments(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {

        Long actorId = JwtUtils.extractUserId(jwt);
        PageResponse<EnrollmentDetailDTO> enrollments = enrollmentService.getCourseEnrollments(courseId, pageable, actorId);
        return ResponseEntity.ok(enrollments);
    }

    /**
     * Get enrollment statistics for a course.
     * Only the course author (mentor) or an admin can access.
     */
    @GetMapping("/course/{courseId}/stats")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Get enrollment statistics for a course (mentor/admin only)")
    public ResponseEntity<EnrollmentStatsDTO> getEnrollmentStats(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = JwtUtils.extractUserId(jwt);
        EnrollmentStatsDTO stats = enrollmentService.getEnrollmentStats(courseId, actorId);
        return ResponseEntity.ok(stats);
    }

    /**
     * Get recent enrollments across all courses.
     * Admin only.
     */
    @GetMapping("/recent")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get recent enrollments (admin only)")
    public ResponseEntity<PageResponse<EnrollmentDetailDTO>> getRecentEnrollments(
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {

        Long actorId = JwtUtils.extractUserId(jwt);
        PageResponse<EnrollmentDetailDTO> enrollments = enrollmentService.getRecentEnrollments(pageable, actorId);
        return ResponseEntity.ok(enrollments);
    }

    // ========== Progress Updates (system-driven only) ==========

    /**
     * Update enrollment completion status for a user.
     * This endpoint is intended for system use (AI progress tracking).
     * Caller must verify authorization before invoking.
     * WARNING: No auth guard — callers (AI service) must enforce ownership.
     */
    @PutMapping("/course/{courseId}/user/{userId}/completion")
    @Operation(summary = "Update course completion status (system use only — callers must authorize)")
    public ResponseEntity<Void> updateCompletionStatus(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "User ID") @PathVariable @NotNull Long userId,
            @Parameter(description = "Completion status") @RequestParam @NotNull Boolean completed) {

        log.info("Updating completion status for user {} in course {} to {}", userId, courseId, completed);
        enrollmentService.updateCompletionStatus(courseId, userId, completed);
        return ResponseEntity.noContent().build();
    }

    /**
     * Update enrollment progress percentage for a user.
     * This endpoint is intended for system use (AI progress tracking).
     * Caller must verify authorization before invoking.
     * WARNING: No auth guard — callers (AI service) must enforce ownership.
     */
    @PutMapping("/course/{courseId}/user/{userId}/progress")
    @Operation(summary = "Update enrollment progress percentage (system use only — callers must authorize)")
    public ResponseEntity<Void> updateProgress(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "User ID") @PathVariable @NotNull Long userId,
            @Parameter(description = "Progress percentage (0-100)") @RequestParam @NotNull Integer progressPercentage) {

        log.info("Updating progress for user {} in course {} to {}%", userId, courseId, progressPercentage);
        enrollmentService.updateProgress(courseId, userId, progressPercentage);
        return ResponseEntity.noContent().build();
    }
}
