package com.exe.skillverse_backend.prechat_service.controller;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.prechat_service.dto.PreChatMessageRequest;
import com.exe.skillverse_backend.prechat_service.dto.PreChatMessageResponse;
import com.exe.skillverse_backend.prechat_service.entity.PreChatMessage;
import com.exe.skillverse_backend.prechat_service.entity.PreChatThreadState;
import com.exe.skillverse_backend.prechat_service.repository.PreChatBlockRepository;
import com.exe.skillverse_backend.prechat_service.repository.PreChatMessageRepository;
import com.exe.skillverse_backend.prechat_service.repository.PreChatReportRepository;
import com.exe.skillverse_backend.prechat_service.repository.PreChatThreadStateRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PreChatControllerTest {

    @Mock
    private PreChatMessageRepository messageRepository;

    @Mock
    private PreChatBlockRepository blockRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PreChatThreadStateRepository threadStateRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PreChatReportRepository reportRepository;

    @InjectMocks
    private PreChatController preChatController;

    private User mentor;
    private User learner;
    private Booking booking;
    private Authentication authentication;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        mentor = User.builder()
                .id(10L)
                .firstName("Mentor")
                .lastName("Name")
                .build();

        learner = User.builder()
                .id(20L)
                .firstName("Learner")
                .lastName("Name")
                .build();

        booking = new Booking();
        booking.setId(100L);
        booking.setMentor(mentor);
        booking.setLearner(learner);
        booking.setStatus(BookingStatus.MENTORING_ACTIVE);

        authentication = mock(Authentication.class);
        jwt = mock(Jwt.class);
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getClaimAsString("userId")).thenReturn("20"); // learner sending
    }

    @Test
    void sendRest_Success() {
        PreChatMessageRequest request = PreChatMessageRequest.builder()
                .bookingId(100L)
                .content("Hello Mentor")
                .build();

        PreChatMessage savedMsg = PreChatMessage.builder()
                .id(1L)
                .booking(booking)
                .mentor(mentor)
                .learner(learner)
                .sender(learner)
                .content("Hello Mentor")
                .readByMentor(false)
                .readByLearner(true)
                .build();
        savedMsg.setCreatedAt(LocalDateTime.now());

        when(bookingRepository.findAccessibleBooking(100L, 20L)).thenReturn(Optional.of(booking));
        when(blockRepository.existsByMentorAndLearner(mentor, learner)).thenReturn(false);
        when(messageRepository.save(any(PreChatMessage.class))).thenReturn(savedMsg);
        when(threadStateRepository.findByMentorAndLearner(mentor, learner)).thenReturn(Optional.empty());

        PreChatMessageResponse response = preChatController.sendRest(request, authentication);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("Hello Mentor", response.getContent());
        assertTrue(response.isChatEnabled());

        verify(messageRepository).save(any(PreChatMessage.class));
        verify(notificationService).createNotification(
                eq(10L),
                eq("Tin nhắn mới từ learner"),
                eq("Hello Mentor"),
                eq(NotificationType.PRECHAT_MESSAGE),
                eq("100"),
                eq(20L)
        );
        verify(messagingTemplate).convertAndSendToUser(eq("10"), eq("/queue/prechat"), any(PreChatMessageResponse.class));
        verify(messagingTemplate).convertAndSendToUser(eq("20"), eq("/queue/prechat"), any(PreChatMessageResponse.class));
    }

    @Test
    void sendRest_InactiveBooking_ThrowsForbidden() {
        booking.setStatus(BookingStatus.COMPLETED); // completed, inactive
        booking.setEndTime(LocalDateTime.now().minusHours(1));

        PreChatMessageRequest request = PreChatMessageRequest.builder()
                .bookingId(100L)
                .content("Hello Mentor")
                .build();

        when(bookingRepository.findAccessibleBooking(100L, 20L)).thenReturn(Optional.of(booking));

        ApiException exception = assertThrows(ApiException.class, () ->
                preChatController.sendRest(request, authentication)
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Session chat đã đóng"));

        verify(messageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void sendRest_UserBlocked_ThrowsForbidden() {
        PreChatMessageRequest request = PreChatMessageRequest.builder()
                .bookingId(100L)
                .content("Hello Mentor")
                .build();

        when(bookingRepository.findAccessibleBooking(100L, 20L)).thenReturn(Optional.of(booking));
        when(blockRepository.existsByMentorAndLearner(mentor, learner)).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class, () ->
                preChatController.sendRest(request, authentication)
        );

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("Bạn đã bị mentor chặn"));

        verify(messageRepository, never()).save(any());
        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void sendStomp_Success() {
        PreChatMessageRequest request = PreChatMessageRequest.builder()
                .bookingId(100L)
                .content("Hello Mentor STOMP")
                .build();

        PreChatMessage savedMsg = PreChatMessage.builder()
                .id(2L)
                .booking(booking)
                .mentor(mentor)
                .learner(learner)
                .sender(learner)
                .content("Hello Mentor STOMP")
                .readByMentor(false)
                .readByLearner(true)
                .build();
        savedMsg.setCreatedAt(LocalDateTime.now());

        when(bookingRepository.findAccessibleBooking(100L, 20L)).thenReturn(Optional.of(booking));
        when(blockRepository.existsByMentorAndLearner(mentor, learner)).thenReturn(false);
        when(messageRepository.save(any(PreChatMessage.class))).thenReturn(savedMsg);
        when(threadStateRepository.findByMentorAndLearner(mentor, learner)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> preChatController.sendStomp(request, authentication));

        verify(messageRepository).save(any(PreChatMessage.class));
        verify(messagingTemplate).convertAndSendToUser(eq("10"), eq("/queue/prechat"), any(PreChatMessageResponse.class));
    }
}
