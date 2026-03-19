package com.exe.skillverse_backend.chat_service.controller;

import com.exe.skillverse_backend.chat_service.dto.ChatMessage;
import com.exe.skillverse_backend.chat_service.service.ChatService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessage chatMessage) {
        log.info("Received message from {} to {}", chatMessage.getSenderId(), chatMessage.getRecipientId());
        ChatMessage saved = chatService.save(chatMessage);
        
        // Send to recipient
        messagingTemplate.convertAndSendToUser(
                String.valueOf(chatMessage.getRecipientId()),
                "/queue/messages",
                saved
        );
        
        // Send back to sender (for multi-device sync)
        messagingTemplate.convertAndSendToUser(
                String.valueOf(chatMessage.getSenderId()),
                "/queue/messages",
                saved
        );
    }

    @GetMapping("/api/messages/{senderId}/{recipientId}")
    @ResponseBody
    public List<ChatMessage> findChatMessages(@PathVariable Long senderId, @PathVariable Long recipientId) {
        return chatService.findChatMessages(senderId, recipientId);
    }
}
