package com.exe.skillverse_backend.ai_knowledge_service.service.impl;

import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeTextExtractor;
import com.exe.skillverse_backend.assignment_ai_service.service.FileTextExtractorService;
import com.exe.skillverse_backend.shared.entity.Media;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Wrapper for text extraction from AI knowledge documents.
 * Reuses FileTextExtractorService for supported document parsing.
 *
 * Supported types:
 * - application/pdf
 * - application/vnd.openxmlformats-officedocument.wordprocessingml.document (DOCX)
 * - text/plain (TXT)
 * - text/markdown (MD)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiKnowledgeTextExtractorImpl implements AiKnowledgeTextExtractor {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/markdown"
    );

    private final FileTextExtractorService fileTextExtractorService;

    @Override
    public String extractText(Media media, String mimeType) {
        if (!isSupported(mimeType)) {
            throw new IllegalArgumentException(
                    "Unsupported file type for AI knowledge: " + mimeType +
                    ". Only PDF, DOCX, TXT, and MD are supported.");
        }

        return fileTextExtractorService.extractText(media, mimeType);
    }

    @Override
    public boolean isSupported(String mimeType) {
        if (mimeType == null) {
            return false;
        }
        
        // Check exact match first
        if (SUPPORTED_TYPES.contains(mimeType)) {
            return true;
        }
        
        String lower = mimeType.toLowerCase();
        return lower.contains("pdf")
                || lower.contains("wordprocessingml")
                || lower.startsWith("text/plain")
                || lower.startsWith("text/markdown")
                || lower.startsWith("text/x-markdown");
    }
}
