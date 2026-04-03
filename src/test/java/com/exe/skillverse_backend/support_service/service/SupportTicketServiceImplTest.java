package com.exe.skillverse_backend.support_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.support_service.dto.CreateTicketRequest;
import com.exe.skillverse_backend.support_service.dto.TicketResponse;
import com.exe.skillverse_backend.support_service.dto.UpdateTicketRequest;
import com.exe.skillverse_backend.support_service.entity.SupportTicket;
import com.exe.skillverse_backend.support_service.repository.SupportTicketRepository;
import com.exe.skillverse_backend.support_service.repository.TicketMessageRepository;
import com.exe.skillverse_backend.support_service.service.impl.SupportTicketServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupportTicketServiceImplTest {

    @Mock
    private SupportTicketRepository ticketRepository;

    @Mock
    private TicketMessageRepository ticketMessageRepository;

    @Mock
    private UserRepository userRepository;

    private SupportTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SupportTicketServiceImpl(ticketRepository, ticketMessageRepository, userRepository);
        lenient().when(ticketRepository.save(any(SupportTicket.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("createTicket should support guest tickets and generate a TK code")
    void createTicket_ShouldSupportGuestTickets() {
        CreateTicketRequest request = CreateTicketRequest.builder()
                .email("guest@skillverse.vn")
                .subject("Cannot access a lesson")
                .category("course")
                .description("The course lesson page keeps failing to load.")
                .build();

        TicketResponse response = service.createTicket(request);

        assertTrue(response.getTicketCode().startsWith("TK-"));
        assertEquals(SupportTicket.TicketStatus.PENDING.name(), response.getStatus());
        verify(userRepository, never()).findById(any());
    }

    @Test
    @DisplayName("getAllTickets should parse filters case-insensitively")
    void getAllTickets_ShouldParseFiltersCaseInsensitively() {
        SupportTicket ticket = SupportTicket.builder()
                .id(5L)
                .ticketCode("TK-ABC12345")
                .email("user@skillverse.vn")
                .subject("Payment issue")
                .category(SupportTicket.TicketCategory.PAYMENT)
                .priority(SupportTicket.TicketPriority.HIGH)
                .status(SupportTicket.TicketStatus.RESPONDED)
                .description("Need refund")
                .build();

        when(ticketRepository.findWithFilters(
                SupportTicket.TicketStatus.RESPONDED,
                SupportTicket.TicketCategory.PAYMENT,
                SupportTicket.TicketPriority.HIGH,
                PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(ticket), PageRequest.of(0, 10), 1));

        TicketResponse response = service.getAllTickets("responded", "payment", "high", PageRequest.of(0, 10))
                .getContent()
                .get(0);

        assertEquals(ticket.getTicketCode(), response.getTicketCode());
        assertEquals("RESPONDED", response.getStatus());
    }

    @Test
    @DisplayName("addUserResponse should append content and move RESPONDED tickets to IN_PROGRESS")
    void addUserResponse_ShouldAppendContentAndMoveToInProgress() {
        SupportTicket ticket = SupportTicket.builder()
                .id(7L)
                .ticketCode("TK-TEST123")
                .email("user@skillverse.vn")
                .subject("Need help")
                .description("Initial description")
                .status(SupportTicket.TicketStatus.RESPONDED)
                .category(SupportTicket.TicketCategory.GENERAL)
                .build();
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        TicketResponse response = service.addUserResponse(ticket.getId(), "Additional details");

        assertEquals("IN_PROGRESS", response.getStatus());
        assertTrue(response.getDescription().contains("Additional details"));
    }

    @Test
    @DisplayName("deleteTicket should reject active tickets")
    void deleteTicket_ShouldRejectActiveTickets() {
        SupportTicket ticket = SupportTicket.builder()
                .id(9L)
                .status(SupportTicket.TicketStatus.IN_PROGRESS)
                .build();
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        assertThrows(IllegalStateException.class, () -> service.deleteTicket(ticket.getId()));
        verify(ticketMessageRepository, never()).deleteByTicketId(ticket.getId());
    }

    @Test
    @DisplayName("deleteTicket should cascade child message cleanup for closed tickets")
    void deleteTicket_ShouldCascadeChildMessageCleanup() {
        SupportTicket ticket = SupportTicket.builder()
                .id(10L)
                .status(SupportTicket.TicketStatus.CLOSED)
                .build();
        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));

        service.deleteTicket(ticket.getId());

        verify(ticketMessageRepository).deleteByTicketId(ticket.getId());
        verify(ticketRepository).delete(ticket);
    }

    @Test
    @DisplayName("updateTicket should assign admin and set resolvedAt for completed tickets")
    void updateTicket_ShouldAssignAdminAndSetResolvedAt() {
        SupportTicket ticket = SupportTicket.builder()
                .id(12L)
                .ticketCode("TK-UPD1234")
                .status(SupportTicket.TicketStatus.PENDING)
                .priority(SupportTicket.TicketPriority.MEDIUM)
                .category(SupportTicket.TicketCategory.ACCOUNT)
                .email("user@skillverse.vn")
                .subject("Reset")
                .description("Help")
                .build();
        User admin = User.builder().id(3L).email("admin@skillverse.vn").build();
        UpdateTicketRequest request = UpdateTicketRequest.builder()
                .status("completed")
                .priority("high")
                .assignedToId(admin.getId())
                .adminResponse("Resolved")
                .build();

        when(ticketRepository.findById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        TicketResponse response = service.updateTicket(ticket.getId(), request);

        assertEquals("COMPLETED", response.getStatus());
        assertEquals("HIGH", response.getPriority());
        assertEquals("Resolved", response.getAdminResponse());
        assertTrue(ticket.getResolvedAt() != null);
    }
}
