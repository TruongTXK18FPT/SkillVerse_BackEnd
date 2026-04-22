package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.CreateBookingIntentRequest;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingDisputeRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingReviewRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.mentor_booking_service.service.impl.BookingServiceImpl;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.payment_service.service.InvoiceService;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingReviewRepository bookingReviewRepository;

    @Mock
    private WalletTransactionRepository transactionRepository;

    @Mock
    private BookingDisputeRepository disputeRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private WalletService walletService;

    @Mock
    private MentorProfileRepository mentorProfileRepository;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private EmailService emailService;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private JourneyRepository journeyRepository;

    private BookingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookingServiceImpl(
                bookingRepository,
                bookingReviewRepository,
                transactionRepository,
                disputeRepository,
                userRepository,
                notificationService,
                walletService,
                mentorProfileRepository,
                userProfileService,
                new ObjectMapper(),
                emailService,
                invoiceService,
                journeyRepository);
        ReflectionTestUtils.setField(service, "jitsiBaseUrl", "https://meet.jit.si");
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("approve should enforce mentor ownership")
    void approve_ShouldEnforceMentorOwnership() {
        Booking booking = booking(200L, BookingStatus.PENDING, LocalDateTime.now().plusDays(2));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThrows(IllegalArgumentException.class, () -> service.approve(99L, booking.getId()));
    }

    @Test
    @DisplayName("getBookingDetail should block non-participants")
    void getBookingDetail_ShouldBlockNonParticipants() {
        Booking booking = booking(200L, BookingStatus.PENDING, LocalDateTime.now().plusDays(2));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThrows(IllegalArgumentException.class, () -> service.getBookingDetail(99L, booking.getId()));
    }

    @Test
    @DisplayName("getBookingDetail should allow chat for active future bookings")
    void getBookingDetail_ShouldExposeChatAllowedForActiveBooking() {
        Booking booking = booking(200L, BookingStatus.CONFIRMED, LocalDateTime.now().plusHours(2));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(mentorProfileRepository.findById(booking.getMentor().getId())).thenReturn(Optional.empty());

        var response = service.getBookingDetail(booking.getLearner().getId(), booking.getId());

        assertTrue(Boolean.TRUE.equals(response.getChatAllowed()));
    }

    @Test
    @DisplayName("getBookingDetail should close chat after the booking has ended")
    void getBookingDetail_ShouldExposeChatClosedAfterBookingEnd() {
        Booking booking = booking(200L, BookingStatus.ONGOING, LocalDateTime.now().minusHours(2));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(mentorProfileRepository.findById(booking.getMentor().getId())).thenReturn(Optional.empty());

        var response = service.getBookingDetail(booking.getLearner().getId(), booking.getId());

        assertEquals(Boolean.FALSE, response.getChatAllowed());
    }

    @Test
    @DisplayName("createBookingWithWallet should reject self-booking")
    void createBookingWithWallet_ShouldRejectSelfBooking() {
        User sameUser = User.builder()
                .id(10L)
                .email("mentor@skillverse.vn")
                .firstName("Mentor")
                .lastName("One")
                .build();
        CreateBookingIntentRequest request = CreateBookingIntentRequest.builder()
                .mentorId(10L)
                .startTime(ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(2))
                .durationMinutes(60)
                .priceVnd(new BigDecimal("500000"))
                .paymentMethod("WALLET")
                .build();

        when(userRepository.findById(10L)).thenReturn(Optional.of(sameUser));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.createBookingWithWallet(10L, request));

        assertEquals("Bạn không thể tự đặt lịch với chính mình", exception.getMessage());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(walletService, never()).freezeCashForBooking(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("reject should skip wallet refunds when the booking was already refunded")
    void reject_ShouldSkipWalletRefundsWhenAlreadyRefunded() {
        Booking booking = booking(200L, BookingStatus.PENDING, LocalDateTime.now().plusDays(2));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(transactionRepository.existsByReferenceIdAndReferenceTypeAndStatus(
                "BOOKING_" + booking.getId(),
                "BOOKING_REFUND",
                WalletTransaction.TransactionStatus.COMPLETED))
                .thenReturn(true);

        Booking rejected = service.reject(booking.getMentor().getId(), booking.getId(), "Mentor unavailable");

        assertEquals(BookingStatus.REJECTED, rejected.getStatus());
        verify(walletService, never()).processRefund(anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("startMeeting should generate the meeting link and mark the booking ongoing")
    void startMeeting_ShouldGenerateMeetingLinkAndMarkBookingOngoing() {
        Booking booking = booking(200L, BookingStatus.CONFIRMED, LocalDateTime.now().minusMinutes(10));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        Booking started = service.startMeeting(booking.getMentor().getId(), booking.getId());

        assertEquals(BookingStatus.ONGOING, started.getStatus());
        assertTrue(started.getMeetingLink().startsWith("https://meet.jit.si/"));
    }

    @Test
    @DisplayName("cancelByLearner should enforce the one-day cancellation window")
    void cancelByLearner_ShouldEnforceOneDayCancellationWindow() {
        Booking booking = booking(200L, BookingStatus.CONFIRMED, LocalDateTime.now().plusHours(12));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThrows(IllegalStateException.class,
                () -> service.cancelByLearner(booking.getLearner().getId(), booking.getId()));
    }

    private Booking booking(Long bookingId, BookingStatus status, LocalDateTime startTime) {
        User mentor = User.builder().id(10L).email("mentor@skillverse.vn").firstName("Mentor").lastName("One").build();
        User learner = User.builder().id(20L).email("learner@skillverse.vn").firstName("Learner").lastName("One").build();
        return Booking.builder()
                .id(bookingId)
                .mentor(mentor)
                .learner(learner)
                .status(status)
                .startTime(startTime)
                .endTime(startTime.plusHours(1))
                .durationMinutes(60)
                .priceVnd(new BigDecimal("500000"))
                .build();
    }
}
