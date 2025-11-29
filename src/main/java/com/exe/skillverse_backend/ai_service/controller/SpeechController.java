package com.exe.skillverse_backend.ai_service.controller;

import com.exe.skillverse_backend.ai_service.dto.request.TtsRequest;
import com.exe.skillverse_backend.ai_service.dto.response.SttResponse;
import com.exe.skillverse_backend.ai_service.service.AiSpeechService;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.net.InetAddress;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/speech")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Speech", description = "Speech-to-Text and Text-to-Speech for AI Career Chat")
@SecurityRequirement(name = "bearerAuth")
public class SpeechController {

    private final AiSpeechService aiSpeechService;
    private final PremiumService premiumService;
    @Value("${ai.speech.tts.enabled:false}")
    private boolean ttsEnabled;

    private Long getUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String userIdStr = jwt.getClaimAsString("userId");
        if (userIdStr == null || userIdStr.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "User ID not found in token");
        }
        try { return Long.valueOf(userIdStr); } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid user ID format");
        }
    }

    @GetMapping("/health")
    @Operation(summary = "AI Speech health", description = "Check configuration and external connectivity for STT/TTS")
    public ResponseEntity<Map<String, Object>> health(Authentication authentication) {
        Long userId = getUserId(authentication);
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("userId", userId);

        Map<String, Object> checks = new HashMap<>();
        checks.put("apiKeyPresent", aiSpeechService != null);
        try {
            InetAddress addr = InetAddress.getByName("api.fpt.ai");
            checks.put("dnsResolved", true);
            checks.put("resolvedAddress", addr.getHostAddress());
        } catch (Exception e) {
            checks.put("dnsResolved", false);
            checks.put("dnsError", e.getMessage());
        }
        body.put("checks", checks);
        return ResponseEntity.ok(body);
    }

    @PostMapping(value = "/stt", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    @Operation(summary = "Speech to Text", description = "Transcribe recorded audio to text")
    public ResponseEntity<SttResponse> speechToText(
            @RequestPart("file") MultipartFile audio,
            @RequestParam(value = "language", required = false) String language,
            Authentication authentication) {

        Long userId = getUserId(authentication);
        log.info("User {} requested STT ({} bytes)", userId, audio.getSize());

        SttResponse result = aiSpeechService.transcribeAudio(audio, language);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/tts", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Text to Speech (Premium only)", description = "Synthesize voice from text for premium accounts")
    public ResponseEntity<byte[]> textToSpeech(
            @RequestBody TtsRequest request,
            Authentication authentication) {

        Long userId = getUserId(authentication);
        log.info("User {} requested TTS: {} chars, voice={} ", userId, request.getText().length(), request.getVoice());

        if (!ttsEnabled) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "TTS tạm thời bị vô hiệu hóa");
        }

        boolean hasPremium = premiumService.hasActivePremiumSubscription(userId);
        if (!hasPremium) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Chỉ tài khoản Premium mới được sử dụng Text-to-Speech");
        }

        AiSpeechService.TtsResult tts = aiSpeechService.synthesizeSpeech(request.getText(), request.getVoice(), request.getSpeed());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(tts.getContentType() != null ? tts.getContentType() : MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentLength(tts.getAudio().length);
        return new ResponseEntity<>(tts.getAudio(), headers, HttpStatus.OK);
    }
}
