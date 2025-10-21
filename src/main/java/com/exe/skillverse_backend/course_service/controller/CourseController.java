package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseCreateDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseDetailDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.service.CourseService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.entity.Media;
import com.exe.skillverse_backend.shared.repository.MediaRepository;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.shared.exception.MediaOperationException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Course Management", description = "APIs for managing courses")
public class CourseController {

    private final CourseService courseService;
    private final MediaRepository mediaRepository;
    private final CloudinaryService cloudinaryService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('MENTOR') or hasAuthority('ADMIN')")
    @Operation(summary = "Create a new course")
    public ResponseEntity<CourseDetailDTO> createCourse(
            @Parameter(description = "Author user ID") @RequestParam @NotNull Long authorId,
            @Parameter(description = "Course title") @RequestParam @NotBlank String title,
            @Parameter(description = "Course description") @RequestParam(required = false) String description,
            @Parameter(description = "Course level") @RequestParam(required = false) String level,
            @Parameter(description = "Thumbnail file") @RequestParam(required = false) MultipartFile thumbnailFile,
            @Parameter(description = "Course price") @RequestParam(required = false) java.math.BigDecimal price,
            @Parameter(description = "Currency") @RequestParam(required = false) String currency) {

        log.info("Creating course by author: {}", authorId);

        // Handle thumbnail file upload if provided
        Long thumbnailMediaId = null;
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            try {
                log.info("Uploading thumbnail file: {}", thumbnailFile.getOriginalFilename());

                // Upload to Cloudinary
                String folder = "skillverse/user_" + authorId;
                Map<String, Object> uploadResult = cloudinaryService.uploadImage(thumbnailFile, folder);

                // Extract Cloudinary response data
                String publicUrl = (String) uploadResult.get("url");
                String publicId = (String) uploadResult.get("public_id");
                String resourceType = (String) uploadResult.get("resource_type");

                // Create Media entity
                Media thumbnail = new Media();
                thumbnail.setUrl(publicUrl);
                thumbnail.setType(thumbnailFile.getContentType());
                thumbnail.setFileName(thumbnailFile.getOriginalFilename());
                thumbnail.setFileSize(thumbnailFile.getSize());
                thumbnail.setUploadedBy(authorId);
                thumbnail.setUploadedAt(LocalDateTime.now());
                thumbnail.setCloudinaryPublicId(publicId);
                thumbnail.setCloudinaryResourceType(resourceType);

                // Save thumbnail to database
                Media savedThumbnail = mediaRepository.save(thumbnail);
                thumbnailMediaId = savedThumbnail.getId();
                log.info("Thumbnail uploaded successfully: {} - {}", savedThumbnail.getId(), savedThumbnail.getUrl());
            } catch (Exception e) {
                log.error("Failed to upload thumbnail: {}", e.getMessage());
                throw new MediaOperationException("Thumbnail upload failed: " + e.getMessage(), e);
            }
        }

        CourseCreateDTO dto = new CourseCreateDTO();
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setLevel(level);
        dto.setThumbnailMediaId(thumbnailMediaId);
        dto.setPrice(price);
        dto.setCurrency(currency);

