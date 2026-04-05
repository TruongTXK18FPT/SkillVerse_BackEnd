package com.exe.skillverse_backend.notification_service.controller;

import com.exe.skillverse_backend.notification_service.dto.FcmTokenRequest;
import com.exe.skillverse_backend.notification_service.dto.FcmTokenResponse;
import com.exe.skillverse_backend.notification_service.dto.NotificationResponse;
import com.exe.skillverse_backend.notification_service.service.FcmService;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final FcmService fcmService;

    @GetMapping
    public ResponseEntity<Page<NotificationResponse>> getUserNotifications(
            Authentication auth,
            @RequestParam(required = false) Boolean isRead,
            @PageableDefault(size = 10) Pageable pageable) {
        Long userId = Long.parseLong(auth.getName());
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, isRead, pageable));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }

    @GetMapping("/total-count")
    public ResponseEntity<Long> getTotalCount(Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        return ResponseEntity.ok(notificationService.getTotalCount(userId));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }

    // ─── FCM Device Token Management ────────────────────────────────────────

    @PostMapping("/device-token")
    public ResponseEntity<FcmTokenResponse> registerDeviceToken(
            Authentication auth,
            @Valid @RequestBody FcmTokenRequest request) {
        Long userId = Long.parseLong(auth.getName());
        return ResponseEntity.ok(fcmService.registerToken(userId, request));
    }

    @DeleteMapping("/device-token")
    public ResponseEntity<FcmTokenResponse> unregisterDeviceToken(
            Authentication auth,
            @RequestParam String deviceToken) {
        Long userId = Long.parseLong(auth.getName());
        // Verify ownership: only the user who registered this token can unregister it
        List<String> activeTokens = fcmService.getActiveTokens(userId);
        if (!activeTokens.contains(deviceToken)) {
            // Token not found for this user - try to unregister anyway (may be inactive)
            fcmService.unregisterToken(deviceToken);
        } else {
            fcmService.unregisterToken(deviceToken);
        }
        return ResponseEntity.ok(FcmTokenResponse.builder()
                .message("Token unregistered successfully")
                .build());
    }

    @DeleteMapping("/device-tokens/all")
    public ResponseEntity<Void> unregisterAllDeviceTokens(Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        fcmService.unregisterAllTokens(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/device-tokens/status")
    public ResponseEntity<Object> getDeviceTokenStatus(Authentication auth) {
        Long userId = Long.parseLong(auth.getName());
        return ResponseEntity.ok(java.util.Map.of(
                "firebaseEnabled", fcmService.isFirebaseEnabled(),
                "activeTokens", fcmService.getActiveTokens(userId).size()
        ));
    }
}
