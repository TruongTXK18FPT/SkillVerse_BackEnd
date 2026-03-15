package com.exe.skillverse_backend.ai_service.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.ai.mistralai.MistralAiChatOptions;
import org.springframework.ai.mistralai.api.MistralAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
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

    @Value("${skillverse.ai.generate-test.model:mistral-large-latest}")
    private String model;

    @Value("${skillverse.ai.generate-test.temperature:0.7}")
    private Double temperature;

    @Value("${skillverse.ai.generate-test.max-tokens:30000}")
    private Integer maxTokens;

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
        log.info("Model: {}, Enabled: {}", model, enabled);

        if (!enabled || generateTestApiKey == null || generateTestApiKey.isBlank()) {
            log.error("Generate Test AI not configured! API Key blank: {}",
                generateTestApiKey == null || generateTestApiKey.isBlank());
            throw new IllegalStateException(
                "Generate Test AI is not properly configured. " +
                "Please set AI_GENERATE_TEST_KEY environment variable."
            );
        }

        MistralAiApi mistralAiApi = new MistralAiApi(generateTestApiKey);

        MistralAiChatOptions options = MistralAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .withMaxTokens(maxTokens)
                .build();

        log.info("Generate Test ChatModel initialized successfully");
        return new MistralAiChatModel(mistralAiApi, options);
    }
}
