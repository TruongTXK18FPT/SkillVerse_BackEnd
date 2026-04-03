package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.response.SttResponse;
import com.exe.skillverse_backend.ai_service.service.AiSpeechService.TtsResult;
import com.exe.skillverse_backend.ai_service.service.impl.AiSpeechServiceImpl;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiSpeechServiceImplTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("transcribeAudio should parse transcript from the configured STT endpoint")
    void transcribeAudio_ShouldParseTranscriptFromConfiguredEndpoint() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/stt", exchange -> {
            byte[] body = "{\"hypotheses\":[{\"transcript\":\"hello world\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        AiSpeechServiceImpl service = new AiSpeechServiceImpl();
        ReflectionTestUtils.setField(service, "fptApiKey", "test-key");
        ReflectionTestUtils.setField(service, "fptSttEndpoint", "http://127.0.0.1:" + server.getAddress().getPort() + "/stt");

        SttResponse response = service.transcribeAudio(
                new MockMultipartFile("file", "audio.wav", "audio/wav", new byte[] {1, 2, 3}),
                "vi");

        assertEquals("hello world", response.getText());
        assertEquals("fpt.ai", response.getSource());
        assertTrue(response.getDurationMs() >= 0);
    }

    @Test
    @DisplayName("synthesizeSpeech should sanitize markdown and fetch audio bytes from the async URL")
    void synthesizeSpeech_ShouldSanitizeMarkdownAndFetchAudioBytes() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        byte[] expectedAudio = new byte[] {10, 20, 30, 40};

        server = HttpServer.create(new InetSocketAddress(0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/tts", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"async\":\"http://127.0.0.1:" + port + "/audio\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.createContext("/audio", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, expectedAudio.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(expectedAudio);
            }
        });
        server.start();

        AiSpeechServiceImpl service = new AiSpeechServiceImpl();
        ReflectionTestUtils.setField(service, "fptApiKey", "test-key");
        ReflectionTestUtils.setField(service, "fptTtsEndpoint", "http://127.0.0.1:" + port + "/tts");

        TtsResult result = service.synthesizeSpeech("## Title\n**Bold** [Link](https://example.com)", "banmai", 0.0);

        assertArrayEquals(expectedAudio, result.getAudio());
        assertEquals(MediaType.valueOf("audio/mpeg"), result.getContentType());
        assertTrue(requestBody.get().contains("Title"));
        assertTrue(requestBody.get().contains("Bold"));
        assertTrue(!requestBody.get().contains("**"));
        assertTrue(!requestBody.get().contains("https://example.com"));
    }

    @Test
    @DisplayName("synthesizeSpeech should reject empty text before calling the provider")
    void synthesizeSpeech_ShouldRejectEmptyTextBeforeCallingTheProvider() {
        AiSpeechServiceImpl service = new AiSpeechServiceImpl();
        ReflectionTestUtils.setField(service, "fptApiKey", "test-key");

        ApiException exception = assertThrows(ApiException.class,
                () -> service.synthesizeSpeech("   ", "banmai", 0.0));

        assertEquals(ErrorCode.VALIDATION_FAILED, exception.getErrorCode());
    }
}
