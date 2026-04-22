package com.exe.skillverse_backend.portfolio_service.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response containing AI-enhanced content for a CV section.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIEnhanceResponse {

    /**
     * The primary AI-enhanced content
     */
    private String enhancedContent;

    /**
     * Alternative versions (2-3 variations) for user to choose from
     */
    private List<String> alternatives;

    /**
     * Section that was enhanced
     */
    private String section;

    /**
     * Item ID if applicable
     */
    private String itemId;

    /**
     * Whether AI processing was successful
     */
    private boolean success;

    /**
     * Error message if enhancement failed
     */
    private String errorMessage;

    /**
     * Suggested improvements or tips from AI
     */
    private String suggestions;
}
