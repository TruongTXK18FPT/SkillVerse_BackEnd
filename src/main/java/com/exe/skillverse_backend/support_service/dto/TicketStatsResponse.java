package com.exe.skillverse_backend.support_service.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for ticket statistics (Admin dashboard)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatsResponse {

    private long totalTickets;
    private long openTickets;       // PENDING status
    private long respondedTickets;  // RESPONDED status
    private long inProgressTickets; // IN_PROGRESS status
    private long resolvedTickets;   // COMPLETED status
    private long closedTickets;     // CLOSED status
    private Map<String, Long> ticketsByCategory;
}
