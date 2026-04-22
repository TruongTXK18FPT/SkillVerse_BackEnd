package com.exe.skillverse_backend.ai_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class LocalAiGateway {

    private final boolean enabled;
    private final ChatModel localAiChatModel;
    private final RestClient ragRestClient;
    private final String baseUrl;
    private final long queueWaitMs;
    private final int maxPending;

    private final Semaphore semaphore = new Semaphore(1, true);
    private final AtomicInteger inFlightAndQueued = new AtomicInteger(0);
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LocalAiGateway(
            @Autowired(required = false) @Qualifier("localAiChatModel") ChatModel localAiChatModel,
            @Value("${skillverse.ai.local.enabled:false}") boolean enabled,
            @Value("${skillverse.ai.local.base-url:}") String baseUrl,
            @Value("${skillverse.ai.local.connect-timeout-ms:1500}") long connectTimeoutMs,
            @Value("${skillverse.ai.local.http-timeout-ms:30000}") long httpTimeoutMs,
            @Value("${skillverse.ai.local.queue-wait-ms:8000}") long queueWaitMs,
            @Value("${skillverse.ai.local.queue-max-pending:2}") int maxPending) {
        this.enabled = enabled;
        this.localAiChatModel = localAiChatModel;
        this.baseUrl = baseUrl;
        this.queueWaitMs = queueWaitMs;
        this.maxPending = maxPending;

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(httpTimeoutMs));

        this.ragRestClient = RestClient.builder()
            .requestFactory(requestFactory)
            .build();
    }

    public boolean isAvailable() {
        return enabled && localAiChatModel != null;
    }

    public String call(String systemPrompt, String userPrompt) {
        if (localAiChatModel == null) {
            throw new IllegalStateException("Local AI not configured");
        }

        int current = inFlightAndQueued.incrementAndGet();
        if (current > 1 + maxPending) {
            inFlightAndQueued.decrementAndGet();
            throw new LocalAiQueueFullException("Local AI queue full (" + current + " requests)");
        }

        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire(queueWaitMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new LocalAiQueueFullException("Local AI queue wait timeout after " + queueWaitMs + "ms");
            }

            var builder = ChatClient.builder(localAiChatModel).build().prompt();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                builder = builder.system(systemPrompt);
            }
            return builder
                .user(userPrompt)
                .call()
                .content();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for Local AI slot", e);
        } finally {
            if (acquired) {
                semaphore.release();
            }
            inFlightAndQueued.decrementAndGet();
        }
    }

    public String fetchRagContext(String query, Map<String, String> filters, int topK) {
        if (!enabled || baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("top_k", topK);
            if (filters != null && !filters.isEmpty()) {
                body.put("filters", filters);
            }

            String url = baseUrl + "/rag/query";
            String responseJson = ragRestClient.post()
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

            String result = StreamSupport.stream(contexts.spliterator(), false)
                .filter(ctx -> ctx.path("score").asDouble(0.0) >= 0.4)
                .map(ctx -> ctx.path("content").asText(""))
                .filter(content -> !content.isBlank())
                .collect(Collectors.joining("\n---\n"));

            return result;

        } catch (Exception e) {
            log.warn("RAG unavailable, proceeding without context: {}", e.getMessage());
            return "";
        }
    }

    public static class LocalAiQueueFullException extends RuntimeException {
        public LocalAiQueueFullException(String message) {
            super(message);
        }
    }
}
