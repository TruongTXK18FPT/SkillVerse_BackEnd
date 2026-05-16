package com.exe.skillverse_backend.ai_rag_service.service;

import com.exe.skillverse_backend.ai_rag_service.config.AiRagProperties;
import com.exe.skillverse_backend.ai_rag_service.dto.ContextResult;
import com.exe.skillverse_backend.ai_rag_service.dto.QueryRequest;
import com.exe.skillverse_backend.ai_rag_service.dto.QueryResponse;
import com.exe.skillverse_backend.runtime_settings.service.AppRuntimeSettingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class AiRagGateway {

    private final RagQueryService queryService;
    private final AiRagProperties properties;
    private final ObjectMapper objectMapper;
    private final AppRuntimeSettingService runtimeSettings;

    // AI-RAG-Service is the remote provider; Java RAG is the in-process provider.
    private final boolean localAiEnabled;
    private final String remoteBaseUrl;
    private final RestClient remoteRestClient;

    public AiRagGateway(
            RagQueryService queryService,
            AiRagProperties properties,
            ObjectMapper objectMapper,
            AppRuntimeSettingService runtimeSettings,
            @Value("${skillverse.ai.local.enabled:false}") boolean localAiEnabled,
            @Value("${skillverse.ai.local.base-url:}") String remoteBaseUrl,
            @Value("${skillverse.ai.local.connect-timeout-ms:1500}") long connectTimeoutMs,
            @Value("${skillverse.ai.local.http-timeout-ms:30000}") long httpTimeoutMs) {

        this.queryService = queryService;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.runtimeSettings = runtimeSettings;
        this.localAiEnabled = localAiEnabled;
        this.remoteBaseUrl = remoteBaseUrl;

        this.remoteRestClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
                    setReadTimeout(Duration.ofMillis(httpTimeoutMs));
                }})
                .build();
    }

    public String fetchRagContext(String query, Map<String, String> filters, int topK) {
        boolean effectiveRemote = localAiEnabled
                && runtimeSettings.isAiRagServiceRuntimeEnabled()
                && remoteBaseUrl != null && !remoteBaseUrl.isBlank();

        boolean effectiveJava = properties.isJavaEnabled()
                && runtimeSettings.isJavaRagFallbackRuntimeEnabled();

        if (effectiveRemote) {
            log.info("RAG query provider selected: AI_RAG_SERVICE topK={} filters={}", topK, filters);
            String result = fetchRemoteContext(query, filters, topK);
            if (result != null && !result.isBlank()) {
                log.info("RAG query provider success: AI_RAG_SERVICE");
                return result;
            } else if (effectiveJava) {
                log.info("RAG query provider fallback: AI_RAG_SERVICE -> JAVA_RAG");
                return fetchJavaContextSafe(query, filters, topK);
            } else {
                log.warn("RAG query provider failed: AI_RAG_SERVICE; Java RAG is disabled or not configured.");
                return "";
            }
        } else if (effectiveJava) {
            log.info("RAG query provider selected: JAVA_RAG topK={} filters={}", topK, filters);
            return fetchJavaContextSafe(query, filters, topK);
        } else {
            log.warn("RAG query skipped: both AI_RAG_SERVICE and JAVA_RAG are disabled or not configured.");
            return "";
        }
    }

    private String fetchJavaContextSafe(String query, Map<String, String> filters, int topK) {
        try {
            String result = fetchJavaContext(query, filters, topK);
            log.info("RAG query provider success: JAVA_RAG");
            return result;
        } catch (Exception e) {
            log.warn("RAG query provider failed: JAVA_RAG reason={}", e.getMessage());
            return "";
        }
    }

    private String fetchJavaContext(String query, Map<String, String> filters, int topK) {
        Map<String, Object> castFilters = null;
        if (filters != null) {
            castFilters = new HashMap<>(filters);
        }

        QueryRequest request = new QueryRequest();
        request.setQuery(query);
        request.setFilters(castFilters);
        request.setTopK(topK);

        QueryResponse response = queryService.query(request);
        if (response == null || response.getContexts() == null || response.getContexts().isEmpty()) {
            log.warn("No contexts found in Java RAG");
            return "";
        }

        return response.getContexts().stream()
                .filter(ctx -> ctx.getScore() >= properties.getScoreThreshold())
                .map(ContextResult::getContent)
                .filter(content -> content != null && !content.isBlank())
                .collect(Collectors.joining("\n---\n"));
    }

    private String fetchRemoteContext(String query, Map<String, String> filters, int topK) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("top_k", topK);
            if (filters != null && !filters.isEmpty()) {
                body.put("filters", filters);
            }

            String url = remoteBaseUrl + "/rag/query";
            String responseJson = remoteRestClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (responseJson == null || responseJson.isBlank()) {
                return "";
            }

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode contexts = root.path("contexts");
            if (!contexts.isArray()) {
                return "";
            }

            return StreamSupport.stream(contexts.spliterator(), false)
                    .filter(ctx -> ctx.path("score").asDouble(0.0) >= properties.getScoreThreshold())
                    .map(ctx -> ctx.path("content").asText(""))
                    .filter(content -> !content.isBlank())
                    .collect(Collectors.joining("\n---\n"));

        } catch (Exception e) {
            log.warn("Remote RAG unavailable: {}", e.getMessage());
            return "";
        }
    }
}
