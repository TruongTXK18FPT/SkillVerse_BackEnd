package com.exe.skillverse_backend.notification_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.community_service.entity.Post;
import com.exe.skillverse_backend.community_service.repository.PostRepository;
import com.exe.skillverse_backend.notification_service.dto.NotificationResponse;
import com.exe.skillverse_backend.notification_service.entity.Notification;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.repository.NotificationRepository;
import com.exe.skillverse_backend.notification_service.service.impl.NotificationServiceImpl;
import com.exe.skillverse_backend.user_service.dto.response.UserProfileResponse;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private PostRepository postRepository;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, userRepository, userProfileService, postRepository);
    }

    @Test
    @DisplayName("createNotification should persist an unread notification for the target user")
    void createNotification_ShouldPersistUnreadNotification() {
        User recipient = User.builder().id(1L).email("recipient@skillverse.vn").build();
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));

        service.createNotification(
                recipient.getId(),
                "Booking created",
                "You have a new booking",
                NotificationType.BOOKING_CREATED,
                "123",
                99L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).saveAndFlush(captor.capture());
        Notification saved = captor.getValue();
        assertEquals(recipient, saved.getUser());
        assertEquals(NotificationType.BOOKING_CREATED, saved.getType());
        assertEquals("123", saved.getRelatedId());
        assertEquals(99L, saved.getSenderId());
        assertFalse(saved.isRead());
    }

    @Test
    @DisplayName("getUserNotifications should enrich sender profile and related post title")
    void getUserNotifications_ShouldEnrichSenderAndPostTitle() {
        User recipient = User.builder().id(1L).build();
        User sender = User.builder()
                .id(2L)
                .firstName("Fallback")
                .lastName("User")
                .avatarUrl("https://cdn/fallback.png")
                .build();
        Notification notification = Notification.builder()
                .id(5L)
                .user(recipient)
                .title("Someone liked your post")
                .message("Liked it")
                .type(NotificationType.LIKE)
                .relatedId("99")
                .senderId(sender.getId())
                .createdAt(LocalDateTime.now())
                .build();
        Post post = Post.builder().id(99L).title("Backend Testing Guide").build();

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(recipient.getId(), PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 10), 1));
        when(userRepository.findById(sender.getId())).thenReturn(Optional.of(sender));
        when(userProfileService.hasProfile(sender.getId())).thenReturn(true);
        when(userProfileService.getProfile(sender.getId())).thenReturn(UserProfileResponse.builder()
                .fullName("Alice Mentor")
                .avatarMediaUrl("https://cdn/avatar.png")
                .build());
        when(postRepository.findById(99L)).thenReturn(Optional.of(post));

        NotificationResponse response = service.getUserNotifications(recipient.getId(), null, PageRequest.of(0, 10))
                .getContent()
                .get(0);

        assertEquals("Alice Mentor", response.getSenderName());
        assertEquals("https://cdn/avatar.png", response.getSenderAvatar());
        assertEquals("Backend Testing Guide", response.getPostTitle());
    }

    @Test
    @DisplayName("markAllAsRead should save only unread notifications")
    void markAllAsRead_ShouldSaveOnlyUnreadNotifications() {
        Notification unread = Notification.builder().id(1L).isRead(false).build();
        Notification alreadyRead = Notification.builder().id(2L).isRead(true).build();
        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(unread, alreadyRead));

        service.markAllAsRead(7L);

        assertEquals(true, unread.isRead());
        verify(notificationRepository).save(unread);
        verify(notificationRepository, never()).save(alreadyRead);
    }

    @Test
    @DisplayName("markAsRead should load and update the notification")
    void markAsRead_ShouldLoadAndUpdateNotification() {
        Notification notification = Notification.builder().id(8L).isRead(false).build();
        when(notificationRepository.findById(8L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.markAsRead(8L);

        assertEquals(true, notification.isRead());
        verify(notificationRepository).save(notification);
    }
}
