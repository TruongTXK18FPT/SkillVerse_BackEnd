package com.exe.skillverse_backend.support_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.support_service.dto.TicketMessageRequest;
import com.exe.skillverse_backend.support_service.dto.TicketMessageResponse;
import com.exe.skillverse_backend.support_service.entity.SupportTicket;
import com.exe.skillverse_backend.support_service.entity.TicketMessage;
import com.exe.skillverse_backend.support_service.repository.SupportTicketRepository;
import com.exe.skillverse_backend.support_service.repository.TicketMessageRepository;
import com.exe.skillverse_backend.support_service.service.TicketChatService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketChatServiceImpl implements TicketChatService {

    private final TicketMessageRepository messageRepository;
    private final SupportTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public TicketMessageResponse sendMessage(TicketMessageRequest request) {
        log.info("Received message for ticket: {}", request.getTicketCode());

        SupportTicket ticket = ticketRepository.findByTicketCode(request.getTicketCode())
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        // Check if ticket is closed - don't allow messages
        if (ticket.getStatus() == SupportTicket.TicketStatus.CLOSED) {
            log.warn("Cannot send message to closed ticket: {}", request.getTicketCode());
            throw new RuntimeException("Cannot send message to closed ticket");
        }

        User sender = null;
        if (request.getSenderId() != null) {
            sender = userRepository.findById(request.getSenderId()).orElse(null);
        }

        TicketMessage.SenderType senderType = TicketMessage.SenderType.valueOf(request.getSenderType());

        TicketMessage message = TicketMessage.builder()
                .ticket(ticket)
                .sender(sender)
                .senderEmail(request.getSenderEmail())
                .senderName(request.getSenderName())
                .senderType(senderType)
                .content(request.getContent())
                .build();

        message = messageRepository.save(message);

        // Update ticket status based on who sent the message
        if (senderType == TicketMessage.SenderType.ADMIN) {
            ticket.setStatus(SupportTicket.TicketStatus.RESPONDED);
        } else if (ticket.getStatus() == SupportTicket.TicketStatus.RESPONDED) {
            ticket.setStatus(SupportTicket.TicketStatus.IN_PROGRESS);
        }
        ticketRepository.save(ticket);

        TicketMessageResponse response = TicketMessageResponse.fromEntity(message);

        // Broadcast message to all subscribers of this ticket
        messagingTemplate.convertAndSend("/topic/ticket." + request.getTicketCode(), response);

        log.info("Message sent and broadcasted for ticket: {}", request.getTicketCode());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketMessageResponse> getMessages(String ticketCode) {
        List<TicketMessage> messages = messageRepository.findByTicketTicketCodeOrderByCreatedAtAsc(ticketCode);
        return messages.stream()
                .map(TicketMessageResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public void markAsRead(String ticketCode, String senderType) {
        SupportTicket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        TicketMessage.SenderType type = TicketMessage.SenderType.valueOf(senderType);
        messageRepository.markMessagesAsRead(ticket.getId(), type);
    }
}