package com.exe.skillverse_backend.ai_rag_service.service;

import com.exe.skillverse_backend.ai_rag_service.config.AiRagProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TextChunker {

    private final AiRagProperties properties;

    public List<String> chunkText(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        // word-based equivalent chunking
        // 150 words ~ 600 chars, 15 words ~ 60 chars
        int charSize = properties.getChunkSize() * 4;
        int charOverlap = properties.getChunkOverlap() * 4;
        int step = charSize - charOverlap;
        
        if (step <= 0) {
            step = charSize;
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;
        int textLength = text.length();

        while (start < textLength) {
            int end = Math.min(start + charSize, textLength);
            String chunk = text.substring(start, end).trim();
            if (!chunk.isBlank()) {
                chunks.add(chunk);
            }
            start += step;
        }

        return chunks;
    }
}
