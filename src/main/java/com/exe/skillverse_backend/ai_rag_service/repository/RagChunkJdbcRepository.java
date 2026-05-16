package com.exe.skillverse_backend.ai_rag_service.repository;

import com.exe.skillverse_backend.ai_rag_service.dto.ContextResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
@Slf4j
public class RagChunkJdbcRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private RowMapper<ContextResult> getContextResultRowMapper() {
        return (rs, rowNum) -> {
            ContextResult result = new ContextResult();
            result.setDocId(rs.getString("doc_id"));
            result.setDocType(rs.getString("doc_type"));
            result.setContent(rs.getString("content"));
            result.setSource(rs.getString("title")); // Using title as source for backwards compatibility
            result.setScore(rs.getDouble("score"));

            String metadataJson = rs.getString("metadata");
            if (metadataJson != null && !metadataJson.isBlank()) {
                try {
                    Map<String, Object> metadata = objectMapper.readValue(metadataJson, new TypeReference<>() {});
                    result.setMetadata(metadata);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse metadata for chunk: {}", result.getDocId(), e);
                }
            }
            return result;
        };
    }

    public void deleteByDocIdPattern(String docId) {
        // pattern: {docId}_chunk_%
        String pattern = docId.replace("\\", "\\\\")
                             .replace("%", "\\%")
                             .replace("_", "\\_") + "\\_chunk\\_%";
        
        String sql = "DELETE FROM rag_chunks WHERE doc_id LIKE ? ESCAPE '\\'";
        jdbcTemplate.update(sql, pattern);
    }

    public void deleteByCourseId(Long courseId) {
        String sql = "DELETE FROM rag_chunks WHERE course_id = ?";
        jdbcTemplate.update(sql, courseId);
    }

    public void insertChunk(String docId, String docType, String title, String content, 
                            List<Double> embedding, Map<String, Object> metadata) {
        String sql = """
            INSERT INTO rag_chunks
                (doc_id, doc_type, title, content, embedding, metadata)
            VALUES
                (?, ?, ?, ?, ?::vector, ?::jsonb)
        """;

        String embeddingStr = formatEmbedding(embedding);
        String metadataStr = "{}";
        try {
            if (metadata != null) {
                metadataStr = objectMapper.writeValueAsString(metadata);
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize metadata for doc_id {}", docId, e);
        }

        jdbcTemplate.update(sql, docId, docType, title, content, embeddingStr, metadataStr);
    }

    public List<ContextResult> queryContexts(List<Double> queryEmbedding, Map<String, Object> filters, int topK, double minScore) {
        String embeddingStr = formatEmbedding(queryEmbedding);

        if (filters == null || filters.isEmpty()) {
            String sql = """
                SELECT
                    doc_id,
                    doc_type,
                    title,
                    content,
                    metadata,
                    1 - (embedding <=> ?::vector) AS score
                FROM rag_chunks
                WHERE 1 - (embedding <=> ?::vector) >= ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
            """;
            return jdbcTemplate.query(sql, getContextResultRowMapper(), embeddingStr, embeddingStr, minScore, embeddingStr, topK);
        } else {
            String sql = """
                SELECT
                    doc_id,
                    doc_type,
                    title,
                    content,
                    metadata,
                    1 - (embedding <=> ?::vector) AS score
                FROM rag_chunks
                WHERE metadata @> ?::jsonb AND 1 - (embedding <=> ?::vector) >= ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
            """;
            
            String filterStr = "{}";
            try {
                filterStr = objectMapper.writeValueAsString(filters);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize filters", e);
            }
            
            return jdbcTemplate.query(sql, getContextResultRowMapper(), embeddingStr, filterStr, embeddingStr, minScore, embeddingStr, topK);
        }
    }

    private String formatEmbedding(List<Double> embedding) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < embedding.size(); i++) {
            sb.append(embedding.get(i));
            if (i < embedding.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
