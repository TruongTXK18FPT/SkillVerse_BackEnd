package com.exe.skillverse_backend.chat_service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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

    private Long groupId;
    private Long senderId;
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
