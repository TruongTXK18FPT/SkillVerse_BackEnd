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

    @Value("${spring.ai.openai.fallback-models:gemini-2.0-flash}")
    private String fallbackModels;

    /**
     * Creates PRIMARY ChatModel bean for Gemini using OpenAI-compatible API
     * This bean will be used by AiRoadmapService for roadmap generation
     *
     * @return ChatModel configured for Gemini primary model
     */
    @Bean
    @Qualifier("geminiChatModel")
    public ChatModel geminiChatModel() {
        return createGeminiChatModel(geminiModel);
    }

    /**
     * Creates FALLBACK ChatModel bean for Gemini 2.0 Flash Experimental
     * Used when primary model quota is exceeded
     * Note: Gemini 1.5 has been deprecated, only 2.0 is available as fallback
     *
     * @return ChatModel configured for Gemini 2.0 Flash Experimental
     */
    @Bean
    @Qualifier("geminiFallback1ChatModel")
    public ChatModel geminiFallback1ChatModel() {
        String model = fallbackModels.trim(); // Only one fallback now
        return createGeminiChatModel(model);
    }

    /**
     * Helper method to create ChatModel with specific Gemini model
     */
    private ChatModel createGeminiChatModel(String modelName) {
        // Create OpenAI API client configured for Gemini's OpenAI-compatible endpoint
        OpenAiApi openAiApi = new OpenAiApi(geminiBaseUrl, geminiApiKey);

        // Configure chat options with specified model and parameters
        OpenAiChatOptions chatOptions = OpenAiChatOptions.builder()
                .withModel(modelName)
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
