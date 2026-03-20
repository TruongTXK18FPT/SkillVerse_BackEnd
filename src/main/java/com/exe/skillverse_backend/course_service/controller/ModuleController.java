package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.assignmentdto.AssignmentSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.lessondto.LessonBriefDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleCreateDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleDetailDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleProgressDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleSummaryDTO;
import com.exe.skillverse_backend.course_service.dto.moduledto.ModuleUpdateDTO;
import com.exe.skillverse_backend.course_service.service.AssignmentService;
import com.exe.skillverse_backend.course_service.service.LessonService;
import com.exe.skillverse_backend.course_service.service.ModuleService;
import com.exe.skillverse_backend.shared.exception.AccessDeniedException;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
@Tag(name = "Modules", description = "APIs for managing course modules")
public class ModuleController {

  private final ModuleService moduleService;
  private final LessonService lessonService;
  private final AssignmentService assignmentService;

  private Long extractUserId(Jwt jwt) {
    return JwtUtils.extractUserId(jwt);
  }

  @PostMapping("/courses/{courseId}/modules")
  @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
  @Operation(summary = "Create a module under a course")
  public ResponseEntity<ModuleDetailDTO> createModule(
      @PathVariable @NotNull Long courseId,
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Long actorId,
      @Valid @RequestBody ModuleCreateDTO dto) {
    Long effectiveActorId = extractUserId(jwt);
    if (actorId != null && !actorId.equals(effectiveActorId)) {
      throw new AccessDeniedException("FORBIDDEN");
    }
    ModuleDetailDTO created = moduleService.createModule(courseId, dto, effectiveActorId);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/modules/{moduleId}")
  @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
  @Operation(summary = "Update a module")
  public ResponseEntity<ModuleDetailDTO> updateModule(
      @PathVariable @NotNull Long moduleId,
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Long actorId,
      @Valid @RequestBody ModuleUpdateDTO dto) {
    Long effectiveActorId = extractUserId(jwt);
    if (actorId != null && !actorId.equals(effectiveActorId)) {
      throw new AccessDeniedException("FORBIDDEN");
    }
    ModuleDetailDTO updated = moduleService.updateModule(moduleId, dto, effectiveActorId);
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/modules/{moduleId}")
  @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
  @Operation(summary = "Delete a module")
  public ResponseEntity<Void> deleteModule(
      @PathVariable @NotNull Long moduleId,
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Long actorId) {
    Long effectiveActorId = extractUserId(jwt);
    if (actorId != null && !actorId.equals(effectiveActorId)) {
      throw new AccessDeniedException("FORBIDDEN");
    }
    moduleService.deleteModule(moduleId, effectiveActorId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/courses/{courseId}/modules")
  @Operation(summary = "List modules of a course")
  public ResponseEntity<List<ModuleSummaryDTO>> listModules(
      @PathVariable @NotNull Long courseId,
      @AuthenticationPrincipal Jwt jwt) {
    Long actorId = extractUserId(jwt);
    return ResponseEntity.ok(moduleService.listModules(courseId, actorId));
  }

  @GetMapping("/courses/{courseId}/modules/full")
  @Operation(summary = "List all modules with lessons, quizzes, and assignments in one request")
  public ResponseEntity<List<ModuleDetailDTO>> listModulesWithContent(
      @PathVariable @NotNull Long courseId,
      @AuthenticationPrincipal Jwt jwt) {
    Long actorId = extractUserId(jwt);
    return ResponseEntity.ok(moduleService.listModulesWithContent(courseId, actorId));
  }

  @GetMapping("/modules/{moduleId}")
  @Operation(summary = "Get module detail with lessons, quizzes, and assignments")
  public ResponseEntity<ModuleDetailDTO> getModuleDetail(
      @PathVariable @NotNull Long moduleId,
      @AuthenticationPrincipal Jwt jwt) {
    Long actorId = extractUserId(jwt);
    ModuleDetailDTO detail = moduleService.getModuleDetail(moduleId, actorId);
    return ResponseEntity.ok(detail);
  }

  @PostMapping("/modules/{moduleId}/assign-lesson/{lessonId}")
  @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
  @Operation(summary = "Assign a lesson to module")
  public ResponseEntity<Void> assignLesson(
      @PathVariable @NotNull Long moduleId,
      @PathVariable @NotNull Long lessonId,
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Long actorId) {
    Long effectiveActorId = extractUserId(jwt);
    if (actorId != null && !actorId.equals(effectiveActorId)) {
      throw new AccessDeniedException("FORBIDDEN");
    }
    moduleService.assignLesson(moduleId, lessonId, effectiveActorId);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/modules/{moduleId}/lessons")
  @Operation(summary = "List lessons in a module")
  public ResponseEntity<List<LessonBriefDTO>> listLessonsByModule(
      @PathVariable @NotNull Long moduleId,
      @AuthenticationPrincipal Jwt jwt) {
    Long actorId = extractUserId(jwt);
    List<LessonBriefDTO> lessons = lessonService.listLessonsByModule(moduleId, actorId);
    return ResponseEntity.ok(lessons);
  }

  @GetMapping("/modules/{moduleId}/progress")
  @Operation(summary = "Get module progress for a user")
  public ResponseEntity<ModuleProgressDTO> getModuleProgress(
      @PathVariable @NotNull Long moduleId,
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(required = false) Long userId) {
    Long actorId = extractUserId(jwt);
    if (userId != null && !userId.equals(actorId)) {
      throw new AccessDeniedException("FORBIDDEN");
    }
    return ResponseEntity.ok(moduleService.getProgress(moduleId, actorId));
  }

  @GetMapping("/modules/{moduleId}/assignments")
  @Operation(summary = "List assignments in a module")
  public ResponseEntity<List<AssignmentSummaryDTO>> listAssignmentsByModule(
      @PathVariable @NotNull Long moduleId,
      @AuthenticationPrincipal Jwt jwt) {
    Long actorId = extractUserId(jwt);
    List<AssignmentSummaryDTO> assignments = assignmentService.listAssignmentsByModule(moduleId, actorId);
    return ResponseEntity.ok(assignments);
  }
}
