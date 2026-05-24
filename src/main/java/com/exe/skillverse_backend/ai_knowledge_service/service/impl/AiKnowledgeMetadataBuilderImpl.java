package com.exe.skillverse_backend.ai_knowledge_service.service.impl;

import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeUseCase;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeMetadataBuilder;
import com.exe.skillverse_backend.ai_knowledge_service.util.AiKnowledgeDocTypeResolver;
import com.exe.skillverse_backend.ai_knowledge_service.util.AiKnowledgeSlugUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds RAG-compatible metadata payloads for AI knowledge documents.
 * 
 * Payload structure per BACKEND_INTEGRATION_GUIDE.md:
 * {
 *   "doc_id": "ai_knowledge_42",
 *   "doc_type": "guide|skill|assignment|lesson",
 *   "title": "...",
 *   "content": "...",
 *   "metadata": {
 *     "doc_type": "...",      // dual-write
 *     "domain": "...",        // use-case specific
 *     "course_id": "...",     // if applicable
 *     "module_id": "...",     // if applicable
 *     "industry": "...",      // if present
 *     "level": "..."          // if present
 *   }
 * }
 */
@Service
@Slf4j
public class AiKnowledgeMetadataBuilderImpl implements AiKnowledgeMetadataBuilder {

    @Override
    public Map<String, Object> buildIngestPayload(AiKnowledgeDocument document) {
        Map<String, Object> payload = new HashMap<>();
        String ragDocType = resolveRagDocType(document);
        
        payload.put("doc_id", generateRagDocId(document));
        payload.put("doc_type", ragDocType);
        payload.put("title", document.getTitle());
        payload.put("content", document.getExtractedText() != null ? document.getExtractedText() : "");
        payload.put("metadata", buildMetadata(document));
        
        return payload;
    }

    @Override
    public Map<String, Object> buildMetadata(AiKnowledgeDocument document) {
        Map<String, Object> metadata = new HashMap<>();
        String ragDocType = resolveRagDocType(document);
        
        // Dual-write document_id for scoped semantic searching
        if (document.getId() != null) {
            metadata.put("document_id", String.valueOf(document.getId()));
        }

        // Dual-write doc_type
        metadata.put("doc_type", ragDocType);
        
        // Build domain based on use case
        String domain = buildDomain(document);
        if (domain != null) {
            metadata.put("domain", domain);
        }
        
        // Dual-write course_id/module_id if present
        if (document.getCourseId() != null) {
            metadata.put("course_id", String.valueOf(document.getCourseId()));
        }
        if (document.getModuleId() != null) {
            metadata.put("module_id", String.valueOf(document.getModuleId()));
        }
        
        // Optional metadata
        if (document.getIndustry() != null && !document.getIndustry().isBlank()) {
            metadata.put("industry", document.getIndustry());
        }
        if (document.getLevel() != null && !document.getLevel().isBlank()) {
            metadata.put("level", document.getLevel());
        }
        
        return metadata;
    }

    @Override
    public String generateRagDocId(AiKnowledgeDocument document) {
        return "ai_knowledge_" + document.getId();
    }

    /**
     * Build the domain key based on use case.
     * 
     * - CHATBOT_GLOBAL: "chatbot_global"
     * - ROADMAP_SKILL: "roadmap_skill_{skillSlug}"
     * - GRADING_ASSIGNMENT: "grading_assignment_{assignmentId}"
     * - GRADING_MODULE: "grading_module"
     * - GRADING_COURSE: "grading_course"
     */
    private String buildDomain(AiKnowledgeDocument document) {
        AiKnowledgeUseCase useCase = document.getUseCase();
        
        switch (useCase) {
            case CHATBOT_GLOBAL:
                return "chatbot_global";
                
            case ROADMAP_SKILL:
                String skillSlug = document.getSkillSlug();
                if (skillSlug == null || skillSlug.isBlank()) {
                    skillSlug = AiKnowledgeSlugUtils.toRoadmapSkillSlug(document.getSkillName());
                }
                return AiKnowledgeSlugUtils.toRoadmapDomain(skillSlug);
                
            // Legacy compatibility only — do not use for new mentor uploads.
            // Runtime AI grading now queries domain=course_content and falls back to DB reading lessons.
            // These cases exist solely to handle existing rows during admin review/reindex/archive.
            case GRADING_ASSIGNMENT:
                return document.getAssignmentId() != null ? "grading_assignment_" + document.getAssignmentId() : null;
                
            case GRADING_MODULE:
                return "grading_module";
                
            case GRADING_COURSE:
                return "grading_course";
                
            default:
                log.warn("Unknown use case: {}", useCase);
                return null;
        }
    }

    private String resolveRagDocType(AiKnowledgeDocument document) {
        return AiKnowledgeDocTypeResolver.resolve(document);
    }
}
