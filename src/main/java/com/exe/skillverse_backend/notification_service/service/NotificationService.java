package com.exe.skillverse_backend.notification_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.notification_service.dto.NotificationResponse;
import com.exe.skillverse_backend.notification_service.entity.Notification;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.repository.NotificationRepository;
import com.exe.skillverse_backend.community_service.repository.PostRepository;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserProfileService userProfileService;
    private final PostRepository postRepository;

    @Transactional
    public void createNotification(Long userId, String title, String message, NotificationType type, String relatedId, Long senderId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .relatedId(relatedId)
                .senderId(senderId)
                .isRead(false)
                .build();

        notificationRepository.save(notification);
    }

    @Transactional
    public void createNotification(Long userId, String title, String message, NotificationType type, String relatedId) {
        createNotification(userId, title, message, type, relatedId, null);
    }

    public Page<NotificationResponse> getUserNotifications(Long userId, Boolean isRead, Pageable pageable) {
        Page<Notification> page;
        if (isRead != null) {
            page = notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(userId, isRead, pageable);
        } else {
            page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return page.map(this::mapToResponse);
    }

    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    public long getTotalCount(Long userId) {
        return notificationRepository.countByUserId(userId);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).forEach(n -> {
            if (!n.isRead()) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    private NotificationResponse mapToResponse(Notification notification) {
        String senderName = null;
        String senderAvatar = null;
        String postTitle = null;

        if (notification.getSenderId() != null) {
            try {
                User sender = userRepository.findById(notification.getSenderId()).orElse(null);
                if (sender != null) {
                    if (userProfileService.hasProfile(sender.getId())) {
                        var profile = userProfileService.getProfile(sender.getId());
                        String profileName = profile.getFullName();
                        String profileAvatar = profile.getAvatarMediaUrl();
                        if (profileName != null && !profileName.isBlank()) {
                            senderName = profileName;
                        }
                        if (profileAvatar != null && !profileAvatar.isBlank()) {
                            senderAvatar = profileAvatar;
                        }
                    }

                    if (senderName == null || senderName.isBlank()) {
                        String fn = sender.getFirstName();
                        String ln = sender.getLastName();
                        String built = ((fn != null ? fn : "") + (ln != null ? " " + ln : "")).trim();
                        senderName = built.isEmpty() ? ("User #" + sender.getId()) : built;
                    }

                    if (senderAvatar == null || senderAvatar.isBlank()) {
                        String entityAvatar = sender.getAvatarUrl();
                        if (entityAvatar != null && !entityAvatar.isBlank()) {
                            senderAvatar = entityAvatar;
                        }
                    }
                }
            } catch (Exception e) {
            }
        }

        if (notification.getRelatedId() != null) {
            try {
                if (notification.getType() == NotificationType.LIKE || notification.getType() == NotificationType.COMMENT) {
                    Long postId = Long.parseLong(notification.getRelatedId());
                    postTitle = postRepository.findById(postId).map(p -> p.getTitle()).orElse(null);
                }
            } catch (Exception e) {
            }
        }

        return NotificationResponse.builder()
                .id(notification.getId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .isRead(notification.isRead())
                .relatedId(notification.getRelatedId())
                .senderId(notification.getSenderId())
                .senderName(senderName)
                .senderAvatar(senderAvatar)
                .createdAt(notification.getCreatedAt())
                .postTitle(postTitle)
                .build();
    }
}
