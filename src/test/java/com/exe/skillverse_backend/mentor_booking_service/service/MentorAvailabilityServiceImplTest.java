package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.mentor_booking_service.dto.request.AvailabilityRequest;
import com.exe.skillverse_backend.mentor_booking_service.entity.MentorAvailability;
import com.exe.skillverse_backend.mentor_booking_service.repository.MentorAvailabilityRepository;
import com.exe.skillverse_backend.mentor_booking_service.service.impl.MentorAvailabilityServiceImpl;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MentorAvailabilityServiceImplTest {

    @Mock
    private MentorAvailabilityRepository repository;

    private MentorAvailabilityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MentorAvailabilityServiceImpl(repository);
        lenient().when(repository.saveAll(any(List.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("addAvailability should reject overlapping one-off slots")
    void addAvailability_ShouldRejectOverlappingOneOffSlots() {
        AvailabilityRequest request = request(false, MentorAvailability.RecurrenceType.NONE, null);
        when(repository.findOverlapping(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(MentorAvailability.builder().id(99L).build()));

        assertThrows(IllegalStateException.class, () -> service.addAvailability(5L, request));
    }

    @Test
    @DisplayName("addAvailability should create recurring weekly slots until recurrence end")
    void addAvailability_ShouldCreateRecurringWeeklySlots() {
        ZonedDateTime start = ZonedDateTime.of(2026, 4, 5, 9, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime end = start.plusHours(1);
        AvailabilityRequest request = new AvailabilityRequest();
        request.setStartTime(start);
        request.setEndTime(end);
        request.setRecurring(true);
        request.setRecurrenceType(MentorAvailability.RecurrenceType.WEEKLY);
        request.setRecurrenceEndDate(start.plusDays(10));

        when(repository.findOverlapping(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());

        List<MentorAvailability> created = service.addAvailability(7L, request);

        assertEquals(2, created.size());
        assertEquals(7L, created.get(0).getMentorId());
        assertEquals(MentorAvailability.RecurrenceType.NONE, created.get(0).getRecurrenceType());
    }

    @Test
    @DisplayName("getAvailability should use a default one-month range when bounds are omitted")
    void getAvailability_ShouldUseDefaultRangeWhenBoundsOmitted() {
        when(repository.findByMentorIdAndDateRange(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(MentorAvailability.builder().id(1L).build()));

        List<MentorAvailability> availabilities = service.getAvailability(9L, null, null);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).findByMentorIdAndDateRange(anyLong(), fromCaptor.capture(), toCaptor.capture());
        assertEquals(1, availabilities.size());
        assertTrue(Duration.between(fromCaptor.getValue(), toCaptor.getValue()).toDays() >= 28);
    }

    private AvailabilityRequest request(boolean recurring, MentorAvailability.RecurrenceType recurrenceType,
            ZonedDateTime recurrenceEndDate) {
        AvailabilityRequest request = new AvailabilityRequest();
        request.setStartTime(ZonedDateTime.of(2026, 4, 5, 9, 0, 0, 0, ZoneId.of("UTC")));
        request.setEndTime(ZonedDateTime.of(2026, 4, 5, 10, 0, 0, 0, ZoneId.of("UTC")));
        request.setRecurring(recurring);
        request.setRecurrenceType(recurrenceType);
        request.setRecurrenceEndDate(recurrenceEndDate);
        return request;
    }
}
