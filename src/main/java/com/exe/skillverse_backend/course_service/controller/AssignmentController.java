package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.assignmentdto.*;
import com.exe.skillverse_backend.course_service.service.AssignmentService;
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
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Assignment Management", description = "APIs for managing assignments and submissions")
public class AssignmentController {

    private final AssignmentService assignmentService;

    // ========== Assignment Management ==========
    @PostMapping
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Create a new assignment for a module")
    public ResponseEntity<AssignmentDetailDTO> createAssignment(
            @Parameter(description = "Module ID") @RequestParam @NotNull Long moduleId,
            @Parameter(description = "Assignment creation data") @Valid @RequestBody AssignmentCreateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Creating assignment for module {} by user {}", moduleId, actorId);
        AssignmentDetailDTO created = assignmentService.createAssignment(moduleId, dto, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{assignmentId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Update an existing assignment")
    public ResponseEntity<AssignmentDetailDTO> updateAssignment(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId,
            @Parameter(description = "Assignment update data") @Valid @RequestBody AssignmentUpdateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Updating assignment {} by user {}", assignmentId, actorId);
        AssignmentDetailDTO updated = assignmentService.updateAssignment(assignmentId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @GetMapping("/{assignmentId}")
    @Operation(summary = "Get assignment details by ID")
    public ResponseEntity<AssignmentDetailDTO> getAssignmentById(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId) {
        
        log.info("Getting assignment details for ID {}", assignmentId);
        AssignmentDetailDTO assignment = assignmentService.getAssignmentById(assignmentId);
        return ResponseEntity.ok(assignment);
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Delete an assignment")
    public ResponseEntity<Void> deleteAssignment(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Deleting assignment {} by user {}", assignmentId, actorId);
        assignmentService.deleteAssignment(assignmentId, actorId);
        return ResponseEntity.noContent().build();
    }

    // ========== Submission Management ==========
    @PostMapping("/{assignmentId}/submissions")
    @Operation(summary = "Submit an assignment")
    public ResponseEntity<AssignmentSubmissionDetailDTO> submitAssignment(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId,
            @Parameter(description = "User ID") @RequestParam @NotNull Long userId,
            @Parameter(description = "Submission data") @Valid @RequestBody AssignmentSubmissionCreateDTO dto) {
        
        log.info("User {} submitting assignment {}", userId, assignmentId);
        AssignmentSubmissionDetailDTO submission = assignmentService.submit(assignmentId, userId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(submission);
    }

    @PutMapping("/submissions/{submissionId}/grade")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Grade an assignment submission")
    public ResponseEntity<AssignmentSubmissionDetailDTO> gradeSubmission(
            @Parameter(description = "Submission ID") @PathVariable @NotNull Long submissionId,
            @Parameter(description = "Grader user ID") @RequestParam @NotNull Long graderId,
            @Parameter(description = "Score") @RequestParam @NotNull BigDecimal score,
            @Parameter(description = "Feedback") @RequestParam(required = false) String feedback) {
        
        log.info("User {} grading submission {} with score {}", graderId, submissionId, score);
        AssignmentSubmissionDetailDTO graded = assignmentService.grade(submissionId, graderId, score, feedback);
        return ResponseEntity.ok(graded);
    }

    @GetMapping("/{assignmentId}/submissions")
    @Operation(summary = "List submissions for an assignment")
    public ResponseEntity<List<AssignmentSubmissionDetailDTO>> listSubmissions(
            @Parameter(description = "Assignment ID") @PathVariable @NotNull Long assignmentId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        List<AssignmentSubmissionDetailDTO> submissions = assignmentService.listSubmissions(assignmentId, pageable);
        return ResponseEntity.ok(submissions);
    }
}