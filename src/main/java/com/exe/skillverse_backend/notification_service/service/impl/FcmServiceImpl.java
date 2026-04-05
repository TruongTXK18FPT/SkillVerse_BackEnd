package com.exe.skillverse_backend.notification_service.service.impl;

import com.exe.skillverse_backend.notification_service.config.FirebaseConfig;
import com.exe.skillverse_backend.notification_service.dto.FcmTokenRequest;
import com.exe.skillverse_backend.notification_service.dto.FcmTokenResponse;
import com.exe.skillverse_backend.notification_service.entity.UserFcmToken;
import com.exe.skillverse_backend.notification_service.repository.UserFcmTokenRepository;
import com.exe.skillverse_backend.notification_service.service.FcmService;
import com.google.firebase.messaging.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmServiceImpl implements FcmService {

    private final UserFcmTokenRepository fcmTokenRepository;
    private final FirebaseMessaging firebaseMessaging;
    private final FirebaseConfig firebaseConfig;

    @Override
    @Transactional
    public FcmTokenResponse registerToken(Long userId, FcmTokenRequest request) {
        if (!firebaseConfig.isFirebaseEnabled()) {
            return FcmTokenResponse.builder()
                    .message("Firebase is not configured. Push notifications disabled.")
                    .build();
        }

        Optional<UserFcmToken> existing = fcmTokenRepository.findByDeviceToken(request.getDeviceToken());

        UserFcmToken token;
        if (existing.isPresent()) {
            token = existing.get();
            token.setUserId(userId);
            token.setActive(true);
            token.setDeviceType(request.getDeviceType() != null ? request.getDeviceType() : "ANDROID");
            token.setDeviceName(request.getDeviceName());
        } else {
            token = UserFcmToken.builder()
                    .userId(userId)
                    .deviceToken(request.getDeviceToken())
                    .deviceType(request.getDeviceType() != null ? request.getDeviceType() : "ANDROID")
                    .deviceName(request.getDeviceName())
                    .active(true)
                    .build();
        }

        UserFcmToken saved = fcmTokenRepository.save(token);
        log.info("FCM token registered for user {}: {}", userId, maskToken(request.getDeviceToken()));

        return FcmTokenResponse.builder()
                .id(saved.getId())
                .deviceToken(maskToken(saved.getDeviceToken()))
                .deviceType(saved.getDeviceType())
                .deviceName(saved.getDeviceName())
                .active(saved.isActive())
                .message("Token registered successfully")
                .build();
    }

    @Override
    @Transactional
    public FcmTokenResponse unregisterToken(String deviceToken) {
        Optional<UserFcmToken> token = fcmTokenRepository.findByDeviceToken(deviceToken);
        if (token.isPresent()) {
            fcmTokenRepository.deleteByDeviceToken(deviceToken);
            log.info("FCM token unregistered: {}", maskToken(deviceToken));
            return FcmTokenResponse.builder()
                    .message("Token unregistered successfully")
                    .build();
        }
        return FcmTokenResponse.builder()
                .message("Token not found")
                .build();
    }

    @Override
    @Transactional
    public void unregisterAllTokens(Long userId) {
        List<UserFcmToken> tokens = fcmTokenRepository.findByUserId(userId);
        fcmTokenRepository.deleteAll(tokens);
        log.info("All FCM tokens unregistered for user {}", userId);
    }

    @Override
    public List<String> getActiveTokens(Long userId) {
        return fcmTokenRepository.findByUserIdAndActiveTrue(userId)
                .stream()
                .map(UserFcmToken::getDeviceToken)
                .collect(Collectors.toList());
    }

    @Override
    @Async("fcmTaskExecutor")
    public void sendPushNotification(Long userId, String title, String body, String dataPayload) {
        sendPushNotification(userId, title, body, dataPayload, null);
    }

    @Override
    @Async("fcmTaskExecutor")
    public void sendPushNotification(Long userId, String title, String body, String dataPayload, String clickAction) {
        if (!firebaseConfig.isFirebaseEnabled()) {
            log.debug("Firebase not enabled, skipping push notification for user {}", userId);
            return;
        }

        if (firebaseMessaging == null) {
            log.warn("FirebaseMessaging bean is null, skipping push notification for user {}", userId);
            return;
        }

        List<String> tokens = getActiveTokens(userId);
        if (tokens.isEmpty()) {
            log.debug("No active FCM tokens for user {}, skipping push notification", userId);
            return;
        }

        try {
            MulticastMessage message = buildMulticastMessage(tokens, title, body, dataPayload, clickAction);
            BatchResponse response = firebaseMessaging.sendEachForMulticast(message);

            log.info("FCM push sent to {} recipients, failures: {}",
                    response.getSuccessCount(), response.getFailureCount());

            handleFailureResponses(response, tokens);

        } catch (FirebaseMessagingException e) {
            log.error("Failed to send FCM push notification to user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    @Async("fcmTaskExecutor")
    public void sendMulticastPushNotification(List<Long> userIds, String title, String body, String dataPayload) {
        if (!firebaseConfig.isFirebaseEnabled()) {
            log.debug("Firebase not enabled, skipping multicast push notification");
            return;
        }

        if (firebaseMessaging == null) {
            log.warn("FirebaseMessaging bean is null, skipping multicast push notification");
            return;
        }

        List<String> allTokens = new ArrayList<>();
        for (Long userId : userIds) {
            allTokens.addAll(getActiveTokens(userId));
        }

        if (allTokens.isEmpty()) {
            log.debug("No active FCM tokens for multicast, skipping");
            return;
        }

        try {
            int batchSize = 500;
            for (int i = 0; i < allTokens.size(); i += batchSize) {
                List<String> batch = allTokens.subList(i, Math.min(i + batchSize, allTokens.size()));
                MulticastMessage message = buildMulticastMessage(batch, title, body, dataPayload, null);
                BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
                log.info("FCM multicast batch {}-{}: {} success, {} failures",
                        i, Math.min(i + batchSize, allTokens.size()),
                        response.getSuccessCount(), response.getFailureCount());
            }
        } catch (FirebaseMessagingException e) {
            log.error("Failed to send FCM multicast push: {}", e.getMessage());
        }
    }

    @Override
    public boolean isFirebaseEnabled() {
        return firebaseConfig.isFirebaseEnabled();
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    private MulticastMessage buildMulticastMessage(
            List<String> tokens,
            String title,
            String body,
            String dataPayload,
            String clickAction) {

        MulticastMessage.Builder builder = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .setAndroidConfig(AndroidConfig.builder()
                        .setNotification(AndroidNotification.builder()
                                .setChannelId("skillverse_notifications")
                                .setDefaultSound(true)
                                .setDefaultVibrateTimings(true)
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound("default")
                                .setBadge(1)
                                .build())
                        .build());

        if (dataPayload != null && !dataPayload.isEmpty()) {
            builder.putAllData(parseDataPayload(dataPayload));
        }

        if (clickAction != null && !clickAction.isEmpty()) {
            builder.putData("click_action", clickAction);
        }

        return builder.build();
    }

    private void handleFailureResponses(BatchResponse response, List<String> tokens) {
        if (response.getFailureCount() == 0) return;

        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            if (!responses.get(i).isSuccessful()) {
                String failedToken = tokens.get(i);
                try {
                    FirebaseMessagingException e = responses.get(i).getException();
                    if (e != null) {
                        String errorMsg = e.getMessage();
                        log.warn("FCM token {} failed: {}", maskToken(failedToken), errorMsg);

                        if (errorMsg != null && (
                                errorMsg.contains("UNREGISTERED") ||
                                errorMsg.contains("INVALID_ARGUMENT") ||
                                errorMsg.contains("NotRegistered") ||
                                errorMsg.contains("invalid"))) {
                            deactivateInvalidToken(failedToken);
                        }
                    }
                } catch (Exception ex) {
                    log.warn("FCM token {} failed (unknown error)", maskToken(failedToken));
                }
            }
        }
    }

    private void deactivateInvalidToken(String token) {
        try {
            fcmTokenRepository.findByDeviceToken(token).ifPresent(fcmToken -> {
                fcmToken.setActive(false);
                fcmTokenRepository.save(fcmToken);
                log.info("Deactivated invalid FCM token for user {}", fcmToken.getUserId());
            });
        } catch (Exception e) {
            log.error("Failed to deactivate invalid FCM token: {}", e.getMessage());
        }
    }

    private Map<String, String> parseDataPayload(String dataPayload) {
        Map<String, String> data = new HashMap<>();
        if (dataPayload != null && !dataPayload.isEmpty()) {
            String[] pairs = dataPayload.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    data.put(kv[0].trim(), kv[1].trim());
                }
            }
        }
        return data;
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 10) return "***";
        return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
    }
}
