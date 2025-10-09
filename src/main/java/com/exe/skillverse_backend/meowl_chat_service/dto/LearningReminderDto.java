package com.exe.skillverse_backend.meowl_chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for Learning Reminders
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningReminderDto {
    
    private Long userId;
    
    private String reminderType; // "daily_check_in", "course_incomplete", "skill_practice", "roadmap_progress"
    
    private String message;
    
    private String language;
    
    private LocalDateTime scheduledFor;
    
    private boolean sent;
}
