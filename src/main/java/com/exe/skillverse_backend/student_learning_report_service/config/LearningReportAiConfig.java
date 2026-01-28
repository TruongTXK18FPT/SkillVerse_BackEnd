package com.exe.skillverse_backend.student_learning_report_service.config;

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
 * Configuration cho Mistral AI dùng cho Student Learning Report.
 * Sử dụng API key riêng biệt để tách biệt quota và billing.
 */
@Slf4j
@Configuration
public class LearningReportAiConfig {

    @Value("${skillverse.ai.learning-report.api-key:}")
    private String learningReportApiKey;

    @Value("${skillverse.ai.learning-report.model:mistral-large-latest}")
    private String model;

    @Value("${skillverse.ai.learning-report.temperature:0.7}")
    private Double temperature;

    @Value("${skillverse.ai.learning-report.max-tokens:30000}")
    private Integer maxTokens;

    @Value("${skillverse.ai.learning-report.enabled:true}")
    private boolean enabled;

    /**
     * Bean ChatModel riêng cho Learning Report service.
     * Sử dụng qualifier "learningReportChatModel" để inject.
     * Bean được tạo lazy để đảm bảo env vars đã load.
     */
    @Lazy
    @Bean("learningReportChatModel")
    @ConditionalOnProperty(name = "skillverse.ai.learning-report.enabled", havingValue = "true", matchIfMissing = true)
    public ChatModel learningReportChatModel() {
        log.info("Initializing Learning Report ChatModel...");
        log.info("API Key present: {}", learningReportApiKey != null && !learningReportApiKey.isBlank());
        log.info("Model: {}, Enabled: {}", model, enabled);
        
        // If disabled or no API key, throw exception with clear message
        if (!enabled || learningReportApiKey == null || learningReportApiKey.isBlank()) {
            log.error("Learning Report AI not configured! API Key blank: {}", 
                learningReportApiKey == null || learningReportApiKey.isBlank());
            throw new IllegalStateException(
                "Learning Report AI is not properly configured. " +
                "Please set LEARNING_REPORT_KEY environment variable."
            );
        }

        MistralAiApi mistralAiApi = new MistralAiApi(learningReportApiKey);

        MistralAiChatOptions options = MistralAiChatOptions.builder()
                .withModel(model)
                .withTemperature(temperature)
                .withMaxTokens(maxTokens)
                .build();

        log.info("Learning Report ChatModel initialized successfully");
        return new MistralAiChatModel(mistralAiApi, options);
    }
}
