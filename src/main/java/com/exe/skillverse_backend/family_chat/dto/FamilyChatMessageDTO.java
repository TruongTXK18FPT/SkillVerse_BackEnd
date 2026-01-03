package com.exe.skillverse_backend.family_chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for family chat messages between parents and students
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FamilyChatMessageDTO {
    private Long id;
    private Long senderId;
    private String senderName;
    private Long recipientId;
    private String content;
    private String timestamp;
}
