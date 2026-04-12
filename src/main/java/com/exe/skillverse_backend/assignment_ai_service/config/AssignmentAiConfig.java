package com.exe.skillverse_backend.assignment_ai_service.config;

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

@Slf4j
@Configuration
public class AssignmentAiConfig {

    @Value("${assignment_ai.api-key:}")
    private String apiKey;

    @Value("${assignment_ai.model:mistral-large-latest}")
    private String model;

    @Value("${assignment_ai.temperature:0.7}")
    private Double temperature;

    @Value("${assignment_ai.max-tokens:30000}")
    private Integer maxTokens;

    @Value("${assignment_ai.enabled:true}")
    private boolean enabled;

    @Lazy
    @Bean("assignmentAiChatModel")
    @ConditionalOnProperty(name = "assignment_ai.enabled", havingValue = "true", matchIfMissing = true)
    public ChatModel assignmentAiChatModel() {
        log.info("Initializing Assignment AI ChatModel...");
        log.info("API Key present: {}", apiKey != null && !apiKey.isBlank());
        log.info("Model: {}, Enabled: {}", model, enabled);

        if (!enabled || apiKey == null || apiKey.isBlank()) {
            log.warn("Assignment AI not configured — API Key blank or disabled. "
                    + "Set ASSIGNMENT_AI_API_KEY in .env to enable. "
                    + "Assignment AI features will be unavailable.");
            return null;
        }

        MistralAiApi mistralAiApi = new MistralAiApi(apiKey);

        MistralAiChatOptions options = MistralAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .withMaxTokens(maxTokens)
                .build();

        log.info("Assignment AI ChatModel initialized successfully");
        return new MistralAiChatModel(mistralAiApi, options);
    }
}
