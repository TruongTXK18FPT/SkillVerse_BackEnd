package com.exe.skillverse_backend.support_service.controller;

import com.exe.skillverse_backend.support_service.dto.CreateTicketRequest;
import com.exe.skillverse_backend.support_service.dto.TicketResponse;
import com.exe.skillverse_backend.support_service.dto.TicketStatsResponse;
import com.exe.skillverse_backend.support_service.dto.UpdateTicketRequest;
import com.exe.skillverse_backend.support_service.service.ISupportTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for Support Ticket operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/support")
@RequiredArgsConstructor
@Tag(name = "Support Tickets", description = "APIs for managing support tickets")
public class SupportTicketController {

    private final ISupportTicketService ticketService;

    // ==================== Public Endpoints ====================

    /**
     * Create a new support ticket (public - no auth required)
     */
    @PostMapping("/tickets")
    @Operation(summary = "Create ticket", 
            description = "Create a new support ticket. No authentication required.")
    public ResponseEntity<TicketResponse> createTicket(
            @Valid @RequestBody CreateTicketRequest request) {
        log.info("Creating support ticket for: {}", request.getEmail());
        TicketResponse response = ticketService.createTicket(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get ticket by code (public - for tracking)
     */
    @GetMapping("/tickets/code/{ticketCode}")
    @Operation(summary = "Get ticket by code", 
            description = "Get ticket details by ticket code for tracking")
    public ResponseEntity<TicketResponse> getTicketByCode(
            @PathVariable String ticketCode) {
        TicketResponse response = ticketService.getTicketByCode(ticketCode);
        return ResponseEntity.ok(response);
    }

    /**
     * Get tickets by email (for non-logged in users to track their tickets)
     */
    @GetMapping("/tickets/email/{email}")
    @Operation(summary = "Get tickets by email", 
            description = "Get all tickets submitted with a specific email")
    public ResponseEntity<List<TicketResponse>> getTicketsByEmail(
            @PathVariable String email) {
        List<TicketResponse> tickets = ticketService.getTicketsByEmail(email);
        return ResponseEntity.ok(tickets);
    }

    // ==================== User Endpoints (Authenticated) ====================

    /**
     * Get tickets for logged-in user
     */
    @GetMapping("/tickets/my")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my tickets", 
            description = "Get all tickets for the authenticated user")
    public ResponseEntity<List<TicketResponse>> getMyTickets(
            @RequestParam Long userId) {
        List<TicketResponse> tickets = ticketService.getTicketsByUserId(userId);
        return ResponseEntity.ok(tickets);
    }

    /**
     * Add response to own ticket (user follow-up)
     */
    @PostMapping("/tickets/{id}/respond")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add response", 
            description = "Add a follow-up response to an existing ticket")
    public ResponseEntity<TicketResponse> addResponse(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String response = body.get("response");
        TicketResponse ticket = ticketService.addUserResponse(id, response);
        return ResponseEntity.ok(ticket);
    }

    /**
     * Close ticket (user only)
     */
    @PostMapping("/tickets/{id}/close")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Close ticket", 
            description = "Close a ticket. Only the ticket owner can close it.")
    public ResponseEntity<TicketResponse> closeTicket(@PathVariable Long id) {
        log.info("User closing ticket with ID: {}", id);
        try {
            TicketResponse ticket = ticketService.updateTicket(id, 
                    UpdateTicketRequest.builder().status("CLOSED").build());
            log.info("Successfully closed ticket: {}", ticket.getTicketCode());
            return ResponseEntity.ok(ticket);
        } catch (Exception e) {
            log.error("Error closing ticket {}: {}", id, e.getMessage(), e);
            throw e;
        }
    }

    // ==================== Admin Endpoints ====================

    /**
     * Get all tickets with filters (Admin)
     */
    @GetMapping("/admin/tickets")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get all tickets (Admin)", 
            description = "Get all tickets with optional filters")
    public ResponseEntity<Page<TicketResponse>> getAllTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String priority,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<TicketResponse> tickets = ticketService.getAllTickets(
                status, category, priority, pageable);
        return ResponseEntity.ok(tickets);
    }

    /**
     * Get ticket by ID (Admin)
     */
    @GetMapping("/admin/tickets/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get ticket by ID (Admin)", 
            description = "Get detailed ticket information")
    public ResponseEntity<TicketResponse> getTicketById(@PathVariable Long id) {
        TicketResponse ticket = ticketService.getTicketById(id);
        return ResponseEntity.ok(ticket);
    }

    /**
     * Update ticket (Admin)
     */
    @PutMapping("/admin/tickets/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update ticket (Admin)", 
            description = "Update ticket status, priority, response, or assignment")
    public ResponseEntity<TicketResponse> updateTicket(
            @PathVariable Long id,
            @RequestBody UpdateTicketRequest request) {
        TicketResponse ticket = ticketService.updateTicket(id, request);
        return ResponseEntity.ok(ticket);
    }

    /**
     * Get tickets assigned to admin
     */
    @GetMapping("/admin/tickets/assigned/{adminId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get assigned tickets (Admin)", 
            description = "Get tickets assigned to a specific admin")
    public ResponseEntity<Page<TicketResponse>> getAssignedTickets(
            @PathVariable Long adminId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<TicketResponse> tickets = ticketService.getTicketsAssignedToAdmin(
                adminId, pageable);
        return ResponseEntity.ok(tickets);
    }

    /**
     * Get ticket statistics (Admin dashboard)
     */
    @GetMapping("/admin/tickets/stats")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get ticket statistics", 
            description = "Get ticket statistics for admin dashboard")
    public ResponseEntity<TicketStatsResponse> getTicketStats() {
        TicketStatsResponse stats = ticketService.getTicketStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Delete ticket (Admin)
     */
    @DeleteMapping("/admin/tickets/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete ticket (Admin)", 
            description = "Delete a ticket permanently")
    public ResponseEntity<Void> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.noContent().build();
    }
}
