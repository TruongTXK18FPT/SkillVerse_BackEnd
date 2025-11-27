package com.exe.skillverse_backend.meowl_chat_service.service;

import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatRequest;
import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatResponse;

/**
 * Interface for Meowl Chat Service
 * Defines the contract for chat operations
 */
public interface IMeowlChatService {
    
    /**
     * Send a message to Meowl and get a cute, helpful response
     * 
     * @param request The chat request containing user message and history
     * @return The response from Meowl
     */
    MeowlChatResponse chat(MeowlChatRequest request);
}
