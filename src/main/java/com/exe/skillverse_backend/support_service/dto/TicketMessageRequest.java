package com.exe.skillverse_backend.support_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessageRequest {
    private String ticketCode;
    private String content;
    private String senderEmail;
    private String senderName;
    private String senderType; // USER or ADMIN
    private Long senderId;
}
