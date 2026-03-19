package com.exe.skillverse_backend.chat_service.service;

import com.exe.skillverse_backend.chat_service.dto.ChatMessage;
import com.exe.skillverse_backend.chat_service.entity.UserChatMessageEntity;
import com.exe.skillverse_backend.chat_service.repository.UserChatMessageRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final UserChatMessageRepository repository;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public ChatMessage save(ChatMessage chatMessage) {
        chatMessage.setStatus(ChatMessage.MessageStatus.RECEIVED);
        // Normalize timestamps to Vietnam timezone to avoid client-side offset (-7h) issues
        chatMessage.setTimestamp(LocalDateTime.now(VN_ZONE));
        
        UserChatMessageEntity entity = UserChatMessageEntity.builder()
                .senderId(chatMessage.getSenderId())
                .recipientId(chatMessage.getRecipientId())
                .senderName(chatMessage.getSenderName())
                .recipientName(chatMessage.getRecipientName())
                .content(chatMessage.getContent())
                .timestamp(chatMessage.getTimestamp())
                .status(chatMessage.getStatus())
                .build();
                
        repository.save(entity);
        return chatMessage;
    }

    public List<ChatMessage> findChatMessages(Long senderId, Long recipientId) {
        List<UserChatMessageEntity> sent = repository.findBySenderIdAndRecipientId(senderId, recipientId);
        List<UserChatMessageEntity> received = repository.findBySenderIdAndRecipientId(recipientId, senderId);
        
        List<UserChatMessageEntity> all = new ArrayList<>();
        all.addAll(sent);
        all.addAll(received);
        
        return all.stream()
                .sorted(Comparator.comparing(UserChatMessageEntity::getTimestamp))
                .map(entity -> ChatMessage.builder()
                        .id(entity.getId())
                        .senderId(entity.getSenderId())
                        .recipientId(entity.getRecipientId())
                        .senderName(entity.getSenderName())
                        .recipientName(entity.getRecipientName())
                        .content(entity.getContent())
                        .timestamp(entity.getTimestamp())
                        .status(entity.getStatus())
                        .build())
                .collect(Collectors.toList());
    }
}
