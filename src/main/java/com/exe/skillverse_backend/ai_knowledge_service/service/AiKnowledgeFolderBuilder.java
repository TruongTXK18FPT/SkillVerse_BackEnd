package com.exe.skillverse_backend.ai_knowledge_service.service;

import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeUseCase;

/**
 * Builds Cloudinary folder paths for AI knowledge documents.
 * Returns relative paths only — CloudinaryServiceImpl prepends baseFolder.
 */
public interface AiKnowledgeFolderBuilder {

    /**
     * Build the storage folder path for a new document upload.
     * 
     * @param useCase the document use case
     * @param mentorId the mentor ID (null for admin uploads)
     * @param skillSlug the skill slug (for roadmap docs)
     * @param courseId the course ID (for grading docs)
     * @param moduleId the module ID (for grading docs)
     * @param assignmentId the assignment ID (for grading docs)
     * @param isPending whether the document is pending approval
     * @return relative folder path (no leading "skillverse/")
     */
    String buildFolder(
            AiKnowledgeUseCase useCase,
            Long mentorId,
            String skillSlug,
            Long courseId,
            Long moduleId,
            Long assignmentId,
            boolean isPending);
}
