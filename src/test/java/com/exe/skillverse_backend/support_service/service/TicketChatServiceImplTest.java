package com.exe.skillverse_backend.support_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.support_service.dto.TicketMessageRequest;
import com.exe.skillverse_backend.support_service.dto.TicketMessageResponse;
import com.exe.skillverse_backend.support_service.entity.SupportTicket;
import com.exe.skillverse_backend.support_service.entity.TicketMessage;
import com.exe.skillverse_backend.support_service.repository.SupportTicketRepository;
import com.exe.skillverse_backend.support_service.repository.TicketMessageRepository;
import com.exe.skillverse_backend.support_service.service.impl.TicketChatServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketChatServiceImplTest {

    @Mock
    private TicketMessageRepository messageRepository;

    @Mock
    private SupportTicketRepository ticketRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private TicketChatServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TicketChatServiceImpl(messageRepository, ticketRepository, userRepository, messagingTemplate);
        lenient().when(messageRepository.save(any(TicketMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("sendMessage should move the ticket to RESPONDED when an admin replies")
    void sendMessage_ShouldMoveTicketToRespondedWhenAdminReplies() {
        SupportTicket ticket = SupportTicket.builder()
                .id(5L)
                .ticketCode("TK-ADMIN001")
                .status(SupportTicket.TicketStatus.PENDING)
                .build();
        User admin = User.builder().id(99L).email("admin@skillverse.vn").build();
        TicketMessageRequest request = TicketMessageRequest.builder()
                .ticketCode(ticket.getTicketCode())
                .content("We are checking it")
                .senderType("ADMIN")
                .senderId(admin.getId())
                .senderName("Admin")
                .senderEmail(admin.getEmail())
                .build();

        when(ticketRepository.findByTicketCode(ticket.getTicketCode())).thenReturn(Optional.of(ticket));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketMessageResponse response = service.sendMessage(request);

        assertEquals("ADMIN", response.getSenderType());
        assertEquals(SupportTicket.TicketStatus.RESPONDED, ticket.getStatus());
        verify(messagingTemplate).convertAndSend("/topic/ticket." + ticket.getTicketCode(), response);
    }

    @Test
    @DisplayName("sendMessage should reject messages for closed tickets")
    void sendMessage_ShouldRejectMessagesForClosedTickets() {
        SupportTicket ticket = SupportTicket.builder()
                .id(6L)
                .ticketCode("TK-CLOSED01")
                .status(SupportTicket.TicketStatus.CLOSED)
                .build();
        when(ticketRepository.findByTicketCode(ticket.getTicketCode())).thenReturn(Optional.of(ticket));

        TicketMessageRequest request = TicketMessageRequest.builder()
                .ticketCode(ticket.getTicketCode())
                .content("Any update?")
                .senderType("USER")
                .build();

        assertThrows(RuntimeException.class, () -> service.sendMessage(request));
    }

    @Test
    @DisplayName("getMessages should map all stored messages")
    void getMessages_ShouldMapAllStoredMessages() {
        SupportTicket ticket = SupportTicket.builder().id(7L).ticketCode("TK-HISTORY").build();
        TicketMessage message = TicketMessage.builder()
                .id(1L)
                .ticket(ticket)
                .senderType(TicketMessage.SenderType.USER)
                .senderName("Learner")
                .content("Hello")
                .build();
        when(messageRepository.findByTicketTicketCodeOrderByCreatedAtAsc(ticket.getTicketCode())).thenReturn(List.of(message));

        List<TicketMessageResponse> responses = service.getMessages(ticket.getTicketCode());

        assertEquals(1, responses.size());
        assertEquals("Hello", responses.get(0).getContent());
    }

    @Test
    @DisplayName("markAsRead should resolve the ticket and delegate to the repository")
    void markAsRead_ShouldDelegateToRepository() {
        SupportTicket ticket = SupportTicket.builder().id(8L).ticketCode("TK-READ001").build();
        when(ticketRepository.findByTicketCode(ticket.getTicketCode())).thenReturn(Optional.of(ticket));

        service.markAsRead(ticket.getTicketCode(), "USER");

        verify(messageRepository).markMessagesAsRead(ticket.getId(), TicketMessage.SenderType.USER);
    }
}
