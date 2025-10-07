package com.exe.skillverse_backend.ai_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Mistral AI API
 */
@Configuration
@ConfigurationProperties(prefix = "mistral.api")
@Data
public class MistralProperties {

    private String key;
    private String baseUrl = "https://api.mistral.ai/v1/chat/completions";
    private String model = "mistral-large-latest";
    private Integer timeout = 30000;
    private Integer maxTokens = 30000;
    private Double temperature = 0.7;
    private Boolean enabled = true;
}
