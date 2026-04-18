package com.exe.skillverse_backend.ai_search_service.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration for AI Search Service (Mistral API)
 */
@Configuration
@ConfigurationProperties(prefix = "skillverse.ai.search")
@Getter
@Setter
@Slf4j
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

    @PostConstruct
    public void init() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .ignoreIfMalformed()
                    .load();

            if (dotenv.get("AI_SEARCH_KEY") != null && !dotenv.get("AI_SEARCH_KEY").isEmpty()) {
                this.apiKey = dotenv.get("AI_SEARCH_KEY");
                log.info("AI_SEARCH_KEY loaded from .env file");
            }
            if (dotenv.get("AI_SEARCH_MODEL") != null) {
                this.model = dotenv.get("AI_SEARCH_MODEL");
            }
            if (dotenv.get("AI_SEARCH_ENABLED") != null) {
                this.enabled = Boolean.parseBoolean(dotenv.get("AI_SEARCH_ENABLED"));
            }
            log.info("AISearchConfig initialized - enabled: {}, apiKey present: {}, model: {}",
                    this.enabled, this.apiKey != null && !this.apiKey.isEmpty(), this.model);
        } catch (Exception e) {
            log.warn("Could not load .env file for AI Search: {}. Using application.yml defaults.", e.getMessage());
        }
    }
}
