package com.exe.skillverse_backend.course_service.controller;

import com.exe.skillverse_backend.course_service.dto.quizdto.*;
import com.exe.skillverse_backend.course_service.service.QuizService;
import com.exe.skillverse_backend.shared.util.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quizzes")
@RequiredArgsConstructor
@Slf4j
@Validated
@Tag(name = "Quiz Management", description = "APIs for managing quizzes, questions, and options")
public class QuizController {

    private final QuizService quizService;

    private Long extractUserId(Jwt jwt) {
        return JwtUtils.extractUserId(jwt);
    }

    // ========== Quiz Management ==========
    @PostMapping
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Create a new quiz for a module")
    public ResponseEntity<QuizDetailDTO> createQuiz(
            @Parameter(description = "Module ID") @RequestParam @NotNull Long moduleId,
            @Parameter(description = "Quiz creation data") @Valid @RequestBody QuizCreateDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Creating quiz for module {} by user {}", moduleId, actorId);
        QuizDetailDTO created = quizService.createQuiz(moduleId, dto, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{quizId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Update an existing quiz")
    public ResponseEntity<QuizDetailDTO> updateQuiz(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @Parameter(description = "Quiz update data") @Valid @RequestBody QuizUpdateDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Updating quiz {} by user {}", quizId, actorId);
        QuizDetailDTO updated = quizService.updateQuiz(quizId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{quizId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Delete a quiz")
    public ResponseEntity<Void> deleteQuiz(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Deleting quiz {} by user {}", quizId, actorId);
        quizService.deleteQuiz(quizId, actorId);
        return ResponseEntity.noContent().build();
    }

    // ========== Question Management ==========
    @PostMapping("/{quizId}/questions")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Add a new question to a quiz")
    public ResponseEntity<QuizQuestionDetailDTO> addQuestion(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @Parameter(description = "Question creation data") @Valid @RequestBody QuizQuestionCreateDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Adding question to quiz {} by user {}", quizId, actorId);
        QuizQuestionDetailDTO created = quizService.addQuestion(quizId, dto, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/questions/{questionId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Update a quiz question")
    public ResponseEntity<QuizQuestionDetailDTO> updateQuestion(
            @Parameter(description = "Question ID") @PathVariable @NotNull Long questionId,
            @Parameter(description = "Question update data") @Valid @RequestBody QuizQuestionUpdateDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Updating question {} by user {}", questionId, actorId);
        QuizQuestionDetailDTO updated = quizService.updateQuestion(questionId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/questions/{questionId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Delete a quiz question")
    public ResponseEntity<Void> deleteQuestion(
            @Parameter(description = "Question ID") @PathVariable @NotNull Long questionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Deleting question {} by user {}", questionId, actorId);
        quizService.deleteQuestion(questionId, actorId);
        return ResponseEntity.noContent().build();
    }

    // ========== Option Management ==========
    @PostMapping("/questions/{questionId}/options")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Add a new option to a question")
    public ResponseEntity<QuizOptionDetailDTO> addOption(
            @Parameter(description = "Question ID") @PathVariable @NotNull Long questionId,
            @Parameter(description = "Option creation data") @Valid @RequestBody QuizOptionCreateDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Adding option to question {} by user {}", questionId, actorId);
        QuizOptionDetailDTO created = quizService.addOption(questionId, dto, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/options/{optionId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Update a quiz option")
    public ResponseEntity<QuizOptionDetailDTO> updateOption(
            @Parameter(description = "Option ID") @PathVariable @NotNull Long optionId,
            @Parameter(description = "Option update data") @Valid @RequestBody QuizOptionUpdateDTO dto,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Updating option {} by user {}", optionId, actorId);
        QuizOptionDetailDTO updated = quizService.updateOption(optionId, dto, actorId);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/options/{optionId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
    @Operation(summary = "Delete a quiz option")
    public ResponseEntity<Void> deleteOption(
            @Parameter(description = "Option ID") @PathVariable @NotNull Long optionId,
            @AuthenticationPrincipal Jwt jwt) {

        Long actorId = extractUserId(jwt);
        log.info("Deleting option {} by user {}", optionId, actorId);
        quizService.deleteOption(optionId, actorId);
        return ResponseEntity.noContent().build();
    }

    // ========== Quiz Query Operations ==========

    @GetMapping("/{quizId}")
    @PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN') or hasRole('CONTENT_ADMIN')")
    @Operation(summary = "Get quiz details by ID")
    public ResponseEntity<QuizDetailDTO> getQuiz(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId) {

        log.info("Getting quiz details for {}", quizId);
        QuizDetailDTO quiz = quizService.getQuiz(quizId);
        return ResponseEntity.ok(quiz);
    }

    @GetMapping("/{quizId}/attempt-view")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get learner-safe quiz details for attempts")
    public ResponseEntity<QuizDetailDTO> getQuizForAttempt(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = extractUserId(jwt);
        log.info("Getting learner-safe quiz details for {}", quizId);
        QuizDetailDTO quiz = quizService.getQuizForAttempt(quizId, userId);
        return ResponseEntity.ok(quiz);
    }

    @PostMapping("/{quizId}/attempt-session/start")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Start (or resume) quiz attempt session for in-progress guard")
    public ResponseEntity<QuizAttemptSessionDTO> startAttemptSession(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = extractUserId(jwt);
        QuizAttemptSessionDTO session = quizService.startAttemptSession(quizId, userId);
        return ResponseEntity.ok(session);
    }

    @PostMapping("/{quizId}/attempt-session/heartbeat")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Refresh active quiz attempt session")
    public ResponseEntity<QuizAttemptSessionDTO> heartbeatAttemptSession(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @Valid @RequestBody QuizAttemptSessionHeartbeatDTO heartbeat,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = extractUserId(jwt);
        QuizAttemptSessionDTO session = quizService.heartbeatAttemptSession(quizId, userId, heartbeat.getSessionToken());
        return ResponseEntity.ok(session);
    }

    @GetMapping("/modules/{moduleId}/quizzes")
    @Operation(summary = "List quizzes by module")
    public ResponseEntity<List<QuizSummaryDTO>> listQuizzesByModule(
            @Parameter(description = "Module ID") @PathVariable @NotNull Long moduleId) {

        log.info("Listing quizzes for module {}", moduleId);
        List<QuizSummaryDTO> quizzes = quizService.listQuizzesByModule(moduleId);
        return ResponseEntity.ok(quizzes);
    }

    // ========== Quiz Attempt & Submission ==========

    @PostMapping("/{quizId}/submit")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit quiz answers and get result")
    public ResponseEntity<?> submitQuiz(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @Parameter(description = "Quiz submission data") @Valid @RequestBody SubmitQuizDTO submitData,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = extractUserId(jwt);

        log.info("[QUIZ_SUBMIT] User {} submitting quiz {}", userId, quizId);
        QuizAttemptDTO attempt = quizService.submitQuiz(quizId, submitData, userId);

        log.info("[QUIZ_SUBMIT] Result: score={}, passed={}", attempt.getScore(), attempt.getPassed());

        return ResponseEntity.ok(Map.of(
                "score", attempt.getScore(),
                "passed", attempt.getPassed(),
                "correctCount", attempt.getCorrectAnswers(),
                "totalQuestions", attempt.getTotalQuestions(),
                "attempt", attempt));
    }

    @GetMapping("/{quizId}/attempts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get user's quiz attempts")
    public ResponseEntity<List<QuizAttemptDTO>> getUserAttempts(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = extractUserId(jwt);

        log.info("Getting attempts for quiz {} by user {}", quizId, userId);
        List<QuizAttemptDTO> attempts = quizService.getUserAttempts(quizId, userId);
        return ResponseEntity.ok(attempts);
    }

    @GetMapping("/{quizId}/my-latest-review")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get latest quiz review for current user")
    public ResponseEntity<QuizAttemptReviewDTO> getMyLatestReview(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = extractUserId(jwt);
        log.info("Getting latest review for quiz {} by user {}", quizId, userId);
        return ResponseEntity.ok(quizService.getMyLatestReview(quizId, userId));
    }

    @PostMapping("/attempts/batch")
    @Operation(summary = "Batch get quiz attempts for a user")
    public ResponseEntity<List<QuizAttemptDTO>> getUserAttemptsBatch(
            @Valid @RequestBody QuizAttemptBatchRequestDTO request) {

        List<QuizAttemptDTO> attempts = quizService.getUserAttemptsBatch(request.getQuizIds(), request.getUserId());
        return ResponseEntity.ok(attempts);
    }

    @GetMapping("/{quizId}/attempt-status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get user's quiz attempt status with retry info")
    public ResponseEntity<QuizAttemptStatusDTO> getAttemptStatus(
            @Parameter(description = "Quiz ID") @PathVariable @NotNull Long quizId,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = extractUserId(jwt);

        log.info("Getting attempt status for quiz {} by user {}", quizId, userId);
        QuizAttemptStatusDTO status = quizService.getAttemptStatus(quizId, userId);
        return ResponseEntity.ok(status);
    }
}
