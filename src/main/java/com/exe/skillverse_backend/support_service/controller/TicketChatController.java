package com.exe.skillverse_backend.support_service.controller;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.support_service.dto.TicketMessageRequest;
import com.exe.skillverse_backend.support_service.dto.TicketMessageResponse;
import com.exe.skillverse_backend.support_service.entity.SupportTicket;
import com.exe.skillverse_backend.support_service.entity.TicketMessage;
import com.exe.skillverse_backend.support_service.repository.SupportTicketRepository;
import com.exe.skillverse_backend.support_service.repository.TicketMessageRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/support/chat")
@RequiredArgsConstructor
@Tag(name = "Ticket Chat", description = "Real-time chat for support tickets")
public class TicketChatController {

    private final TicketMessageRepository messageRepository;
    private final SupportTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * WebSocket endpoint to send a message in a ticket chat
     */
    @MessageMapping("/ticket.sendMessage")
    @Transactional
    public void sendMessage(@Payload TicketMessageRequest request) {
        log.info("Received message for ticket: {}", request.getTicketCode());
        
        SupportTicket ticket = ticketRepository.findByTicketCode(request.getTicketCode())
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        // Check if ticket is closed - don't allow messages
        if (ticket.getStatus() == SupportTicket.TicketStatus.CLOSED) {
            log.warn("Cannot send message to closed ticket: {}", request.getTicketCode());
            return;
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
    }

    /**
     * REST endpoint to get all messages for a ticket
     */
    @GetMapping("/{ticketCode}/messages")
    @Operation(summary = "Get ticket messages", description = "Get all messages for a ticket by its code")
    public ResponseEntity<List<TicketMessageResponse>> getMessages(@PathVariable String ticketCode) {
        List<TicketMessage> messages = messageRepository.findByTicketTicketCodeOrderByCreatedAtAsc(ticketCode);
        List<TicketMessageResponse> responses = messages.stream()
                .map(TicketMessageResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * REST endpoint to send a message (alternative to WebSocket)
     */
    @PostMapping("/{ticketCode}/messages")
    @Transactional
    @Operation(summary = "Send message", description = "Send a message to a ticket chat")
    public ResponseEntity<TicketMessageResponse> sendMessageRest(
            @PathVariable String ticketCode,
            @RequestBody TicketMessageRequest request) {
        
        request.setTicketCode(ticketCode);
        
        SupportTicket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));

        // Check if ticket is closed
        if (ticket.getStatus() == SupportTicket.TicketStatus.CLOSED) {
            return ResponseEntity.badRequest().build();
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

        // Update ticket status
        if (senderType == TicketMessage.SenderType.ADMIN) {
            ticket.setStatus(SupportTicket.TicketStatus.RESPONDED);
        } else if (ticket.getStatus() == SupportTicket.TicketStatus.RESPONDED) {
            ticket.setStatus(SupportTicket.TicketStatus.IN_PROGRESS);
        }
        ticketRepository.save(ticket);

        TicketMessageResponse response = TicketMessageResponse.fromEntity(message);

        // Also broadcast via WebSocket
        messagingTemplate.convertAndSend("/topic/ticket." + ticketCode, response);

        return ResponseEntity.ok(response);
    }

    /**
     * Mark messages as read
     */
    @PostMapping("/{ticketCode}/messages/read")
    @Transactional
    @Operation(summary = "Mark messages as read", description = "Mark all messages from a specific sender type as read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String ticketCode,
            @RequestParam String senderType) {
        
        SupportTicket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new RuntimeException("Ticket not found"));
        
        TicketMessage.SenderType type = TicketMessage.SenderType.valueOf(senderType);
        messageRepository.markMessagesAsRead(ticket.getId(), type);
        
        return ResponseEntity.ok().build();
    }
}
