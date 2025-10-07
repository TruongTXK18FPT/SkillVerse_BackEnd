package com.exe.skillverse_backend.ai_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced Gemini API configuration with fallback models
 */
@Configuration
@ConfigurationProperties(prefix = "gemini.api")
@Data
public class GeminiProperties {

    private String key;
    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta/models";
    private String model = "gemini-2.0-flash-exp";
    private List<String> fallbackModels = new ArrayList<>(List.of("gemini-1.5-flash", "gemini-1.5-pro"));
    private Integer timeout = 30000;
    private Integer maxTokens = 30000;
    private Double temperature = 0.7;
}
