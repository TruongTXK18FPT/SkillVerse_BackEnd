package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDispute;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDisputeEvidence;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDisputeResponse;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingDisputeEvidenceRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingDisputeRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingDisputeResponseRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_booking_service.service.impl.BookingDisputeServiceImpl;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingDisputeServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingDisputeRepository disputeRepository;

    @Mock
    private BookingDisputeEvidenceRepository evidenceRepository;

    @Mock
    private BookingDisputeResponseRepository responseRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private WalletService walletService;

    @Mock
    private JourneyRepository journeyRepository;

    private BookingDisputeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BookingDisputeServiceImpl(
                bookingRepository,
                disputeRepository,
                evidenceRepository,
                responseRepository,
                userRepository,
                notificationService,
                walletService,
                journeyRepository);
        lenient().when(disputeRepository.save(any(BookingDispute.class))).thenAnswer(invocation -> {
            BookingDispute dispute = invocation.getArgument(0);
            if (dispute.getId() == null) {
                dispute.setId(500L);
            }
            return dispute;
        });
        lenient().when(evidenceRepository.save(any(BookingDisputeEvidence.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(responseRepository.save(any(BookingDisputeResponse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("openDispute should allow the learner to dispute an eligible booking and notify the mentor")
    void openDispute_ShouldAllowLearnerForEligibleBookingAndNotifyMentor() {
        Booking booking = booking(100L, BookingStatus.PENDING_COMPLETION);
        booking.setMeetingLink("https://meet.jit.si/test-room");
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));
        when(disputeRepository.existsByBooking_Id(booking.getId())).thenReturn(false);

        BookingDispute dispute = service.openDispute(booking.getLearner().getId(), booking.getId(), "Session quality issue");

        assertEquals(BookingDispute.DisputeStatus.OPEN, dispute.getStatus());
        assertEquals(booking.getLearner().getId(), dispute.getInitiatorId());
        assertEquals(booking.getMentor().getId(), dispute.getRespondentId());
        assertEquals(BookingStatus.DISPUTED, booking.getStatus());
        assertNull(booking.getMeetingLink());
        verify(notificationService).createNotification(
                eq(booking.getMentor().getId()),
                anyString(),
                anyString(),
                eq(NotificationType.DISPUTE_OPENED),
                eq(dispute.getId().toString()),
                eq(booking.getLearner().getId()));
    }

    @Test
    @DisplayName("openDispute should reject non-learners")
    void openDispute_ShouldRejectNonLearners() {
        Booking booking = booking(100L, BookingStatus.PENDING_COMPLETION);
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.openDispute(booking.getMentor().getId(), booking.getId(), "No access"));

        assertTrue(exception.getMessage().contains("người học"));
    }

    @Test
    @DisplayName("openDispute should reject bookings that are not yet disputable")
    void openDispute_ShouldRejectBookingsThatAreNotYetDisputable() {
        Booking booking = booking(100L, BookingStatus.PENDING);
        booking.setEndTime(LocalDateTime.now().plusHours(2));
        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        assertThrows(IllegalStateException.class,
                () -> service.openDispute(booking.getLearner().getId(), booking.getId(), "Too early"));
    }

    @Test
    @DisplayName("submitEvidence should accept uploads from dispute participants")
    void submitEvidence_ShouldAcceptEvidenceFromParticipants() {
        BookingDispute dispute = openDisputeEntity();
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        BookingDisputeEvidence evidence = service.submitEvidence(
                dispute.getInitiatorId(),
                dispute.getId(),
                BookingDisputeEvidence.EvidenceType.FILE,
                "Chat transcript",
                "https://cdn.skillverse/evidence.png",
                "evidence.png",
                "Important proof");

        assertEquals(dispute, evidence.getDispute());
        assertEquals(dispute.getInitiatorId(), evidence.getSubmittedBy());
        assertEquals(BookingDisputeEvidence.EvidenceType.FILE, evidence.getEvidenceType());
        assertEquals("evidence.png", evidence.getFileName());
    }

    @Test
    @DisplayName("submitEvidence should reject non-participants")
    void submitEvidence_ShouldRejectNonParticipants() {
        BookingDispute dispute = openDisputeEntity();
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        assertThrows(IllegalArgumentException.class,
                () -> service.submitEvidence(999L, dispute.getId(), BookingDisputeEvidence.EvidenceType.TEXT,
                        "No access", null, null, null));
    }

    @Test
    @DisplayName("respondToEvidence should persist the response with the actor full name")
    void respondToEvidence_ShouldPersistResponseWithActorFullName() {
        BookingDispute dispute = openDisputeEntity();
        BookingDisputeEvidence evidence = BookingDisputeEvidence.builder()
                .id(700L)
                .dispute(dispute)
                .submittedBy(dispute.getInitiatorId())
                .evidenceType(BookingDisputeEvidence.EvidenceType.TEXT)
                .content("Original evidence")
                .build();
        User actor = User.builder()
                .id(dispute.getRespondentId())
                .firstName("Mentor")
                .lastName("Expert")
                .build();

        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));
        when(evidenceRepository.findById(evidence.getId())).thenReturn(Optional.of(evidence));
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));

        BookingDisputeResponse response = service.respondToEvidence(actor.getId(), dispute.getId(), evidence.getId(),
                "This was already delivered");

        assertEquals(actor.getId(), response.getRespondedBy());
        assertEquals("Mentor Expert", response.getRespondedByName());
        assertEquals("This was already delivered", response.getContent());
    }

    @Test
    @DisplayName("resolveDispute should fully refund the learner and notify both parties")
    void resolveDispute_ShouldFullyRefundLearnerAndNotifyBothParties() {
        BookingDispute dispute = openDisputeEntity();
        Booking booking = dispute.getBooking();
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        BookingDispute resolved = service.resolveDispute(900L, dispute.getId(),
                BookingDispute.DisputeResolution.FULL_REFUND, "Learner wins", null);

        assertEquals(BookingDispute.DisputeStatus.RESOLVED, resolved.getStatus());
        assertEquals(new BigDecimal("1000000"), resolved.getRefundAmount());
        assertEquals(BookingStatus.REFUNDED, booking.getStatus());
        verify(walletService).unfreezeForBooking(
                booking.getLearner().getId(),
                booking.getPriceVnd(),
                booking.getId(),
                "Dispute resolved: Full refund to learner");
        verify(notificationService).createNotification(
                eq(booking.getLearner().getId()),
                anyString(),
                anyString(),
                eq(NotificationType.DISPUTE_RESOLVED),
                eq(dispute.getId().toString()),
                eq(900L));
        verify(notificationService).createNotification(
                eq(booking.getMentor().getId()),
                anyString(),
                anyString(),
                eq(NotificationType.DISPUTE_RESOLVED),
                eq(dispute.getId().toString()),
                eq(900L));
    }

    @Test
    @DisplayName("resolveDispute should reject invalid partial amounts")
    void resolveDispute_ShouldRejectInvalidPartialAmounts() {
        BookingDispute dispute = openDisputeEntity();
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        assertThrows(IllegalArgumentException.class,
                () -> service.resolveDispute(900L, dispute.getId(),
                        BookingDispute.DisputeResolution.PARTIAL_RELEASE, "Invalid", BigDecimal.ZERO));
        verify(walletService, never()).payMentorForBooking(anyLong(), any(), anyLong());
    }

    @Test
    @DisplayName("reviewEvidenceAndResolve should move disputes into investigation when evidence is under review")
    void reviewEvidenceAndResolve_ShouldMoveDisputesIntoInvestigationWhenEvidenceIsUnderReview() {
        BookingDispute dispute = openDisputeEntity();
        BookingDisputeEvidence evidence = BookingDisputeEvidence.builder()
                .id(700L)
                .dispute(dispute)
                .submittedBy(dispute.getInitiatorId())
                .evidenceType(BookingDisputeEvidence.EvidenceType.TEXT)
                .content("Original evidence")
                .build();

        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));
        when(evidenceRepository.findById(evidence.getId())).thenReturn(Optional.of(evidence));

        BookingDisputeEvidence reviewed = service.reviewEvidenceAndResolve(
                900L,
                dispute.getId(),
                evidence.getId(),
                BookingDisputeEvidence.EvidenceReviewStatus.UNDER_REVIEW,
                null,
                "Investigating");

        assertEquals(BookingDisputeEvidence.EvidenceReviewStatus.UNDER_REVIEW, reviewed.getReviewStatus());
        assertEquals(BookingDispute.DisputeStatus.UNDER_INVESTIGATION, dispute.getStatus());
        assertEquals(900L, reviewed.getReviewedBy());
    }

    private Booking booking(Long bookingId, BookingStatus status) {
        User mentor = User.builder().id(10L).email("mentor@skillverse.vn").build();
        User learner = User.builder().id(20L).email("learner@skillverse.vn").build();
        return Booking.builder()
                .id(bookingId)
                .mentor(mentor)
                .learner(learner)
                .status(status)
                .startTime(LocalDateTime.now().minusHours(2))
                .endTime(LocalDateTime.now().minusHours(1))
                .durationMinutes(60)
                .priceVnd(new BigDecimal("1000000"))
                .meetingLink("https://meet.jit.si/room")
                .build();
    }

    private BookingDispute openDisputeEntity() {
        Booking booking = booking(100L, BookingStatus.DISPUTED);
        return BookingDispute.builder()
                .id(500L)
                .booking(booking)
                .initiatorId(booking.getLearner().getId())
                .respondentId(booking.getMentor().getId())
                .reason("Session quality issue")
                .status(BookingDispute.DisputeStatus.OPEN)
                .build();
    }
}
