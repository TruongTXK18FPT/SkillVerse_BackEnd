package com.exe.skillverse_backend.meowl_chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for Meowl Chat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeowlChatResponse {
    
    private String message;
    
    private String originalMessage; // Original AI response before cute formatting
    
    private boolean success;
    
    private LocalDateTime timestamp;
    
    private List<MeowlReminder> reminders; // Learning reminders
    
    private List<MeowlNotification> notifications; // Platform notifications
    
    private String mood; // Meowl's mood: "happy", "excited", "encouraging", "playful"
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MeowlReminder {
        private String type; // "course", "skill", "roadmap"
        private String title;
        private String description;
        private String actionUrl;
        private String emoji;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MeowlNotification {
        private String type; // "progress", "achievement", "tip", "motivation"
        private String message;
        private String emoji;
        private LocalDateTime createdAt;
    }
}
