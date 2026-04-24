package com.exe.skillverse_backend.ai_knowledge_service.util;

import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeUseCase;

public final class AiKnowledgeDocTypeResolver {
    private AiKnowledgeDocTypeResolver() {
    }

    public static String resolve(AiKnowledgeUseCase useCase, String fallbackDocType) {
        if (useCase == null) {
            return fallbackDocType;
        }
        return switch (useCase) {
            case CHATBOT_GLOBAL -> "guide";
            case ROADMAP_SKILL -> "skill";
            // Kept for historical document compatibility — not an active upload path.
            case GRADING_ASSIGNMENT, GRADING_MODULE -> "assignment";
            case GRADING_COURSE -> "lesson";
        };
    }

    public static String resolve(AiKnowledgeDocument document) {
        if (document == null) {
            return null;
        }
        return resolve(document.getUseCase(), document.getDocType());
    }
}
