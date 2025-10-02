package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseCreateDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseDetailDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.service.CourseService;
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
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Course Management", description = "APIs for managing courses")
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Create a new course")
    public ResponseEntity<CourseDetailDTO> createCourse(
            @Parameter(description = "Author user ID") @RequestParam @NotNull Long authorId,
            @Parameter(description = "Course creation data") @Valid @RequestBody CourseCreateDTO dto) {
        
        log.info("Creating course by author: {}", authorId);
        CourseDetailDTO created = courseService.createCourse(authorId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{courseId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Update an existing course")
    public ResponseEntity<CourseDetailDTO> updateCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Course update data") @Valid @RequestBody CourseUpdateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Updating course {} by user {}", courseId, actorId);
        CourseDetailDTO updated = courseService.updateCourse(courseId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Delete a course")
    public ResponseEntity<Void> deleteCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Deleting course {} by user {}", courseId, actorId);
        courseService.deleteCourse(courseId, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{courseId}")
    @Operation(summary = "Get course details")
    public ResponseEntity<CourseDetailDTO> getCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId) {
        
        CourseDetailDTO course = courseService.getCourse(courseId);
        return ResponseEntity.ok(course);
    }

    @GetMapping
    @Operation(summary = "List courses with search and filtering")
    public ResponseEntity<PageResponse<CourseSummaryDTO>> listCourses(
            @Parameter(description = "Search query") @RequestParam(required = false) String q,
            @Parameter(description = "Course status filter") @RequestParam(required = false) CourseStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        
        PageResponse<CourseSummaryDTO> courses = courseService.listCourses(q, status, pageable);
        return ResponseEntity.ok(courses);
    }

    // ========== Admin-only Course Approval Endpoints ==========
    
    @PostMapping("/{courseId}/submit")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Submit course for admin approval")
    public ResponseEntity<CourseDetailDTO> submitCourseForApproval(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Submitting course {} for approval by user {}", courseId, actorId);
        CourseDetailDTO submitted = courseService.submitCourseForApproval(courseId, actorId);
        return ResponseEntity.ok(submitted);
    }

    @PostMapping("/{courseId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Approve a course (Admin only)")
    public ResponseEntity<CourseDetailDTO> approveCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Admin user ID") @RequestParam @NotNull Long adminId) {
        
        log.info("Admin {} approving course {}", adminId, courseId);
        CourseDetailDTO approved = courseService.approveCourse(courseId, adminId);
        return ResponseEntity.ok(approved);
    }

    @PostMapping("/{courseId}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reject a course (Admin only)")
    public ResponseEntity<CourseDetailDTO> rejectCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Admin user ID") @RequestParam @NotNull Long adminId,
            @Parameter(description = "Rejection reason") @RequestParam(required = false) String reason) {
        
        log.info("Admin {} rejecting course {} with reason: {}", adminId, courseId, reason);
        CourseDetailDTO rejected = courseService.rejectCourse(courseId, adminId, reason);
        return ResponseEntity.ok(rejected);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List courses pending approval (Admin only)")
    public ResponseEntity<PageResponse<CourseSummaryDTO>> listPendingCourses(
            @PageableDefault(size = 20) Pageable pageable) {
        
        PageResponse<CourseSummaryDTO> pendingCourses = courseService.listCoursesByStatus(CourseStatus.PENDING, pageable);
        return ResponseEntity.ok(pendingCourses);
    }

    // ========== Debug Endpoints ==========
    
    @GetMapping("/debug/count")
    @Operation(summary = "Debug: Get total course count")
    public ResponseEntity<Map<String, Object>> getCourseCount() {
        Map<String, Object> response = new HashMap<>();
        response.put("totalCourses", courseService.getTotalCourseCount());
        response.put("message", "Debug endpoint - total courses in database");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/debug/list")
    @Operation(summary = "Debug: List all courses with IDs")
    public ResponseEntity<Map<String, Object>> listAllCourses() {
        Map<String, Object> response = new HashMap<>();
        response.put("courses", courseService.getAllCoursesForDebug());
        response.put("message", "Debug endpoint - all courses in database");
        return ResponseEntity.ok(response);
    }
}