package com.exe.skillverse_backend.support_service.controller;

import com.exe.skillverse_backend.support_service.dto.TicketMessageRequest;
import com.exe.skillverse_backend.support_service.dto.TicketMessageResponse;
import com.exe.skillverse_backend.support_service.service.TicketChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/support/chat")
@RequiredArgsConstructor
@Tag(name = "Ticket Chat", description = "Real-time chat for support tickets")
public class TicketChatController {

    private final TicketChatService ticketChatService;

    /**
     * WebSocket endpoint to send a message in a ticket chat
     */
    @MessageMapping("/ticket.sendMessage")
    public void sendMessage(@Payload TicketMessageRequest request) {
        try {
            ticketChatService.sendMessage(request);
        } catch (Exception e) {
            log.error("Error sending message via WebSocket", e);
        }
    }

    /**
     * REST endpoint to get all messages for a ticket
     */
    @GetMapping("/{ticketCode}/messages")
    @Operation(summary = "Get ticket messages", description = "Get all messages for a ticket by its code")
    public ResponseEntity<List<TicketMessageResponse>> getMessages(@PathVariable String ticketCode) {
        return ResponseEntity.ok(ticketChatService.getMessages(ticketCode));
    }

    /**
     * REST endpoint to send a message (alternative to WebSocket)
     */
    @PostMapping("/{ticketCode}/messages")
    @Operation(summary = "Send message", description = "Send a message to a ticket chat")
    public ResponseEntity<TicketMessageResponse> sendMessageRest(
            @PathVariable String ticketCode,
            @RequestBody TicketMessageRequest request) {
        
        request.setTicketCode(ticketCode);
        try {
            return ResponseEntity.ok(ticketChatService.sendMessage(request));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Mark messages as read
     */
    @PostMapping("/{ticketCode}/messages/read")
    @Operation(summary = "Mark messages as read", description = "Mark all messages from a specific sender type as read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String ticketCode,
            @RequestParam String senderType) {
        
        ticketChatService.markAsRead(ticketCode, senderType);
        return ResponseEntity.ok().build();
    }
}
