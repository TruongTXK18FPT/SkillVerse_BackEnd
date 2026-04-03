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

    private AiStudySupportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiStudySupportServiceImpl(
                studySessionRepository,
                userRepository,
                userSubscriptionRepository,
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

    private StudySessionResponse session(String title, LocalDateTime start, LocalDateTime end) {
        return StudySessionResponse.builder()
                .title(title)
                .startTime(start)
                .endTime(end)
                .status(StudySessionStatus.SCHEDULED)
                .build();
    }
}
