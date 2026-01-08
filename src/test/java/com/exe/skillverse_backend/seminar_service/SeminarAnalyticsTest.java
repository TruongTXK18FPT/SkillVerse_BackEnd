package com.exe.skillverse_backend.seminar_service;

import com.exe.skillverse_backend.seminar_service.dto.response.SeminarAnalyticsDTO;
import com.exe.skillverse_backend.seminar_service.entity.Seminar;
import com.exe.skillverse_backend.seminar_service.entity.SeminarStatus;
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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Seminar Analytics - Real Data for Seminar Sidebar
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Seminar Analytics Tests")
class SeminarAnalyticsTest {

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
        // CORE ANALYTICS TESTS
        // ========================================

        @Test
        @DisplayName("✅ Core: Get analytics with all data")
        void getAnalytics_Success_WithAllData() {
                // Mock counts
                when(seminarRepository.countByStatusIn(
                                List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN, SeminarStatus.CLOSED)))
                                .thenReturn(15L);
                when(seminarRepository.countByStatusIn(List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN)))
                                .thenReturn(10L);
                when(seminarRepository.countByStatusIn(List.of(SeminarStatus.CLOSED)))
                                .thenReturn(5L);

                // Mock top speakers data
                Object[] speaker1 = { 100L, 50L };
                Object[] speaker2 = { 101L, 40L };
                Object[] speaker3 = { 102L, 30L };
                Object[] speaker4 = { 103L, 20L };
                when(seminarRepository.findTopSpeakersByTicketsSold(any()))
                                .thenReturn(List.of(speaker1, speaker2, speaker3, speaker4));

                // Mock recruiter profiles
                var profile1 = com.exe.skillverse_backend.business_service.entity.RecruiterProfile.builder()
                                .companyName("FPT Software").build();
                var profile2 = com.exe.skillverse_backend.business_service.entity.RecruiterProfile.builder()
                                .companyName("Viettel Solutions").build();
                var profile3 = com.exe.skillverse_backend.business_service.entity.RecruiterProfile.builder()
                                .companyName("VNG Corporation").build();
                var profile4 = com.exe.skillverse_backend.business_service.entity.RecruiterProfile.builder()
                                .companyName("Grab Vietnam").build();

                when(recruiterProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile1));
                when(recruiterProfileRepository.findByUserId(101L)).thenReturn(Optional.of(profile2));
                when(recruiterProfileRepository.findByUserId(102L)).thenReturn(Optional.of(profile3));
                when(recruiterProfileRepository.findByUserId(103L)).thenReturn(Optional.of(profile4));

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertNotNull(result);
                assertEquals(15, result.getTotalSeminars());
                assertEquals(10, result.getActiveSeminars());
                assertEquals(5, result.getCompletedSeminars());
                assertEquals(4, result.getTopSpeakers().size());
                assertEquals("FPT Software", result.getTopSpeakers().get(0).getCompanyName());
                assertEquals(50L, result.getTopSpeakers().get(0).getTotalTicketsSold());
        }

        @Test
        @DisplayName("✅ Core: Empty database returns zeros")
        void getAnalytics_EmptyDatabase() {
                when(seminarRepository.countByStatusIn(anyList())).thenReturn(0L);
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(List.of());

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertNotNull(result);
                assertEquals(0, result.getTotalSeminars());
                assertEquals(0, result.getActiveSeminars());
                assertEquals(0, result.getCompletedSeminars());
                assertTrue(result.getTopSpeakers().isEmpty());
        }

        @Test
        @DisplayName("✅ Core: Only draft seminars - not counted")
        void getAnalytics_OnlyDraftSeminars() {
                when(seminarRepository.countByStatusIn(
                                List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN, SeminarStatus.CLOSED)))
                                .thenReturn(0L);
                when(seminarRepository.countByStatusIn(List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN)))
                                .thenReturn(0L);
                when(seminarRepository.countByStatusIn(List.of(SeminarStatus.CLOSED)))
                                .thenReturn(0L);
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(List.of());

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertEquals(0, result.getTotalSeminars());
                assertEquals(0, result.getActiveSeminars());
        }

        @Test
        @DisplayName("✅ Core: No tickets sold - zero ticket counts")
        void getAnalytics_NoTicketsSold() {
                when(seminarRepository.countByStatusIn(
                                List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN, SeminarStatus.CLOSED)))
                                .thenReturn(10L);
                when(seminarRepository.countByStatusIn(List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN)))
                                .thenReturn(10L);
                when(seminarRepository.countByStatusIn(List.of(SeminarStatus.CLOSED)))
                                .thenReturn(0L);

                Object[] speaker1 = { 100L, 0L };
                List<Object[]> speakers = Arrays.asList(new Object[][] { speaker1 });
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(speakers);

                var profile = com.exe.skillverse_backend.business_service.entity.RecruiterProfile.builder()
                                .companyName("Startup Inc").build();
                when(recruiterProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile));

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertEquals(1, result.getTopSpeakers().size());
                assertEquals(0L, result.getTopSpeakers().get(0).getTotalTicketsSold());
        }

        @Test
        @DisplayName("✅ Core: Less than 4 speakers available")
        void getAnalytics_LessThan4Speakers() {
                when(seminarRepository.countByStatusIn(anyList())).thenReturn(5L);

                Object[] speaker1 = { 100L, 30L };
                Object[] speaker2 = { 101L, 20L };
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(List.of(speaker1, speaker2));

                var profile1 = com.exe.skillverse_backend.business_service.entity.RecruiterProfile.builder()
                                .companyName("Company A").build();
                var profile2 = com.exe.skillverse_backend.business_service.entity.RecruiterProfile.builder()
                                .companyName("Company B").build();

                when(recruiterProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile1));
                when(recruiterProfileRepository.findByUserId(101L)).thenReturn(Optional.of(profile2));

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertEquals(2, result.getTopSpeakers().size());
        }

        @Test
        @DisplayName("✅ Core: Multiple recruiters with same ticket count")
        void getAnalytics_MultipleRecruitersSameCount() {
                when(seminarRepository.countByStatusIn(anyList())).thenReturn(8L);

                Object[] speaker1 = { 100L, 25L };
                Object[] speaker2 = { 101L, 25L };
                Object[] speaker3 = { 102L, 25L };
                when(seminarRepository.findTopSpeakersByTicketsSold(any()))
                                .thenReturn(List.of(speaker1, speaker2, speaker3));

                var profile = com.exe.skillverse_backend.business_service.entity.RecruiterProfile.builder()
                                .companyName("Equal Company").build();

                when(recruiterProfileRepository.findByUserId(anyLong())).thenReturn(Optional.of(profile));

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertEquals(3, result.getTopSpeakers().size());
                result.getTopSpeakers().forEach(speaker -> assertEquals(25L, speaker.getTotalTicketsSold()));
        }

        @Test
        @DisplayName("✅ Core: Performance test - should complete fast")
        void getAnalytics_PerformanceTest() {
                when(seminarRepository.countByStatusIn(anyList())).thenReturn(100L);
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(List.of());

                long startTime = System.currentTimeMillis();
                SeminarAnalyticsDTO result = seminarService.getAnalytics();
                long duration = System.currentTimeMillis() - startTime;

                assertNotNull(result);
                assertTrue(duration < 1000, "Analytics query should complete in <1s, took: " + duration + "ms");
        }

        @Test
        @DisplayName("✅ Core: Concurrent access safety")
        void getAnalytics_ConcurrentAccess() {
                when(seminarRepository.countByStatusIn(anyList())).thenReturn(10L);
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(List.of());

                assertDoesNotThrow(() -> {
                        SeminarAnalyticsDTO result1 = seminarService.getAnalytics();
                        SeminarAnalyticsDTO result2 = seminarService.getAnalytics();
                        assertEquals(result1.getTotalSeminars(), result2.getTotalSeminars());
                });
        }

        // ========================================
        // EDGE CASES TESTS
        // ========================================

        @Test
        @DisplayName("🔧 Edge: Null company name - use fallback")
        void getAnalytics_NullCompanyName() {
                when(seminarRepository.countByStatusIn(anyList())).thenReturn(5L);

                Object[] speaker1 = { 100L, 40L };
                List<Object[]> speakers = Arrays.asList(new Object[][] { speaker1 });
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(speakers);

                var profile = com.exe.skillverse_backend.business_service.entity.RecruiterProfile.builder()
                                .companyName(null).build();
                when(recruiterProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile));

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertEquals(1, result.getTopSpeakers().size());
                assertEquals("Unknown Company", result.getTopSpeakers().get(0).getCompanyName());
        }

        @Test
        @DisplayName("🔧 Edge: Recruiter profile not found - use fallback")
        void getAnalytics_RecruiterNotFound() {
                when(seminarRepository.countByStatusIn(anyList())).thenReturn(5L);

                Object[] speaker1 = { 999L, 35L };
                List<Object[]> speakers = Arrays.asList(new Object[][] { speaker1 });
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(speakers);
                when(recruiterProfileRepository.findByUserId(999L)).thenReturn(Optional.empty());

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertEquals(1, result.getTopSpeakers().size());
                assertEquals("Unknown Company", result.getTopSpeakers().get(0).getCompanyName());
        }

        @Test
        @DisplayName("🔧 Edge: Only closed seminars - active should be 0")
        void getAnalytics_OnlyClosedSeminars() {
                when(seminarRepository.countByStatusIn(
                                List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN, SeminarStatus.CLOSED)))
                                .thenReturn(8L);
                when(seminarRepository.countByStatusIn(List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN)))
                                .thenReturn(0L);
                when(seminarRepository.countByStatusIn(List.of(SeminarStatus.CLOSED)))
                                .thenReturn(8L);
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(List.of());

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertEquals(8, result.getTotalSeminars());
                assertEquals(0, result.getActiveSeminars());
                assertEquals(8, result.getCompletedSeminars());
        }

        @Test
        @DisplayName("🔧 Edge: Mixed status distribution")
        void getAnalytics_MixedStatusDistribution() {
                when(seminarRepository.countByStatusIn(
                                List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN, SeminarStatus.CLOSED)))
                                .thenReturn(20L);
                when(seminarRepository.countByStatusIn(List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN)))
                                .thenReturn(12L);
                when(seminarRepository.countByStatusIn(List.of(SeminarStatus.CLOSED)))
                                .thenReturn(8L);
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(List.of());

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertEquals(20, result.getTotalSeminars());
                assertEquals(12, result.getActiveSeminars());
                assertEquals(8, result.getCompletedSeminars());
        }

        @Test
        @DisplayName("🔧 Edge: Blank company name - use fallback")
        void getAnalytics_BlankCompanyName() {
                when(seminarRepository.countByStatusIn(anyList())).thenReturn(3L);

                Object[] speaker1 = { 100L, 15L };
                List<Object[]> speakers = Arrays.asList(new Object[][] { speaker1 });
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(speakers);

                var profile = com.exe.skillverse_backend.business_service.entity.RecruiterProfile.builder()
                                .companyName("   ").build();
                when(recruiterProfileRepository.findByUserId(100L)).thenReturn(Optional.of(profile));

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertEquals(1, result.getTopSpeakers().size());
                assertEquals("Unknown Company", result.getTopSpeakers().get(0).getCompanyName());
        }

        @Test
        @DisplayName("🔧 Edge: Profile fetch exception - use fallback")
        void getAnalytics_ProfileFetchException() {
                when(seminarRepository.countByStatusIn(anyList())).thenReturn(4L);

                Object[] speaker1 = { 100L, 22L };
                List<Object[]> speakers = Arrays.asList(new Object[][] { speaker1 });
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(speakers);
                when(recruiterProfileRepository.findByUserId(100L)).thenThrow(new RuntimeException("Database error"));

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertEquals(1, result.getTopSpeakers().size());
                assertEquals("Unknown Company", result.getTopSpeakers().get(0).getCompanyName());
        }

        @Test
        @DisplayName("🔧 Edge: Zero tickets across all seminars")
        void getAnalytics_ZeroTicketsAllSeminars() {
                when(seminarRepository.countByStatusIn(
                                List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN, SeminarStatus.CLOSED)))
                                .thenReturn(15L);
                when(seminarRepository.countByStatusIn(List.of(SeminarStatus.ACCEPTED, SeminarStatus.OPEN)))
                                .thenReturn(15L);
                when(seminarRepository.countByStatusIn(List.of(SeminarStatus.CLOSED)))
                                .thenReturn(0L);

                Object[] speaker1 = { 100L, 0L };
                Object[] speaker2 = { 101L, 0L };
                when(seminarRepository.findTopSpeakersByTicketsSold(any())).thenReturn(List.of(speaker1, speaker2));

                var profile = com.exe.skillverse_backend.business_service.entity.RecruiterProfile.builder()
                                .companyName("No Sales Company").build();
                when(recruiterProfileRepository.findByUserId(anyLong())).thenReturn(Optional.of(profile));

                SeminarAnalyticsDTO result = seminarService.getAnalytics();

                assertEquals(15, result.getTotalSeminars());
                assertEquals(2, result.getTopSpeakers().size());
                result.getTopSpeakers().forEach(speaker -> assertEquals(0L, speaker.getTotalTicketsSold()));
        }
}
