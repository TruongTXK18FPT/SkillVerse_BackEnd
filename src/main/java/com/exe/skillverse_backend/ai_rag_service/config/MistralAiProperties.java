package com.exe.skillverse_backend.ai_rag_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "skillverse.ai.mistral")
@Data
public class MistralAiProperties {
    private EmbeddingProperties embedding = new EmbeddingProperties();

    @Data
    public static class EmbeddingProperties {
        private String apiKey;
        private String baseUrl = "https://api.mistral.ai/v1";
        private String model = "mistral-embed";
        private long timeoutMs = 60000;
    }

}
