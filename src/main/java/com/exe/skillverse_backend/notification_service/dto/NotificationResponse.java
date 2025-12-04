package com.exe.skillverse_backend.notification_service.dto;

import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private String title;
    private String message;
    private NotificationType type;
    private boolean isRead;
    private String relatedId;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String postTitle;
    private LocalDateTime createdAt;
}
