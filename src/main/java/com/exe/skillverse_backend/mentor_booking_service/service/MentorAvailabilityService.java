package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.mentor_booking_service.dto.request.AvailabilityRequest;
import com.exe.skillverse_backend.mentor_booking_service.entity.MentorAvailability;
import com.exe.skillverse_backend.mentor_booking_service.repository.MentorAvailabilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MentorAvailabilityService {
    private final MentorAvailabilityRepository repository;

    @Transactional
    public List<MentorAvailability> addAvailability(Long mentorId, AvailabilityRequest request) {
        List<MentorAvailability> availabilities = new ArrayList<>();

        // Convert request times to UTC LocalDateTime
        LocalDateTime startUtc = request.getStartTime().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime endUtc = request.getEndTime().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime recurrenceEndUtc = request.getRecurrenceEndDate() != null 
                ? request.getRecurrenceEndDate().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime() 
                : null;

        if (!request.isRecurring()) {
            availabilities.add(createEntity(mentorId, startUtc, endUtc));
        } else {
            LocalDateTime currentStart = startUtc;
            LocalDateTime currentEnd = endUtc;
            LocalDateTime endDate = recurrenceEndUtc;

            if (endDate == null) {
                // Default to 3 months if not specified
                endDate = currentStart.plusMonths(3);
            }

            while (currentStart.isBefore(endDate)) {
                availabilities.add(createEntity(mentorId, currentStart, currentEnd));

                switch (request.getRecurrenceType()) {
                    case DAILY:
                        currentStart = currentStart.plusDays(1);
                        currentEnd = currentEnd.plusDays(1);
                        break;
                    case WEEKLY:
                        currentStart = currentStart.plusWeeks(1);
                        currentEnd = currentEnd.plusWeeks(1);
                        break;
                    case MONTHLY:
                        currentStart = currentStart.plusMonths(1);
                        currentEnd = currentEnd.plusMonths(1);
                        break;
                    default:
                        currentStart = endDate; // Break loop
                        break;
                }
            }
        }

        return repository.saveAll(availabilities);
    }

    private MentorAvailability createEntity(Long mentorId, LocalDateTime start, LocalDateTime end) {
        return MentorAvailability.builder()
                .mentorId(mentorId)
                .startTime(start)
                .endTime(end)
                .isRecurring(false) // Stored as individual slots
                .recurrenceType(MentorAvailability.RecurrenceType.NONE)
                .build();
    }

    public List<MentorAvailability> getAvailability(Long mentorId, LocalDateTime from, LocalDateTime to) {
        if (from == null) from = LocalDateTime.now().minusDays(1);
        if (to == null) to = from.plusMonths(1);
        return repository.findByMentorIdAndDateRange(mentorId, from, to);
    }

    @Transactional
    public void deleteAvailability(Long id) {
        repository.deleteById(id);
    }
}
