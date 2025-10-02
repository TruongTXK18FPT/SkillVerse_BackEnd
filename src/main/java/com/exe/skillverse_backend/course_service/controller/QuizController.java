package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.quizdto.*;
import com.exe.skillverse_backend.course_service.service.QuizService;
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

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Quiz Management", description = "APIs for managing quizzes, questions, and options")
public class QuizController {

    private final QuizService quizService;

    // ========== Quiz Management ==========
    @PostMapping
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Create a new quiz for a lesson")
    public ResponseEntity<QuizDetailDTO> createQuiz(
            @Parameter(description = "Lesson ID") @RequestParam @NotNull Long lessonId,
            @Parameter(description = "Quiz creation data") @Valid @RequestBody QuizCreateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Creating quiz for lesson {} by user {}", lessonId, actorId);
        QuizDetailDTO created = quizService.createQuiz(lessonId, dto, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{quizId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Update an existing quiz")
    public ResponseEntity<QuizDetailDTO> updateQuiz(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @Parameter(description = "Quiz update data") @Valid @RequestBody QuizUpdateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Updating quiz {} by user {}", quizId, actorId);
        QuizDetailDTO updated = quizService.updateQuiz(quizId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{quizId}")
    @Operation(summary = "Delete a quiz")
    public ResponseEntity<Void> deleteQuiz(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Deleting quiz {} by user {}", quizId, actorId);
        quizService.deleteQuiz(quizId, actorId);
        return ResponseEntity.noContent().build();
    }

    // ========== Question Management ==========
    @PostMapping("/{quizId}/questions")
    @Operation(summary = "Add a new question to a quiz")
    public ResponseEntity<QuizQuestionDetailDTO> addQuestion(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @Parameter(description = "Question creation data") @Valid @RequestBody QuizQuestionCreateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Adding question to quiz {} by user {}", quizId, actorId);
        QuizQuestionDetailDTO created = quizService.addQuestion(quizId, dto, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/questions/{questionId}")
    @Operation(summary = "Update a quiz question")
    public ResponseEntity<QuizQuestionDetailDTO> updateQuestion(
            @Parameter(description = "Question ID") @PathVariable @NotNull Long questionId,
            @Parameter(description = "Question update data") @Valid @RequestBody QuizQuestionUpdateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Updating question {} by user {}", questionId, actorId);
        QuizQuestionDetailDTO updated = quizService.updateQuestion(questionId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/questions/{questionId}")
    @Operation(summary = "Delete a quiz question")
    public ResponseEntity<Void> deleteQuestion(
            @Parameter(description = "Question ID") @PathVariable @NotNull Long questionId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Deleting question {} by user {}", questionId, actorId);
        quizService.deleteQuestion(questionId, actorId);
        return ResponseEntity.noContent().build();
    }

    // ========== Option Management ==========
    @PostMapping("/questions/{questionId}/options")
    @Operation(summary = "Add a new option to a question")
    public ResponseEntity<QuizOptionDetailDTO> addOption(
            @Parameter(description = "Question ID") @PathVariable @NotNull Long questionId,
            @Parameter(description = "Option creation data") @Valid @RequestBody QuizOptionCreateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Adding option to question {} by user {}", questionId, actorId);
        QuizOptionDetailDTO created = quizService.addOption(questionId, dto, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/options/{optionId}")
    @Operation(summary = "Update a quiz option")
    public ResponseEntity<QuizOptionDetailDTO> updateOption(
            @Parameter(description = "Option ID") @PathVariable @NotNull Long optionId,
            @Parameter(description = "Option update data") @Valid @RequestBody QuizOptionUpdateDTO dto,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Updating option {} by user {}", optionId, actorId);
        QuizOptionDetailDTO updated = quizService.updateOption(optionId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/options/{optionId}")
    @Operation(summary = "Delete a quiz option")
    public ResponseEntity<Void> deleteOption(
            @Parameter(description = "Option ID") @PathVariable @NotNull Long optionId,
            @Parameter(description = "Actor user ID") @RequestParam @NotNull Long actorId) {
        
        log.info("Deleting option {} by user {}", optionId, actorId);
        quizService.deleteOption(optionId, actorId);
        return ResponseEntity.noContent().build();
    }
}