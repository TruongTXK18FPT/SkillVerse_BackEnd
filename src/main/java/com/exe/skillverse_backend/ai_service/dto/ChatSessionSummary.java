package com.exe.skillverse_backend.ai_service.dto;

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
     * Total message count in session
     */
    private Integer messageCount;
}
