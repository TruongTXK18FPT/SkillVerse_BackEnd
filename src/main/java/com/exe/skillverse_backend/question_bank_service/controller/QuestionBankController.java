package com.exe.skillverse_backend.question_bank_service.controller;

import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.UpdateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankResponse;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankSummaryResponse;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankService;
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

@RestController
@RequestMapping("/api/v1/admin/question-banks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin - Question Banks", description = "Manage question banks and their questions")
@PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
public class QuestionBankController {

    private final QuestionBankService questionBankService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Create a new question bank")
    public ResponseEntity<QuestionBankResponse> createBank(@Valid @RequestBody CreateQuestionBankRequest request) {
        return ResponseEntity.ok(questionBankService.createBank(request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "List all question banks")
    public ResponseEntity<Page<QuestionBankSummaryResponse>> listBanks(
            @RequestParam(required = false) Long domainId,
            @RequestParam(required = false) Long jobPositionId,
            @RequestParam(required = false) Long skillId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(questionBankService.listBanks(domainId, jobPositionId, skillId, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Get question bank by ID with stats")
    public ResponseEntity<QuestionBankResponse> getBank(@PathVariable Long id) {
        return ResponseEntity.ok(questionBankService.getBankById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Update question bank")
    public ResponseEntity<QuestionBankResponse> updateBank(
            @PathVariable Long id,
            @RequestBody UpdateQuestionBankRequest request) {
        return ResponseEntity.ok(questionBankService.updateBank(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Delete (soft) question bank")
    public ResponseEntity<Void> deleteBank(@PathVariable Long id) {
        questionBankService.deleteBank(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sync-job-positions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Auto-create missing question banks for all active job positions")
    public ResponseEntity<Void> syncJobPositionBanks() {
        questionBankService.syncJobPositionBanks();
        return ResponseEntity.ok().build();
    }
}
