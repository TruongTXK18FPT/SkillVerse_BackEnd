package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDTO;
import com.exe.skillverse_backend.course_service.entity.enums.CourseRevisionStatus;
import com.exe.skillverse_backend.course_service.service.CourseRevisionService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

@RestController
@RequestMapping("/api/admin/course-revisions")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Admin Course Revision", description = "Admin APIs for revision approval workflow")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
public class AdminCourseRevisionController {

    private final CourseRevisionService courseRevisionService;

    @GetMapping
    @Operation(summary = "List course revision queue for admin")
    public ResponseEntity<PageResponse<CourseRevisionDTO>> listRevisions(
            @Parameter(description = "Revision status filter (defaults to PENDING)")
            @RequestParam(required = false) CourseRevisionStatus status,
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.ASC) Pageable pageable) {

        CourseRevisionStatus effectiveStatus = status == null ? CourseRevisionStatus.PENDING : status;
        PageResponse<CourseRevisionDTO> revisions =
                courseRevisionService.listAdminRevisions(effectiveStatus, pageable);
        return ResponseEntity.ok(revisions);
    }

    @PostMapping("/{revisionId}/approve")
    @Operation(summary = "Approve a pending course revision")
    public ResponseEntity<CourseRevisionDTO> approveRevision(
            @Parameter(description = "Revision ID") @PathVariable @NotNull Long revisionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = JwtUtils.extractUserId(jwt);
        log.info("Admin {} approving course revision {}", adminId, revisionId);
        CourseRevisionDTO approved = courseRevisionService.approveRevision(revisionId, adminId);
        return ResponseEntity.ok(approved);
    }

    @PostMapping("/{revisionId}/reject")
    @Operation(summary = "Reject a pending course revision")
    public ResponseEntity<CourseRevisionDTO> rejectRevision(
            @Parameter(description = "Revision ID") @PathVariable @NotNull Long revisionId,
            @Parameter(description = "Reject reason") @RequestParam(required = false) String reason,
            @AuthenticationPrincipal Jwt jwt) {

        Long adminId = JwtUtils.extractUserId(jwt);
        log.info("Admin {} rejecting course revision {} with reason: {}", adminId, revisionId, reason);
        CourseRevisionDTO rejected = courseRevisionService.rejectRevision(revisionId, adminId, reason);
        return ResponseEntity.ok(rejected);
    }
}
