package com.exe.skillverse_backend.chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GroupChatMessageDTO {
    private Long id;
    private Long groupId;
    private Long senderId;
    private String senderName;
    private String content;
    private LocalDateTime timestamp;
    
    /**
     * Message type: TEXT, EMOJI, GIF, IMAGE
     */
    private String messageType;
    
    /**
     * URL for GIF content
     */
    private String gifUrl;
    
    /**
     * URL for Image content
     */
    private String imageUrl;
    
    /**
     * Custom emoji code
     */
    private String emojiCode;
    
    /**
     * Sender's avatar URL
     */
    private String senderAvatarUrl;
}
