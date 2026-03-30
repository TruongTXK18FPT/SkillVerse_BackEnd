package com.exe.skillverse_backend.ai_service.dto;

import com.exe.skillverse_backend.ai_service.enums.ChatMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for chat session summary
 * Provides session metadata with title preview
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionSummary {

    private Long sessionId;

    /**
     * Title preview (first user message, truncated)
     */
    private String title;

    /**
     * Timestamp of latest message in session
     */
    private LocalDateTime lastMessageAt;

    /**
     * Timestamp when session was created
     */
    private LocalDateTime createdAt;

    /**
     * Total message count in session
     */
    private Integer messageCount;

    /**
     * Persisted session mode for reliable frontend filtering
     */
    private ChatMode chatMode;
}
