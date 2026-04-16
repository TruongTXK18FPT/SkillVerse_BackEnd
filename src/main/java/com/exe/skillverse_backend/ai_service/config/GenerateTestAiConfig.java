package com.exe.skillverse_backend.ai_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * Configuration for AI Generate Test (Mistral AI).
 * Used for guided journey - generating assessment tests based on user goals.
 */
@Slf4j
@Configuration
public class GenerateTestAiConfig {

    @Value("${skillverse.ai.generate-test.api-key:}")
    private String generateTestApiKey;

    @Value("${skillverse.ai.generate-test.base-url:https://api.mistral.ai}")
    private String baseUrl;

    @Value("${skillverse.ai.generate-test.model:mistral-large-latest}")
    private String model;

    @Value("${skillverse.ai.generate-test.temperature:0.7}")
    private Double temperature;

    @Value("${skillverse.ai.generate-test.max-tokens:30000}")
    private Integer maxTokens;

    @Value("${skillverse.ai.generate-test.timeout-ms:180000}")
    private long timeoutMs;

    @Value("${skillverse.ai.generate-test.enabled:true}")
    private boolean enabled;

    /**
     * Bean ChatModel for Generate Test AI.
     * Uses qualifier "generateTestChatModel" for injection.
     */
    @Lazy
    @Bean("generateTestChatModel")
    @ConditionalOnProperty(name = "skillverse.ai.generate-test.enabled", havingValue = "true", matchIfMissing = true)
    public ChatModel generateTestChatModel() {
        log.info("Initializing Generate Test ChatModel...");
        log.info("API Key present: {}", generateTestApiKey != null && !generateTestApiKey.isBlank());
        log.info("Model: {}, Enabled: {}, Base URL: {}", model, enabled, baseUrl);

        if (!enabled || generateTestApiKey == null || generateTestApiKey.isBlank()) {
            log.error("Generate Test AI not configured! API Key blank: {}",
                generateTestApiKey == null || generateTestApiKey.isBlank());
            throw new IllegalStateException(
                "Generate Test AI is not properly configured. " +
                "Please set AI_GENERATE_TEST_KEY environment variable."
            );
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                "Generate Test AI base URL is not properly configured. " +
                "Please set AI_GENERATE_TEST_BASE_URL."
            );
        }

        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(java.time.Duration.ofMillis(timeoutMs));
        requestFactory.setReadTimeout(java.time.Duration.ofMillis(timeoutMs));

        org.springframework.web.client.RestClient.Builder restClientBuilder = org.springframework.web.client.RestClient.builder()
                .requestFactory(requestFactory);

        MistralAiApi mistralAiApi = new MistralAiApi(
                baseUrl,
                generateTestApiKey,
                restClientBuilder,
                new org.springframework.web.client.DefaultResponseErrorHandler());

        MistralAiChatOptions options = MistralAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .withMaxTokens(maxTokens)
                .build();

        log.info("Generate Test ChatModel initialized successfully");
        return new MistralAiChatModel(mistralAiApi, options);
    }
}
