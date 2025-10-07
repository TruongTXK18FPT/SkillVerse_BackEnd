package com.exe.skillverse_backend.ai_service.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI Configuration for multiple AI providers
 * - Mistral AI: For chatbot (auto-configured by
 * spring-ai-mistral-ai-spring-boot-starter)
 * - Gemini: For roadmap generation (using OpenAI-compatible API)
 */
@Configuration
public class SpringAiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String geminiApiKey;

    @Value("${spring.ai.openai.base-url}")
    private String geminiBaseUrl;

    @Value("${spring.ai.openai.chat.options.model}")
    private String geminiModel;

    @Value("${spring.ai.openai.chat.options.temperature:0.7}")
    private Double geminiTemperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:30000}")
    private Integer geminiMaxTokens;

    /**
     * Creates a ChatModel bean for Gemini using OpenAI-compatible API
     * This bean will be used by AiRoadmapService for roadmap generation
     *
     * @return ChatModel configured for Gemini
     */
    @Bean
    @Qualifier("geminiChatModel")
    public ChatModel geminiChatModel() {
        // Create OpenAI API client configured for Gemini's OpenAI-compatible endpoint
        OpenAiApi openAiApi = new OpenAiApi(geminiBaseUrl, geminiApiKey);

        // Configure chat options with Gemini model and parameters
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .withModel(geminiModel)
                .withTemperature(geminiTemperature)
                .withMaxTokens(geminiMaxTokens)
                .build();

        // Create and return OpenAiChatModel with Gemini configuration
        return new OpenAiChatModel(openAiApi, chatOptions);
    }

    /**
     * Note: Mistral ChatModel is auto-configured by Spring Boot Auto-configuration
     * and will be available with @Qualifier("mistralAiChatModel")
     */
}
