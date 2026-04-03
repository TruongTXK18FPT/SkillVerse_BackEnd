package com.exe.skillverse_backend.ai_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratePromptRequest {

    @NotBlank(message = "Domain is required")
    private String domain;

    @NotBlank(message = "Industry is required")
    private String industry;

    @NotBlank(message = "Job role is required")
    private String jobRole;

    /**
     * Optional comma-separated keywords for fuzzy matching.
     */
    private String keywords;

    /**
     * If provided, AI uses this as reference context when generating domainRules.
     * Frontend passes existing domainRules from domainRulesMap when an existing domain is selected.
     */
    private String existingDomainRules;

    /**
     * Free-text hint for the AI (e.g., "phù hợp fresh graduate", "focus cloud-native").
     */
    private String generationHint;
}
