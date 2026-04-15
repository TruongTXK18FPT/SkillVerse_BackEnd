package com.exe.skillverse_backend.study_service.service;

import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.premium_service.repository.UserSubscriptionRepository;
import com.exe.skillverse_backend.study_service.dto.request.CheckScheduleHealthRequest;
import com.exe.skillverse_backend.study_service.dto.request.GenerateScheduleRequest;
import com.exe.skillverse_backend.study_service.dto.response.ScheduleHealthReport;
import com.exe.skillverse_backend.study_service.dto.response.StudySessionResponse;
import com.exe.skillverse_backend.study_service.entity.StudySessionStatus;
import com.exe.skillverse_backend.study_service.repository.StudySessionRepository;
import com.exe.skillverse_backend.study_service.service.impl.AiStudySupportServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class AiStudySupportServiceImplTest {

    @Mock
    private StudySessionRepository studySessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private com.exe.skillverse_backend.course_service.repository.ModuleRepository moduleRepository;

    @Mock
    private com.exe.skillverse_backend.course_service.repository.LessonRepository lessonRepository;

    private AiStudySupportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiStudySupportServiceImpl(
                studySessionRepository,
                userRepository,
                userSubscriptionRepository,
                moduleRepository,
                lessonRepository,
                new ObjectMapper());
    }

    @Test
    @DisplayName("generateSchedule should block late-night sessions until the user confirms them")
    void generateSchedule_ShouldBlockLateNightSessionsUntilUserConfirmsThem() {
        AiStudySupportServiceImpl spy = Mockito.spy(service);
        LocalDateTime futureLateNight = LocalDateTime.now().plusDays(1).withHour(23).withMinute(30).withSecond(0).withNano(0);
        doReturn(List.of(StudySessionResponse.builder()
                .title("Night study")
                .description("Late night")
                .startTime(futureLateNight)
                .endTime(futureLateNight.plusMinutes(60))
                .status(StudySessionStatus.SCHEDULED)
                .build()))
                .when(spy).generateProposedSchedule(any(Long.class), any(GenerateScheduleRequest.class));

        GenerateScheduleRequest request = new GenerateScheduleRequest();
        request.setTimezone("Asia/Ho_Chi_Minh");
        request.setDurationMinutes(60);
        request.setAllowLateNight(true);
        request.setConfirmLateNight(false);
        request.setDeadline(LocalDate.now().plusDays(7));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> spy.generateSchedule(1L, request));

        assertTrue(exception.getMessage().contains("khuya"));
    }

    @Test
    @DisplayName("checkScheduleHealth should flag invalid windows, short breaks and daily overload")
    void checkScheduleHealth_ShouldFlagInvalidWindowsShortBreaksAndDailyOverload() {
        CheckScheduleHealthRequest request = new CheckScheduleHealthRequest();
        LocalDateTime day = LocalDate.now().plusDays(1).atStartOfDay();
        request.setSessions(new ArrayList<>(List.of(
                session("Early session", day.withHour(5).withMinute(30), day.withHour(6).withMinute(30)),
                session("Packed session", day.withHour(6).withMinute(35), day.withHour(8).withMinute(35)))));
        request.setTimezone("Asia/Ho_Chi_Minh");
        request.setEarliestStartLocalTime("06:00");
        request.setLatestEndLocalTime("22:00");
        request.setBreakMinutesBetweenSessions(15);
        request.setMaxDailyStudyMinutes(120);
        request.setStudyPreference("morning");
        request.setIdealFocusWindows(List.of("06:00-09:00"));

        ScheduleHealthReport report = service.checkScheduleHealth(request);

        assertFalse(report.isHealthy());
        assertFalse(report.getErrors().isEmpty());
        assertFalse(report.getWarnings().isEmpty());
        assertTrue(report.getSessionScores().size() == 2);
    }

    @Test
    @DisplayName("suggestHealthyAdjustments should move sessions into allowed windows")
    void suggestHealthyAdjustments_ShouldMoveSessionsIntoAllowedWindows() {
        CheckScheduleHealthRequest request = new CheckScheduleHealthRequest();
        LocalDateTime day = LocalDate.now().plusDays(1).atStartOfDay();
        request.setSessions(List.of(
                session("Too early", day.withHour(5).withMinute(0), day.withHour(6).withMinute(0)),
                session("Too late", day.withHour(21).withMinute(30), day.withHour(22).withMinute(30))));
        request.setTimezone("Asia/Ho_Chi_Minh");
        request.setEarliestStartLocalTime("06:00");
        request.setLatestEndLocalTime("22:00");
        request.setBreakMinutesBetweenSessions(15);
        request.setMaxDailyStudyMinutes(180);
        request.setStudyPreference("custom");
        request.setIdealFocusWindows(List.of("06:00-10:00"));

        ScheduleHealthReport report = service.suggestHealthyAdjustments(request);

        assertFalse(report.getSuggestions().isEmpty());
        assertTrue(report.getAdjustedSessions().get(0).getStartTime().getHour() >= 6);
        assertTrue(report.getAdjustedSessions().stream().allMatch(s -> s.getEndTime() != null));
    }

    // ─── normalizeSessions timezone edge-case tests ───────────────────────────────────

    private StudySessionResponse session(String title, LocalDateTime start, LocalDateTime end) {
        return StudySessionResponse.builder()
                .title(title)
                .startTime(start)
                .endTime(end)
                .status(StudySessionStatus.SCHEDULED)
                .build();
    }

    // Helper: invoke normalizeSessions via reflection since it's private
    private List<StudySessionResponse> normalize(
            List<StudySessionResponse> sessions,
            int durationMinutes,
            GenerateScheduleRequest request) {
        try {
            var method = AiStudySupportServiceImpl.class.getDeclaredMethod(
                    "normalizeSessions",
                    List.class, int.class, ZoneId.class, GenerateScheduleRequest.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<StudySessionResponse> result = (List<StudySessionResponse>) method.invoke(
                    service, sessions, durationMinutes, ZoneId.of("Asia/Ho_Chi_Minh"), request);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private GenerateScheduleRequest makeRequest() {
        GenerateScheduleRequest req = new GenerateScheduleRequest();
        req.setDurationMinutes(60);
        req.setStudyPreference("afternoon");
        req.setAvoidLateNight(false);
        req.setAllowLateNight(false);
        return req;
    }

    private LocalDate stableBaseDate() {
        // Use a near-future base date so normalization assertions do not depend on current wall-clock time.
        return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1);
    }

    @Test
    @DisplayName("TZ-3: AI returns past date → should shift to baseDate, keep time")
    void normalize_TZ3_pastDate_shiftsToBaseDate() {
        GenerateScheduleRequest req = makeRequest();
        LocalDate baseDate = stableBaseDate();
        req.setStartDate(baseDate);

        // AI mistakenly placed session on a date before baseDate.
        LocalDateTime aiTime = LocalDateTime.of(baseDate.minusDays(7), LocalTime.of(10, 0));
        List<StudySessionResponse> input = List.of(session("Past date", aiTime, aiTime.plusHours(1)));

        List<StudySessionResponse> result = normalize(input, 60, req);

        assertTrue(result.get(0).getStartTime().toLocalDate().equals(baseDate),
            "Date should be shifted to baseDate, got: " + result.get(0).getStartTime());
        assertTrue(result.get(0).getStartTime().getHour() == 10,
                "Hour should be preserved as 10");
    }

    @Test
    @DisplayName("TZ-4: AI returns hour < 6 → should replace with preferredStart")
    void normalize_TZ4_earlyHour_replacesWithPreferredStart() {
        GenerateScheduleRequest req = makeRequest();
        LocalDate baseDate = stableBaseDate();
        req.setStartDate(baseDate);
        req.setPreferredTimeWindows(List.of("13:30-17:00")); // afternoon

        // AI returned 03:00 — likely timezone confusion
        LocalDateTime aiTime = LocalDateTime.of(baseDate, LocalTime.of(3, 0));
        List<StudySessionResponse> input = List.of(session("Early hour", aiTime, aiTime.plusHours(1)));

        List<StudySessionResponse> result = normalize(input, 60, req);

        assertTrue(result.get(0).getStartTime().getHour() >= 6,
                "Hour should be >= 6 after TZ-4 fix, got: " + result.get(0).getStartTime().getHour());
    }

    @Test
    @DisplayName("TZ-3 + TZ-4: AI returns past date AND early hour → TZ-3 fixes date, TZ-4 fixes hour")
    void normalize_TZ3AndTZ4_pastDateAndEarlyHour_fixesBoth() {
        GenerateScheduleRequest req = makeRequest();
        LocalDate baseDate = stableBaseDate();
        req.setStartDate(baseDate);
        req.setPreferredTimeWindows(List.of("18:30-22:00"));

        // AI returned a date before baseDate and early hour
        LocalDateTime aiTime = LocalDateTime.of(baseDate.minusDays(7), LocalTime.of(2, 0));
        List<StudySessionResponse> input = List.of(session("Both wrong", aiTime, aiTime.plusHours(1)));

        List<StudySessionResponse> result = normalize(input, 60, req);

        assertTrue(result.get(0).getStartTime().toLocalDate().equals(baseDate),
            "Date should be baseDate, got: " + result.get(0).getStartTime());
        assertTrue(result.get(0).getStartTime().getHour() >= 6,
                "Hour should be >= 6, got: " + result.get(0).getStartTime().getHour());
    }

    @Test
    @DisplayName("TZ-4 should NOT replace intentionally early sessions (morning preference)")
    void normalize_TZ4_intentionalMorningSession_preserved() {
        GenerateScheduleRequest req = makeRequest();
        LocalDate baseDate = stableBaseDate();
        req.setStartDate(baseDate);
        req.setStudyPreference("morning");
        req.setPreferredTimeWindows(List.of("06:00-09:00"));

        // User wants morning sessions — 06:30 is intentional
        LocalDateTime morningTime = LocalDateTime.of(baseDate, LocalTime.of(6, 30));
        List<StudySessionResponse> input = List.of(session("Morning", morningTime, morningTime.plusHours(1)));

        List<StudySessionResponse> result = normalize(input, 60, req);

        assertTrue(result.get(0).getStartTime().getHour() == 6,
                "Intentional morning session hour should be preserved");
    }

    @Test
    @DisplayName("LB-1: avoidLateNight=true should clip sessions before earliest allowed")
    void normalize_LB1_avoidLateNight_clipsEarlySessions() {
        GenerateScheduleRequest req = makeRequest();
        LocalDate baseDate = stableBaseDate();
        req.setStartDate(baseDate);
        req.setAvoidLateNight(true);
        req.setAllowLateNight(false);
        req.setEarliestStartLocalTime("07:00");
        req.setLatestEndLocalTime("22:00");
        req.setPreferredTimeWindows(List.of("07:00-22:00"));

        // Session before allowed window
        LocalDateTime tooEarly = LocalDateTime.of(baseDate, LocalTime.of(5, 0));
        List<StudySessionResponse> input = List.of(session("Too early", tooEarly, tooEarly.plusHours(1)));

        List<StudySessionResponse> result = normalize(input, 60, req);

        assertTrue(result.get(0).getStartTime().getHour() >= 7,
                "Hour should be clipped to >= 07:00, got: " + result.get(0).getStartTime());
    }

    @Test
    @DisplayName("LB-1: allowLateNight=true should preserve sessions at 23:00")
    void normalize_LB1_allowLateNight_preservesNightSessions() {
        GenerateScheduleRequest req = makeRequest();
        LocalDate baseDate = stableBaseDate();
        req.setStartDate(baseDate);
        req.setAllowLateNight(true);
        req.setAvoidLateNight(false);

        LocalDateTime nightTime = LocalDateTime.of(baseDate, LocalTime.of(23, 0));
        List<StudySessionResponse> input = List.of(session("Night owl", nightTime, nightTime.plusHours(1)));

        List<StudySessionResponse> result = normalize(input, 60, req);

        assertTrue(result.get(0).getStartTime().getHour() == 23,
                "Night session hour should be preserved, got: " + result.get(0).getStartTime().getHour());
    }

    @Test
    @DisplayName("Null sessions in list should be skipped gracefully")
    void normalize_nullItemsInList_skipped() {
        GenerateScheduleRequest req = makeRequest();
        LocalDate baseDate = stableBaseDate();
        req.setStartDate(baseDate);

        @SuppressWarnings("unchecked")
        List<StudySessionResponse> input = new ArrayList<>();
        input.add(session("Valid", LocalDateTime.of(baseDate, LocalTime.of(10, 0)), LocalDateTime.of(baseDate, LocalTime.of(11, 0))));
        input.add(null);
        input.add(session("Also valid", LocalDateTime.of(baseDate, LocalTime.of(14, 0)), LocalDateTime.of(baseDate, LocalTime.of(15, 0))));

        List<StudySessionResponse> result = normalize(input, 60, req);

        assertTrue(result.size() == 2, "Should have exactly 2 valid sessions, got: " + result.size());
    }

    @Test
    @DisplayName("Empty session list should return empty list")
    void normalize_emptyList_returnsEmpty() {
        GenerateScheduleRequest req = makeRequest();
        List<StudySessionResponse> result = normalize(List.of(), 60, req);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Session in the past should be shifted to now + 5 min")
    void normalize_pastSession_shiftsToFuture() {
        GenerateScheduleRequest req = makeRequest();
        req.setStartDate(LocalDate.of(2020, 1, 1)); // far past date

        LocalDateTime pastTime = LocalDateTime.of(2020, 1, 1, 10, 0);
        List<StudySessionResponse> input = List.of(session("Past", pastTime, pastTime.plusHours(1)));

        List<StudySessionResponse> result = normalize(input, 60, req);

        assertTrue(result.get(0).getStartTime().isAfter(LocalDateTime.now()),
                "Past session should be shifted to future, got: " + result.get(0).getStartTime());
    }

    @Test
    @DisplayName("Null startDate in request should use current date as baseDate")
    void normalize_nullStartDate_usesCurrentDate() {
        GenerateScheduleRequest req = makeRequest();
        req.setStartDate(null); // null = use today

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDateTime aiTime = LocalDateTime.of(today, LocalTime.of(10, 0));
        List<StudySessionResponse> input = List.of(session("Today", aiTime, aiTime.plusHours(1)));

        List<StudySessionResponse> result = normalize(input, 60, req);

        assertTrue(result.get(0).getStartTime().toLocalDate().equals(today),
                "Date should be today, got: " + result.get(0).getStartTime().toLocalDate());
    }
}
