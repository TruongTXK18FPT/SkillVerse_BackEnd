package com.exe.skillverse_backend.chat_service.entity;

import com.exe.skillverse_backend.chat_service.dto.ChatMessage;
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
@Table(name = "user_chat_messages")
public class UserChatMessageEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long senderId;
    private Long recipientId;
    private String senderName;
    private String recipientName;
    private String content;
    private LocalDateTime timestamp;
    
    @Enumerated(EnumType.STRING)
    private ChatMessage.MessageStatus status;
}
