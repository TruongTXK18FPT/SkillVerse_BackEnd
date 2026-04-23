package com.exe.skillverse_backend.ai_knowledge_service.service;

import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;

import java.util.Map;

/**
 * Builds RAG-compatible metadata payloads for AI knowledge documents.
 * Produces payloads aligned with BACKEND_INTEGRATION_GUIDE.md filter keys.
 */
public interface AiKnowledgeMetadataBuilder {

    /**
     * Build the complete RAG ingest payload for a document.
     * 
     * @param document the AI knowledge document
     * @return map containing doc_id, doc_type, title, content, metadata
     */
    Map<String, Object> buildIngestPayload(AiKnowledgeDocument document);

    /**
     * Build the metadata map for a document.
     * Dual-writes required keys: doc_type, domain, course_id, module_id, industry, level.
     * 
     * @param document the AI knowledge document
     * @return metadata map for RAG ingestion
     */
    Map<String, Object> buildMetadata(AiKnowledgeDocument document);

    /**
     * Generate the RAG doc_id for a document.
     * Format: "ai_knowledge_{id}"
     * 
     * @param document the AI knowledge document
     * @return the RAG doc_id
     */
    String generateRagDocId(AiKnowledgeDocument document);
}
