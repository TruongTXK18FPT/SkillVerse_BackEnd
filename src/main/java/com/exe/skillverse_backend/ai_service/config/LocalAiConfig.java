package com.exe.skillverse_backend.ai_service.config;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
public class LocalAiConfig {

    @Value("${skillverse.ai.local.base-url:}")
    private String baseUrl;

    @Value("${skillverse.ai.local.model:qwen3.5:27b}")
    private String model;

    @Value("${skillverse.ai.local.connect-timeout-ms:1500}")
    private long connectTimeoutMs;

    @Value("${skillverse.ai.local.http-timeout-ms:30000}")
    private long httpTimeoutMs;

    @Bean("localAiChatModel")
    @Lazy
    @ConditionalOnProperty(name = "skillverse.ai.local.enabled", havingValue = "true")
    public ChatModel localAiChatModel() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Local AI is enabled but skillverse.ai.local.base-url is blank");
        }

        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(httpTimeoutMs));

        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(requestFactory);

        OpenAiApi api = new OpenAiApi(
                baseUrl,
                "local",
                restClientBuilder,
                WebClient.builder(),
                new DefaultResponseErrorHandler());

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model)
                .withTemperature(0.7)
                .withMaxTokens(30000)
                .build();

        log.info("LocalAiChatModel initialized with base-url={}, model={}", baseUrl, model);
        return new OpenAiChatModel(api, options);
    }
}
