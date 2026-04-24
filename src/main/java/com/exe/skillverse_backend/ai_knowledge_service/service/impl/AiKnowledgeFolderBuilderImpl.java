package com.exe.skillverse_backend.ai_knowledge_service.service.impl;

import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeUseCase;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeFolderBuilder;
import org.springframework.stereotype.Service;

/**
 * Builds Cloudinary folder paths for AI knowledge documents.
 * 
 * Folder structure (relative paths only):
 * - ai-knowledge/chatbot/global
 * - ai-knowledge/roadmap/pending/{mentorId}/{skillSlug}
 * - ai-knowledge/roadmap/approved/{skillSlug}
 * - ai-knowledge/grading/pending/{mentorId}/courses/{courseId}/modules/{moduleId}
 * - ai-knowledge/grading/pending/{mentorId}/courses/{courseId}/modules/{moduleId}/assignments/{assignmentId}
 * - ai-knowledge/grading/approved/courses/{courseId}/modules/{moduleId}
 * - ai-knowledge/grading/approved/courses/{courseId}/modules/{moduleId}/assignments/{assignmentId}
 */
@Service
public class AiKnowledgeFolderBuilderImpl implements AiKnowledgeFolderBuilder {

    private static final String BASE = "ai-knowledge";

    @Override
    public String buildFolder(
            AiKnowledgeUseCase useCase,
            Long mentorId,
            String skillSlug,
            Long courseId,
            Long moduleId,
            Long assignmentId,
            boolean isPending) {

        switch (useCase) {
            case CHATBOT_GLOBAL:
                return BASE + "/chatbot/global";

            case ROADMAP_SKILL:
                if (isPending && mentorId != null) {
                    return BASE + "/roadmap/pending/" + mentorId + "/" + skillSlug;
                }
                return BASE + "/roadmap/approved/" + skillSlug;

            // Kept for historical document compatibility — not an active upload path.
            // Mentor grading-doc uploads were removed 2026-04-24.
            case GRADING_ASSIGNMENT:
                return buildGradingFolder(mentorId, courseId, moduleId, assignmentId, isPending);

            case GRADING_MODULE:
                return buildGradingFolder(mentorId, courseId, moduleId, null, isPending);

            case GRADING_COURSE:
                return buildGradingFolder(mentorId, courseId, null, null, isPending);

            default:
                throw new IllegalArgumentException("Unsupported AI knowledge use case: " + useCase);
        }
    }

    private String buildGradingFolder(
            Long mentorId,
            Long courseId,
            Long moduleId,
            Long assignmentId,
            boolean isPending) {

        StringBuilder sb = new StringBuilder(BASE).append("/grading");

        if (isPending && mentorId != null) {
            sb.append("/pending/").append(mentorId);
        } else {
            sb.append("/approved");
        }

        if (courseId != null) {
            sb.append("/courses/").append(courseId);
        }

        if (moduleId != null) {
            sb.append("/modules/").append(moduleId);
        }

        if (assignmentId != null) {
            sb.append("/assignments/").append(assignmentId);
        }

        return sb.toString();
    }
}
