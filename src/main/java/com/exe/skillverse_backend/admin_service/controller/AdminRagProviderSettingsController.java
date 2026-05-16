package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.dto.RagProviderSettingsResponse;
import com.exe.skillverse_backend.admin_service.dto.UpdateRagProviderSettingRequest;
import com.exe.skillverse_backend.ai_rag_service.config.AiRagProperties;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.runtime_settings.service.AppRuntimeSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/admin/rag-provider-settings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin RAG Provider Settings", description = "Runtime toggles for RAG backends")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminRagProviderSettingsController {

    private final AppRuntimeSettingService runtimeSettingService;
    private final AiRagProperties aiRagProperties;

    @Value("${skillverse.ai.local.enabled:false}")
    private boolean localAiEnvEnabled;

    @Value("${skillverse.ai.local.base-url:}")
    private String localAiBaseUrl;

    @Value("${skillverse.ai.mistral.embedding.api-key:}")
    private String mistralEmbeddingApiKey;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Get RAG provider settings")
    public ResponseEntity<RagProviderSettingsResponse> getSettings() {
        return ResponseEntity.ok(buildResponse());
    }

    @PatchMapping("/ai-rag-service")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Enable or disable AI-RAG-Service at runtime")
    public ResponseEntity<RagProviderSettingsResponse> updateAiRagService(
            @RequestBody UpdateRagProviderSettingRequest request,
            @AuthenticationPrincipal User currentUser) {

        if (request.isEnabled()) {
            if (!localAiEnvEnabled) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot enable AI-RAG-Service: LOCAL_AI_ENABLED is false in environment.");
            }
            if (localAiBaseUrl == null || localAiBaseUrl.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot enable AI-RAG-Service: LOCAL_AI_BASE_URL is not configured.");
            }
        }

        Long userId = currentUser != null ? currentUser.getId() : null;
        runtimeSettingService.setAiRagServiceEnabled(request.isEnabled(), userId);
        log.info("Admin {} set AI-RAG-Service runtime={}", userId, request.isEnabled());
        return ResponseEntity.ok(buildResponse());
    }

    @PatchMapping("/local-ai-generation")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Enable or disable Local AI generation at runtime")
    public ResponseEntity<RagProviderSettingsResponse> updateLocalAiGeneration(
            @RequestBody UpdateRagProviderSettingRequest request,
            @AuthenticationPrincipal User currentUser) {

        if (request.isEnabled() && !localAiEnvEnabled) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot enable Local AI generation: LOCAL_AI_ENABLED is false in environment.");
        }

        Long userId = currentUser != null ? currentUser.getId() : null;
        runtimeSettingService.setLocalAiGenerationEnabled(request.isEnabled(), userId);
        log.info("Admin {} set Local AI generation runtime={}", userId, request.isEnabled());
        return ResponseEntity.ok(buildResponse());
    }

    @PatchMapping("/java-rag-fallback")
    @PreAuthorize("hasRole('ADMIN') or hasRole('AI_ADMIN')")
    @Operation(summary = "Enable or disable Java RAG at runtime")
    public ResponseEntity<RagProviderSettingsResponse> updateJavaRagFallback(
            @RequestBody UpdateRagProviderSettingRequest request,
            @AuthenticationPrincipal User currentUser) {

        if (request.isEnabled()) {
            if (!aiRagProperties.isJavaEnabled()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot enable Java RAG: AI_RAG_JAVA_ENABLED is false in environment.");
            }
            if (mistralEmbeddingApiKey == null || mistralEmbeddingApiKey.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot enable Java RAG: MISTRAL_EMBEDDING_API_KEY is not configured.");
            }
        }

        Long userId = currentUser != null ? currentUser.getId() : null;
        runtimeSettingService.setJavaRagFallbackEnabled(request.isEnabled(), userId);
        log.info("Admin {} set Java RAG runtime={}", userId, request.isEnabled());
        return ResponseEntity.ok(buildResponse());
    }

    private RagProviderSettingsResponse buildResponse() {
        boolean aiRagServiceConfigured = localAiEnvEnabled
                && localAiBaseUrl != null && !localAiBaseUrl.isBlank();
        boolean javaRagEnvEnabled = aiRagProperties.isJavaEnabled();
        boolean mistralKeyPresent = mistralEmbeddingApiKey != null && !mistralEmbeddingApiKey.isBlank();
        boolean aiRagServiceOn = runtimeSettingService.isAiRagServiceRuntimeEnabled();
        boolean localAiGenerationOn = runtimeSettingService.isLocalAiGenerationRuntimeEnabled();
        boolean javaRagOn = runtimeSettingService.isJavaRagFallbackRuntimeEnabled();

        String effectiveMode;
        boolean effectiveRemote = aiRagServiceConfigured && aiRagServiceOn;
        boolean effectiveJava = javaRagEnvEnabled && javaRagOn && mistralKeyPresent;
        if (effectiveRemote && effectiveJava) {
            effectiveMode = "BOTH";
        } else if (effectiveRemote) {
            effectiveMode = "AI_RAG_SERVICE";
        } else if (effectiveJava) {
            effectiveMode = "JAVA_RAG";
        } else {
            effectiveMode = "NONE";
        }

        return RagProviderSettingsResponse.builder()
                .aiRagServiceConfigured(aiRagServiceConfigured)
                .localAiGenerationConfigured(localAiEnvEnabled)
                .localAiGenerationRuntimeEnabled(localAiGenerationOn)
                .aiRagServiceRuntimeEnabled(aiRagServiceOn)
                .localAiBaseUrlPresent(localAiBaseUrl != null && !localAiBaseUrl.isBlank())
                .javaRagEnvEnabled(javaRagEnvEnabled)
                .javaRagRuntimeEnabled(javaRagOn)
                .mistralEmbeddingKeyPresent(mistralKeyPresent)
                .effectiveMode(effectiveMode)
                .build();
    }
}
