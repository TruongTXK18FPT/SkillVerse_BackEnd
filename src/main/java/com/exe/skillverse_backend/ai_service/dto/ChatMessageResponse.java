package com.exe.skillverse_backend.ai_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for chat message responses
 * Avoids exposing full User entity and prevents lazy loading issues
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {

    private Long id;
    private Long sessionId;
    private String userMessage;
    private String aiResponse;
    private LocalDateTime createdAt;

    /**
     * Basic user info (avoid exposing sensitive data)
     */
    private Long userId;
    private String userEmail;
}
