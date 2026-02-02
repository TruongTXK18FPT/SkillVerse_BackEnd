package com.exe.skillverse_backend.meowl_chat_service.service;

import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatRequest;
import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatResponse;

import java.util.List;

/**
 * Interface for Meowl Chat Service
 * Defines the contract for chat operations
 */
public interface MeowlChatService {

    /**
     * Send a message to Meowl and get a cute, helpful response
     * 
     * @param request The chat request containing user message and history
     * @return The response from Meowl
     */
    MeowlChatResponse chat(MeowlChatRequest request);

    /**
     * Get chat history for a specific user
     * 
     * @param userId The ID of the user
     * @return List of chat messages
     */
    List<MeowlChatRequest.ChatMessage> getChatHistory(Long userId);

    /**
     * Clear chat history for a specific user (e.g. on logout)
     * 
     * @param userId The ID of the user
     */
    void clearChatHistory(Long userId);
}
