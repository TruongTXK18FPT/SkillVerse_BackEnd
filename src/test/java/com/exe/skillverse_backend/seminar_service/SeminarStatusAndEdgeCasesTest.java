package com.exe.skillverse_backend.seminar_service;

import com.exe.skillverse_backend.seminar_service.dto.response.SeminarTicketResponse;
import com.exe.skillverse_backend.seminar_service.entity.Seminar;
import com.exe.skillverse_backend.seminar_service.entity.SeminarStatus;
import com.exe.skillverse_backend.seminar_service.entity.SeminarTicket;
import com.exe.skillverse_backend.seminar_service.repository.SeminarRepository;
import com.exe.skillverse_backend.seminar_service.repository.SeminarTicketRepository;
import com.exe.skillverse_backend.seminar_service.service.impl.SeminarServiceImpl;
import com.exe.skillverse_backend.seminar_service.validation.SeminarValidator;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Seminar Status and Edge Cases
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Seminar Status and Edge Cases Tests")
class SeminarStatusAndEdgeCasesTest {

    @Mock
    private SeminarRepository seminarRepository;
    @Mock
    private SeminarTicketRepository ticketRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private RecruiterProfileRepository recruiterProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private WalletTransactionRepository walletTransactionRepository;
    @Mock
    private SeminarValidator seminarValidator;

    @InjectMocks
    private SeminarServiceImpl seminarService;

    private static final String RECRUITER_ID = "100";
    private static final String BUYER_ID = "200";
    private static final Long SEMINAR_ID = 1L;
    private static final BigDecimal PRICE = new BigDecimal("100000");
    private static final LocalDateTime FUTURE_START = LocalDateTime.now().plusDays(7);
    private static final LocalDateTime FUTURE_END = LocalDateTime.now().plusDays(8);

    private Seminar testSeminar;

    @BeforeEach
    void setUp() {
        testSeminar = Seminar.builder()
                .id(SEMINAR_ID)
                .title("Test Seminar")
                .description("Description")
                .meetingLink("https://meet.test.com/abc")
                .startTime(FUTURE_START)
                .endTime(FUTURE_END)
                .price(PRICE)
                .status(SeminarStatus.ACCEPTED)
                .creatorId(RECRUITER_ID)
                .maxCapacity(50)
                .ticketsSold(0)
                .version(0L)
                .build();
    }

    // ========================================
    // SEMINAR STATUS TESTS
    // ========================================

    @Test
    @DisplayName("✅ Status: Can buy ticket with ACCEPTED status")
    void buyTicket_AcceptedStatus() {
        testSeminar.setStatus(SeminarStatus.ACCEPTED);

        when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
        when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
        when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
        when(ticketRepository.save(any(SeminarTicket.class))).thenAnswer(i -> i.getArgument(0));

        SeminarTicketResponse response = seminarService.buyTicket(SEMINAR_ID, BUYER_ID);

        assertNotNull(response);
    }

    @Test
    @DisplayName("✅ Status: Can buy ticket with OPEN status")
    void buyTicket_OpenStatus() {
        testSeminar.setStatus(SeminarStatus.OPEN);

        when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
        when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
        when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
        when(ticketRepository.save(any(SeminarTicket.class))).thenAnswer(i -> i.getArgument(0));

        SeminarTicketResponse response = seminarService.buyTicket(SEMINAR_ID, BUYER_ID);

        assertNotNull(response);
    }

    @Test
    @DisplayName("❌ Status: Cannot buy ticket with DRAFT status")
    void buyTicket_DraftStatus() {
        testSeminar.setStatus(SeminarStatus.DRAFT);
        when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

        assertThrows(IllegalStateException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));
    }

    @Test
    @DisplayName("❌ Status: Cannot buy ticket with PENDING status")
    void buyTicket_PendingStatus() {
        testSeminar.setStatus(SeminarStatus.PENDING);
        when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

        assertThrows(IllegalStateException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));
    }

    @Test
    @DisplayName("❌ Status: Cannot buy ticket with REJECTED status")
    void buyTicket_RejectedStatus() {
        testSeminar.setStatus(SeminarStatus.REJECTED);
        when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

        assertThrows(IllegalStateException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));
    }

    @Test
    @DisplayName("❌ Status: Cannot buy ticket with CLOSED status")
    void buyTicket_ClosedStatus() {
        testSeminar.setStatus(SeminarStatus.CLOSED);
        when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

        assertThrows(IllegalStateException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));
    }

    // ========================================
    // EDGE CASES & BOUNDARY TESTS
    // ========================================

    @Test
    @DisplayName("✅ Edge: Seminar with capacity = 1")
    void buyTicket_CapacityOne() {
        testSeminar.setMaxCapacity(1);
        testSeminar.setTicketsSold(0);

        when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
        when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
        when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
        when(ticketRepository.save(any(SeminarTicket.class))).thenAnswer(i -> i.getArgument(0));

        SeminarTicketResponse response = seminarService.buyTicket(SEMINAR_ID, BUYER_ID);

        assertNotNull(response);
    }

    @Test
    @DisplayName("✅ Edge: Seminar with very large capacity")
    void buyTicket_LargeCapacity() {
        testSeminar.setMaxCapacity(10000);

        when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
        when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
        when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
        when(ticketRepository.save(any(SeminarTicket.class))).thenAnswer(i -> i.getArgument(0));

        SeminarTicketResponse response = seminarService.buyTicket(SEMINAR_ID, BUYER_ID);

        assertNotNull(response);
    }

    @Test
    @DisplayName("✅ Edge: Seminar starting in exactly 1 minute")
    void buyTicket_StartingSoon() {
        testSeminar.setStartTime(LocalDateTime.now().plusMinutes(1));
        testSeminar.setEndTime(LocalDateTime.now().plusHours(2));

        when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
        when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
        when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
        when(ticketRepository.save(any(SeminarTicket.class))).thenAnswer(i -> i.getArgument(0));

        SeminarTicketResponse response = seminarService.buyTicket(SEMINAR_ID, BUYER_ID);

        assertNotNull(response);
    }

    @Test
    @DisplayName("❌ Edge: Seminar ending in exactly 1 second")
    void buyTicket_EndingSoon() {
        testSeminar.setEndTime(LocalDateTime.now().minusSeconds(1));
        when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

        assertThrows(IllegalStateException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));
    }

    @Test
    @DisplayName("✅ Edge: Seminar not found returns proper exception")
    void buyTicket_SeminarNotFound() {
        when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));
    }
}
