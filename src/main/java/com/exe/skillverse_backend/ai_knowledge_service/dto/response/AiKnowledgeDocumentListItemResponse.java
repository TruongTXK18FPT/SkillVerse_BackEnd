package com.exe.skillverse_backend.ai_knowledge_service.dto.response;

import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeApprovalStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeIngestionStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeUseCase;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiKnowledgeDocumentListItemResponse {
    private Long id;
    private String title;
    private String description;
    private AiKnowledgeUseCase useCase;
    private AiKnowledgeApprovalStatus approvalStatus;
    private AiKnowledgeIngestionStatus ingestionStatus;
    private Long uploadedByUserId;
    private Long mentorId;
    private String skillName;
    private String skillSlug;
    private Long courseId;
    private Long moduleId;
    private Long assignmentId;
    private String docType;
    private String mimeType;
    private Long fileSizeBytes;
    private String originalFileName;
    private String storageUrl;
    private String reviewNote;
    private LocalDateTime approvedAt;
    private LocalDateTime indexedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
