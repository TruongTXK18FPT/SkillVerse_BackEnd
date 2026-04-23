package com.exe.skillverse_backend.ai_knowledge_service.controller;

import com.exe.skillverse_backend.ai_knowledge_service.dto.request.MentorGradingKnowledgeSubmissionRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.request.MentorRoadmapKnowledgeSubmissionRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.response.AiKnowledgeDocumentDetailResponse;
import com.exe.skillverse_backend.ai_knowledge_service.dto.response.AiKnowledgeDocumentListItemResponse;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeDocumentService;
import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mentor/ai-knowledge")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MENTOR')")
public class MentorAiKnowledgeController {

    private final AiKnowledgeDocumentService aiKnowledgeDocumentService;
    private final AiKnowledgeUserResolver userResolver;

    @PostMapping("/roadmap-documents")
    public ResponseEntity<AiKnowledgeDocumentDetailResponse> submitRoadmapDocument(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute MentorRoadmapKnowledgeSubmissionRequest request) {
        User mentor = userResolver.resolve(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiKnowledgeDocumentService.submitMentorRoadmapDocument(mentor, request));
    }

    @PostMapping("/grading-documents")
    public ResponseEntity<AiKnowledgeDocumentDetailResponse> submitGradingDocument(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute MentorGradingKnowledgeSubmissionRequest request) {
        User mentor = userResolver.resolve(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiKnowledgeDocumentService.submitMentorGradingDocument(mentor, request));
    }

    @GetMapping("/documents")
    public ResponseEntity<Page<AiKnowledgeDocumentListItemResponse>> listMyDocuments(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User mentor = userResolver.resolve(jwt);
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(aiKnowledgeDocumentService.listMentorDocuments(mentor, pageable));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<AiKnowledgeDocumentDetailResponse> getMyDocumentDetail(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        User mentor = userResolver.resolve(jwt);
        return ResponseEntity.ok(aiKnowledgeDocumentService.getMentorDocumentDetail(mentor, id));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> deleteMyPendingSubmission(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id) {
        User mentor = userResolver.resolve(jwt);
        aiKnowledgeDocumentService.deleteMentorPendingSubmission(mentor, id);
        return ResponseEntity.noContent().build();
    }
}
