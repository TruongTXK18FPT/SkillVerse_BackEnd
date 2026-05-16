package com.exe.skillverse_backend.ai_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exe.skillverse_backend.runtime_settings.service.AppRuntimeSettingService;
import java.lang.reflect.Field;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class LocalAiGatewayTest {

    private LocalAiGateway gateway(boolean enabled, ChatModel model) {
        return new LocalAiGateway(model, runtimeSettings(true), enabled, "", 1500L, 5000L, 4000L, 1);
    }

    private AppRuntimeSettingService runtimeSettings(boolean localGenerationEnabled) {
        AppRuntimeSettingService runtimeSettings = mock(AppRuntimeSettingService.class);
        when(runtimeSettings.isLocalAiGenerationRuntimeEnabled()).thenReturn(localGenerationEnabled);
        return runtimeSettings;
    }

    @Test
    @DisplayName("isAvailable returns false when enabled=false even if model is set")
    void isAvailable_ReturnsFalse_WhenDisabled() {
        ChatModel model = mock(ChatModel.class);
        LocalAiGateway gw = gateway(false, model);
        assertFalse(gw.isAvailable());
    }

    @Test
    @DisplayName("isAvailable returns false when model is null even if enabled=true")
    void isAvailable_ReturnsFalse_WhenModelNull() {
        LocalAiGateway gw = gateway(true, null);
        assertFalse(gw.isAvailable());
    }

    @Test
    @DisplayName("isAvailable returns true when enabled=true and model is set")
    void isAvailable_ReturnsTrue_WhenEnabledAndModelPresent() {
        ChatModel model = mock(ChatModel.class);
        LocalAiGateway gw = gateway(true, model);
        assertTrue(gw.isAvailable());
    }

    @Test
    @DisplayName("fetchRagContext returns empty string when disabled, no HTTP call made")
    void fetchRagContext_ReturnsEmpty_WhenDisabled() {
        ChatModel model = mock(ChatModel.class);
        LocalAiGateway gw = gateway(false, model);
        String result = gw.fetchRagContext("some query", null, 5);
        assertEquals("", result);
    }

    @Test
    @DisplayName("fetchRagContext returns empty string when base-url is blank")
    void fetchRagContext_ReturnsEmpty_WhenBaseUrlBlank() {
        ChatModel model = mock(ChatModel.class);
        LocalAiGateway gw = new LocalAiGateway(model, runtimeSettings(true), true, "", 1500L, 5000L, 4000L, 1);
        String result = gw.fetchRagContext("query", null, 5);
        assertEquals("", result);
    }

    @Test
    @DisplayName("fetchRagContext returns empty string on network error (graceful fallback)")
    void fetchRagContext_ReturnsEmpty_OnNetworkError() {
        ChatModel model = mock(ChatModel.class);
        // Port 1 is almost always refused immediately
        LocalAiGateway gw = new LocalAiGateway(model, runtimeSettings(true), true, "http://localhost:1", 100L, 500L, 4000L, 1);
        String result = gw.fetchRagContext("career advice", null, 5);
        assertEquals("", result);
    }

    @Test
    @DisplayName("call throws LocalAiQueueFullException when queue is full")
    void call_ThrowsQueueFull_WhenQueueExceeded() throws Exception {
        ChatModel model = mock(ChatModel.class);
        // maxPending=0 means only 1 concurrent request allowed
        LocalAiGateway gw = new LocalAiGateway(model, runtimeSettings(true), true, "http://localhost", 1500L, 5000L, 4000L, 0);

        // Simulate 1 request already in-flight via reflection
        Field field = LocalAiGateway.class.getDeclaredField("inFlightAndQueued");
        field.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicInteger) field.get(gw)).set(1);

        assertThrows(LocalAiGateway.LocalAiQueueFullException.class,
                () -> gw.call("sys", "user"));
    }
}
