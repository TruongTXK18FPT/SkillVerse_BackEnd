package com.exe.skillverse_backend.assignment_ai_service.controller;

import com.exe.skillverse_backend.assignment_ai_service.dto.AiGradingResultDTO;
import com.exe.skillverse_backend.assignment_ai_service.service.AssignmentAiGradingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai-grading")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('MENTOR') or hasRole('ADMIN')")
public class AiGradingController {

    private final AssignmentAiGradingService aiGradingService;

    /**
     * Trigger AI grading for a submission.
     * POST /api/ai-grading/generate/{submissionId}
     */
    @PostMapping("/generate/{submissionId}")
    public ResponseEntity<AiGradingResultDTO> generateAiGrade(
            @PathVariable("submissionId") Long submissionId,
            @AuthenticationPrincipal Jwt jwt) {
        Long mentorId = extractUserId(jwt);
        AiGradingResultDTO result = aiGradingService.generateAiGrade(submissionId, mentorId);
        return ResponseEntity.ok(result);
    }

    /**
     * Get AI grade result for a submission.
     * GET /api/ai-grading/result/{submissionId}
     */
    @GetMapping("/result/{submissionId}")
    public ResponseEntity<AiGradingResultDTO> getAiGradeResult(@PathVariable("submissionId") Long submissionId) {
        return ResponseEntity.ok(aiGradingService.getAiGradeResult(submissionId));
    }

    /**
     * Toggle Trust AI for an assignment.
     * PUT /api/ai-grading/assignment/{assignmentId}/trust-ai?enabled=true|false
     */
    @PutMapping("/assignment/{assignmentId}/trust-ai")
    public ResponseEntity<Void> toggleTrustAi(
            @PathVariable("assignmentId") Long assignmentId,
            @RequestParam boolean enabled) {
        aiGradingService.toggleTrustAi(assignmentId, enabled);
        return ResponseEntity.ok().build();
    }

    /**
     * Student requests mentor to review an AI-graded submission (dispute).
     * PUT /api/ai-grading/dispute/{submissionId}
     */
    @PutMapping("/dispute/{submissionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> requestMentorReview(
            @PathVariable("submissionId") Long submissionId,
            @RequestBody(required = false) String reason,
            @AuthenticationPrincipal Jwt jwt) {
        aiGradingService.requestMentorReview(submissionId, extractUserId(jwt), reason);
        return ResponseEntity.ok().build();
    }

    private Long extractUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
