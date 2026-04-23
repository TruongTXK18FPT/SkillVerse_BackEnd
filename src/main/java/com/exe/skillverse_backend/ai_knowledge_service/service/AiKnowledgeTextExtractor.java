package com.exe.skillverse_backend.ai_knowledge_service.service;

import com.exe.skillverse_backend.shared.entity.Media;

/**
 * Wrapper for text extraction from AI knowledge documents.
 * Reuses FileTextExtractorService for supported document parsing.
 */
public interface AiKnowledgeTextExtractor {

    /**
     * Extract text from a media file for AI knowledge ingestion.
     * 
     * @param media the media entity with URL
     * @param mimeType the MIME type of the file
     * @return extracted text content
     * @throws IllegalArgumentException if file type is not supported
     */
    String extractText(Media media, String mimeType);

    /**
     * Check if the given MIME type is supported for text extraction.
     * 
     * @param mimeType the MIME type to check
     * @return true if supported
     */
    boolean isSupported(String mimeType);
}
