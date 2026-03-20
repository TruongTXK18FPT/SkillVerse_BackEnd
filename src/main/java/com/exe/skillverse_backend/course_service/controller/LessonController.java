package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonCreateDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonDetailDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonUpdateDTO;
import com.exe.skillverse_backend.course_service.service.LessonService;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/api/lessons")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Lesson Management", description = "APIs for managing course lessons")
public class LessonController {

    private final LessonService lessonService;

    private Long extractUserId(Jwt jwt) {
        return JwtUtils.extractUserId(jwt);
    }

    @PostMapping
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Add a new lesson to a module")
    public ResponseEntity<LessonBriefDTO> addLesson(
            @Parameter(description = "Module ID") @RequestParam @NotNull Long moduleId,
            @Parameter(description = "Lesson creation data") @Valid @RequestBody LessonCreateDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Adding lesson to module {} by user {}", moduleId, actorId);
        LessonBriefDTO created = lessonService.addLesson(moduleId, dto, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{lessonId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Update an existing lesson")
    public ResponseEntity<LessonBriefDTO> updateLesson(
            @Parameter(description = "Lesson ID") @PathVariable @NotNull Long lessonId,
            @Parameter(description = "Lesson update data") @Valid @RequestBody LessonUpdateDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Updating lesson {} by user {}", lessonId, actorId);
        LessonBriefDTO updated = lessonService.updateLesson(lessonId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{lessonId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Delete a lesson")
    public ResponseEntity<Void> deleteLesson(
            @Parameter(description = "Lesson ID") @PathVariable @NotNull Long lessonId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Deleting lesson {} by user {}", lessonId, actorId);
        lessonService.deleteLesson(lessonId, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/modules/{moduleId}/lessons")
    @Operation(summary = "List lessons by module")
    public ResponseEntity<List<LessonBriefDTO>> listLessonsByModule(
            @Parameter(description = "Module ID") @PathVariable @NotNull Long moduleId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        List<LessonBriefDTO> lessons = lessonService.listLessonsByModule(moduleId, actorId);
        return ResponseEntity.ok(lessons);
    }

    @GetMapping("/{lessonId}")
    @Operation(summary = "Get lesson detail by ID")
    public ResponseEntity<LessonDetailDTO> getLessonById(
            @Parameter(description = "Lesson ID") @PathVariable @NotNull Long lessonId,
            @AuthenticationPrincipal Jwt jwt) {
        Long actorId = extractUserId(jwt);
        LessonDetailDTO lesson = lessonService.getLesson(lessonId, actorId);
        return ResponseEntity.ok(lesson);
    }

    @GetMapping("/modules/{moduleId}/lessons/{lessonId}/next")
    @Operation(summary = "Get next lesson in a module")
    public ResponseEntity<LessonBriefDTO> getNextLesson(
            @PathVariable @NotNull Long moduleId,
            @PathVariable @NotNull Long lessonId,
            @AuthenticationPrincipal Jwt jwt) {
        Long actorId = extractUserId(jwt);
        LessonBriefDTO next = lessonService.getNextLesson(moduleId, lessonId, actorId);
        return ResponseEntity.ok(next);
    }

    @GetMapping("/modules/{moduleId}/lessons/{lessonId}/prev")
    @Operation(summary = "Get previous lesson in a module")
    public ResponseEntity<LessonBriefDTO> getPrevLesson(
            @PathVariable @NotNull Long moduleId,
            @PathVariable @NotNull Long lessonId,
            @AuthenticationPrincipal Jwt jwt) {
        Long actorId = extractUserId(jwt);
        LessonBriefDTO prev = lessonService.getPreviousLesson(moduleId, lessonId, actorId);
        return ResponseEntity.ok(prev);
    }

    @PutMapping("/modules/{moduleId}/lessons/{lessonId}/complete")
    @Operation(summary = "Mark lesson as completed for a user")
    public ResponseEntity<Void> completeLesson(
            @PathVariable @NotNull Long moduleId,
            @PathVariable @NotNull Long lessonId,
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Long userId) {
        Long actorId = extractUserId(jwt);
        if (userId != null && !userId.equals(actorId)) {
            throw new AccessDeniedException("FORBIDDEN");
        }
        lessonService.markLessonCompleted(moduleId, lessonId, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/progress/course/{courseId}/user/{userId}/completed-ids")
    @Operation(summary = "Get completed lesson IDs for a user in a course")
    public ResponseEntity<List<Long>> getCompletedLessonIds(
            @PathVariable @NotNull Long courseId,
            @PathVariable @NotNull Long userId,
            @AuthenticationPrincipal Jwt jwt) {
        Long actorId = extractUserId(jwt);
        if (!userId.equals(actorId)) {
            throw new AccessDeniedException("FORBIDDEN");
        }
        return ResponseEntity.ok(lessonService.listCompletedLessonIds(courseId, actorId));
    }
}
