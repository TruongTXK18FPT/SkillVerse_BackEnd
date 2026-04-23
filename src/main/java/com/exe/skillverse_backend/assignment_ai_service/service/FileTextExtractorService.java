package com.exe.skillverse_backend.assignment_ai_service.service;

import com.exe.skillverse_backend.shared.entity.Media;

/**
 * Service for extracting text content from supported document files.
 * Used by AI grading and AI knowledge ingestion.
 */
public interface FileTextExtractorService {

    /**
     * Extract text from PDF, DOCX, TXT, or Markdown file.
     * Max 50,000 characters to prevent token overflow.
     *
     * @param media       the Media entity with URL and metadata
     * @param contentType the MIME type of the file
     * @return extracted text content
     * @throws IllegalArgumentException if unsupported content type
     * @throws IllegalArgumentException if file exceeds size limit
     */
    String extractText(Media media, String contentType);
}
