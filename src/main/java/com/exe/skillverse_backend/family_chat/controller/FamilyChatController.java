package com.exe.skillverse_backend.family_chat.controller;

import com.exe.skillverse_backend.family_chat.dto.FamilyChatMessageDTO;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * WebSocket controller for family chat between parents and students
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class FamilyChatController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle family chat messages
     * Message pattern: /app/family/{parentId}/{studentId}
     * Broadcast pattern: /topic/family/{parentId}/{studentId}
     */
    @MessageMapping("/family/{parentId}/{studentId}")
    public void processFamilyMessage(
            @DestinationVariable Long parentId,
            @DestinationVariable Long studentId,
            @Payload FamilyChatMessageDTO message) {
        
        log.info("Received family message from user {} to user {}", message.getSenderId(), message.getRecipientId());
        
        // Validate sender is either parent or student
        if (!message.getSenderId().equals(parentId) && !message.getSenderId().equals(studentId)) {
            log.warn("Invalid sender {} for family chat between {} and {}", 
                message.getSenderId(), parentId, studentId);
            return;
        }
        
        // Validate recipient is either parent or student
        if (!message.getRecipientId().equals(parentId) && !message.getRecipientId().equals(studentId)) {
            log.warn("Invalid recipient {} for family chat between {} and {}", 
                message.getRecipientId(), parentId, studentId);
            return;
        }
        
        // Add server timestamp
        if (message.getTimestamp() == null) {
            message.setTimestamp(Instant.now().toString());
        }
        
        // Generate message ID if not present
        if (message.getId() == null) {
            message.setId(System.currentTimeMillis());
        }
        
        // Broadcast to both parent and student
        String topic = String.format("/topic/family/%d/%d", parentId, studentId);
        messagingTemplate.convertAndSend(topic, message);
        
        log.info("Broadcasted family message to topic: {}", topic);
    }
}
