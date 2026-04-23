package com.exe.skillverse_backend.ai_knowledge_service.service.impl;

import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeApprovalStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeIngestionStatus;
import com.exe.skillverse_backend.ai_knowledge_service.repository.AiKnowledgeDocumentRepository;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeIngestionService;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeMetadataBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Service for ingesting/deleting AI knowledge documents to/from RAG.
 * 
 * Endpoints used:
 * - POST /rag/ingest — ingest documents (idempotent by doc_id)
 * - DELETE /rag/document/{doc_id} — delete document
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiKnowledgeIngestionServiceImpl implements AiKnowledgeIngestionService {

    private final AiKnowledgeMetadataBuilder metadataBuilder;
    private final AiKnowledgeDocumentRepository documentRepository;

    @Value("${skillverse.ai.local.base-url:}")
    private String ragBaseUrl;

    @Value("${skillverse.ai.local.enabled:false}")
    private boolean ragEnabled;

    @Value("${skillverse.ai.local.connect-timeout-ms:1500}")
    private long connectTimeoutMs;

    @Value("${skillverse.ai.local.http-timeout-ms:30000}")
    private long httpTimeoutMs;

    private RestClient restClient;

    private RestClient getRestClient() {
        if (restClient == null) {
            log.info(
                    "Initializing AI knowledge RAG client with base-url={}, connect-timeout-ms={}, http-timeout-ms={}",
                    ragBaseUrl,
                    connectTimeoutMs,
                    httpTimeoutMs
            );
            restClient = RestClient.builder()
                    .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                        setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
                        setReadTimeout(Duration.ofMillis(httpTimeoutMs));
                    }})
                    .build();
        }
        return restClient;
    }

    @Override
    public void ingest(AiKnowledgeDocument document) {
        if (document.getApprovalStatus() != AiKnowledgeApprovalStatus.APPROVED) {
            throw new IllegalStateException("Cannot ingest document that is not approved");
        }

        if (document.getExtractedText() == null || document.getExtractedText().isBlank()) {
            log.warn("Document {} has no extracted text, marking as FAILED", document.getId());
            document.setIngestionStatus(AiKnowledgeIngestionStatus.FAILED);
            documentRepository.save(document);
            return;
        }

        // Generate and set RAG doc ID if not already set
        if (document.getRagDocId() == null) {
            document.setRagDocId(metadataBuilder.generateRagDocId(document));
        }

        if (!ragEnabled || ragBaseUrl == null || ragBaseUrl.isBlank()) {
            log.warn("RAG is disabled or not configured, keeping document {} as NOT_INGESTED", document.getId());
            // Keep status as NOT_INGESTED to distinguish infra/config issues from actual ingest failures
            // Admin can retry ingestion after RAG is enabled/configured
            document.setIngestionStatus(AiKnowledgeIngestionStatus.NOT_INGESTED);
            documentRepository.save(document);
            return;
        }

        try {
            Map<String, Object> payload = metadataBuilder.buildIngestPayload(document);
            Map<String, Object> requestBody = Map.of("documents", List.of(payload));

            String url = ragBaseUrl + "/rag/ingest";
            
            getRestClient().post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .toBodilessEntity();

            document.setIngestionStatus(AiKnowledgeIngestionStatus.INDEXED);
            document.setIndexedAt(LocalDateTime.now());
            documentRepository.save(document);

            log.info("Successfully ingested document {} with ragDocId {}", 
                    document.getId(), document.getRagDocId());

        } catch (Exception e) {
            log.error("Failed to ingest document {}: {}", document.getId(), e.getMessage());
            document.setIngestionStatus(AiKnowledgeIngestionStatus.FAILED);
            documentRepository.save(document);
        }
    }

    @Override
    public void deleteFromRag(AiKnowledgeDocument document) {
        if (document.getRagDocId() == null) {
            log.debug("Document {} has no ragDocId, nothing to delete from RAG", document.getId());
            return;
        }

        if (document.getIngestionStatus() != AiKnowledgeIngestionStatus.INDEXED) {
            log.debug("Document {} was not indexed, nothing to delete from RAG", document.getId());
            return;
        }

        if (!ragEnabled || ragBaseUrl == null || ragBaseUrl.isBlank()) {
            log.warn("RAG is disabled, skipping deletion for document {}", document.getId());
            return;
        }

        try {
            String url = ragBaseUrl + "/rag/document/" + document.getRagDocId();
            
            getRestClient().delete()
                    .uri(url)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Successfully deleted document {} from RAG", document.getId());

        } catch (Exception e) {
            log.error("Failed to delete document {} from RAG: {}", document.getId(), e.getMessage());
            // Don't throw — deletion failure shouldn't block archive operation
        }
    }

    @Override
    public void reindex(AiKnowledgeDocument document) {
        if (document.getApprovalStatus() != AiKnowledgeApprovalStatus.APPROVED) {
            throw new IllegalStateException("Cannot reindex document that is not approved");
        }

        // Reindex is just re-ingest with the same ragDocId (idempotent)
        ingest(document);
    }
}
