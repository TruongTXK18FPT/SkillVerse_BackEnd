package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.codingdto.*;
import com.exe.skillverse_backend.course_service.service.CodelabService;
import com.exe.skillverse_backend.shared.dto.PageResponse;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/codelabs")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Codelab Management", description = "APIs for managing coding exercises, test cases, and submissions")
public class CodelabController {

    private final CodelabService codelabService;

    // ========== Exercise Management ==========
    @PostMapping
    @Operation(summary = "Create a new coding exercise for a lesson")
    public ResponseEntity<CodingExerciseDetailDTO> createExercise(
            @Parameter(description = "Lesson ID") @RequestParam @NotNull Long lessonId,
            @Parameter(description = "Exercise creation data") @Valid @RequestBody CodingExerciseCreateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Creating coding exercise for lesson {} by user {}", lessonId, actorId);
        CodingExerciseDetailDTO created = codelabService.createExercise(lessonId, dto, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{exerciseId}")
    @Operation(summary = "Update an existing coding exercise")
    public ResponseEntity<CodingExerciseDetailDTO> updateExercise(
            @Parameter(description = "Exercise ID") @PathVariable @NotNull Long exerciseId,
            @Parameter(description = "Exercise update data") @Valid @RequestBody CodingExerciseUpdateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Updating coding exercise {} by user {}", exerciseId, actorId);
        CodingExerciseDetailDTO updated = codelabService.updateExercise(exerciseId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{exerciseId}")
    @Operation(summary = "Delete a coding exercise")
    public ResponseEntity<Void> deleteExercise(
            @Parameter(description = "Exercise ID") @PathVariable @NotNull Long exerciseId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Deleting coding exercise {} by user {}", exerciseId, actorId);
        codelabService.deleteExercise(exerciseId, actorId);
        return ResponseEntity.noContent().build();
    }

    // ========== Test Case Management ==========
    @PostMapping("/{exerciseId}/test-cases")
    @Operation(summary = "Add a new test case to a coding exercise")
    public ResponseEntity<CodingTestCaseDTO> addTestCase(
            @Parameter(description = "Exercise ID") @PathVariable @NotNull Long exerciseId,
            @Parameter(description = "Test case creation data") @Valid @RequestBody CodingTestCaseCreateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Adding test case to exercise {} by user {}", exerciseId, actorId);
        CodingTestCaseDTO created = codelabService.addTestCase(exerciseId, dto, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/test-cases/{testCaseId}")
    @Operation(summary = "Update a test case")
    public ResponseEntity<CodingTestCaseDTO> updateTestCase(
            @Parameter(description = "Test case ID") @PathVariable @NotNull Long testCaseId,
            @Parameter(description = "Test case update data") @Valid @RequestBody CodingTestCaseUpdateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Updating test case {} by user {}", testCaseId, actorId);
        CodingTestCaseDTO updated = codelabService.updateTestCase(testCaseId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/test-cases/{testCaseId}")
    @Operation(summary = "Delete a test case")
    public ResponseEntity<Void> deleteTestCase(
            @Parameter(description = "Test case ID") @PathVariable @NotNull Long testCaseId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Deleting test case {} by user {}", testCaseId, actorId);
        codelabService.deleteTestCase(testCaseId, actorId);
        return ResponseEntity.noContent().build();
    }

    // ========== Submission Management ==========
    @PostMapping("/{exerciseId}/submissions")
    @Operation(summary = "Submit a coding solution")
    public ResponseEntity<CodingSubmissionDetailDTO> submitSolution(
            @Parameter(description = "Exercise ID") @PathVariable @NotNull Long exerciseId,
            @Parameter(description = "User ID") @RequestParam @NotNull Long userId,
            @Parameter(description = "Submission data") @Valid @RequestBody CodingSubmissionCreateDTO dto) {
        
        log.info("User {} submitting solution for exercise {}", userId, exerciseId);
        CodingSubmissionDetailDTO submission = codelabService.submit(exerciseId, userId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(submission);
    }

    @GetMapping("/{exerciseId}/submissions")
    @Operation(summary = "List submissions for a coding exercise")
    public ResponseEntity<PageResponse<CodingSubmissionDetailDTO>> listSubmissions(
            @Parameter(description = "Exercise ID") @PathVariable @NotNull Long exerciseId,
            @PageableDefault(size = 20) Pageable pageable) {
        
        PageResponse<CodingSubmissionDetailDTO> submissions = codelabService.listSubmissions(exerciseId, pageable);
        return ResponseEntity.ok(submissions);
    }
}