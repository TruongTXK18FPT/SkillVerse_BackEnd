package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionDiffDTO;
import com.exe.skillverse_backend.course_service.dto.coursedto.CourseRevisionUpdateDTO;
import com.exe.skillverse_backend.course_service.service.CourseRevisionService;
import com.exe.skillverse_backend.course_service.service.impl.CourseRevisionDiffService;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/course-revisions")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Course Revision", description = "Mentor APIs for revision workflow")
public class CourseRevisionController {

    private final CourseRevisionService courseRevisionService;
    private final CourseRevisionDiffService courseRevisionDiffService;

    @GetMapping("/{revisionId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Get revision detail (mentor/admin)")
    public ResponseEntity<CourseRevisionDTO> getRevision(
            @Parameter(description = "Revision ID") @PathVariable @NotNull Long revisionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = JwtUtils.extractUserId(jwt);
        CourseRevisionDTO revision = courseRevisionService.getRevision(revisionId, actorId);
        return ResponseEntity.ok(revision);
    }

    @GetMapping("/{revisionId}/diff")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get revision diff (mentor/admin)")
    public ResponseEntity<CourseRevisionDiffDTO> getRevisionDiff(
            @Parameter(description = "Revision ID") @PathVariable @NotNull Long revisionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = JwtUtils.extractUserId(jwt);
        log.debug("User {} requesting diff for revision {}", actorId, revisionId);
        CourseRevisionDiffDTO diff = courseRevisionDiffService.computeDiff(revisionId);
        return ResponseEntity.ok(diff);
    }

    @PostMapping("/{revisionId}/submit")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Submit course revision for admin review")
    public ResponseEntity<CourseRevisionDTO> submitRevision(
            @Parameter(description = "Revision ID") @PathVariable @NotNull Long revisionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = JwtUtils.extractUserId(jwt);
        log.info("Submitting course revision {} by user {}", revisionId, actorId);
        CourseRevisionDTO submitted = courseRevisionService.submitRevision(revisionId, actorId);
        return ResponseEntity.ok(submitted);
    }

    @PutMapping("/{revisionId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Edit a draft/rejected course revision")
    public ResponseEntity<CourseRevisionDTO> updateRevision(
            @Parameter(description = "Revision ID") @PathVariable @NotNull Long revisionId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody CourseRevisionUpdateDTO dto) {

        Long actorId = JwtUtils.extractUserId(jwt);
        log.info("Updating course revision {} by user {}", revisionId, actorId);
        CourseRevisionDTO updated = courseRevisionService.updateRevision(revisionId, dto, actorId);
        return ResponseEntity.ok(updated);
    }
}
