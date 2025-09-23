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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Course Management", description = "APIs for managing courses")
public class CourseController {

    private final CourseService courseService;

    @PostMapping
    @Operation(summary = "Create a new course")
    public ResponseEntity<CourseDetailDTO> createCourse(
            @Parameter(description = "Author user ID") @RequestParam @NotNull Long authorId,
            @Parameter(description = "Course creation data") @Valid @RequestBody CourseCreateDTO dto) {
        
        log.info("Creating course by author: {}", authorId);
        CourseDetailDTO created = courseService.createCourse(authorId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{courseId}")
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
}