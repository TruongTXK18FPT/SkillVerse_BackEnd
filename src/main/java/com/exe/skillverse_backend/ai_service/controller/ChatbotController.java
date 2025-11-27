package com.exe.skillverse_backend.ai_service.controller;

import com.exe.skillverse_backend.ai_service.dto.ChatMessageResponse;
import com.exe.skillverse_backend.ai_service.dto.ChatSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.request.ChatRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ChatResponse;
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
     * Utility method to validate authentication and extract user ID
     * Prevents code duplication across all endpoints
     */
    private Long validateAuthenticationAndGetUserId(Authentication authentication) {
        // STRICT AUTH VALIDATION
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Authentication required");
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();
        if (jwt == null) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid authentication token");
        }

        String userIdStr = jwt.getClaimAsString("userId");
        if (userIdStr == null || userIdStr.trim().isEmpty()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "User ID not found in token");
        }

        try {
            return Long.valueOf(userIdStr);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "Invalid user ID format");
        }
    }

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

        Long userId = validateAuthenticationAndGetUserId(authentication);

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
     * @return List of messages in chronological order (as DTOs)
     */
    @GetMapping("/history/{sessionId}")
    @Operation(summary = "Get Chat History", description = "Retrieve conversation history for a specific session")
    public ResponseEntity<List<ChatMessageResponse>> getHistory(
            @PathVariable Long sessionId,
            Authentication authentication) {

        Long userId = validateAuthenticationAndGetUserId(authentication);

        log.info("User {} fetching chat history for session {}", userId, sessionId);

        List<ChatMessageResponse> history = aiChatbotService.getConversationHistory(sessionId, userId);

        return ResponseEntity.ok(history);
    }

    /**
     * Get all chat sessions for current user with titles
     * 
     * @param authentication Current authenticated user
     * @return List of session summaries with titles
     */
    @GetMapping("/sessions")
    @Operation(summary = "Get User Sessions", description = "Get all chat sessions with title previews for the current user")
    public ResponseEntity<List<ChatSessionSummary>> getSessions(Authentication authentication) {
        Long userId = validateAuthenticationAndGetUserId(authentication);

        log.info("User {} fetching all chat sessions", userId);

        List<ChatSessionSummary> sessions = aiChatbotService.getUserSessions(userId);

        return ResponseEntity.ok(sessions);
    }

    /**
     * Delete a chat session
     * 
     * @param sessionId      Session ID to delete
     * @param authentication Current authenticated user
     * @return Success message
     */
    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Delete Chat Session", description = "Delete a chat session and all its messages")
    public ResponseEntity<Void> deleteSession(
            @PathVariable Long sessionId,
            Authentication authentication) {

        Long userId = validateAuthenticationAndGetUserId(authentication);

        log.info("User {} deleting chat session {}", userId, sessionId);

        aiChatbotService.deleteSession(sessionId, userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Rename a chat session
     * 
     * @param sessionId      Session ID to rename
     * @param newTitle       New title for the session
     * @param authentication Current authenticated user
     * @return Updated session summary
     */
    @PatchMapping("/sessions/{sessionId}")
    @Operation(summary = "Rename Chat Session", description = "Update the title of a chat session")
    public ResponseEntity<ChatSessionSummary> renameSession(
            @PathVariable Long sessionId,
            @RequestParam String newTitle,
            Authentication authentication) {

        Long userId = validateAuthenticationAndGetUserId(authentication);

        log.info("User {} renaming chat session {} to '{}'", userId, sessionId, newTitle);

        ChatSessionSummary updated = aiChatbotService.renameSession(sessionId, userId, newTitle);

        return ResponseEntity.ok(updated);
    }

    // ==================== ADMIN ENDPOINTS ====================

    /**
     * Get total chat statistics for admin dashboard
     * @return Map with totalSessions and totalMessages
     */
    @GetMapping("/admin/stats")
    @Operation(summary = "Get Chat Statistics (Admin)", 
               description = "Get total chat sessions and messages count for admin dashboard")
    public ResponseEntity<java.util.Map<String, Long>> getChatStats() {
        log.info("Admin fetching chat statistics");
        
        Long totalSessions = aiChatbotService.getTotalSessionCount();
        Long totalMessages = aiChatbotService.getTotalMessageCount();
        
        java.util.Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("totalSessions", totalSessions);
        stats.put("totalMessages", totalMessages);
        
        return ResponseEntity.ok(stats);
    }
}
