package com.exe.skillverse_backend.ai_usage_service.util;

import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiProviderType;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for counting tokens from AI provider responses.
 * Supports both exact token counts from provider metadata and estimated counts.
 */
@Slf4j
public final class TokenCounterUtil {

    private TokenCounterUtil() {
        // Utility class
    }

    /**
     * Simple token estimation based on character count.
     * Conservative estimate: ~4 characters per token for most languages.
     */
    public static long estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0L;
        }
        return (long) Math.ceil(text.length() / 4.0);
    }

    /**
     * Extract token counts from Gemini API response.
     * Gemini returns usageMetadata at root level with:
     * - promptTokenCount
     * - candidatesTokenCount
     * - totalTokenCount
     */
    public static TokenCounts extractGeminiTokenCounts(JsonNode responseNode) {
        if (responseNode == null) {
            return TokenCounts.estimated(0, 0);
        }

        JsonNode usageMetadata = responseNode.path("usageMetadata");
        if (usageMetadata.isMissingNode()) {
            return TokenCounts.estimated(0, 0);
        }

        int promptTokens = usageMetadata.path("promptTokenCount").asInt(0);
        int completionTokens = usageMetadata.path("candidatesTokenCount").asInt(0);
        int totalTokens = usageMetadata.path("totalTokenCount").asInt(0);

        if (totalTokens == 0 && (promptTokens > 0 || completionTokens > 0)) {
            totalTokens = promptTokens + completionTokens;
        }

        return TokenCounts.exact(promptTokens, completionTokens, totalTokens);
    }

    /**
     * Extract token counts from Mistral/OpenAI API response.
     * Response contains usage object with:
     * - prompt_tokens
     * - completion_tokens
     * - total_tokens
     */
    public static TokenCounts extractOpenAiCompatibleTokenCounts(JsonNode responseNode) {
        if (responseNode == null) {
            return TokenCounts.estimated(0, 0);
        }

        JsonNode usage = responseNode.path("usage");
        if (usage.isMissingNode()) {
            return TokenCounts.estimated(0, 0);
        }

        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(0);

        if (totalTokens == 0 && (promptTokens > 0 || completionTokens > 0)) {
            totalTokens = promptTokens + completionTokens;
        }

        return TokenCounts.exact(promptTokens, completionTokens, totalTokens);
    }

    /**
     * Extract token counts from Ollama/Local AI response.
     * Response contains:
     * - prompt_eval_count (prompt tokens)
     * - eval_count (completion tokens)
     */
    public static TokenCounts extractOllamaTokenCounts(JsonNode responseNode) {
        if (responseNode == null) {
            return TokenCounts.estimated(0, 0);
        }

        int promptTokens = responseNode.path("prompt_eval_count").asInt(0);
        int completionTokens = responseNode.path("eval_count").asInt(0);
        int totalTokens = promptTokens + completionTokens;

        return TokenCounts.exact(promptTokens, completionTokens, totalTokens);
    }

    /**
     * Create estimated token counts from prompt and response text.
     */
    public static TokenCounts estimateFromText(String promptText, String responseText) {
        long promptTokens = estimateTokens(promptText);
        long completionTokens = estimateTokens(responseText);
        return TokenCounts.estimated(promptTokens, completionTokens);
    }

    /**
     * Record class for token counts.
     */
    public record TokenCounts(long promptTokens, long completionTokens, long totalTokens, boolean estimated) {
        public static TokenCounts exact(long prompt, long completion, long total) {
            return new TokenCounts(prompt, completion, total, false);
        }

        public static TokenCounts exact(long prompt, long completion) {
            long total = prompt + completion;
            return new TokenCounts(prompt, completion, total, false);
        }

        public static TokenCounts estimated(long prompt, long completion) {
            long total = prompt + completion;
            return new TokenCounts(prompt, completion, total, true);
        }
    }

    /**
     * Determine provider type from model name or configuration.
     */
    public static AiProviderType detectProviderType(String modelName, boolean isLocalAi) {
        if (isLocalAi) {
            return AiProviderType.LOCAL_AI;
        }
        if (modelName == null) {
            return AiProviderType.UNKNOWN;
        }
        String lower = modelName.toLowerCase();
        if (lower.contains("gemini")) {
            return AiProviderType.GEMINI;
        }
        if (lower.contains("mistral")) {
            return AiProviderType.MISTRAL;
        }
        if (lower.contains("gpt") || lower.contains("openai")) {
            return AiProviderType.OPENAI_COMPATIBLE;
        }
        return AiProviderType.UNKNOWN;
    }
}
