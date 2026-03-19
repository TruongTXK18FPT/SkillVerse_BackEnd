package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.ChatMessageResponse;
import com.exe.skillverse_backend.ai_service.dto.ChatSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.request.ChatRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ChatResponse;
import com.exe.skillverse_backend.auth_service.entity.User;
import java.util.List;

public interface AiChatbotService {

    ChatResponse chat(ChatRequest request, User user);

    List<ChatMessageResponse> getConversationHistory(Long sessionId, Long userId);

    List<ChatSessionSummary> getUserSessions(Long userId);

    void deleteSession(Long sessionId, Long userId);

    ChatSessionSummary renameSession(Long sessionId, Long userId, String newTitle);

    Long getTotalSessionCount();

    Long getTotalMessageCount();
}
