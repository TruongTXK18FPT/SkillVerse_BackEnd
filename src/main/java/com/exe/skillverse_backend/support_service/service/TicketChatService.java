package com.exe.skillverse_backend.support_service.service;

import com.exe.skillverse_backend.support_service.dto.TicketMessageRequest;
import com.exe.skillverse_backend.support_service.dto.TicketMessageResponse;

import java.util.List;

public interface TicketChatService {
    TicketMessageResponse sendMessage(TicketMessageRequest request);
    List<TicketMessageResponse> getMessages(String ticketCode);
    void markAsRead(String ticketCode, String senderType);
}