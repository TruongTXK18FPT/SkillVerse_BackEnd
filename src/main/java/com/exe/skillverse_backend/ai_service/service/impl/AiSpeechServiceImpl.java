package com.exe.skillverse_backend.ai_service.service.impl;

import com.exe.skillverse_backend.ai_service.dto.response.SttResponse;
import com.exe.skillverse_backend.ai_service.service.AiSpeechService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.io.ByteArrayOutputStream;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.MultipartBodyBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiSpeechServiceImpl implements AiSpeechService {

    @Value("${ai.fpt.api-key:${FPT_AI_KEY:}}")
    private String fptApiKey;

    @Value("${ai.fpt.stt.endpoint:https://api.fpt.ai/hmi/asr/general}")
    private String fptSttEndpoint;

    @Value("${ai.fpt.tts.endpoint:https://api.fpt.ai/hmi/tts/v5}")
    private String fptTtsEndpoint;

    private final WebClient webClient = WebClient.builder().build();

    @Override
    public SttResponse transcribeAudio(MultipartFile audio, String language) {
        ensureApiKey();
        try {
            byte[] bytes = audio.getBytes();
            long start = System.currentTimeMillis();

            // Use multipart/form-data with a sanitized filename to avoid provider tmp-file issues
            String safeFilename = "audio.wav"; // do not include user identifiers or special characters
            String json = postMultipartWithRetry(fptSttEndpoint, bytes, safeFilename, 3, Duration.ofMillis(500), "STT");
            validateSttResponse(json);

            long duration = System.currentTimeMillis() - start;
            String transcript = parseTranscript(json);
            if (!StringUtils.hasText(transcript)) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Không thể nhận diện giọng nói (STT không trả về kết quả)");
            }
            return new SttResponse(transcript, 0.0, "fpt.ai", duration);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("STT failed: {}", e.getMessage());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Lỗi xử lý STT", e.getMessage());
        }
    }

    @Override
    public TtsResult synthesizeSpeech(String text, String voice, Double speed) {
        ensureApiKey();
        if (!StringUtils.hasText(text)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Text không được để trống");
        }

        // Sanitize markdown formatting for speech output only
        String speechText = com.exe.skillverse_backend.ai_service.util.MarkdownSpeechSanitizer.sanitizeForSpeech(text);

        String finalVoice = StringUtils.hasText(voice) ? voice : "banmai"; // default Vietnamese voice
        double finalSpeed = speed != null ? Math.max(-2.0, Math.min(2.0, speed)) : 0.0; // FPT supports -2..2

        try {
            log.info("TTS request: length={}, voice={}, speed={}", speechText.length(), finalVoice, finalSpeed);
            // Call FPT TTS (v5) which returns JSON with "async" URL
            String ttsResponse = postJsonWithRetry(fptTtsEndpoint,
                    java.util.Map.of("text", speechText, "voice", finalVoice, "speed", finalSpeed),
                    3, Duration.ofMillis(500), "TTS");
            validateTtsResponse(ttsResponse);

            String audioUrl = extractAsyncUrl(ttsResponse);
            if (!StringUtils.hasText(audioUrl)) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "FPT TTS không trả về URL audio");
            }

            // Poll until audio is ready, then download bytes (more robust than fixed attempts)
            byte[] audioBytes = pollAndFetchAudio(audioUrl, Duration.ofSeconds(30));
            return new TtsResult(audioBytes, MediaType.valueOf("audio/mpeg"));
        } catch (ApiException e) {
            if (e.getErrorCode() == ErrorCode.SERVICE_UNAVAILABLE) {
                try {
                    byte[] merged = synthesizeInChunks(speechText, finalVoice, finalSpeed);
                    if (merged != null && merged.length > 0) {
                        log.info("TTS chunked fallback succeeded: {} bytes", merged.length);
                        return new TtsResult(merged, MediaType.valueOf("audio/mpeg"));
                    }
                } catch (Exception chunkEx) {
                    log.warn("TTS chunked fallback failed: {}", chunkEx.getMessage());
                }
            }
            throw e;
        } catch (Exception e) {
            log.error("TTS failed: {}", e.getMessage());
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Lỗi xử lý TTS", e.getMessage());
        }
    }

    private void ensureApiKey() {
        if (!StringUtils.hasText(fptApiKey)) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Thiếu FPT_AI_KEY trong cấu hình hệ thống");
        }
    }

    private byte[] synthesizeInChunks(String text, String voice, double speed) {
        String[] sentences = text.split("(?<=[.!?])\\s+|\\n+");
        java.util.List<String> chunks = new java.util.ArrayList<>();
        StringBuilder buf = new StringBuilder();
        int maxLen = 240;
        for (String s : sentences) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            if (buf.length() + t.length() + 1 <= maxLen) {
                if (buf.length() > 0) buf.append(' ');
                buf.append(t);
            } else {
                if (buf.length() > 0) {
                    chunks.add(buf.toString());
                    buf.setLength(0);
                }
                if (t.length() <= maxLen) {
                    buf.append(t);
                } else {
                    // hard split long sentence
                    int start = 0;
                    while (start < t.length()) {
                        int end = Math.min(start + maxLen, t.length());
                        chunks.add(t.substring(start, end));
                        start = end;
                    }
                }
            }
        }
        if (buf.length() > 0) chunks.add(buf.toString());

        if (chunks.isEmpty()) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int idx = 0;
        for (String piece : chunks) {
            idx++;
            String resp = postJsonWithRetry(fptTtsEndpoint,
                    java.util.Map.of("text", piece, "voice", voice, "speed", speed),
                    3, Duration.ofMillis(500), "TTS-Chunk");
            validateTtsResponse(resp);
            String url = extractAsyncUrl(resp);
            if (!StringUtils.hasText(url)) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "FPT TTS không trả về URL audio (chunk " + idx + ")");
            }
            byte[] audio = pollAndFetchAudio(url, Duration.ofSeconds(25));
            out.write(audio, 0, audio.length);
        }
        return out.toByteArray();
    }

    private String parseTranscript(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            // Try common FPT STT formats
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (node.has("hypotheses") && node.get("hypotheses").isArray() && node.get("hypotheses").size() > 0) {
                com.fasterxml.jackson.databind.JsonNode first = node.get("hypotheses").get(0);
                if (first.has("transcript")) return first.get("transcript").asText();
            }
            if (node.has("result")) {
                return node.get("result").asText();
            }
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse STT JSON, returning raw: {}", e.getMessage());
            return null;
        }
    }

    private String extractAsyncUrl(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (node.has("async")) {
                String v = node.get("async").asText();
                if (StringUtils.hasText(v)) return v;
            }
            if (node.has("url")) return node.get("url").asText();
            if (node.path("data").has("async")) return node.path("data").get("async").asText();
            if (node.has("async_url")) return node.get("async_url").asText();
            if (node.has("link")) return node.get("link").asText();
            return null;
        } catch (Exception e) {
            log.warn("Failed to parse TTS JSON: {}", e.getMessage());
            return null;
        }
    }

    private byte[] pollAndFetchAudio(String audioUrl, Duration timeout) {
        long start = System.currentTimeMillis();
        Duration interval = Duration.ofMillis(700);
        while (System.currentTimeMillis() - start < timeout.toMillis()) {
            try {
                ResponseEntity<byte[]> resp = webClient.get()
                        .uri(URI.create(audioUrl))
                        .accept(MediaType.ALL)
                        .retrieve()
                        .toEntity(byte[].class)
                        .block(Duration.ofSeconds(10));
                if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null && resp.getBody().length > 0) {
                    MediaType ct = resp.getHeaders().getContentType();
                    boolean audioType = ct != null && (ct.toString().startsWith("audio/") || ct.equals(MediaType.APPLICATION_OCTET_STREAM));
                    if (audioType) {
                        return resp.getBody();
                    }
                    String snippet = null;
                    try {
                        String s = new String(resp.getBody(), java.nio.charset.StandardCharsets.UTF_8);
                        snippet = s.substring(0, Math.min(120, s.length()));
                    } catch (Exception ignored) {}
                    log.debug("Audio not ready: status={}, contentType={}, bodySnippet={}", resp.getStatusCode().value(), ct, snippet);
                }
            } catch (Exception ignored) {}
            try { Thread.sleep(interval.toMillis()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        try {
            return fetchWithRetry(audioUrl, 3, Duration.ofMillis(500));
        } catch (ApiException ignored) {}
        throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Audio chưa sẵn sàng hoặc tải thất bại từ FPT TTS");
    }

    private String postWithRetry(String url, byte[] body, MediaType contentType, int maxAttempts, Duration initialDelay, String label) {
        int attempt = 0;
        Duration delay = initialDelay;
        while (true) {
            attempt++;
            final int currentAttempt = attempt;
            try {
                return webClient.post()
                        .uri(url)
                        .header("api-key", fptApiKey)
                        .contentType(contentType)
                        .bodyValue(body)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty(label + " error")
                                .flatMap(msg -> {
                                    int status = resp.statusCode().value();
                                    log.warn("{} attempt {} failed: status={}, body={} ", label, currentAttempt, status, msg);
                                    Map<String, Object> details = new java.util.HashMap<>();
                                    details.put("providerStatus", status);
                                    details.put("providerBody", msg);
                                    return Mono.error(new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "FPT " + label + " failed", details));
                                }))
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(30));
            } catch (ApiException e) {
                if (attempt >= maxAttempts) throw e;
            } catch (Exception e) {
                log.warn("{} attempt {} exception: {}", label, attempt, e.getMessage());
                if (attempt >= maxAttempts) {
                    throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "FPT " + label + " unreachable", e.getMessage());
                }
            }
            try { Thread.sleep(delay.toMillis()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            delay = delay.plus(delay);
        }
    }

    private void validateTtsResponse(String json) {
        if (!StringUtils.hasText(json)) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "FPT TTS không trả về dữ liệu");
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            boolean hasError = node.has("error") || (node.has("status") && node.get("status").asInt() != 200);
            if (hasError) {
                Map<String, Object> details = new java.util.HashMap<>();
                details.put("providerBody", json);
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "FPT TTS trả về lỗi", details);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("TTS response parse warning: {}", e.getMessage());
        }
    }

    @PostConstruct
    public void initCheck() {
        boolean hasKey = StringUtils.hasText(fptApiKey);
        log.info("FPT Speech config initialized: keyPresent={}, sttEndpoint={}, ttsEndpoint={}", hasKey, fptSttEndpoint, fptTtsEndpoint);
    }

    private String postMultipartWithRetry(String url, byte[] fileBytes, String filename, int maxAttempts, Duration initialDelay, String label) {
        int attempt = 0;
        Duration delay = initialDelay;
        while (true) {
            attempt++;
            final int currentAttempt = attempt;
            try {
                ByteArrayResource resource = new ByteArrayResource(fileBytes) {
                    @Override
                    public String getFilename() {
                        return filename;
                    }
                };
                MultipartBodyBuilder builder = new MultipartBodyBuilder();
                builder.part("file", resource)
                        .filename(filename)
                        .contentType(MediaType.parseMediaType("audio/wav"))
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                ContentDisposition.builder("form-data")
                                        .name("file")
                                        .filename(filename)
                                        .build()
                                        .toString());

                return webClient.post()
                        .uri(url)
                        .header("api-key", fptApiKey)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .body(BodyInserters.fromMultipartData(builder.build()))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty(label + " error")
                                .flatMap(msg -> {
                                    int status = resp.statusCode().value();
                                    log.warn("{} attempt {} failed (multipart): status={}, body={} ", label, currentAttempt, status, msg);
                                    Map<String, Object> details = new java.util.HashMap<>();
                                    details.put("providerStatus", status);
                                    details.put("providerBody", msg);
                                    return Mono.error(new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "FPT " + label + " failed", details));
                                }))
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(30));
            } catch (ApiException e) {
                if (attempt >= maxAttempts) throw e;
            } catch (Exception e) {
                log.warn("{} attempt {} exception (multipart): {}", label, attempt, e.getMessage());
                if (attempt >= maxAttempts) {
                    throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "FPT " + label + " unreachable", e.getMessage());
                }
            }
            try { Thread.sleep(delay.toMillis()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            delay = delay.plus(delay);
        }
    }

    private void validateSttResponse(String json) {
        if (!StringUtils.hasText(json)) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "FPT STT không trả về dữ liệu");
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (node.has("status")) {
                int status = node.get("status").asInt();
                if (status != 0 && status != 200) {
                    Map<String, Object> details = new java.util.HashMap<>();
                    details.put("providerBody", json);
                    throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "FPT STT trả về lỗi", details);
                }
            }
            boolean hasHypotheses = node.has("hypotheses") && node.get("hypotheses").isArray() && node.get("hypotheses").size() > 0;
            boolean hasResult = node.has("result") && StringUtils.hasText(node.get("result").asText());
            if (!hasHypotheses && !hasResult) {
                Map<String, Object> details = new java.util.HashMap<>();
                details.put("providerBody", json);
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "STT không trả về transcript", details);
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("STT response parse warning: {}", e.getMessage());
        }
    }

    private String postJsonWithRetry(String url, Object jsonBody, int maxAttempts, Duration initialDelay, String label) {
        int attempt = 0;
        Duration delay = initialDelay;
        while (true) {
            attempt++;
            final int currentAttempt = attempt;
            try {
                return webClient.post()
                        .uri(url)
                        .header("api-key", fptApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(jsonBody))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                                .defaultIfEmpty(label + " error")
                                .flatMap(msg -> {
                                    int status = resp.statusCode().value();
                                    log.warn("{} attempt {} failed: status={}, body={} ", label, currentAttempt, status, msg);
                                    Map<String, Object> details = new java.util.HashMap<>();
                                    details.put("providerStatus", status);
                                    details.put("providerBody", msg);
                                    return Mono.error(new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "FPT " + label + " failed", details));
                                }))
                        .bodyToMono(String.class)
                        .block(Duration.ofSeconds(30));
            } catch (ApiException e) {
                if (attempt >= maxAttempts) throw e;
            } catch (Exception e) {
                log.warn("{} attempt {} exception: {}", label, attempt, e.getMessage());
                if (attempt >= maxAttempts) {
                    throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "FPT " + label + " unreachable", e.getMessage());
                }
            }
            try { Thread.sleep(delay.toMillis()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            delay = delay.plus(delay);
        }
    }

    private byte[] fetchWithRetry(String url, int maxAttempts, Duration initialDelay) {
        int attempt = 0;
        Duration delay = initialDelay;
        while (true) {
            attempt++;
            try {
                ResponseEntity<byte[]> resp = webClient.get()
                        .uri(URI.create(url))
                        .accept(MediaType.ALL)
                        .retrieve()
                        .toEntity(byte[].class)
                        .block(Duration.ofSeconds(10));
                if (resp != null && resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null && resp.getBody().length > 0) {
                    return resp.getBody();
                }
                int status = resp != null ? resp.getStatusCode().value() : -1;
                log.warn("Fetch audio attempt {} incomplete: status={} length={} ", attempt, status, resp != null && resp.getBody() != null ? resp.getBody().length : -1);
            } catch (Exception e) {
                log.warn("Fetch audio attempt {} exception: {}", attempt, e.getMessage());
                if (attempt >= maxAttempts) {
                    throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Audio tải thất bại từ FPT TTS", e.getMessage());
                }
            }
            if (attempt >= maxAttempts) {
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Audio chưa sẵn sàng hoặc tải thất bại từ FPT TTS");
            }
            try { Thread.sleep(delay.toMillis()); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            delay = delay.plus(delay);
        }
    }
}
