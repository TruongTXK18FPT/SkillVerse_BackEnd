package com.exe.skillverse_backend.ai_rag_service.controller;

import com.exe.skillverse_backend.ai_rag_service.dto.DocumentInput;
import com.exe.skillverse_backend.ai_rag_service.dto.IngestRequest;
import com.exe.skillverse_backend.ai_rag_service.dto.IngestResponse;
import com.exe.skillverse_backend.ai_rag_service.dto.QueryRequest;
import com.exe.skillverse_backend.ai_rag_service.dto.QueryResponse;
import com.exe.skillverse_backend.ai_rag_service.service.RagIngestionService;
import com.exe.skillverse_backend.ai_rag_service.service.RagQueryService;
import com.exe.skillverse_backend.ai_rag_service.service.RagStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal RAG HTTP API — mirrors the AI-RAG-Service Python contract so
 * existing HTTP callers (e.g. external integrations) can point here instead.
 *
 * These endpoints are ADMIN-only. Internal Java callers should use
 * RagIngestionService / RagQueryService directly without HTTP.
 */
@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
@Slf4j
public class RagController {

    private final RagIngestionService ingestionService;
    private final RagQueryService queryService;
    private final RagStatsService statsService;

    // ─── Ingest ──────────────────────────────────────────────────────────────

    @PostMapping("/ingest")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IngestResponse> ingest(@RequestBody IngestRequest request) {
        int totalChunks = 0;
        int docsProcessed = 0;

        for (DocumentInput doc : request.getDocuments()) {
            int chunks = ingestionService.ingestDocument(doc);
            totalChunks += chunks;
            docsProcessed++;
        }

        IngestResponse response = IngestResponse.builder()
                .status("ok")
                .chunksCreated(totalChunks)
                .documentsProcessed(docsProcessed)
                .build();

        log.info("Ingested {} documents, {} total chunks", docsProcessed, totalChunks);
        return ResponseEntity.ok(response);
    }

    // ─── Query ───────────────────────────────────────────────────────────────

    @PostMapping("/query")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QueryResponse> query(@RequestBody QueryRequest request) {
        QueryResponse response = queryService.query(request);
        return ResponseEntity.ok(response);
    }

    // ─── Stats ───────────────────────────────────────────────────────────────

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(statsService.getStats());
    }

    // ─── Delete by doc_id ─────────────────────────────────────────────────────

    @DeleteMapping("/document/{docId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteDocument(@PathVariable String docId) {
        statsService.deleteByDocId(docId);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "doc_id", docId,
                "message", "Chunks deleted"
        ));
    }

    // ─── Delete by course_id ─────────────────────────────────────────────────

    @DeleteMapping("/course/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteCourse(@PathVariable Long courseId) {
        statsService.deleteByCourseId(courseId);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "course_id", courseId,
                "message", "Chunks deleted"
        ));
    }
}