        CourseDetailDTO created = courseService.createCourse(authorId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(value = "/{courseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('MENTOR') or hasAuthority('ADMIN')")
    @Operation(summary = "Update an existing course")
    public ResponseEntity<CourseDetailDTO> updateCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId,
            @Parameter(description = "Course title") @RequestParam @NotBlank String title,
            @Parameter(description = "Course description") @RequestParam(required = false) String description,
            @Parameter(description = "Course level") @RequestParam(required = false) String level,
            @Parameter(description = "Thumbnail file") @RequestParam(required = false) MultipartFile thumbnailFile,
            @Parameter(description = "Course price") @RequestParam(required = false) java.math.BigDecimal price,
            @Parameter(description = "Currency") @RequestParam(required = false) String currency) {

        log.info("Updating course {} by user {}", courseId, actorId);

        // Handle thumbnail file upload if provided
        Long thumbnailMediaId = null;
        if (thumbnailFile != null && !thumbnailFile.isEmpty()) {
            try {
                log.info("Uploading new thumbnail file: {}", thumbnailFile.getOriginalFilename());

                // Upload to Cloudinary
                String folder = "skillverse/user_" + actorId;
                Map<String, Object> uploadResult = cloudinaryService.uploadImage(thumbnailFile, folder);

                // Extract Cloudinary response data
                String publicUrl = (String) uploadResult.get("url");
                String publicId = (String) uploadResult.get("public_id");
                String resourceType = (String) uploadResult.get("resource_type");

                // Create Media entity
                Media thumbnail = new Media();
                thumbnail.setUrl(publicUrl);
                thumbnail.setType(thumbnailFile.getContentType());
                thumbnail.setFileName(thumbnailFile.getOriginalFilename());
                thumbnail.setFileSize(thumbnailFile.getSize());
                thumbnail.setUploadedBy(actorId);
                thumbnail.setUploadedAt(LocalDateTime.now());
                thumbnail.setCloudinaryPublicId(publicId);
                thumbnail.setCloudinaryResourceType(resourceType);

                // Save thumbnail to database
                Media savedThumbnail = mediaRepository.save(thumbnail);
                thumbnailMediaId = savedThumbnail.getId();
                log.info("Thumbnail uploaded successfully: {} - {}", savedThumbnail.getId(), savedThumbnail.getUrl());
            } catch (Exception e) {
                log.error("Failed to upload thumbnail: {}", e.getMessage());
                throw new MediaOperationException("Thumbnail upload failed: " + e.getMessage(), e);
            }
        }

        CourseUpdateDTO dto = new CourseUpdateDTO();
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setLevel(level);
        dto.setThumbnailMediaId(thumbnailMediaId);
        dto.setPrice(price);
        dto.setCurrency(currency);

        CourseDetailDTO updated = courseService.updateCourse(courseId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasAuthority('MENTOR') or hasAuthority('ADMIN')")
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

    @GetMapping("/by-author/{authorId}")
    @Operation(summary = "List courses by author")
    public ResponseEntity<PageResponse<CourseSummaryDTO>> listCoursesByAuthor(
            @Parameter(description = "Author user ID") @PathVariable @NotNull Long authorId,
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("Listing courses by author: {}", authorId);
        PageResponse<CourseSummaryDTO> courses = courseService.listCoursesByAuthor(authorId, pageable);
        return ResponseEntity.ok(courses);
    }

    // ========== Admin-only Course Approval Endpoints ==========

    @PostMapping("/{courseId}/submit")
    @PreAuthorize("hasAuthority('MENTOR') or hasAuthority('ADMIN')")
    @Operation(summary = "Submit course for admin approval")
    public ResponseEntity<CourseDetailDTO> submitCourseForApproval(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {

        log.info("Submitting course {} for approval by user {}", courseId, actorId);
        CourseDetailDTO submitted = courseService.submitCourseForApproval(courseId, actorId);
        return ResponseEntity.ok(submitted);
    }

    @PostMapping("/{courseId}/approve")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Approve a course (Admin only)")
    public ResponseEntity<CourseDetailDTO> approveCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Admin user ID") @RequestParam @NotNull Long adminId) {

        log.info("Admin {} approving course {}", adminId, courseId);
        CourseDetailDTO approved = courseService.approveCourse(courseId, adminId);
        return ResponseEntity.ok(approved);
    }

    @PostMapping("/{courseId}/reject")
    @PreAuthorize("hasAuthority('ADMIN')")
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
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "List courses pending approval (Admin only)")
    public ResponseEntity<PageResponse<CourseSummaryDTO>> listPendingCourses(
            @PageableDefault(size = 20) Pageable pageable) {

        PageResponse<CourseSummaryDTO> pendingCourses = courseService.listCoursesByStatus(CourseStatus.PENDING,
                pageable);
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