package com.exe.skillverse_backend.ai_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptGenerationResponse {

    /** Markdown text (200-400 words) */
    private String domainRules;

    /** Markdown text (800-3000 words) */
    private String rolePrompt;

    /** Comma-separated suggested keywords for fuzzy matching */
    private String suggestedKeywords;

    private int domainRulesWordCount;
    private int rolePromptWordCount;

    // Echoed back for frontend confirmation
    private String domain;
    private String industry;
    private String jobRole;
}
