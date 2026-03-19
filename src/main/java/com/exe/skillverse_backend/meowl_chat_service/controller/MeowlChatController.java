package com.exe.skillverse_backend.meowl_chat_service.controller;

import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatRequest;
import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatResponse;
import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlOnboardingContextResponse;
import com.exe.skillverse_backend.meowl_chat_service.service.MeowlChatService;
import com.exe.skillverse_backend.meowl_chat_service.service.MeowlReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * REST Controller for Meowl Chat Service
 * Provides endpoints for chat, reminders, and notifications
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/meowl")
@RequiredArgsConstructor
@Tag(name = "Meowl Chat", description = "Cute AI assistant for learning support")
public class MeowlChatController {

    private final MeowlChatService meowlChatService;
    private final MeowlReminderService reminderService;

    /**
     * Send a message to Meowl and get a cute response
     */
    @PostMapping("/chat")
    @Operation(summary = "Chat with Meowl", description = "Send a message and get a cute, helpful response from Meowl")
    public ResponseEntity<MeowlChatResponse> chat(@RequestBody MeowlChatRequest request, Authentication authentication) {
        log.info("Received chat request from user: {}", request.getUserId());

        try {
            Long authUserId = extractAuthenticatedUserId(authentication);
            if (authUserId != null) {
                if (request.getUserId() == null) {
                    request.setUserId(authUserId);
                } else if (!authUserId.equals(request.getUserId())) {
                    return ResponseEntity.status(403).body(MeowlChatResponse.builder()
                            .message("Access denied: user mismatch.")
                            .success(false)
                            .mood("apologetic")
                            .build());
                }
            } else if (request.getUserId() != null) {
                // Guest mode must not carry authenticated userId.
                request.setUserId(null);
            }

            MeowlChatResponse response = meowlChatService.chat(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error processing chat request: ", e);

            String language = request.getLanguage() != null ? request.getLanguage() : "en";
            String errorMessage = language.equals("vi")
                    ? "Meo ơi! 🐱 Có lỗi xảy ra. Thử lại sau nhé! ✨"
                    : "Meow! 🐱 Something went wrong. Please try again! ✨";

            MeowlChatResponse errorResponse = MeowlChatResponse.builder()
                    .message(errorMessage)
                    .success(false)
                    .mood("apologetic")
                    .build();

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Get chat history for a user
     */
    @GetMapping("/history/{userId}")
    @Operation(summary = "Get chat history", description = "Get persistent chat history for a user")
    public ResponseEntity<List<MeowlChatRequest.ChatMessage>> getChatHistory(
            @PathVariable Long userId,
            Authentication authentication) {
        if (!canAccessUser(authentication, userId)) {
            return ResponseEntity.status(403).build();
        }
        log.info("Getting chat history for user: {}", userId);
        try {
            List<MeowlChatRequest.ChatMessage> history = meowlChatService.getChatHistory(userId);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            log.error("Error getting chat history: ", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Clear chat history for a user
     */
    @DeleteMapping("/history/{userId}")
    @Operation(summary = "Clear chat history", description = "Clear chat history for a user (e.g. on logout)")
    public ResponseEntity<Void> clearChatHistory(@PathVariable Long userId, Authentication authentication) {
        if (!canAccessUser(authentication, userId)) {
            return ResponseEntity.status(403).build();
        }
        log.info("Clearing chat history for user: {}", userId);
        try {
            meowlChatService.clearChatHistory(userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error clearing chat history: ", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Get learning reminders for a user
     */
    @GetMapping("/reminders/{userId}")
    @Operation(summary = "Get learning reminders", description = "Get personalized learning reminders for a user")
    public ResponseEntity<List<MeowlChatResponse.MeowlReminder>> getReminders(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "en") String language,
            Authentication authentication) {
        if (!canAccessUser(authentication, userId)) {
            return ResponseEntity.status(403).body(List.of());
        }

        log.info("Getting reminders for user: {}", userId);

        try {
            List<MeowlChatResponse.MeowlReminder> reminders = reminderService.getRemindersForUser(userId, language);
            return ResponseEntity.ok(reminders);
        } catch (Exception e) {
            log.error("Error getting reminders: ", e);
            return ResponseEntity.status(500).body(List.of());
        }
    }

    /**
     * Get notifications for a user
     */
    @GetMapping("/notifications/{userId}")
    @Operation(summary = "Get notifications", description = "Get learning tips and motivational messages")
    public ResponseEntity<List<MeowlChatResponse.MeowlNotification>> getNotifications(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "en") String language,
            Authentication authentication) {
        if (!canAccessUser(authentication, userId)) {
            return ResponseEntity.status(403).body(List.of());
        }

        log.info("Getting notifications for user: {}", userId);

        try {
            List<MeowlChatResponse.MeowlNotification> notifications = reminderService.getNotifications(userId,
                    language);
            return ResponseEntity.ok(notifications);
        } catch (Exception e) {
            log.error("Error getting notifications: ", e);
            return ResponseEntity.status(500).body(List.of());
        }
    }

    /**
     * Get role-aware onboarding context for Meowl chat UI.
     */
    @GetMapping("/onboarding/{userId}")
    @Operation(summary = "Get Meowl onboarding context", description = "Returns role-aware welcome, quick actions, and suggested prompts")
    public ResponseEntity<MeowlOnboardingContextResponse> getOnboardingContext(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "en") String language,
            @RequestParam(required = false) String activeRole,
            Authentication authentication) {
        if (!canAccessUser(authentication, userId)) {
            return ResponseEntity.status(403).build();
        }

        try {
            MeowlOnboardingContextResponse response = meowlChatService.getOnboardingContext(userId, language, activeRole);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting onboarding context for user {}", userId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Mark onboarding guidance as seen for a user.
     */
    @PostMapping("/onboarding/{userId}/seen")
    @Operation(summary = "Mark onboarding seen", description = "Stores onboarding viewed state and current preferred role mode")
    public ResponseEntity<Void> markOnboardingSeen(
            @PathVariable Long userId,
            @RequestParam(required = false) String activeRole,
            Authentication authentication) {
        if (!canAccessUser(authentication, userId)) {
            return ResponseEntity.status(403).build();
        }

        try {
            meowlChatService.markOnboardingSeen(userId, activeRole);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error marking onboarding as seen for user {}", userId, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if Meowl service is running")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Meowl is awake and ready to help! đŸ±âœ¨");
    }

    private boolean canAccessUser(Authentication authentication, Long targetUserId) {
        Long authUserId = extractAuthenticatedUserId(authentication);
        if (authUserId == null) {
            return false;
        }
        if (authUserId.equals(targetUserId)) {
            return true;
        }

        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().toUpperCase(Locale.ROOT))
                .anyMatch(authority -> authority.contains("ADMIN"));
    }

    private Long extractAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            return Long.parseLong(authentication.getName());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
