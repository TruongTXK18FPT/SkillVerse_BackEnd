package com.exe.skillverse_backend.support_service.dto;

import com.exe.skillverse_backend.support_service.entity.SupportTicket;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for support ticket
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

    private Long id;
    private String ticketCode;
    private String email;
    private String subject;
    private String category;
    private String priority;
    private String description;
    private String status;
    private String adminResponse;
    private String assignedToName;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // User info
    private Long userId;
    private String userName;

    public static TicketResponse fromEntity(SupportTicket ticket) {
        return TicketResponse.builder()
                .id(ticket.getId())
                .ticketCode(ticket.getTicketCode())
                .email(ticket.getEmail())
                .subject(ticket.getSubject())
                .category(ticket.getCategory().name())
                .priority(ticket.getPriority().name())
                .description(ticket.getDescription())
                .status(ticket.getStatus().name())
                .adminResponse(ticket.getAdminResponse())
                .assignedToName(ticket.getAssignedTo() != null 
                        ? ticket.getAssignedTo().getFirstName() + " " 
                        + ticket.getAssignedTo().getLastName() : null)
                .resolvedAt(ticket.getResolvedAt())
                .createdAt(ticket.getCreatedAt())
                .updatedAt(ticket.getUpdatedAt())
                .userId(ticket.getUser() != null ? ticket.getUser().getId() : null)
                .userName(ticket.getUser() != null 
                        ? ticket.getUser().getFirstName() + " " 
                        + ticket.getUser().getLastName() : null)
                .build();
    }
}
