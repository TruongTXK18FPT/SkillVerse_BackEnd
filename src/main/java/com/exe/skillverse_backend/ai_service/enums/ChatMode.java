package com.exe.skillverse_backend.ai_service.enums;

/**
 * Enum representing different chat modes in the AI chatbot system
 */
public enum ChatMode {
    /**
     * General career advisory mode - provides broad career guidance
     * Uses default SYSTEM_PROMPT for general career counseling
     */
    GENERAL_CAREER_ADVISOR,
    
    /**
     * Expert mode - specialized advice for specific domain/industry/role
     * Uses ExpertPromptService to get domain-specific system prompts
     */
    EXPERT_MODE
}
