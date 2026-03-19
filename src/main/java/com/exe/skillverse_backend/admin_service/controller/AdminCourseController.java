package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.dto.response.CourseStatsResponse;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseDetailDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseSummaryDTO;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseUpgradePolicy;
import com.exe.skillverse_backend.course_service.service.CourseService;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin Course Management Controller.
 * <p>
 * All admin-only course operations are centralized here under /api/admin/courses,
 * following the same pattern as other admin controllers (AdminApplicationController, etc.).
 * </p>
 * <p>
 * Delegates to CourseService for business logic — no duplication of service code.
 * </p>
 */
@RestController
@RequestMapping("/api/admin/courses")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Admin Course Management", description = "Admin-only APIs for managing courses: approval, rejection, suspension, and statistics")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
public class AdminCourseController {

    private final CourseService courseService;

    // ========== Query Endpoints ==========

    @GetMapping
    @Operation(summary = "List all courses with optional status filter (Admin only)",
            description = "Returns all courses regardless of status. Use ?status= to filter by specific status.")
    public ResponseEntity<PageResponse<CourseSummaryDTO>> listAllCourses(
            @Parameter(description = "Search query (title)") @RequestParam(required = false) String q,
            @Parameter(description = "Filter by course status") @RequestParam(required = false) CourseStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("Admin listing courses — status={}, q={}, page={}", status, q, pageable.getPageNumber());
        PageResponse<CourseSummaryDTO> courses = courseService.listCourses(q, status, pageable);
        return ResponseEntity.ok(courses);
    }

    @GetMapping("/pending")
    @Operation(summary = "List courses pending approval (Admin only)")
    public ResponseEntity<PageResponse<CourseSummaryDTO>> listPendingCourses(
            @PageableDefault(size = 20) Pageable pageable) {

        PageResponse<CourseSummaryDTO> pendingCourses = courseService.listCoursesByStatus(CourseStatus.PENDING, pageable);
        return ResponseEntity.ok(pendingCourses);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get course statistics grouped by status (Admin only)",
            description = "Returns counts for each course status: PENDING, PUBLIC, REJECTED, SUSPENDED, DRAFT, ARCHIVED, and total ALL.")
    public ResponseEntity<CourseStatsResponse> getCourseStats() {
        log.info("Admin fetching course statistics");
        Map<String, Long> stats = courseService.getCourseStats();

        CourseStatsResponse response = CourseStatsResponse.builder()
                .totalPending(stats.getOrDefault("PENDING", 0L))
                .totalApproved(stats.getOrDefault("PUBLIC", 0L))
                .totalRejected(stats.getOrDefault("REJECTED", 0L))
                .totalSuspended(stats.getOrDefault("SUSPENDED", 0L))
                .totalDraft(stats.getOrDefault("DRAFT", 0L))
                .totalArchived(stats.getOrDefault("ARCHIVED", 0L))
                .totalAll(stats.getOrDefault("ALL", 0L))
                .build();

        return ResponseEntity.ok(response);
    }

    // ========== Action Endpoints ==========

    @PostMapping("/{courseId}/approve")
    @Operation(summary = "Approve a PENDING course (Admin only)")
    public ResponseEntity<CourseDetailDTO> approveCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = extractUserId(jwt);
        log.info("Admin {} approving course {}", adminId, courseId);
        CourseDetailDTO approved = courseService.approveCourse(courseId, adminId);
        return ResponseEntity.ok(approved);
    }

    @PostMapping("/{courseId}/reject")
    @Operation(summary = "Reject a PENDING course with reason (Admin only)")
    public ResponseEntity<CourseDetailDTO> rejectCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Rejection reason") @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = extractUserId(jwt);
        log.info("Admin {} rejecting course {} — reason: {}", adminId, courseId, reason);
        CourseDetailDTO rejected = courseService.rejectCourse(courseId, adminId, reason);
        return ResponseEntity.ok(rejected);
    }

    @PostMapping("/{courseId}/suspend")
    @Operation(summary = "Suspend a PUBLIC course due to violations (Admin only)")
    public ResponseEntity<CourseDetailDTO> suspendCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Suspension reason") @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = extractUserId(jwt);
        log.info("Admin {} suspending course {} — reason: {}", adminId, courseId, reason);
        CourseDetailDTO suspended = courseService.suspendCourse(courseId, adminId, reason);
        return ResponseEntity.ok(suspended);
    }

    @PostMapping("/{courseId}/restore")
    @Operation(summary = "Restore a SUSPENDED course back to PUBLIC (Admin only)")
    public ResponseEntity<CourseDetailDTO> restoreCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = extractUserId(jwt);
        log.info("Admin {} restoring suspended course {}", adminId, courseId);
        CourseDetailDTO restored = courseService.restoreCourse(courseId, adminId);
        return ResponseEntity.ok(restored);
    }

    @PostMapping("/{courseId}/upgrade-policy")
    @Operation(summary = "Update course upgrade policy (MANUAL/AUTO_COMPATIBLE_ONLY)")
    public ResponseEntity<CourseDetailDTO> updateUpgradePolicy(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Upgrade policy") @RequestParam @NotNull CourseUpgradePolicy policy,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = extractUserId(jwt);
        log.info("Admin {} updating course {} upgrade policy to {}", adminId, courseId, policy);
        CourseDetailDTO updated = courseService.updateUpgradePolicy(courseId, policy, adminId);
        return ResponseEntity.ok(updated);
    }

    // ========== Helpers ==========

    private Long extractUserId(Jwt jwt) {
        return JwtUtils.extractUserId(jwt);
    }
}
