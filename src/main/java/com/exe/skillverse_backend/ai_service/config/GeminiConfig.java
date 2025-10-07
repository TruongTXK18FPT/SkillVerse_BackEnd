package com.exe.skillverse_backend.ai_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for Gemini AI API integration
 */
@Configuration
public class GeminiConfig {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.base-url}")
    private String baseUrl;

    @Value("${gemini.api.model}")
    private String model;

    @Value("${gemini.api.timeout:30000}")
    private int timeout;

    @Value("${gemini.api.max-tokens:30000}")
    private int maxTokens;

    @Value("${gemini.api.temperature:0.7}")
    private double temperature;

    /**
     * Create RestTemplate bean for Gemini API calls
     */
    @Bean(name = "geminiRestTemplate")
    public RestTemplate geminiRestTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);

        RestTemplate restTemplate = new RestTemplate(factory);
        return restTemplate;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getModel() {
        return model;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public double getTemperature() {
        return temperature;
    }

    /**
     * Build full API URL for content generation
     */
    public String getGenerateContentUrl() {
        return String.format("%s/%s:generateContent?key=%s", baseUrl, model, apiKey);
    }
}
