package com.exe.skillverse_backend.ai_service.dto.response;

import com.exe.skillverse_backend.ai_service.enums.ChatMode;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for chatbot messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private Long sessionId;
    private String message;
    private String aiResponse;
    private LocalDateTime timestamp;
    
    /**
     * Current chat mode
     */
    private ChatMode chatMode;
    
    /**
     * Expert context (only populated in EXPERT_MODE)
     */
    private ExpertContext expertContext;

    /**
     * Detected domain from user message (e.g., "it", "business", "design")
     * Populated when smart detection identifies a domain from keywords
     */
    private String detectedDomain;
    
    /**
     * Nested class for expert mode context information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpertContext {
        private String domain;
        private String industry;
        private String jobRole;
        private String expertName; // e.g., "Backend Developer Expert"
        private String mediaUrl; // Icon/avatar URL for the expert from Cloudinary
    }
}
