package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleCreateDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleUpdateDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleDetailDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSummaryDTO;
import com.exe.skillverse_backend.course_service.service.ModuleService;
import com.exe.skillverse_backend.course_service.service.LessonService;
import com.exe.skillverse_backend.course_service.service.AssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
@Tag(name = "Modules", description = "APIs for managing course modules")
public class ModuleController {

  private final ModuleService moduleService;
  private final LessonService lessonService;
  private final AssignmentService assignmentService;

  @PostMapping("/courses/{courseId}/modules")
  @PreAuthorize("hasAuthority('MENTOR') or hasAuthority('ADMIN')")
  @Operation(summary = "Create a module under a course")
  public ResponseEntity<ModuleDetailDTO> createModule(
      @PathVariable @NotNull Long courseId,
      @RequestParam @NotNull Long actorId,
      @Valid @RequestBody ModuleCreateDTO dto) {
    ModuleDetailDTO created = moduleService.createModule(courseId, dto, actorId);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/modules/{moduleId}")
  @PreAuthorize("hasAuthority('MENTOR') or hasAuthority('ADMIN')")
  @Operation(summary = "Update a module")
  public ResponseEntity<ModuleDetailDTO> updateModule(
      @PathVariable @NotNull Long moduleId,
      @RequestParam @NotNull Long actorId,
      @Valid @RequestBody ModuleUpdateDTO dto) {
    ModuleDetailDTO updated = moduleService.updateModule(moduleId, dto, actorId);
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/modules/{moduleId}")
  @PreAuthorize("hasAuthority('MENTOR') or hasAuthority('ADMIN')")
  @Operation(summary = "Delete a module")
  public ResponseEntity<Void> deleteModule(
      @PathVariable @NotNull Long moduleId,
      @RequestParam @NotNull Long actorId) {
    moduleService.deleteModule(moduleId, actorId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/courses/{courseId}/modules")
  @Operation(summary = "List modules of a course")
  public ResponseEntity<List<ModuleSummaryDTO>> listModules(
      @PathVariable @NotNull Long courseId) {
    return ResponseEntity.ok(moduleService.listModules(courseId));
  }

  @PostMapping("/modules/{moduleId}/assign-lesson/{lessonId}")
  @PreAuthorize("hasAuthority('MENTOR') or hasAuthority('ADMIN')")
  @Operation(summary = "Assign a lesson to module")
  public ResponseEntity<Void> assignLesson(
      @PathVariable @NotNull Long moduleId,
      @PathVariable @NotNull Long lessonId,
      @RequestParam @NotNull Long actorId) {
    moduleService.assignLesson(moduleId, lessonId, actorId);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/modules/{moduleId}/lessons")
  @Operation(summary = "List lessons in a module")
  public ResponseEntity<List<LessonBriefDTO>> listLessonsByModule(
      @PathVariable @NotNull Long moduleId) {
    List<LessonBriefDTO> lessons = lessonService.listLessonsByModule(moduleId);
    return ResponseEntity.ok(lessons);
  }

  @GetMapping("/modules/{moduleId}/progress")
  @Operation(summary = "Get module progress for a user")
  public ResponseEntity<com.exe.skillverse_backend.course_service.dto.moduledto.ModuleProgressDTO> getModuleProgress(
      @PathVariable @NotNull Long moduleId,
      @RequestParam @NotNull Long userId) {
    return ResponseEntity.ok(moduleService.getProgress(moduleId, userId));
  }

  @GetMapping("/modules/{moduleId}/assignments")
  @Operation(summary = "List assignments in a module")
  public ResponseEntity<List<AssignmentSummaryDTO>> listAssignmentsByModule(
      @PathVariable @NotNull Long moduleId) {
    List<AssignmentSummaryDTO> assignments = assignmentService.listAssignmentsByModule(moduleId);
    return ResponseEntity.ok(assignments);
  }
}
