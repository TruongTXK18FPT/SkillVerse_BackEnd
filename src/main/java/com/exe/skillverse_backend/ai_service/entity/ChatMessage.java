package com.exe.skillverse_backend.ai_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Entity representing a chat message in the AI career counseling chatbot
 */
@Entity
@Table(name = "chat_messages", indexes = {
        @Index(name = "idx_chat_user_session", columnList = "user_id, session_id"),
        @Index(name = "idx_chat_created", columnList = "created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User who sent the message
     * EAGER fetch to avoid lazy loading serialization issues
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    /**
     * Owning chat session for this message pair
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ChatSession chatSession;

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

    public Long getSessionId() {
        return chatSession != null ? chatSession.getId() : null;
    }
}
