package com.exe.skillverse_backend.meowl_chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
