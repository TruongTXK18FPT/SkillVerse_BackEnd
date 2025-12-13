package com.exe.skillverse_backend.ai_service.dto.request;

import com.exe.skillverse_backend.ai_service.enums.ChatMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for chatbot conversation
 * Supports two modes:
 * 1. GENERAL_CAREER_ADVISOR - General career counseling (default)
 * 2. EXPERT_MODE - Specialized advice for specific domain/industry/role
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    @NotBlank(message = "Message cannot be empty")
    @Size(max = 2000, message = "Message cannot exceed 2000 characters")
    private String message;

    /**
     * Optional session ID for maintaining conversation context
     * If null, a new conversation session will be created
     */
    private Long sessionId;

    /**
     * Chat mode selection
     * Default: GENERAL_CAREER_ADVISOR for backward compatibility
     */
    @Builder.Default
    private ChatMode chatMode = ChatMode.GENERAL_CAREER_ADVISOR;

    /**
     * Optional: AI agent mode selection
     * Example: "deep-research-pro-preview-12-2025"
     */
    private String aiAgentMode;

    /**
     * Optional: Broad field (e.g., "Information Technology")
     * Required when chatMode = EXPERT_MODE
     */
    private String domain;

    /**
     * Optional: Industry/Sector (e.g., "Software Development")
     * Required when chatMode = EXPERT_MODE
     */
    private String industry;

    /**
     * Optional: Specific Role (e.g., "Backend Developer")
     * Required when chatMode = EXPERT_MODE
     */
    private String jobRole;
}
