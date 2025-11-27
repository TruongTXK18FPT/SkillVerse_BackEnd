package com.exe.skillverse_backend.meowl_chat_service.controller;

import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatRequest;
import com.exe.skillverse_backend.meowl_chat_service.dto.MeowlChatResponse;
import com.exe.skillverse_backend.meowl_chat_service.service.IMeowlChatService;
import com.exe.skillverse_backend.meowl_chat_service.service.MeowlReminderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    private final IMeowlChatService meowlChatService;
    private final MeowlReminderService reminderService;

    /**
     * Send a message to Meowl and get a cute response
     */
    @PostMapping("/chat")
    @Operation(summary = "Chat with Meowl", description = "Send a message and get a cute, helpful response from Meowl")
    public ResponseEntity<MeowlChatResponse> chat(@RequestBody MeowlChatRequest request) {
        log.info("Received chat request from user: {}", request.getUserId());
        
        try {
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
     * Get learning reminders for a user
     */
    @GetMapping("/reminders/{userId}")
    @Operation(summary = "Get learning reminders", description = "Get personalized learning reminders for a user")
    public ResponseEntity<List<MeowlChatResponse.MeowlReminder>> getReminders(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "en") String language) {
        
        log.info("Getting reminders for user: {}", userId);
        
        try {
            List<MeowlChatResponse.MeowlReminder> reminders =
                    reminderService.getRemindersForUser(userId, language);
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
            @RequestParam(defaultValue = "en") String language) {
        
        log.info("Getting notifications for user: {}", userId);
        
        try {
            List<MeowlChatResponse.MeowlNotification> notifications =
                    reminderService.getNotifications(userId, language);
            return ResponseEntity.ok(notifications);
        } catch (Exception e) {
            log.error("Error getting notifications: ", e);
            return ResponseEntity.status(500).body(List.of());
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check if Meowl service is running")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Meowl is awake and ready to help! 🐱✨");
    }
}
