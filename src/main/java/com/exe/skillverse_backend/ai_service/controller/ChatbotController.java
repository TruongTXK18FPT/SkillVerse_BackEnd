package com.exe.skillverse_backend.ai_service.controller;

import com.exe.skillverse_backend.ai_service.dto.request.ChatRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ChatResponse;
import com.exe.skillverse_backend.ai_service.entity.ChatMessage;
import com.exe.skillverse_backend.ai_service.service.AiChatbotService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for AI-powered career counseling chatbot
 */
@RestController
@RequestMapping("/api/v1/ai/chat")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Chatbot", description = "AI-powered career counseling and guidance chatbot")
@SecurityRequirement(name = "bearerAuth")
public class ChatbotController {

    private final AiChatbotService aiChatbotService;
    private final UserRepository userRepository;

    /**
     * Send a message to the AI career counselor
     * 
     * @param request        Chat message request
     * @param authentication Current authenticated user
     * @return AI response with session ID
     */
    @PostMapping
    @Operation(summary = "Chat with AI Career Counselor", description = "Send a message to Meowl, the AI career counselor. Get advice on majors, careers, skills, and trends.")
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found"));

        log.info("User {} chatting - session: {}, message length: {}",
                userId, request.getSessionId(), request.getMessage().length());

        ChatResponse response = aiChatbotService.chat(request, user);

        return ResponseEntity.ok(response);
    }

    /**
     * Get conversation history for a session
     * 
     * @param sessionId      Chat session ID
     * @param authentication Current authenticated user
     * @return List of messages in chronological order
     */
    @GetMapping("/history/{sessionId}")
    @Operation(summary = "Get Chat History", description = "Retrieve conversation history for a specific session")
    public ResponseEntity<List<ChatMessage>> getHistory(
            @PathVariable Long sessionId,
            Authentication authentication) {

        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} fetching chat history for session {}", userId, sessionId);

        List<ChatMessage> history = aiChatbotService.getConversationHistory(sessionId, userId);

        return ResponseEntity.ok(history);
    }

    /**
     * Get all chat sessions for current user
     * 
     * @param authentication Current authenticated user
     * @return List of session IDs
     */
    @GetMapping("/sessions")
    @Operation(summary = "Get User Sessions", description = "Get all chat session IDs for the current user")
    public ResponseEntity<List<Long>> getSessions(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        log.info("User {} fetching all chat sessions", userId);

        List<Long> sessions = aiChatbotService.getUserSessions(userId);

        return ResponseEntity.ok(sessions);
    }
}
