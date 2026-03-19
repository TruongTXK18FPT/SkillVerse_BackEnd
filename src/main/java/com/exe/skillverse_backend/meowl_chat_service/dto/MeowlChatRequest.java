package com.exe.skillverse_backend.meowl_chat_service.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for Meowl Chat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeowlChatRequest {
    
    private String message;
    
    private String language; // "en" or "vi"
    
    private Long userId; // Optional: for personalized responses

    /**
     * Optional role mode from UI.
     * Supported values: LEARNER, MENTOR, RECRUITER
     */
    private String activeRole;

    /**
     * Optional client session identifier (used for chat metadata/debugging).
     */
    private String sessionId;
    
    private List<ChatMessage> chatHistory; // Optional: conversation context
    
    private boolean includeReminders; // Whether to check for reminders
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String role; // "user" or "assistant"
        private String content;
    }
}
