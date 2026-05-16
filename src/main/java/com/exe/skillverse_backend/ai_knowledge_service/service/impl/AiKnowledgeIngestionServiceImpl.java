package com.exe.skillverse_backend.ai_knowledge_service.service.impl;

import com.exe.skillverse_backend.ai_knowledge_service.entity.AiKnowledgeDocument;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeApprovalStatus;
import com.exe.skillverse_backend.ai_knowledge_service.entity.enums.AiKnowledgeIngestionStatus;
import com.exe.skillverse_backend.ai_knowledge_service.repository.AiKnowledgeDocumentRepository;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeIngestionService;
import com.exe.skillverse_backend.ai_knowledge_service.service.AiKnowledgeMetadataBuilder;
import com.exe.skillverse_backend.ai_rag_service.config.AiRagProperties;
import com.exe.skillverse_backend.ai_rag_service.dto.DocumentInput;
import com.exe.skillverse_backend.ai_rag_service.service.RagIngestionService;
import com.exe.skillverse_backend.runtime_settings.service.AppRuntimeSettingService;
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
    private final RagIngestionService ragIngestionService;
    private final AppRuntimeSettingService runtimeSettings;
    private final AiRagProperties aiRagProperties;

    @Value("${skillverse.ai.local.enabled:false}")
    private boolean localAiEnabled;

    @Value("${skillverse.ai.local.base-url:}")
    private String remoteBaseUrl;

    @Value("${skillverse.ai.local.connect-timeout-ms:1500}")
    private long connectTimeoutMs;

    @Value("${skillverse.ai.local.http-timeout-ms:30000}")
    private long httpTimeoutMs;

    private RestClient restClient;

    private RestClient getRestClient() {
        if (restClient == null) {
            log.info(
                    "Initializing AI knowledge RAG remote client with base-url={}, connect-timeout-ms={}, http-timeout-ms={}",
                    remoteBaseUrl,
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

        boolean effectiveRemote = localAiEnabled
                && runtimeSettings.isAiRagServiceRuntimeEnabled()
                && remoteBaseUrl != null && !remoteBaseUrl.isBlank();
        boolean effectiveJava = aiRagProperties.isJavaEnabled()
                && runtimeSettings.isJavaRagFallbackRuntimeEnabled();

        if (!effectiveRemote && !effectiveJava) {
            log.warn("RAG is disabled at runtime, keeping document {} as NOT_INGESTED", document.getId());
            document.setIngestionStatus(AiKnowledgeIngestionStatus.NOT_INGESTED);
            documentRepository.save(document);
            return;
        }

        try {
            Map<String, Object> payloadMap = metadataBuilder.buildIngestPayload(document);

            if (effectiveRemote) {
                log.info("RAG ingest provider selected: AI_RAG_SERVICE documentId={} ragDocId={}",
                        document.getId(), document.getRagDocId());
                try {
                    Map<String, Object> requestBody = Map.of("documents", List.of(payloadMap));
                    getRestClient().post().uri(remoteBaseUrl + "/rag/ingest")
                            .contentType(MediaType.APPLICATION_JSON).body(requestBody).retrieve().toBodilessEntity();
                } catch (Exception e) {
                    if (effectiveJava) {
                        log.warn("RAG ingest provider fallback: AI_RAG_SERVICE -> JAVA_RAG documentId={} reason={}",
                                document.getId(), e.getMessage());
                        try {
                            executeJavaIngest(document, payloadMap);
                        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests te) {
                            handleTooManyRequests(document);
                            return;
                        }
                    } else {
                        throw e;
                    }
                }
            } else if (effectiveJava) {
                log.info("RAG ingest provider selected: JAVA_RAG documentId={} ragDocId={}",
                        document.getId(), document.getRagDocId());
                try {
                    executeJavaIngest(document, payloadMap);
                } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests te) {
                    handleTooManyRequests(document);
                    return;
                }
            }

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

        boolean effectiveJava = aiRagProperties.isJavaEnabled()
                && runtimeSettings.isJavaRagFallbackRuntimeEnabled();
        boolean effectiveRemote = localAiEnabled
                && runtimeSettings.isAiRagServiceRuntimeEnabled()
                && remoteBaseUrl != null && !remoteBaseUrl.isBlank();

        if (effectiveJava && !effectiveRemote) {
            try {
                ragIngestionService.deleteDocument(document.getRagDocId());
                log.info("Successfully deleted document {} from Java RAG", document.getId());
            } catch (Exception e) {
                log.error("Failed to delete document {} from Java RAG: {}", document.getId(), e.getMessage());
            }
        } else if (effectiveRemote) {
            try {
                String url = remoteBaseUrl + "/rag/document/" + document.getRagDocId();
                getRestClient().delete()
                        .uri(url)
                        .retrieve()
                        .toBodilessEntity();
                log.info("Successfully deleted document {} from Remote RAG", document.getId());
            } catch (Exception e) {
                log.error("Failed to delete document {} from Remote RAG: {}", document.getId(), e.getMessage());
            }
        } else {
            log.warn("RAG is disabled at runtime, skipping deletion for document {}", document.getId());
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

    private void executeJavaIngest(AiKnowledgeDocument document, Map<String, Object> payloadMap) {
        DocumentInput documentInput = new DocumentInput();
        documentInput.setDocId((String) payloadMap.get("doc_id"));
        documentInput.setDocType((String) payloadMap.get("doc_type"));
        documentInput.setTitle((String) payloadMap.get("title"));
        documentInput.setContent((String) payloadMap.get("content"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) payloadMap.get("metadata");
        documentInput.setMetadata(metadata);

        int chunks = ragIngestionService.ingestDocument(documentInput);
        log.info("Successfully ingested document {} using Java RAG. Created {} chunks.", document.getId(), chunks);
    }

    private void handleTooManyRequests(AiKnowledgeDocument document) {
        log.warn("Mistral API 429 Too Many Requests during ingestion for document {}. Setting status to NOT_INGESTED", document.getId());
        document.setIngestionStatus(AiKnowledgeIngestionStatus.NOT_INGESTED);
        documentRepository.save(document);
    }
}
