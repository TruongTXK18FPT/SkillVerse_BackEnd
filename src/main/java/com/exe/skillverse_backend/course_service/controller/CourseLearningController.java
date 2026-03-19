package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningStatusDTO;
import com.exe.skillverse_backend.course_service.dto.progressdto.CourseLearningRevisionInfoDTO;
import com.exe.skillverse_backend.course_service.service.CourseLearningProgressService;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/course-learning")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Course Learning", description = "APIs for student course-learning progress and completion state")
public class CourseLearningController {

    private final CourseLearningProgressService courseLearningProgressService;

    @GetMapping("/courses/{courseId}/status")
    @Operation(summary = "Get aggregated learning completion state for the current user in a course")
    public ResponseEntity<CourseLearningStatusDTO> getCourseLearningStatus(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = JwtUtils.extractUserId(jwt);
        log.info("Getting course learning status for course {} and user {}", courseId, userId);
        CourseLearningStatusDTO status = courseLearningProgressService.getCourseLearningStatus(courseId, userId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/courses/{courseId}/revision-info")
    @Operation(summary = "Get learner pinned revision and active revision info for a course")
    public ResponseEntity<CourseLearningRevisionInfoDTO> getLearningRevisionInfo(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = JwtUtils.extractUserId(jwt);
        CourseLearningRevisionInfoDTO info = courseLearningProgressService.getLearningRevisionInfo(courseId, userId);
        return ResponseEntity.ok(info);
    }

    @PostMapping("/courses/{courseId}/upgrade-to-active")
    @Operation(summary = "Upgrade current learner pinned revision to course active revision")
    public ResponseEntity<CourseLearningRevisionInfoDTO> upgradeToActiveRevision(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = JwtUtils.extractUserId(jwt);
        CourseLearningRevisionInfoDTO info = courseLearningProgressService.upgradeToActiveRevision(courseId, userId);
        return ResponseEntity.ok(info);
    }
}
