package com.exe.skillverse_backend.chat_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "group_chat_messages")
public class GroupChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private GroupChat group;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", insertable = false, updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User sender;

    private String senderName;
    private String content;
    
    /**
     * Message type: TEXT, EMOJI, GIF, IMAGE
     */
    @Column(length = 20)
    @Builder.Default
    private String messageType = "TEXT";
    
    /**
     * URL for GIF content
     */
    @Column(length = 500)
    private String gifUrl;
    
    /**
     * URL for Image content
     */
    @Column(length = 500)
    private String imageUrl;
    
    /**
     * Custom emoji code
     */
    @Column(length = 100)
    private String emojiCode;
    
    /**
     * Sender's avatar URL
     */
    @Column(length = 500)
    private String senderAvatarUrl;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
