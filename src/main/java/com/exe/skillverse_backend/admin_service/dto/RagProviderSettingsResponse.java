package com.exe.skillverse_backend.admin_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagProviderSettingsResponse {

    /** Whether LOCAL_AI_ENABLED=true AND LOCAL_AI_BASE_URL is present in env */
    private boolean aiRagServiceConfigured;

    /** Whether LOCAL_AI_ENABLED=true in env for generation calls */
    private boolean localAiGenerationConfigured;

    /** Current runtime toggle value for LocalAiGateway.call(...) */
    private boolean localAiGenerationRuntimeEnabled;

    /** Current runtime toggle value for AI-RAG-Service */
    private boolean aiRagServiceRuntimeEnabled;

    /** Whether LOCAL_AI_BASE_URL is non-blank (never reveals the actual URL) */
    private boolean localAiBaseUrlPresent;

    /** Whether AI_RAG_JAVA_ENABLED=true in env */
    private boolean javaRagEnvEnabled;

    /** Current runtime toggle value for Java RAG Fallback */
    private boolean javaRagRuntimeEnabled;

    /** Whether MISTRAL_EMBEDDING_API_KEY is non-blank */
    private boolean mistralEmbeddingKeyPresent;

    /**
     * Effective mode derived from the current settings:
     * AI_RAG_SERVICE | JAVA_RAG | BOTH | NONE
     */
    private String effectiveMode;
}
