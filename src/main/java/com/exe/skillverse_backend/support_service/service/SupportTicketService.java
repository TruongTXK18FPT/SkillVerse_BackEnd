package com.exe.skillverse_backend.support_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.support_service.dto.CreateTicketRequest;
import com.exe.skillverse_backend.support_service.dto.TicketResponse;
import com.exe.skillverse_backend.support_service.dto.TicketStatsResponse;
import com.exe.skillverse_backend.support_service.dto.UpdateTicketRequest;
import com.exe.skillverse_backend.support_service.entity.SupportTicket;
import com.exe.skillverse_backend.support_service.entity.SupportTicket.TicketCategory;
import com.exe.skillverse_backend.support_service.entity.SupportTicket.TicketPriority;
import com.exe.skillverse_backend.support_service.entity.SupportTicket.TicketStatus;
import com.exe.skillverse_backend.support_service.repository.SupportTicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of support ticket service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportTicketService implements ISupportTicketService {

    private final SupportTicketRepository ticketRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        log.info("Creating support ticket for email: {}", request.getEmail());

        User user = null;
        if (request.getUserId() != null) {
            user = userRepository.findById(request.getUserId()).orElse(null);
        }

        String ticketCode = generateTicketCode();

        SupportTicket ticket = SupportTicket.builder()
                .ticketCode(ticketCode)
                .email(request.getEmail())
                .subject(request.getSubject())
                .category(TicketCategory.valueOf(request.getCategory().toUpperCase()))
                .priority(TicketPriority.valueOf(
                        request.getPriority() != null 
                        ? request.getPriority().toUpperCase() 
                        : "MEDIUM"))
                .description(request.getDescription())
                .user(user)
                .status(TicketStatus.PENDING)
                .build();

        ticket = ticketRepository.save(ticket);
        log.info("Created ticket with code: {}", ticketCode);

        return TicketResponse.fromEntity(ticket);
    }

    @Override
    public TicketResponse getTicketById(Long id) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + id));
        return TicketResponse.fromEntity(ticket);
    }

    @Override
    public TicketResponse getTicketByCode(String ticketCode) {
        SupportTicket ticket = ticketRepository.findByTicketCode(ticketCode)
                .orElseThrow(() -> new NotFoundException(
                        "Ticket not found with code: " + ticketCode));
        return TicketResponse.fromEntity(ticket);
    }

    @Override
    public List<TicketResponse> getTicketsByUserId(Long userId) {
        return ticketRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(TicketResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<TicketResponse> getTicketsByEmail(String email) {
        return ticketRepository.findByEmailOrderByCreatedAtDesc(email)
                .stream()
                .map(TicketResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Page<TicketResponse> getAllTickets(
            String status,
            String category,
            String priority,
            Pageable pageable) {

        TicketStatus ticketStatus = status != null 
                ? TicketStatus.valueOf(status.toUpperCase()) : null;
        TicketCategory ticketCategory = category != null 
                ? TicketCategory.valueOf(category.toUpperCase()) : null;
        TicketPriority ticketPriority = priority != null 
                ? TicketPriority.valueOf(priority.toUpperCase()) : null;

        return ticketRepository.findWithFilters(
                ticketStatus, ticketCategory, ticketPriority, pageable)
                .map(TicketResponse::fromEntity);
    }

    @Override
    public Page<TicketResponse> getTicketsAssignedToAdmin(Long adminId, Pageable pageable) {
        return ticketRepository.findByAssignedToId(adminId, pageable)
                .map(TicketResponse::fromEntity);
    }

    @Override
    @Transactional
    public TicketResponse updateTicket(Long id, UpdateTicketRequest request) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + id));

        if (request.getStatus() != null) {
            TicketStatus newStatus = TicketStatus.valueOf(request.getStatus().toUpperCase());
            ticket.setStatus(newStatus);

            if (newStatus == TicketStatus.COMPLETED || newStatus == TicketStatus.CLOSED) {
                ticket.setResolvedAt(LocalDateTime.now());
            }
        }

        if (request.getPriority() != null) {
            ticket.setPriority(TicketPriority.valueOf(request.getPriority().toUpperCase()));
        }

        if (request.getAdminResponse() != null) {
            ticket.setAdminResponse(request.getAdminResponse());
        }

        if (request.getAssignedToId() != null) {
            User admin = userRepository.findById(request.getAssignedToId())
                    .orElseThrow(() -> new NotFoundException(
                            "Admin not found with id: " + request.getAssignedToId()));
            ticket.setAssignedTo(admin);
        }

        ticket = ticketRepository.save(ticket);
        log.info("Updated ticket: {}", ticket.getTicketCode());

        return TicketResponse.fromEntity(ticket);
    }

    @Override
    @Transactional
    public TicketResponse addUserResponse(Long id, String response) {
        SupportTicket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found with id: " + id));

        // Append user response to description
        String updatedDescription = ticket.getDescription() 
                + "\n\n--- User Update (" + LocalDateTime.now() + ") ---\n" 
                + response;
        ticket.setDescription(updatedDescription);

        // If ticket was responded, change to in_progress on user reply
        if (ticket.getStatus() == TicketStatus.RESPONDED) {
            ticket.setStatus(TicketStatus.IN_PROGRESS);
        }

        ticket = ticketRepository.save(ticket);
        return TicketResponse.fromEntity(ticket);
    }

    @Override
    public TicketStatsResponse getTicketStats() {
        long total = ticketRepository.count();
        long open = ticketRepository.countByStatus(TicketStatus.PENDING);
        long responded = ticketRepository.countByStatus(TicketStatus.RESPONDED);
        long inProgress = ticketRepository.countByStatus(TicketStatus.IN_PROGRESS);
        long resolved = ticketRepository.countByStatus(TicketStatus.COMPLETED);
        long closed = ticketRepository.countByStatus(TicketStatus.CLOSED);

        Map<String, Long> byCategory = new HashMap<>();
        for (Object[] row : ticketRepository.countByCategory()) {
            byCategory.put(((TicketCategory) row[0]).name(), (Long) row[1]);
        }

        return TicketStatsResponse.builder()
                .totalTickets(total)
                .openTickets(open)
                .respondedTickets(responded)
                .inProgressTickets(inProgress)
                .resolvedTickets(resolved)
                .closedTickets(closed)
                .ticketsByCategory(byCategory)
                .build();
    }

    @Override
    @Transactional
    public void deleteTicket(Long id) {
        if (!ticketRepository.existsById(id)) {
            throw new NotFoundException("Ticket not found with id: " + id);
        }
        ticketRepository.deleteById(id);
        log.info("Deleted ticket with id: {}", id);
    }

    private String generateTicketCode() {
        String uuid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "TK-" + uuid;
    }
}
