package com.exe.skillverse_backend.ai_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for AI provider selection and fallback behavior
 */
@Configuration
@ConfigurationProperties(prefix = "ai.provider")
@Data
public class AiProviderProperties {

    /**
     * Provider for roadmap generation: "gemini" or "mistral"
     */
    private String roadmap = "gemini";

    /**
     * Provider for chatbot (career trends, latest info): "gemini" or "mistral"
     */
    private String chatbot = "mistral";

    /**
     * Enable automatic fallback to alternative provider if primary fails
     */
    private Boolean enableFallback = true;
}
