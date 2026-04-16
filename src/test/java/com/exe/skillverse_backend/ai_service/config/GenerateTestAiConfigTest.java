package com.exe.skillverse_backend.ai_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mistralai.MistralAiChatModel;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriComponentsBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerateTestAiConfigTest {

    @Test
    void generateTestChatModel_ShouldUseConfiguredAbsoluteBaseUrl() {
        GenerateTestAiConfig config = new GenerateTestAiConfig();
        ReflectionTestUtils.setField(config, "generateTestApiKey", "test-api-key");
        ReflectionTestUtils.setField(config, "baseUrl", "https://api.mistral.ai");
        ReflectionTestUtils.setField(config, "model", "mistral-large-latest");
        ReflectionTestUtils.setField(config, "temperature", 0.7d);
        ReflectionTestUtils.setField(config, "maxTokens", 2048);
        ReflectionTestUtils.setField(config, "timeoutMs", 5000L);
        ReflectionTestUtils.setField(config, "enabled", true);

        ChatModel chatModel = config.generateTestChatModel();

        assertInstanceOf(MistralAiChatModel.class, chatModel);

        Object mistralAiApi = ReflectionTestUtils.getField(chatModel, "mistralAiApi");
        Object restClient = ReflectionTestUtils.getField(mistralAiApi, "restClient");
        Object uriBuilderFactory = ReflectionTestUtils.getField(restClient, "uriBuilderFactory");

        assertInstanceOf(DefaultUriBuilderFactory.class, uriBuilderFactory);

        UriComponentsBuilder baseUri =
                (UriComponentsBuilder) ReflectionTestUtils.getField(uriBuilderFactory, "baseUri");

        assertNotNull(baseUri);
        assertEquals("https://api.mistral.ai", baseUri.build().toUriString());
    }

    @Test
    void generateTestChatModel_ShouldRejectBlankBaseUrl() {
        GenerateTestAiConfig config = new GenerateTestAiConfig();
        ReflectionTestUtils.setField(config, "generateTestApiKey", "test-api-key");
        ReflectionTestUtils.setField(config, "baseUrl", "");
        ReflectionTestUtils.setField(config, "model", "mistral-large-latest");
        ReflectionTestUtils.setField(config, "temperature", 0.7d);
        ReflectionTestUtils.setField(config, "maxTokens", 2048);
        ReflectionTestUtils.setField(config, "timeoutMs", 5000L);
        ReflectionTestUtils.setField(config, "enabled", true);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, config::generateTestChatModel);

        assertEquals("Generate Test AI base URL is not properly configured. Please set AI_GENERATE_TEST_BASE_URL.",
                exception.getMessage());
    }
}
