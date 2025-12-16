package com.exe.skillverse_backend.notification_service.service;

import com.exe.skillverse_backend.notification_service.dto.NotificationResponse;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface NotificationService {
    void createNotification(Long userId, String title, String message, NotificationType type, String relatedId,
            Long senderId);

    void createNotification(Long userId, String title, String message, NotificationType type, String relatedId);

    Page<NotificationResponse> getUserNotifications(Long userId, Boolean isRead, Pageable pageable);

    long getUnreadCount(Long userId);

    long getTotalCount(Long userId);

    void markAsRead(Long notificationId);

    void markAllAsRead(Long userId);
}
