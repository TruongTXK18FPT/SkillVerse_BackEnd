package com.exe.skillverse_backend.ai_knowledge_service.mapper;

import com.exe.skillverse_backend.ai_knowledge_service.dto.response.AiKnowledgeDocumentDetailResponse;
import com.exe.skillverse_backend.ai_knowledge_service.dto.response.AiKnowledgeDocumentListItemResponse;
import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;
import org.springframework.stereotype.Component;

@Component
public class AiKnowledgeDocumentMapper {

    public AiKnowledgeDocumentListItemResponse toListItem(AiKnowledgeDocument document) {
        return AiKnowledgeDocumentListItemResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .description(document.getDescription())
                .useCase(document.getUseCase())
                .approvalStatus(document.getApprovalStatus())
                .ingestionStatus(document.getIngestionStatus())
                .uploadedByUserId(document.getUploadedByUserId())
                .mentorId(document.getMentorId())
                .skillName(document.getSkillName())
                .skillSlug(document.getSkillSlug())
                .courseId(document.getCourseId())
                .moduleId(document.getModuleId())
                .assignmentId(document.getAssignmentId())
                .docType(document.getDocType())
                .mimeType(document.getMimeType())
                .fileSizeBytes(document.getFileSizeBytes())
                .originalFileName(document.getOriginalFileName())
                .storageUrl(document.getStorageUrl())
                .reviewNote(document.getReviewNote())
                .approvedAt(document.getApprovedAt())
                .indexedAt(document.getIndexedAt())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    public AiKnowledgeDocumentDetailResponse toDetail(AiKnowledgeDocument document) {
        return AiKnowledgeDocumentDetailResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .description(document.getDescription())
                .useCase(document.getUseCase())
                .approvalStatus(document.getApprovalStatus())
                .ingestionStatus(document.getIngestionStatus())
                .uploadedByUserId(document.getUploadedByUserId())
                .approvedByUserId(document.getApprovedByUserId())
                .mentorId(document.getMentorId())
                .skillName(document.getSkillName())
                .skillSlug(document.getSkillSlug())
                .industry(document.getIndustry())
                .level(document.getLevel())
                .courseId(document.getCourseId())
                .moduleId(document.getModuleId())
                .assignmentId(document.getAssignmentId())
                .docType(document.getDocType())
                .ragDocId(document.getRagDocId())
                .mimeType(document.getMimeType())
                .fileSizeBytes(document.getFileSizeBytes())
                .originalFileName(document.getOriginalFileName())
                .storageFolder(document.getStorageFolder())
                .storageUrl(document.getStorageUrl())
                .extractedText(document.getExtractedText())
                .extractError(document.getExtractError())
                .reviewNote(document.getReviewNote())
                .approvedAt(document.getApprovedAt())
                .indexedAt(document.getIndexedAt())
                .archivedAt(document.getArchivedAt())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }
}
