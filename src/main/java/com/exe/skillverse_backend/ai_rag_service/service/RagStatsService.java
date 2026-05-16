package com.exe.skillverse_backend.ai_rag_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagStatsService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> getStats() {
        Long totalChunks = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag_chunks",
                Long.class);

        List<Map<String, Object>> byDocType = jdbcTemplate.queryForList(
                "SELECT doc_type, COUNT(*) AS count FROM rag_chunks GROUP BY doc_type ORDER BY count DESC");

        List<Map<String, Object>> byIndustry = jdbcTemplate.queryForList(
                "SELECT metadata->>'industry' AS industry, COUNT(*) AS count " +
                "FROM rag_chunks " +
                "WHERE metadata->>'industry' IS NOT NULL " +
                "GROUP BY industry ORDER BY count DESC");

        List<Map<String, Object>> byCourse = jdbcTemplate.queryForList(
                "SELECT metadata->>'course_id' AS course_id, COUNT(*) AS count " +
                "FROM rag_chunks " +
                "WHERE metadata->>'course_id' IS NOT NULL " +
                "GROUP BY course_id ORDER BY count DESC " +
                "LIMIT 20");

        return Map.of(
                "total_chunks", totalChunks != null ? totalChunks : 0L,
                "by_doc_type", byDocType,
                "by_industry", byIndustry,
                "by_course", byCourse
        );
    }

    public void deleteByDocId(String docId) {
        String pattern = docId.replace("\\", "\\\\")
                              .replace("%", "\\%")
                              .replace("_", "\\_") + "\\_chunk\\_%";
        int deleted = jdbcTemplate.update(
                "DELETE FROM rag_chunks WHERE doc_id LIKE ? ESCAPE '\\'", pattern);
        log.info("Deleted {} chunks for doc_id '{}'", deleted, docId);
    }

    public void deleteByCourseId(Long courseId) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM rag_chunks WHERE course_id = ?", courseId);
        log.info("Deleted {} chunks for course_id {}", deleted, courseId);
    }
}
