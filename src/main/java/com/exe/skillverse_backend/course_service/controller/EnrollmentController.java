package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollRequestDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentDetailDTO;
import com.exe.skillverse_backend.course_service.dto.enrollmentdto.EnrollmentStatsDTO;
import com.exe.skillverse_backend.course_service.service.EnrollmentService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Enrollment Management", description = "APIs for managing course enrollments and progress")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    @Operation(summary = "Enroll a user in a course")
    public ResponseEntity<EnrollmentDetailDTO> enrollUser(
            @Parameter(description = "Enrollment request data") @Valid @RequestBody EnrollRequestDTO dto,
            @Parameter(description = "User ID") @RequestParam @NotNull Long userId) {
        
        log.info("Enrolling user {} in course {}", userId, dto.getCourseId());
        EnrollmentDetailDTO enrollment = enrollmentService.enrollUser(dto, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(enrollment);
    }

    @DeleteMapping("/course/{courseId}/user/{userId}")
    @Operation(summary = "Unenroll a user from a course")
    public ResponseEntity<Void> unenrollUser(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "User ID") @PathVariable @NotNull Long userId) {
        
        log.info("Unenrolling user {} from course {}", userId, courseId);
        enrollmentService.unenrollUser(courseId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/course/{courseId}/user/{userId}")
    @Operation(summary = "Get enrollment details")
    public ResponseEntity<EnrollmentDetailDTO> getEnrollment(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "User ID") @PathVariable @NotNull Long userId) {
        
        EnrollmentDetailDTO enrollment = enrollmentService.getEnrollment(courseId, userId);
        return ResponseEntity.ok(enrollment);
    }

    @GetMapping("/course/{courseId}/user/{userId}/status")
    @Operation(summary = "Check if user is enrolled in course")
    public ResponseEntity<Map<String, Boolean>> checkEnrollmentStatus(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "User ID") @PathVariable @NotNull Long userId) {
        
        boolean enrolled = enrollmentService.isUserEnrolled(courseId, userId);
        return ResponseEntity.ok(Map.of("enrolled", enrolled));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "List enrollments for a user")
    public ResponseEntity<PageResponse<EnrollmentDetailDTO>> getUserEnrollments(
            @Parameter(description = "User ID") @PathVariable @NotNull Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        PageResponse<EnrollmentDetailDTO> enrollments = enrollmentService.getUserEnrollments(userId, pageable);
        return ResponseEntity.ok(enrollments);
    }

    @GetMapping("/course/{courseId}")
    @Operation(summary = "List enrollments for a course (instructor/admin only)")
    public ResponseEntity<PageResponse<EnrollmentDetailDTO>> getCourseEnrollments(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        PageResponse<EnrollmentDetailDTO> enrollments = enrollmentService.getCourseEnrollments(courseId, pageable, actorId);
        return ResponseEntity.ok(enrollments);
    }

    @PutMapping("/course/{courseId}/user/{userId}/completion")
    @Operation(summary = "Update course completion status")
    public ResponseEntity<Void> updateCompletionStatus(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "User ID") @PathVariable @NotNull Long userId,
            @Parameter(description = "Completion status") @RequestParam @NotNull Boolean completed) {
        
        log.info("Updating completion status for user {} in course {} to {}", userId, courseId, completed);
        enrollmentService.updateCompletionStatus(courseId, userId, completed);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/course/{courseId}/user/{userId}/progress")
    @Operation(summary = "Update enrollment progress percentage")
    public ResponseEntity<Void> updateProgress(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "User ID") @PathVariable @NotNull Long userId,
            @Parameter(description = "Progress percentage (0-100)") @RequestParam @NotNull Integer progressPercentage) {
        
        log.info("Updating progress for user {} in course {} to {}%", userId, courseId, progressPercentage);
        enrollmentService.updateProgress(courseId, userId, progressPercentage);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/course/{courseId}/stats")
    @Operation(summary = "Get enrollment statistics for a course (instructor/admin only)")
    public ResponseEntity<EnrollmentStatsDTO> getEnrollmentStats(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        EnrollmentStatsDTO stats = enrollmentService.getEnrollmentStats(courseId, actorId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/recent")
    @Operation(summary = "Get recent enrollments (admin only)")
    public ResponseEntity<PageResponse<EnrollmentDetailDTO>> getRecentEnrollments(
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        PageResponse<EnrollmentDetailDTO> enrollments = enrollmentService.getRecentEnrollments(pageable, actorId);
        return ResponseEntity.ok(enrollments);
    }
}