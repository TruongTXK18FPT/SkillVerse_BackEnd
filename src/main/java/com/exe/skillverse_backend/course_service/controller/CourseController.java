package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseCreateDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseDetailDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseUpdateDTO;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.service.CourseRevisionService;
import com.exe.skillverse_backend.course_service.service.CourseService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Course Management", description = "APIs for managing courses")
public class CourseController {

    private final CourseService courseService;
    private final CourseRevisionService courseRevisionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Create a new course")
    public ResponseEntity<CourseDetailDTO> createCourse(
            @Parameter(description = "Author user ID") @RequestParam @NotNull Long authorId,
            @Parameter(description = "Course title") @RequestParam @NotBlank String title,
            @Parameter(description = "Course description") @RequestParam(required = false) String description,
            @Parameter(description = "Course level") @RequestParam(required = false) String level,
            @Parameter(description = "Course category") @RequestParam(required = false) String category,
            @Parameter(description = "Short description") @RequestParam(required = false) String shortDescription,
            @Parameter(description = "Estimated duration (hours)") @RequestParam(required = false) Integer estimatedDurationHours,
            @Parameter(description = "Course language") @RequestParam(required = false) String language,
            @Parameter(description = "Learning objectives") @RequestParam(required = false) List<String> learningObjectives,
            @Parameter(description = "Course requirements") @RequestParam(required = false) List<String> requirements,
            @Parameter(description = "Thumbnail file") @RequestParam(required = false) MultipartFile thumbnailFile,
            @Parameter(description = "Course price") @RequestParam(required = false) BigDecimal price,
            @Parameter(description = "Currency") @RequestParam(required = false) String currency) {

        log.info("Creating course by author: {}", authorId);

        CourseCreateDTO dto = new CourseCreateDTO();
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setLevel(level);
        dto.setCategory(category);
        dto.setShortDescription(shortDescription);
        dto.setEstimatedDurationHours(estimatedDurationHours);
        dto.setLanguage(language);
        dto.setLearningObjectives(learningObjectives);
        dto.setRequirements(requirements);
        dto.setPrice(price);
        dto.setCurrency(currency);

        CourseDetailDTO created = courseService.createCourse(authorId, dto, thumbnailFile);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(value = "/{courseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Update an existing course")
    public ResponseEntity<CourseDetailDTO> updateCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId,
            @Parameter(description = "Course title") @RequestParam @NotBlank String title,
            @Parameter(description = "Course description") @RequestParam(required = false) String description,
            @Parameter(description = "Course level") @RequestParam(required = false) String level,
            @Parameter(description = "Course category") @RequestParam(required = false) String category,
            @Parameter(description = "Short description") @RequestParam(required = false) String shortDescription,
            @Parameter(description = "Estimated duration (hours)") @RequestParam(required = false) Integer estimatedDurationHours,
            @Parameter(description = "Course language") @RequestParam(required = false) String language,
            @Parameter(description = "Learning objectives") @RequestParam(required = false) List<String> learningObjectives,
            @Parameter(description = "Course requirements") @RequestParam(required = false) List<String> requirements,
            @Parameter(description = "Thumbnail file") @RequestParam(required = false) MultipartFile thumbnailFile,
            @Parameter(description = "Course price") @RequestParam(required = false) BigDecimal price,
            @Parameter(description = "Currency") @RequestParam(required = false) String currency) {

        log.info("Updating course {} by user {}", courseId, actorId);

        CourseUpdateDTO dto = new CourseUpdateDTO();
        dto.setTitle(title);
        dto.setDescription(description);
        dto.setLevel(level);
        dto.setCategory(category);
        dto.setShortDescription(shortDescription);
        dto.setEstimatedDurationHours(estimatedDurationHours);
        dto.setLanguage(language);
        dto.setLearningObjectives(learningObjectives);
        dto.setRequirements(requirements);
        dto.setPrice(price);
        dto.setCurrency(currency);

        CourseDetailDTO updated = courseService.updateCourse(courseId, dto, actorId, thumbnailFile);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{courseId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Archive a course (hard delete only when policy allows)")
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
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = null;
        if (jwt != null) {
            String userId = jwt.getClaimAsString("userId");
            if (userId != null) {
                actorId = Long.valueOf(userId);
            }
        }
        CourseDetailDTO course = courseService.getCourse(courseId, actorId);
        return ResponseEntity.ok(course);
    }

    @GetMapping
    @Operation(summary = "List courses with search and filtering",
            description = "Public endpoint. Non-admin users can only see PUBLIC courses. " +
                    "The status parameter is ignored for non-admin callers.")
    public ResponseEntity<PageResponse<CourseSummaryDTO>> listCourses(
            @Parameter(description = "Search query") @RequestParam(required = false) String q,
            @Parameter(description = "Course status filter (admin only)") @RequestParam(required = false) CourseStatus status,
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20) Pageable pageable) {

        // Security: non-admin users can only see PUBLIC courses
        if (!isAdmin(jwt)) {
            status = CourseStatus.PUBLIC;
        }

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

    // ========== Mentor Workflow Endpoints ==========

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

    @PostMapping("/{courseId}/revisions")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Create a new draft revision for a PUBLIC course")
    public ResponseEntity<CourseRevisionDTO> createRevision(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = JwtUtils.extractUserId(jwt);
        log.info("Creating revision for course {} by user {}", courseId, actorId);
        CourseRevisionDTO revision = courseRevisionService.createRevision(courseId, actorId);
        return ResponseEntity.ok(revision);
    }

    @GetMapping("/{courseId}/revisions")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "List revisions of a course (mentor/admin)")
    public ResponseEntity<PageResponse<CourseRevisionDTO>> listCourseRevisions(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @Parameter(description = "Optional revision status filter")
            @RequestParam(required = false) CourseRevisionStatus status,
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "revisionNumber", direction = Sort.Direction.DESC) Pageable pageable) {

        Long actorId = JwtUtils.extractUserId(jwt);
        PageResponse<CourseRevisionDTO> revisions =
                courseRevisionService.listCourseRevisions(courseId, actorId, status, pageable);
        return ResponseEntity.ok(revisions);
    }

    // ========== Helpers ==========

    /**
     * Check if the current JWT holder has ADMIN or CONTENT_ADMIN role.
     * Used to restrict the public listing endpoint.
     */
    private boolean isAdmin(Jwt jwt) {
        if (jwt == null) return false;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_CONTENT_ADMIN"));
    }

}
