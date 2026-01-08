package com.exe.skillverse_backend.seminar_service;

import com.exe.skillverse_backend.seminar_service.dto.request.SeminarCreateRequest;
import com.exe.skillverse_backend.seminar_service.dto.request.SeminarUpdateRequest;
import com.exe.skillverse_backend.seminar_service.dto.response.SeminarResponse;
import com.exe.skillverse_backend.seminar_service.entity.Seminar;
import com.exe.skillverse_backend.seminar_service.entity.SeminarStatus;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Seminar CRUD operations - Creation and Update
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Seminar CRUD Operations Tests")
class SeminarCrudTest {

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
    private static final Long SEMINAR_ID = 1L;
    private static final BigDecimal PRICE = new BigDecimal("100000");
    private static final LocalDateTime FUTURE_START = LocalDateTime.now().plusDays(7);
    private static final LocalDateTime FUTURE_END = LocalDateTime.now().plusDays(8);

    private Seminar testSeminar;
    private SeminarCreateRequest createRequest;

    @BeforeEach
    void setUp() {
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
    // SEMINAR CREATION TESTS
    // ========================================

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

        doThrow(new ValidationException("Dữ liệu không hợp lệ", List.of("Giá vé không được âm")))
                .when(seminarValidator).validateCreateRequest(any(SeminarCreateRequest.class));

        assertThrows(ValidationException.class,
                () -> seminarService.createSeminar(createRequest, null, RECRUITER_ID));
    }

    // ========================================
    // SEMINAR UPDATE TESTS
    // ========================================

    @Test
    @DisplayName("✅ Happy: Update seminar in DRAFT status")
    void updateSeminar_Success() {
        testSeminar.setStatus(SeminarStatus.DRAFT);
        SeminarUpdateRequest updateRequest = new SeminarUpdateRequest();
        updateRequest.setTitle("Updated Title");
        updateRequest.setMaxCapacity(200);

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
        SeminarUpdateRequest updateRequest = new SeminarUpdateRequest();
        updateRequest.setTitle("Updated Title");

        when(seminarRepository.findById(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

        assertThrows(IllegalStateException.class,
                () -> seminarService.updateSeminar(SEMINAR_ID, updateRequest, RECRUITER_ID));
    }

    @Test
    @DisplayName("❌ Unhappy: Update seminar by non-owner")
    void updateSeminar_Unauthorized() {
        testSeminar.setStatus(SeminarStatus.DRAFT);
        SeminarUpdateRequest updateRequest = new SeminarUpdateRequest();
        updateRequest.setTitle("Updated Title");

        when(seminarRepository.findById(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

        assertThrows(IllegalArgumentException.class,
                () -> seminarService.updateSeminar(SEMINAR_ID, updateRequest, "999"));
    }

    @Test
    @DisplayName("❌ Unhappy: Reduce capacity below tickets sold")
    void updateSeminar_CapacityBelowSold() {
        testSeminar.setStatus(SeminarStatus.DRAFT);
        testSeminar.setTicketsSold(50);
        SeminarUpdateRequest updateRequest = new SeminarUpdateRequest();
        updateRequest.setMaxCapacity(30);

        when(seminarRepository.findById(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));

        assertThrows(IllegalStateException.class,
                () -> seminarService.updateSeminar(SEMINAR_ID, updateRequest, RECRUITER_ID));
    }

    @Test
    @DisplayName("❌ Unhappy: Change price when tickets already sold")
    void updateSeminar_ChangePriceWithSoldTickets() {
        testSeminar.setStatus(SeminarStatus.DRAFT);
        testSeminar.setTicketsSold(10);
        SeminarUpdateRequest updateRequest = new SeminarUpdateRequest();
        updateRequest.setPrice(new BigDecimal("200000"));

        when(seminarRepository.findById(SEMINAR_ID)).thenReturn(Optional.of(testSeminar));
        when(ticketRepository.existsBySeminar_Id(SEMINAR_ID)).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> seminarService.updateSeminar(SEMINAR_ID, updateRequest, RECRUITER_ID));
    }
}
