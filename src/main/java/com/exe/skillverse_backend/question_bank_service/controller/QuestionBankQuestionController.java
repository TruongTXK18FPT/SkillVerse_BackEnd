package com.exe.skillverse_backend.question_bank_service.controller;

import com.exe.skillverse_backend.question_bank_service.dto.request.AiGenerateDraftRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.BulkImportRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.UpdateQuestionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.AiDraftResponse;
import com.exe.skillverse_backend.question_bank_service.dto.response.ImportResultResponse;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionResponse;
import com.exe.skillverse_backend.question_bank_service.service.AiDraftGenerationService;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankQuestionService;
import com.exe.skillverse_backend.question_bank_service.service.QuestionImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/question-banks/{bankId}/questions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Question Bank Questions", description = "Manage questions within a question bank")
@PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
public class QuestionBankQuestionController {

    private final QuestionBankQuestionService questionService;
    private final QuestionImportService importService;
    private final AiDraftGenerationService aiDraftService;

    // ==================== Question CRUD ====================

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Add a single question to a bank")
    public ResponseEntity<QuestionResponse> addQuestion(
            @PathVariable Long bankId,
            @Valid @RequestBody CreateQuestionRequest request) {
        return ResponseEntity.ok(questionService.addQuestion(bankId, request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "List questions in a bank")
    public ResponseEntity<Page<QuestionResponse>> listQuestions(
            @PathVariable Long bankId,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String skillArea,
            @RequestParam(required = false) String category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(questionService.listQuestions(bankId, difficulty, skillArea, category, pageable));
    }

    @GetMapping("/{questionId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Get a specific question")
    public ResponseEntity<QuestionResponse> getQuestion(
            @PathVariable Long bankId,
            @PathVariable Long questionId) {
        return ResponseEntity.ok(questionService.getQuestion(bankId, questionId));
    }

    @PutMapping("/{questionId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Update a question")
    public ResponseEntity<QuestionResponse> updateQuestion(
            @PathVariable Long bankId,
            @PathVariable Long questionId,
            @RequestBody UpdateQuestionRequest request) {
        return ResponseEntity.ok(questionService.updateQuestion(bankId, questionId, request));
    }

    @DeleteMapping("/{questionId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Delete (soft) a question")
    public ResponseEntity<Void> deleteQuestion(
            @PathVariable Long bankId,
            @PathVariable Long questionId) {
        questionService.deleteQuestion(bankId, questionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Bulk add questions (from import confirm or AI approve)")
    public ResponseEntity<?> bulkAddQuestions(
            @PathVariable Long bankId,
            @RequestBody List<CreateQuestionRequest> questions,
            @RequestParam(required = false, defaultValue = "MANUAL") String source) {
        int count = questionService.bulkAddQuestions(bankId, questions, source);
        return ResponseEntity.ok(Map.of("savedCount", count, "message", "Added " + count + " questions"));
    }

    // ==================== Import ====================

    @PostMapping("/import/preview")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Preview import from CSV/JSON file")
    public ResponseEntity<BulkImportRequest> previewImport(
            @PathVariable Long bankId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(importService.previewImport(file, bankId));
    }

    @PostMapping("/import/confirm")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Confirm and save imported questions")
    public ResponseEntity<ImportResultResponse> confirmImport(
            @PathVariable Long bankId,
            @RequestBody List<CreateQuestionRequest> questions,
            @RequestParam(required = false, defaultValue = "IMPORT") String source) {
        return ResponseEntity.ok(importService.confirmImport(bankId, questions, source));
    }

    // ==================== AI Draft Generation ====================

    @PostMapping("/ai-generate")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Generate draft questions via AI for admin review")
    public ResponseEntity<AiDraftResponse> generateAiDraft(
            @PathVariable Long bankId,
            @RequestBody AiGenerateDraftRequest request) {
        return ResponseEntity.ok(aiDraftService.generateDraftQuestions(bankId, request));
    }
}
