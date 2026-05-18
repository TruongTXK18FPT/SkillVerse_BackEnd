package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.CreateBookingIntentRequest;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingDisputeRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingReviewRepository;
import com.exe.skillverse_backend.mentor_booking_service.service.impl.BookingServiceImpl;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.payment_service.service.InvoiceService;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.user_service.service.UserProfileService;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.exe.skillverse_backend.mentor_matching_service.service.MentorTeachingEligibilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingReviewRepository bookingReviewRepository;
    @Mock private WalletTransactionRepository transactionRepository;
    @Mock private BookingDisputeRepository disputeRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;
    @Mock private WalletService walletService;
    @Mock private MentorProfileRepository mentorProfileRepository;
    @Mock private UserProfileService userProfileService;
    @Mock private EmailService emailService;
    @Mock private InvoiceService invoiceService;
    @Mock private JourneyRepository journeyRepository;
    @Mock private PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;
    @Mock private MentorTeachingEligibilityService mentorTeachingEligibilityService;

    private BookingServiceImpl service;
    private ObjectMapper objectMapper = new ObjectMapper();

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
                objectMapper,
                emailService,
                invoiceService,
                journeyRepository,
                portfolioExtendedProfileRepository,
                mentorTeachingEligibilityService
        );
        ReflectionTestUtils.setField(service, "jitsiBaseUrl", "https://meet.jit.si");
        lenient().when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("createBookingWithWallet: ROADMAP_MENTORING should auto-set journey flags")
    void createBookingWithWallet_RoadmapMentoring_ShouldSetJourneyFlags() {
        Long learnerId = 20L;
        Long mentorId = 10L;
        Long journeyId = 300L;
        CreateBookingIntentRequest request = CreateBookingIntentRequest.builder()
                .mentorId(mentorId)
                .bookingType("ROADMAP_MENTORING")
                .journeyId(journeyId)
                .startTime(ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1))
                .priceVnd(new BigDecimal("1000000"))
                .build();

        User mentor = User.builder().id(mentorId).email("mentor@test.com").build();
        User learner = User.builder().id(learnerId).email("learner@test.com").build();
        Journey journey = Journey.builder().id(journeyId).user(learner).roadmapSessionId(50L).build();

        when(userRepository.findById(mentorId)).thenReturn(Optional.of(mentor));
        when(userRepository.findById(learnerId)).thenReturn(Optional.of(learner));
        when(journeyRepository.findById(journeyId)).thenReturn(Optional.of(journey));
        when(portfolioExtendedProfileRepository.findByUserId(mentorId)).thenReturn(Optional.empty()); // Assume not configured yet for simplicity or mock it
        
        // Mocking portfolioExtendedProfile for price validation
        var portfolioProfile = mock(com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile.class);
        when(portfolioExtendedProfileRepository.findByUserId(mentorId)).thenReturn(Optional.of(portfolioProfile));
        when(portfolioProfile.getRoadmapMentoringPrice()).thenReturn(1000000.0);
        when(bookingRepository.save(any())).thenAnswer(i -> {
            Booking b = i.getArgument(0);
            b.setId(500L);
            return b;
        });

        Booking saved = service.createBookingWithWallet(learnerId, request);

        assertEquals(BookingStatus.PENDING, saved.getStatus());
        assertEquals("ROADMAP_MENTORING", saved.getBookingType());
        assertEquals(50L, saved.getRoadmapSessionId());
        
        verify(journeyRepository, atLeastOnce()).save(argThat(j -> 
            Boolean.TRUE.equals(j.getFinalVerificationRequired()) && 
            Boolean.TRUE.equals(j.getJourneyOutputVerificationRequired())
        ));
        verify(walletService).freezeCashForBooking(eq(learnerId), eq(new BigDecimal("1000000")), anyLong());
    }

    @Test
    @DisplayName("approve: ROADMAP_MENTORING should transition to MENTORING_ACTIVE")
    void approve_RoadmapMentoring_ShouldTransitionToMentoringActive() {
        Long mentorId = 10L;
        Booking booking = Booking.builder()
                .id(100L)
                .mentor(User.builder().id(mentorId).build())
                .learner(User.builder().id(20L).email("learner@test.com").build())
                .status(BookingStatus.PENDING)
                .bookingType("ROADMAP_MENTORING")
                .journeyId(300L)
                .build();

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        when(journeyRepository.findById(300L)).thenReturn(Optional.of(Journey.builder().id(300L).build()));

        Booking approved = service.approve(mentorId, 100L);

        assertEquals(BookingStatus.MENTORING_ACTIVE, approved.getStatus());
        assertNotNull(approved.getRoadmapMentoringStartedAt());
    }

    @Test
    @DisplayName("finalizeSessionCompletion: should release escrow and reward mentor")
    void finalizeSessionCompletion_ShouldReleaseEscrowAndRewardMentor() {
        User mentor = User.builder().id(10L).build();
        User learner = User.builder().id(20L).build();
        Booking booking = Booking.builder()
                .id(100L)
                .mentor(mentor)
                .learner(learner)
                .priceVnd(new BigDecimal("1000000"))
                .status(BookingStatus.PENDING_COMPLETION)
                .build();

        MentorProfile profile = MentorProfile.builder()
                .userId(10L)
                .skillPoints(100)
                .currentLevel(1)
                .badges("[]")
                .build();

        when(mentorProfileRepository.findByUserId(10L)).thenReturn(Optional.of(profile));
        when(bookingRepository.countByMentorAndStatus(eq(mentor), eq(BookingStatus.COMPLETED))).thenReturn(1L);

        // Access private method via Reflection or just trigger it via complete()
        // Here we test the result of complete() when learner already confirmed
        booking.setConfirmedByLearner(true);
        booking.setStartTime(LocalDateTime.now().minusHours(2));
        booking.setDurationMinutes(60);
        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));

        Booking finalized = service.complete(10L, 100L);

        assertEquals(BookingStatus.COMPLETED, finalized.getStatus());
        verify(walletService).chargeFrozenForBooking(eq(20L), eq(new BigDecimal("1000000")), eq(100L));
        verify(walletService).payMentorForBooking(eq(10L), eq(new BigDecimal("800000.00")), eq(100L));
        
        // Skill points: 100 + 20 (base) + 50 (first session bonus) = 170
        // Level: 170 / 100 = 1
        assertEquals(170, profile.getSkillPoints());
        verify(mentorProfileRepository).save(profile);
    }

    @Test
    @DisplayName("reject: should refund learner and reset journey flags if needed")
    void reject_ShouldRefundAndResetJourneyFlags() {
        Long mentorId = 10L;
        Booking booking = Booking.builder()
                .id(100L)
                .mentor(User.builder().id(mentorId).build())
                .learner(User.builder().id(20L).email("learner@test.com").build())
                .status(BookingStatus.PENDING)
                .bookingType("JOURNEY_MENTORING")
                .journeyId(300L)
                .priceVnd(new BigDecimal("500000"))
                .build();

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));
        when(bookingRepository.existsActiveJourneyBookingForAnyMentor(eq(300L), anyList())).thenReturn(false);
        when(journeyRepository.findById(300L)).thenReturn(Optional.of(Journey.builder().id(300L).build()));

        service.reject(mentorId, 100L, "Too busy");

        assertEquals(BookingStatus.REJECTED, booking.getStatus());
        verify(walletService).processRefund(eq(20L), eq(new BigDecimal("500000")), anyString(), eq("BOOKING_100"));
        verify(journeyRepository, atLeastOnce()).save(argThat(j -> Boolean.FALSE.equals(j.getFinalVerificationRequired())));
    }

    @Test
    @DisplayName("learnerConfirmComplete: should transition to PENDING_COMPLETION if mentor hasn't completed")
    void learnerConfirmComplete_MentorNotDone_ShouldTransitionToPending() {
        Long learnerId = 20L;
        Booking booking = Booking.builder()
                .id(100L)
                .learner(User.builder().id(learnerId).build())
                .mentor(User.builder().id(10L).email("mentor@test.com").build())
                .status(BookingStatus.ONGOING)
                .startTime(LocalDateTime.now().minusHours(2))
                .durationMinutes(60)
                .build();

        when(bookingRepository.findById(100L)).thenReturn(Optional.of(booking));

        Booking result = service.learnerConfirmComplete(learnerId, 100L);

        assertEquals(BookingStatus.PENDING_COMPLETION, result.getStatus());
        assertTrue(result.getConfirmedByLearner());
        assertNotNull(result.getCompletionDeadline());
        verify(notificationService).createNotification(eq(10L), anyString(), anyString(), any(), anyString(), eq(learnerId));
    }
}
