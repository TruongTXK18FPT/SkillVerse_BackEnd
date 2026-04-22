package com.exe.skillverse_backend.question_bank_service.controller;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankSubmissionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.ReviewQuestionBankSubmissionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankSubmissionResponse;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankSubmissionService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Question Bank Submissions", description = "Mentor contribution queue for question bank review")
public class QuestionBankSubmissionController {

    private final QuestionBankSubmissionService questionBankSubmissionService;
    private final UserRepository userRepository;

    @PostMapping("/mentor/question-bank-submissions")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Mentor submits a question bank contribution for admin review")
    public ResponseEntity<QuestionBankSubmissionResponse> createSubmission(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateQuestionBankSubmissionRequest request) {
        User mentor = resolveUser(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(questionBankSubmissionService.createSubmission(mentor, request));
    }

    @GetMapping("/mentor/question-bank-submissions")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Mentor gets their question bank submissions")
    public ResponseEntity<List<QuestionBankSubmissionResponse>> getMySubmissions(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        User mentor = resolveUser(jwt);
        return ResponseEntity.ok(questionBankSubmissionService.getMySubmissions(mentor));
    }

    @GetMapping("/mentor/question-bank-submissions/{submissionId}")
    @PreAuthorize("hasRole('MENTOR')")
    @Operation(summary = "Mentor gets details of one submission")
    public ResponseEntity<QuestionBankSubmissionResponse> getMySubmissionDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long submissionId) {
        User mentor = resolveUser(jwt);
        return ResponseEntity.ok(questionBankSubmissionService.getMySubmissionDetail(mentor, submissionId));
    }

    @GetMapping("/admin/question-bank-submissions")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Admin gets mentor question bank submissions")
    public ResponseEntity<Page<QuestionBankSubmissionResponse>> getAdminSubmissions(
            @RequestParam(required = false) List<String> statuses,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(questionBankSubmissionService.getAdminSubmissions(statuses, pageable));
    }

    @GetMapping("/admin/question-bank-submissions/{submissionId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Admin gets detail of one mentor submission")
    public ResponseEntity<QuestionBankSubmissionResponse> getAdminSubmissionDetail(@PathVariable Long submissionId) {
        return ResponseEntity.ok(questionBankSubmissionService.getAdminSubmissionDetail(submissionId));
    }

    @PostMapping("/admin/question-bank-submissions/{submissionId}/review")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Admin approves or rejects a mentor submission")
    public ResponseEntity<QuestionBankSubmissionResponse> reviewSubmission(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long submissionId,
            @Valid @RequestBody ReviewQuestionBankSubmissionRequest request) {
        User admin = resolveUser(jwt);
        return ResponseEntity.ok(questionBankSubmissionService.reviewSubmission(submissionId, admin, request));
    }

    @GetMapping("/admin/question-bank-submissions/count-pending")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Admin counts pending mentor submissions")
    public ResponseEntity<Map<String, Long>> countPending() {
        return ResponseEntity.ok(Map.of("count", questionBankSubmissionService.countPendingSubmissions()));
    }

    private User resolveUser(Jwt jwt) {
        Long userId = Long.parseLong(jwt.getSubject());
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found: " + userId));
    }
}
