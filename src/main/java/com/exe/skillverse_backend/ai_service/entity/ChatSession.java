package com.exe.skillverse_backend.ai_service.entity;

import com.exe.skillverse_backend.ai_service.enums.ChatMode;
import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "chat_sessions", indexes = {
        @Index(name = "idx_chat_sessions_user", columnList = "user_id"),
        @Index(name = "idx_chat_sessions_user_last_message", columnList = "user_id, last_message_at"),
        @Index(name = "idx_chat_sessions_mode", columnList = "chat_mode"),
        @Index(name = "idx_chat_sessions_expert_prompt", columnList = "expert_prompt_config_id"),
        @Index(name = "idx_chat_sessions_taxonomy", columnList = "taxonomy_entry_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "chat_mode", nullable = false, length = 40)
    private ChatMode chatMode = ChatMode.GENERAL_CAREER_ADVISOR;

    @Column(name = "custom_title", length = 100)
    private String customTitle;

    @Column(length = 255)
    private String domain;

    @Column(length = 255)
    private String industry;

    @Column(name = "job_role", length = 255)
    private String jobRole;

    /**
     * Detected domain from smart detection (e.g., "it", "business", "design")
     * Persisted to maintain expert persona across session when user's message
     * doesn't contain clear domain keywords
     */
    @Column(name = "detected_domain", length = 50)
    private String detectedDomain;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expert_prompt_config_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ExpertPromptConfig expertPromptConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "taxonomy_entry_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TaxonomyEntry taxonomyEntry;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_message_at", nullable = false)
    private LocalDateTime lastMessageAt;

    @Builder.Default
    @OneToMany(mappedBy = "chatSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ChatMessage> messages = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (lastMessageAt == null) {
            lastMessageAt = createdAt;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
