package com.exe.skillverse_backend.family_chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for family chat messages between parents and students
 * @deprecated Deprecated in favor of modern real-time group/mentor chats. Use with caution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Deprecated
public class FamilyChatMessageDTO {
    private Long id;
    private Long senderId;
    private String senderName;
    private Long recipientId;
    private String content;
    private String timestamp;
}
