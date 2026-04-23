package com.exe.skillverse_backend.ai_knowledge_service.controller;

import com.exe.skillverse_backend.ai_knowledge_service.dto.request.AdminChatbotKnowledgeUploadRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.request.AdminRoadmapKnowledgeUploadRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.request.ReviewAiKnowledgeRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.response.AiKnowledgeDocumentDetailResponse;
import com.exe.skillverse_backend.ai_knowledge_service.dto.response.AiKnowledgeDocumentListItemResponse;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeApprovalStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeIngestionStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeUseCase;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai-knowledge")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
public class AdminAiKnowledgeController {

    private final AiKnowledgeDocumentService aiKnowledgeDocumentService;
    private final AiKnowledgeUserResolver userResolver;

    @PostMapping("/chatbot-documents")
    public ResponseEntity<AiKnowledgeDocumentDetailResponse> uploadChatbotDocument(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute AdminChatbotKnowledgeUploadRequest request) {
        User admin = userResolver.resolve(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiKnowledgeDocumentService.uploadAdminChatbotDocument(admin, request));
    }

    @PostMapping("/roadmap-documents")
    public ResponseEntity<AiKnowledgeDocumentDetailResponse> uploadRoadmapDocument(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @ModelAttribute AdminRoadmapKnowledgeUploadRequest request) {
        User admin = userResolver.resolve(jwt);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiKnowledgeDocumentService.uploadAdminRoadmapDocument(admin, request));
    }

    @GetMapping("/documents")
    public ResponseEntity<Page<AiKnowledgeDocumentListItemResponse>> listDocuments(
            @RequestParam(required = false) AiKnowledgeUseCase useCase,
            @RequestParam(required = false) AiKnowledgeApprovalStatus approvalStatus,
            @RequestParam(required = false) AiKnowledgeIngestionStatus ingestionStatus,
            @RequestParam(required = false) String skillSlug,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long moduleId,
            @RequestParam(required = false) Long assignmentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(aiKnowledgeDocumentService.listAdminDocuments(
                useCase,
                approvalStatus,
                ingestionStatus,
                normalize(skillSlug),
                courseId,
                moduleId,
                assignmentId,
                pageable
        ));
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<AiKnowledgeDocumentDetailResponse> getDocumentDetail(@PathVariable Long id) {
        return ResponseEntity.ok(aiKnowledgeDocumentService.getAdminDocumentDetail(id));
    }

    @PostMapping("/documents/{id}/review")
    public ResponseEntity<AiKnowledgeDocumentDetailResponse> reviewDocument(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody ReviewAiKnowledgeRequest request) {
        User admin = userResolver.resolve(jwt);
        return ResponseEntity.ok(aiKnowledgeDocumentService.reviewDocument(id, admin, request));
    }

    @PostMapping("/documents/{id}/reindex")
    public ResponseEntity<AiKnowledgeDocumentDetailResponse> reindexDocument(@PathVariable Long id) {
        return ResponseEntity.ok(aiKnowledgeDocumentService.reindexDocument(id));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> archiveDocument(@PathVariable Long id) {
        aiKnowledgeDocumentService.archiveDocument(id);
        return ResponseEntity.noContent().build();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
