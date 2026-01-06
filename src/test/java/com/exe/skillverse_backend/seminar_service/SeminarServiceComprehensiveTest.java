package com.exe.skillverse_backend.seminar_service;

import com.exe.skillverse_backend.seminar_service.dto.request.SeminarCreateRequest;
import com.exe.skillverse_backend.seminar_service.dto.request.SeminarUpdateRequest;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarResponse;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarTicketResponse;
import com.exe.skillverse_backend.seminar_service.entity.Seminar;
import com.exe.skillverse_backend.seminar_service.entity.SeminarStatus;
import com.exe.skillverse_backend.seminar_service.entity.SeminarTicket;
import com.exe.skillverse_backend.seminar_service.repository.SeminarRepository;
import com.exe.skillverse_backend.seminar_service.repository.SeminarTicketRepository;
import com.exe.skillverse_backend.seminar_service.service.impl.SeminarServiceImpl;
import com.exe.skillverse_backend.seminar_service.validation.SeminarValidator;
import com.exe.skillverse_backend.shared.exception.ValidationException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for Seminar Service covering all business scenarios.
 * 
 * Test Coverage:
 * ✅ Happy Path: Normal seminar creation, update, ticket purchase
 * ✅ Validation: Input validation, business rule enforcement
 * ✅ Security: Self-purchase prevention, access control
 * ✅ Capacity: Limit enforcement, sold-out scenarios
 * ✅ Race Conditions: Concurrent purchase prevention
 * ✅ Edge Cases: Null values, boundary conditions
 * ✅ Error Handling: Exception scenarios
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Comprehensive Seminar Service Tests")
class SeminarServiceComprehensiveTest {

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

    // Test constants
    private static final String RECRUITER_ID = "100";
    private static final String BUYER_ID = "200";
    private static final Long SEMINAR_ID = 1L;
    private static final BigDecimal PRICE = new BigDecimal("100000");
    private static final LocalDateTime FUTURE_START = LocalDateTime.now().plusDays(7);
    private static final LocalDateTime FUTURE_END = LocalDateTime.now().plusDays(8);

    private Seminar testSeminar;
    private SeminarCreateRequest createRequest;

    @BeforeEach
    void setUp() {
        // Setup mock behavior for validator - by default, do nothing (allow valid
        // requests)
        // Use lenient() because some tests don't call these methods (e.g., entity
        // tests, buyTicket tests)
        lenient().doNothing().when(seminarValidator).validateCreateRequest(any(SeminarCreateRequest.class));
        lenient().doNothing().when(seminarValidator).validateUpdateRequest(any(SeminarUpdateRequest.class), anyInt());

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

        createRequest = new SeminarCreateRequest();
        createRequest.setTitle("New Seminar");
        createRequest.setDescription("Description");
        createRequest.setMeetingLink("https://meet.test.com");
        createRequest.setStartTime(FUTURE_START);
        createRequest.setEndTime(FUTURE_END);
        createRequest.setPrice(PRICE);
        createRequest.setMaxCapacity(100);
    }

    // ========================================
    // 1. SEMINAR CREATION TESTS
    // ========================================

    @Nested
    @DisplayName("Seminar Creation Tests")
    class SeminarCreationTests {

        @Test
        @DisplayName("✅ Happy: Create seminar with all valid fields")
        void createSeminar_Success() {
            when(seminarRepository.save(any(Seminar.class))).thenAnswer(i -> {
                Seminar s = i.getArgument(0);
                s.setId(SEMINAR_ID);
                return s;
            });

            SeminarResponse response = seminarService.createSeminar(createRequest, null, RECRUITER_ID);

            assertNotNull(response);
            assertEquals("New Seminar", response.getTitle());
            assertEquals(100, response.getMaxCapacity());
            assertEquals(0, response.getTicketsSold());
            assertFalse(response.isSoldOut());
            verify(seminarRepository).save(any(Seminar.class));
        }

        @Test
        @DisplayName("✅ Happy: Create seminar with unlimited capacity (null)")
        void createSeminar_UnlimitedCapacity() {
            createRequest.setMaxCapacity(null);

            when(seminarRepository.save(any(Seminar.class))).thenAnswer(i -> {
                Seminar s = i.getArgument(0);
                s.setId(SEMINAR_ID);
                return s;
            });

            SeminarResponse response = seminarService.createSeminar(createRequest, null, RECRUITER_ID);

            assertNull(response.getMaxCapacity());
            assertNull(response.getRemainingCapacity());
            assertFalse(response.isSoldOut());
        }

