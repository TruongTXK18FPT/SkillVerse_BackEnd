package com.exe.skillverse_backend.support_service.service;

import com.exe.skillverse_backend.support_service.dto.CreateTicketRequest;
import com.exe.skillverse_backend.support_service.dto.TicketResponse;
import com.exe.skillverse_backend.support_service.dto.TicketStatsResponse;
import com.exe.skillverse_backend.support_service.dto.UpdateTicketRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for support ticket operations
 */
public interface ISupportTicketService {

    /**
     * Create a new support ticket
     */
    TicketResponse createTicket(CreateTicketRequest request);

    /**
     * Get ticket by ID
     */
    TicketResponse getTicketById(Long id);

    /**
     * Get ticket by ticket code
     */
    TicketResponse getTicketByCode(String ticketCode);

    /**
     * Get all tickets for a user
     */
    List<TicketResponse> getTicketsByUserId(Long userId);

    /**
     * Get all tickets for an email (for non-logged in users)
     */
    List<TicketResponse> getTicketsByEmail(String email);

    /**
     * Get all tickets with pagination and filters (Admin)
     */
    Page<TicketResponse> getAllTickets(
            String status,
            String category,
            String priority,
            Pageable pageable);

    /**
     * Get tickets assigned to an admin
     */
    Page<TicketResponse> getTicketsAssignedToAdmin(Long adminId, Pageable pageable);

    /**
     * Update ticket (Admin)
     */
    TicketResponse updateTicket(Long id, UpdateTicketRequest request);

    /**
     * Add response to ticket (User)
     */
    TicketResponse addUserResponse(Long id, String response);

    /**
     * Get ticket statistics (Admin dashboard)
     */
    TicketStatsResponse getTicketStats();

    /**
     * Delete ticket (Admin)
     */
    void deleteTicket(Long id);
}
