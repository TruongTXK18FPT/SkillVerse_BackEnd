package com.exe.skillverse_backend.notification_service.dto;

import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private NotificationPayload payload;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private String postTitle;
    private LocalDateTime createdAt;
}