        @Test
        @DisplayName("✅ Happy: Create seminar with unlimited capacity (0 converts to null)")
        void createSeminar_ZeroCapacityConvertsToNull() {
            createRequest.setMaxCapacity(0);

            when(seminarRepository.save(any(Seminar.class))).thenAnswer(i -> {
                Seminar s = i.getArgument(0);
                s.setId(SEMINAR_ID);
                return s;
            });

            SeminarResponse response = seminarService.createSeminar(createRequest, null, RECRUITER_ID);

            assertNull(response.getMaxCapacity());
        }

        @Test
        @DisplayName("✅ Happy: Create free seminar (price = 0)")
        void createSeminar_FreeSeminar() {
            createRequest.setPrice(BigDecimal.ZERO);

            when(seminarRepository.save(any(Seminar.class))).thenAnswer(i -> {
                Seminar s = i.getArgument(0);
                s.setId(SEMINAR_ID);
                return s;
            });

            SeminarResponse response = seminarService.createSeminar(createRequest, null, RECRUITER_ID);

            assertEquals(BigDecimal.ZERO, response.getPrice());
        }

        @Test
        @DisplayName("❌ Unhappy: Create seminar with endTime before startTime")
        void createSeminar_InvalidTimeRange() {
            createRequest.setStartTime(FUTURE_END);
            createRequest.setEndTime(FUTURE_START);

            // Configure validator to throw ValidationException for invalid time range
            doThrow(new ValidationException("Dữ liệu không hợp lệ",
                    List.of("Thời gian kết thúc phải sau thời gian bắt đầu")))
                    .when(seminarValidator).validateCreateRequest(any(SeminarCreateRequest.class));

            assertThrows(ValidationException.class,
                    () -> seminarService.createSeminar(createRequest, null, RECRUITER_ID));
        }

        @Test
        @DisplayName("❌ Unhappy: Create seminar with negative price")
        void createSeminar_NegativePrice() {
            createRequest.setPrice(new BigDecimal("-100"));

            // Configure validator to throw ValidationException for negative price
            doThrow(new ValidationException("Dữ liệu không hợp lệ", List.of("Giá vé không được âm")))
                    .when(seminarValidator).validateCreateRequest(any(SeminarCreateRequest.class));

            assertThrows(ValidationException.class,
                    () -> seminarService.createSeminar(createRequest, null, RECRUITER_ID));
        }
    }

    // ========================================
    // 2. SEMINAR UPDATE TESTS
    // ========================================

    @Nested
    @DisplayName("Seminar Update Tests")
    class SeminarUpdateTests {

        private SeminarUpdateRequest updateRequest;

        @BeforeEach
        void setupUpdate() {
            testSeminar.setStatus(SeminarStatus.DRAFT);
            updateRequest = new SeminarUpdateRequest();
            updateRequest.setTitle("Updated Title");
            updateRequest.setMaxCapacity(200);
        }

