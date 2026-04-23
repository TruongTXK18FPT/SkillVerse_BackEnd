package com.exe.skillverse_backend.ai_knowledge_service.service;

import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;

/**
 * Service for ingesting/deleting AI knowledge documents to/from RAG.
 * Wraps /rag/ingest and /rag/document/{doc_id} endpoints.
 */
public interface AiKnowledgeIngestionService {

    /**
     * Ingest a document into the RAG system.
     * Updates document's ragDocId, ingestionStatus, and indexedAt.
     * 
     * @param document the document to ingest (must be APPROVED)
     * @throws IllegalStateException if document is not approved
     */
    void ingest(AiKnowledgeDocument document);

    /**
     * Delete a document from the RAG system.
     * Only deletes if document was previously indexed.
     * 
     * @param document the document to delete from RAG
     */
    void deleteFromRag(AiKnowledgeDocument document);

    /**
     * Reindex a document in the RAG system.
     * Re-ingests with the same ragDocId (idempotent).
     * 
     * @param document the document to reindex (must be APPROVED)
     * @throws IllegalStateException if document is not approved
     */
    void reindex(AiKnowledgeDocument document);
}
