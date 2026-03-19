package com.exe.skillverse_backend.ai_search_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for AI Search Service beans
 * Note: AISearchConfig is now a @Configuration class with @ConfigurationProperties
 */
@Configuration
public class AIConfig {

    @Bean(name = "aiSearchRestTemplate")
    public RestTemplate aiSearchRestTemplate() {
        return new RestTemplate();
    }
}
