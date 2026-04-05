package com.exe.skillverse_backend.notification_service.service;

import com.exe.skillverse_backend.notification_service.dto.FcmTokenRequest;
import com.exe.skillverse_backend.notification_service.dto.FcmTokenResponse;

import java.util.List;

public interface FcmService {

    FcmTokenResponse registerToken(Long userId, FcmTokenRequest request);

    FcmTokenResponse unregisterToken(String deviceToken);

    void unregisterAllTokens(Long userId);

    List<String> getActiveTokens(Long userId);

    void sendPushNotification(Long userId, String title, String body, String dataPayload);

    void sendPushNotification(Long userId, String title, String body, String dataPayload, String clickAction);

    void sendMulticastPushNotification(List<Long> userIds, String title, String body, String dataPayload);

    boolean isFirebaseEnabled();
}
