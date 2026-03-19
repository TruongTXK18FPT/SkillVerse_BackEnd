package com.exe.skillverse_backend.ai_search_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for AI Search Service (Mistral API)
 */
@Configuration
@ConfigurationProperties(prefix = "skillverse.ai.search")
@Getter
@Setter
public class AISearchConfig {

    private String apiKey;
    private String model = "mistral-large-latest";
    private String baseUrl = "https://api.mistral.ai/v1/chat/completions";
    private boolean enabled = true;
    private int maxTokens = 30000;
    private double temperature = 0.7;
    private int timeout = 30000;

    // Rate limiting
    private int maxRequestsPerMinute = 10;
    private int maxTokensPerDay = 100000;

    // Cache settings
    private boolean cacheEnabled = true;
    private int cacheTtlMinutes = 60;
}
