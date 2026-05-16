package com.exe.skillverse_backend.ai_rag_service.provider;

import com.exe.skillverse_backend.ai_rag_service.config.MistralAiProperties;
import com.exe.skillverse_backend.ai_rag_service.dto.EmbeddingRequest;
import com.exe.skillverse_backend.ai_rag_service.dto.EmbeddingResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MistralEmbeddingProvider implements EmbeddingProvider {

    private final MistralAiProperties properties;
    private final RestClient restClient;

    public MistralEmbeddingProvider(MistralAiProperties properties) {
        this.properties = properties;
        
        long timeoutMs = properties.getEmbedding().getTimeoutMs();
        this.restClient = RestClient.builder()
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofMillis(5000));
                    setReadTimeout(Duration.ofMillis(timeoutMs));
                }})
                .build();
    }

    @Override
    public List<List<Double>> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        String apiKey = properties.getEmbedding().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("MISTRAL_EMBEDDING_API_KEY is not configured");
        }

        String url = properties.getEmbedding().getBaseUrl() + "/embeddings";
        String model = properties.getEmbedding().getModel();
        log.info("Calling Mistral embedding API model={} batchSize={}", model, texts.size());

        EmbeddingRequest request = EmbeddingRequest.builder()
                .model(model)
                .input(texts)
                .build();

        try {
            EmbeddingResponse response = restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(EmbeddingResponse.class);

            if (response == null || response.getData() == null) {
                throw new IllegalStateException("Empty response from Mistral embedding API");
            }

            // Mistral returns embeddings in the same order as input
            List<List<Double>> result = new ArrayList<>();
            for (EmbeddingResponse.EmbeddingData data : response.getData()) {
                result.add(data.getEmbedding());
            }
            int dimensions = result.isEmpty() || result.get(0) == null ? 0 : result.get(0).size();
            log.info("Mistral embedding API success model={} batchSize={} dimensions={}",
                    model, result.size(), dimensions);
            return result;
            
        } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
            log.warn("Mistral API rate limit exceeded (429 Too Many Requests) during embedding");
            throw e;
        } catch (Exception e) {
            log.error("Error calling Mistral embedding API: {}", e.getMessage());
            throw new RuntimeException("Embedding generation failed", e);
        }
    }
}