        @Test
        @DisplayName("✅ Happy: Update seminar in DRAFT status")
        void updateSeminar_Success() {
            when(seminarRepository.findById(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(seminarRepository.save(any(Seminar.class))).thenReturn(testSeminar);

            SeminarResponse response = seminarService.updateSeminar(SEMINAR_ID, updateRequest, RECRUITER_ID);

            assertEquals("Updated Title", testSeminar.getTitle());
            verify(seminarRepository).save(testSeminar);
        }

        @Test
        @DisplayName("❌ Unhappy: Update seminar not in DRAFT status")
        void updateSeminar_NotDraftStatus() {
            testSeminar.setStatus(SeminarStatus.ACCEPTED);
            when(seminarRepository.findById(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

            assertThrows(IllegalStateException.class,
                    () -> seminarService.updateSeminar(SEMINAR_ID, updateRequest, RECRUITER_ID));
        }

        @Test
        @DisplayName("❌ Unhappy: Update seminar by non-owner")
        void updateSeminar_Unauthorized() {
            when(seminarRepository.findById(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

            assertThrows(IllegalArgumentException.class,
                    () -> seminarService.updateSeminar(SEMINAR_ID, updateRequest, "999"));
        }

        @Test
        @DisplayName("❌ Unhappy: Reduce capacity below tickets sold")
        void updateSeminar_CapacityBelowSold() {
            testSeminar.setTicketsSold(50);
            updateRequest.setMaxCapacity(30);

            when(seminarRepository.findById(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

            assertThrows(IllegalStateException.class,
                    () -> seminarService.updateSeminar(SEMINAR_ID, updateRequest, RECRUITER_ID));
        }

        @Test
        @DisplayName("❌ Unhappy: Change price when tickets already sold")
        void updateSeminar_ChangePriceWithSoldTickets() {
            testSeminar.setTicketsSold(10);
            updateRequest.setPrice(new BigDecimal("200000"));

            when(seminarRepository.findById(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.existsBySeminar_Id(SEMINAR_ID)).thenReturn(true);

            assertThrows(IllegalStateException.class,
                    () -> seminarService.updateSeminar(SEMINAR_ID, updateRequest, RECRUITER_ID));
        }
    }

    // ========================================
    // 3. TICKET PURCHASE TESTS - HAPPY PATH
    // ========================================

    @Nested
    @DisplayName("Ticket Purchase - Happy Path")
    class TicketPurchaseHappyTests {

        @Test
        @DisplayName("✅ Happy: Buy ticket with capacity available")
        void buyTicket_Success() {
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
            when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
            when(ticketRepository.save(any(SeminarTicket.class))).thenAnswer(i -> i.getArgument(0));

            SeminarTicketResponse response = seminarService.buyTicket(SEMINAR_ID, BUYER_ID);

            assertNotNull(response);
            verify(walletService).deductCash(eq(Long.parseLong(BUYER_ID)), eq(PRICE), anyString(), anyString(),
                    anyString());
            verify(walletService).payRecruiterForSeminar(eq(Long.parseLong(RECRUITER_ID)), any(BigDecimal.class),
                    eq(SEMINAR_ID));
            verify(ticketRepository).save(any(SeminarTicket.class));
        }

        @Test
        @DisplayName("✅ Happy: Buy ticket for free seminar (no payment)")
        void buyTicket_FreeSeminar() {
            testSeminar.setPrice(BigDecimal.ZERO);
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
            when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
            when(ticketRepository.save(any(SeminarTicket.class))).thenAnswer(i -> i.getArgument(0));

            SeminarTicketResponse response = seminarService.buyTicket(SEMINAR_ID, BUYER_ID);

            assertNotNull(response);
            verify(walletService, never()).deductCash(anyLong(), any(), anyString(), anyString(), anyString());
            verify(ticketRepository).save(any(SeminarTicket.class));
        }

        @Test
        @DisplayName("✅ Happy: Buy ticket for unlimited capacity seminar")
        void buyTicket_UnlimitedCapacity() {
            testSeminar.setMaxCapacity(null);
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
            when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
            when(ticketRepository.save(any(SeminarTicket.class))).thenAnswer(i -> i.getArgument(0));

            SeminarTicketResponse response = seminarService.buyTicket(SEMINAR_ID, BUYER_ID);

            assertNotNull(response);
        }
    }

    // ========================================
    // 4. TICKET PURCHASE TESTS - SECURITY
    // ========================================

    @Nested
    @DisplayName("Ticket Purchase - Security Tests")
    class TicketPurchaseSecurityTests {

        @Test
        @DisplayName("❌ Security: Prevent self-purchase")
        void buyTicket_PreventSelfPurchase() {
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

            assertThrows(IllegalArgumentException.class, () -> seminarService.buyTicket(SEMINAR_ID, RECRUITER_ID));

            verify(walletService, never()).deductCash(anyLong(), any(), anyString(), anyString(), anyString());
            verify(ticketRepository, never()).save(any());
        }

        @Test
        @DisplayName("❌ Security: Cannot buy ticket for non-ACCEPTED/OPEN seminar")
        void buyTicket_InvalidStatus() {
            testSeminar.setStatus(SeminarStatus.DRAFT);
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

            assertThrows(IllegalStateException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));
        }

        @Test
        @DisplayName("❌ Security: Cannot buy ticket for ended seminar")
        void buyTicket_SeminarEnded() {
            testSeminar.setEndTime(LocalDateTime.now().minusDays(1));
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

            assertThrows(IllegalStateException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));
        }

        @Test
        @DisplayName("❌ Security: Cannot buy duplicate ticket (application level)")
        void buyTicket_DuplicateAtApplicationLevel() {
            SeminarTicket existingTicket = new SeminarTicket();
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID))
                    .thenReturn(Optional.of(existingTicket));

            assertThrows(IllegalStateException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));

            verify(seminarRepository, never()).incrementTicketsSoldIfAvailable(any());
        }

        @Test
        @DisplayName("❌ Security: Cannot buy duplicate ticket (database level)")
        void buyTicket_DuplicateAtDatabaseLevel() {
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
            when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
            when(ticketRepository.save(any(SeminarTicket.class)))
                    .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

            assertThrows(IllegalStateException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));

            verify(seminarRepository).decrementTicketsSold(SEMINAR_ID);
        }
    }

    // ========================================
    // 5. CAPACITY MANAGEMENT TESTS
    // ========================================

    @Nested
    @DisplayName("Capacity Management Tests")
    class CapacityManagementTests {

        @Test
        @DisplayName("❌ Capacity: Cannot buy ticket when sold out")
        void buyTicket_SoldOut() {
            testSeminar.setTicketsSold(50); // maxCapacity = 50
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());

            assertThrows(IllegalStateException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));

            verify(seminarRepository, never()).incrementTicketsSoldIfAvailable(any());
        }

        @Test
        @DisplayName("❌ Capacity: Atomic capacity check fails (DB returns 0)")
        void buyTicket_AtomicCapacityCheckFails() {
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
            when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(0);

            assertThrows(IllegalStateException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));

            verify(walletService, never()).deductCash(anyLong(), any(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("✅ Capacity: Buy last available ticket")
        void buyTicket_LastTicket() {
            testSeminar.setTicketsSold(49); // maxCapacity = 50
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
            when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
            when(ticketRepository.save(any(SeminarTicket.class))).thenAnswer(i -> i.getArgument(0));

            SeminarTicketResponse response = seminarService.buyTicket(SEMINAR_ID, BUYER_ID);

            assertNotNull(response);
        }

        @Test
        @DisplayName("✅ Entity: isSoldOut() returns true when at capacity")
        void entityTest_IsSoldOut() {
            testSeminar.setTicketsSold(50);
            assertTrue(testSeminar.isSoldOut());
        }

        @Test
        @DisplayName("✅ Entity: isSoldOut() returns false for unlimited capacity")
        void entityTest_UnlimitedNotSoldOut() {
            testSeminar.setMaxCapacity(null);
            testSeminar.setTicketsSold(1000);
            assertFalse(testSeminar.isSoldOut());
        }

        @Test
        @DisplayName("✅ Entity: getRemainingCapacity() calculates correctly")
        void entityTest_RemainingCapacity() {
            testSeminar.setTicketsSold(30);
            assertEquals(20, testSeminar.getRemainingCapacity());
        }

        @Test
        @DisplayName("✅ Entity: getRemainingCapacity() returns null for unlimited")
        void entityTest_UnlimitedRemainingCapacity() {
            testSeminar.setMaxCapacity(null);
            assertNull(testSeminar.getRemainingCapacity());
        }
    }

    // ========================================
    // 6. RACE CONDITION PREVENTION TESTS
    // ========================================

    @Nested
    @DisplayName("Race Condition Prevention Tests")
    class RaceConditionTests {

        @Test
        @DisplayName("✅ Race: Pessimistic locking used")
        void buyTicket_UsesPessimisticLock() {
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
            when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
            when(ticketRepository.save(any(SeminarTicket.class))).thenAnswer(i -> i.getArgument(0));

            seminarService.buyTicket(SEMINAR_ID, BUYER_ID);

            verify(seminarRepository).findByIdWithLock(SEMINAR_ID);
            verify(seminarRepository, never()).findById(SEMINAR_ID); // Not using regular findById
        }

        @Test
        @DisplayName("✅ Race: Atomic increment used for capacity")
        void buyTicket_UsesAtomicIncrement() {
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
            when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
            when(ticketRepository.save(any(SeminarTicket.class))).thenAnswer(i -> i.getArgument(0));

            seminarService.buyTicket(SEMINAR_ID, BUYER_ID);

            verify(seminarRepository).incrementTicketsSoldIfAvailable(SEMINAR_ID);
        }

        @Test
        @DisplayName("✅ Race: Rollback capacity on payment failure")
        void buyTicket_RollbackOnPaymentFailure() {
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
            when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
            doThrow(new RuntimeException("Payment failed"))
                    .when(walletService).deductCash(anyLong(), any(), anyString(), anyString(), anyString());

            assertThrows(RuntimeException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));

            verify(seminarRepository).decrementTicketsSold(SEMINAR_ID);
        }

        @Test
        @DisplayName("✅ Race: Rollback capacity on constraint violation")
        void buyTicket_RollbackOnConstraintViolation() {
            when(seminarRepository.findByIdWithLock(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
            when(ticketRepository.findByUserIdAndSeminarId(BUYER_ID, SEMINAR_ID)).thenReturn(Optional.empty());
            when(seminarRepository.incrementTicketsSoldIfAvailable(SEMINAR_ID)).thenReturn(1);
            when(ticketRepository.save(any(SeminarTicket.class)))
                    .thenThrow(new DataIntegrityViolationException("Unique constraint"));

            assertThrows(IllegalStateException.class, () -> seminarService.buyTicket(SEMINAR_ID, BUYER_ID));

            verify(seminarRepository).decrementTicketsSold(SEMINAR_ID);
        }
    }

    // ========================================
    // 7. EDGE CASES & BOUNDARY TESTS
    // ========================================

    @Nested
    @DisplayName("Edge Cases & Boundary Tests")
    class EdgeCaseTests {

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

    // ========================================
    // 8. SEMINAR STATUS TESTS
    // ========================================

    @Nested
    @DisplayName("Seminar Status Tests")
    class SeminarStatusTests {

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
    }
}
