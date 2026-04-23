package com.exe.skillverse_backend.ai_knowledge_service.service;

import com.exe.skillverse_backend.ai_knowledge_service.dto.request.AdminChatbotKnowledgeUploadRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.request.AdminRoadmapKnowledgeUploadRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.request.MentorGradingKnowledgeSubmissionRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.request.MentorRoadmapKnowledgeSubmissionRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.request.ReviewAiKnowledgeRequest;
import com.exe.skillverse_backend.ai_knowledge_service.dto.response.AiKnowledgeDocumentDetailResponse;
import com.exe.skillverse_backend.ai_knowledge_service.dto.response.AiKnowledgeDocumentListItemResponse;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeApprovalStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeIngestionStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeUseCase;
import com.exe.skillverse_backend.auth_service.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AiKnowledgeDocumentService {
    AiKnowledgeDocumentDetailResponse uploadAdminChatbotDocument(User admin, AdminChatbotKnowledgeUploadRequest request);

    AiKnowledgeDocumentDetailResponse uploadAdminRoadmapDocument(User admin, AdminRoadmapKnowledgeUploadRequest request);

    AiKnowledgeDocumentDetailResponse submitMentorRoadmapDocument(User mentor, MentorRoadmapKnowledgeSubmissionRequest request);

    AiKnowledgeDocumentDetailResponse submitMentorGradingDocument(User mentor, MentorGradingKnowledgeSubmissionRequest request);

    Page<AiKnowledgeDocumentListItemResponse> listAdminDocuments(
            AiKnowledgeUseCase useCase,
            AiKnowledgeApprovalStatus approvalStatus,
            AiKnowledgeIngestionStatus ingestionStatus,
            String skillSlug,
            Long courseId,
            Long moduleId,
            Long assignmentId,
            Pageable pageable);

    AiKnowledgeDocumentDetailResponse getAdminDocumentDetail(Long id);

    AiKnowledgeDocumentDetailResponse reviewDocument(Long id, User admin, ReviewAiKnowledgeRequest request);

    AiKnowledgeDocumentDetailResponse reindexDocument(Long id);

    void archiveDocument(Long id);

    Page<AiKnowledgeDocumentListItemResponse> listMentorDocuments(User mentor, Pageable pageable);

    AiKnowledgeDocumentDetailResponse getMentorDocumentDetail(User mentor, Long id);

    void deleteMentorPendingSubmission(User mentor, Long id);
}
