package com.exe.skillverse_backend.ai_service.dto.response;

import com.exe.skillverse_backend.ai_service.enums.ChatMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
