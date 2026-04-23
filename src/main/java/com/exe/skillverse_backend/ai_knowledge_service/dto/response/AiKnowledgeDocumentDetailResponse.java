package com.exe.skillverse_backend.ai_knowledge_service.dto.response;

import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeApprovalStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeIngestionStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeUseCase;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiKnowledgeDocumentDetailResponse {
    private Long id;
    private String title;
    private String description;
    private AiKnowledgeUseCase useCase;
    private AiKnowledgeApprovalStatus approvalStatus;
    private AiKnowledgeIngestionStatus ingestionStatus;
    private Long uploadedByUserId;
    private Long approvedByUserId;
    private Long mentorId;
    private String skillName;
    private String skillSlug;
    private String industry;
    private String level;
    private Long courseId;
    private Long moduleId;
    private Long assignmentId;
    private String docType;
    private String ragDocId;
    private String mimeType;
    private Long fileSizeBytes;
    private String originalFileName;
    private String storageFolder;
    private String storageUrl;
    private String extractedText;
    private String extractError;
    private String reviewNote;
    private LocalDateTime approvedAt;
    private LocalDateTime indexedAt;
    private LocalDateTime archivedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
