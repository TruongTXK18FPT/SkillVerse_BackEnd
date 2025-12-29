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
public class ChatMessage {
    private Long id;
    private Long senderId;
    private Long recipientId;
    private String senderName;
    private String recipientName;
    private String content;
    private LocalDateTime timestamp;
    private MessageStatus status;

    public enum MessageStatus {
        RECEIVED, DELIVERED
    }
}
