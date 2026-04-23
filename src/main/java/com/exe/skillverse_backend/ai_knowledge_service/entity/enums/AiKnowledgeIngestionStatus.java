package com.exe.skillverse_backend.ai_knowledge_service.entity.enums;

/**
 * Ingestion status for AI knowledge documents.
 * Tracks whether the document has been indexed in the RAG system.
 */
public enum AiKnowledgeIngestionStatus {
    NOT_INGESTED,
    INDEXED,
    FAILED
}
