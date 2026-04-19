package com.exe.skillverse_backend.notification_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.community_service.repository.PostRepository;
import com.exe.skillverse_backend.notification_service.dto.NotificationPayload;
import com.exe.skillverse_backend.notification_service.dto.NotificationResponse;
import com.exe.skillverse_backend.notification_service.entity.Notification;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.repository.NotificationRepository;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.notification_service.service.FcmService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserProfileService userProfileService;
    private final PostRepository postRepository;
    private final FcmService fcmService;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(Long userId, String title, String message, NotificationType type, String relatedId,
            Long senderId) {
        persistNotification(userId, title, message, type, relatedId, null, senderId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(Long userId, String title, String message, NotificationType type, String relatedId) {
        persistNotification(userId, title, message, type, relatedId, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(Long userId, String title, String message, NotificationType type, String relatedId,
            NotificationPayload payload, Long senderId) {
        persistNotification(userId, title, message, type, relatedId, payload, senderId);
    }

    private void persistNotification(Long userId, String title, String message, NotificationType type, String relatedId,
            NotificationPayload payload, Long senderId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .relatedId(relatedId)
                .payloadJson(serializePayload(payload))
                .senderId(senderId)
                .isRead(false)
                .build();

        notificationRepository.saveAndFlush(notification);

        // Send push notification to mobile devices (async, non-blocking)
        if (fcmService.isFirebaseEnabled()) {
            Map<String, String> pushData = buildPushData(notification, payload);
            String dataPayload = toDataPayload(pushData);
            fcmService.sendPushNotification(userId, title, message, dataPayload);
        }
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
        notificationRepository.markAllAsReadByUserId(userId);
    }

    private NotificationResponse mapToResponse(Notification notification) {
        String senderName = null;
        String senderAvatar = null;
        String postTitle = null;
        NotificationPayload payload = deserializePayload(notification.getPayloadJson());
        if (payload == null) {
            payload = deriveLegacyPayload(notification);
        }

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
                if (notification.getType() == NotificationType.LIKE
                        || notification.getType() == NotificationType.COMMENT) {
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
                .payload(payload)
                .senderId(notification.getSenderId())
                .senderName(senderName)
                .senderAvatar(senderAvatar)
                .createdAt(notification.getCreatedAt())
                .postTitle(postTitle)
                .build();
    }

    private String serializePayload(NotificationPayload payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private NotificationPayload deserializePayload(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payloadJson, NotificationPayload.class);
        } catch (Exception e) {
            return null;
        }
    }

    private NotificationPayload deriveLegacyPayload(Notification notification) {
        if (notification.getType() == NotificationType.ASSIGNMENT_GRADED) {
            Long submissionId = parseLong(notification.getRelatedId());
            return NotificationPayload.forAssignmentGraded(null, submissionId);
        }

        String title = notification.getTitle() != null
                ? notification.getTitle().toLowerCase()
                : "";
        if (notification.getType() == NotificationType.SYSTEM && title.contains("mua khóa học")) {
            Long courseId = parseCourseId(notification.getRelatedId());
            return NotificationPayload.forCoursePurchase(courseId);
        }

        return null;
    }

    private Long parseLong(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseCourseId(String relatedId) {
        if (relatedId == null || relatedId.isBlank()) {
            return null;
        }
        if (relatedId.startsWith("COURSE_")) {
            return parseLong(relatedId.substring("COURSE_".length()));
        }
        return parseLong(relatedId);
    }

    private Map<String, String> buildPushData(Notification notification, NotificationPayload payload) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", notification.getType().name());
        data.put("notificationId", String.valueOf(notification.getId()));
        if (notification.getRelatedId() != null && !notification.getRelatedId().isBlank()) {
            data.put("relatedId", notification.getRelatedId());
        }

        NotificationPayload effectivePayload = payload != null ? payload : deriveLegacyPayload(notification);
        if (effectivePayload == null) {
            return data;
        }

        if (effectivePayload.getAction() != null) {
            NotificationPayload.Action action = effectivePayload.getAction();
            if (action.getKey() != null && !action.getKey().isBlank()) {
                data.put("actionKey", action.getKey());
            }
            if (action.getPath() != null && !action.getPath().isBlank()) {
                data.put("actionPath", action.getPath());
            }
            if (action.getAnchor() != null && !action.getAnchor().isBlank()) {
                data.put("actionAnchor", action.getAnchor());
            }
        }

        if (effectivePayload.getResource() != null) {
            NotificationPayload.Resource resource = effectivePayload.getResource();
            if (resource.getCourseId() != null) {
                data.put("resourceCourseId", String.valueOf(resource.getCourseId()));
            }
            if (resource.getAssignmentId() != null) {
                data.put("resourceAssignmentId", String.valueOf(resource.getAssignmentId()));
            }
            if (resource.getSubmissionId() != null) {
                data.put("resourceSubmissionId", String.valueOf(resource.getSubmissionId()));
            }
        }

        data.put("payloadVersion", "2");
        return data;
    }

    private String toDataPayload(Map<String, String> data) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : data.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }
}
