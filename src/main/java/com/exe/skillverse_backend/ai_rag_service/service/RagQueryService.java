package com.exe.skillverse_backend.ai_rag_service.service;

import com.exe.skillverse_backend.ai_rag_service.config.AiRagProperties;
import com.exe.skillverse_backend.ai_rag_service.dto.ContextResult;
import com.exe.skillverse_backend.ai_rag_service.dto.QueryRequest;
import com.exe.skillverse_backend.ai_rag_service.dto.QueryResponse;
import com.exe.skillverse_backend.ai_rag_service.provider.EmbeddingProvider;
import com.exe.skillverse_backend.ai_rag_service.repository.RagChunkJdbcRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagQueryService {

    private final AiRagProperties properties;
    private final EmbeddingProvider embeddingProvider;
    private final RagChunkJdbcRepository repository;

    public QueryResponse query(QueryRequest request) {
        long startTime = System.currentTimeMillis();
        
        int topK = request.getTopK() != null ? request.getTopK() : properties.getTopKDefault();
        if (topK > properties.getTopKMax()) {
            topK = properties.getTopKMax();
        }

        // Embed query
        List<List<Double>> embeddings = embeddingProvider.embed(List.of(request.getQuery()));
        if (embeddings.isEmpty()) {
            throw new RuntimeException("Failed to generate embedding for query");
        }
        List<Double> queryEmbedding = embeddings.get(0);

        // Vector search
        List<ContextResult> contexts = repository.queryContexts(
                queryEmbedding, 
                request.getFilters(), 
                topK, 
                properties.getScoreThreshold()
        );

        if (contexts.isEmpty()) {
            log.warn("No contexts found for query: {}", request.getQuery());
        } else {
            log.info("Found {} contexts for query with topK={}", contexts.size(), topK);
        }

        long endTime = System.currentTimeMillis();
        return QueryResponse.builder()
                .contexts(contexts)
                .queryTimeMs(endTime - startTime)
                .build();
    }
}
