package com.exe.skillverse_backend.ai_rag_service.controller;

import com.exe.skillverse_backend.ai_rag_service.dto.EmbeddingRequest;
import com.exe.skillverse_backend.ai_rag_service.dto.EmbeddingResponse;
import com.exe.skillverse_backend.ai_rag_service.provider.EmbeddingProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OpenAI-compatible gateway endpoints.
 *
 * These endpoints allow existing HTTP callers that target the AI-RAG-Service
 * Python process to talk to the Java monolith instead.
 *
 * This endpoint is ADMIN-only — it is not intended for end users.
 */
@RestController
@RequestMapping("/api/v1/ai-gateway")
@RequiredArgsConstructor
@Slf4j
public class AiGatewayController {

    private final EmbeddingProvider embeddingProvider;

    /**
     * POST /api/v1/ai-gateway/v1/embeddings
     *
     * OpenAI-compatible embeddings forwarded to Mistral.
     */
    @PostMapping("/v1/embeddings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmbeddingResponse> embeddings(
            @RequestBody EmbeddingRequest request) {

        log.info("Embedding request via AI Gateway, inputs={}", request.getInput().size());
        List<List<Double>> vectors = embeddingProvider.embed(request.getInput());

        EmbeddingResponse response = EmbeddingResponse.builder()
                .object("list")
                .model(request.getModel())
                .data(buildData(vectors))
                .build();

        return ResponseEntity.ok(response);
    }

    private List<EmbeddingResponse.EmbeddingData> buildData(List<List<Double>> vectors) {
        var result = new java.util.ArrayList<EmbeddingResponse.EmbeddingData>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            result.add(EmbeddingResponse.EmbeddingData.builder()
                    .object("embedding")
                    .index(i)
                    .embedding(vectors.get(i))
                    .build());
        }
        return result;
    }
}
