package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonCreateDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonUpdateDTO;
import com.exe.skillverse_backend.course_service.service.LessonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@RestController
@RequestMapping("/api/lessons")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Lesson Management", description = "APIs for managing course lessons")
public class LessonController {

    private final LessonService lessonService;

    @PostMapping
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Add a new lesson to a course")
    public ResponseEntity<LessonBriefDTO> addLesson(
            @Parameter(description = "Course ID") @RequestParam @NotNull Long courseId,
            @Parameter(description = "Lesson creation data") @Valid @RequestBody LessonCreateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Adding lesson to course {} by user {}", courseId, actorId);
        LessonBriefDTO created = lessonService.addLesson(courseId, dto, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{lessonId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Update an existing lesson")
    public ResponseEntity<LessonBriefDTO> updateLesson(
            @Parameter(description = "Lesson ID") @PathVariable @NotNull Long lessonId,
            @Parameter(description = "Lesson update data") @Valid @RequestBody LessonUpdateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Updating lesson {} by user {}", lessonId, actorId);
        LessonBriefDTO updated = lessonService.updateLesson(lessonId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{lessonId}")
    @Operation(summary = "Delete a lesson")
    public ResponseEntity<Void> deleteLesson(
            @Parameter(description = "Lesson ID") @PathVariable @NotNull Long lessonId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Deleting lesson {} by user {}", lessonId, actorId);
        lessonService.deleteLesson(lessonId, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/course/{courseId}")
    @Operation(summary = "List lessons by course")
    public ResponseEntity<List<LessonBriefDTO>> listLessonsByCourse(
            @Parameter(description = "Course ID") @PathVariable @NotNull Long courseId) {
        
        List<LessonBriefDTO> lessons = lessonService.listLessonsByCourse(courseId);
        return ResponseEntity.ok(lessons);
    }
}