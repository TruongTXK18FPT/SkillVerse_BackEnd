package com.exe.skillverse_backend.support_service.dto;

import com.exe.skillverse_backend.support_service.entity.TicketMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessageResponse {
    private Long id;
    private String ticketCode;
    private Long senderId;
    private String senderEmail;
    private String senderName;
    private String senderType;
    private String content;
    private Boolean isRead;
    private LocalDateTime createdAt;

    public static TicketMessageResponse fromEntity(TicketMessage message) {
        return TicketMessageResponse.builder()
                .id(message.getId())
                .ticketCode(message.getTicket().getTicketCode())
                .senderId(message.getSender() != null ? message.getSender().getId() : null)
                .senderEmail(message.getSenderEmail())
                .senderName(message.getSenderName())
                .senderType(message.getSenderType().name())
                .content(message.getContent())
                .isRead(message.getIsRead())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
