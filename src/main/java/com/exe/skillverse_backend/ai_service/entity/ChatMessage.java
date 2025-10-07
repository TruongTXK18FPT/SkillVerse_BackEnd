package com.exe.skillverse_backend.ai_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing a chat message in the AI career counseling chatbot
 */
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_user_session", columnList = "user_id, session_id"),
        @Index(name = "idx_chat_created", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User who sent the message
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Session ID for conversation grouping
     * Allows maintaining context across multiple messages
     */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /**
     * User's input message
     */
    @Column(nullable = false, length = 2000)
    private String userMessage;

    /**
     * AI's generated response
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String aiResponse;

    /**
     * Timestamp when message was sent
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
