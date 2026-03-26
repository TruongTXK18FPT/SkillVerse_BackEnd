package com.exe.skillverse_backend.mentor_booking_service.service.impl;

import com.exe.skillverse_backend.mentor_booking_service.dto.request.AvailabilityRequest;
import com.exe.skillverse_backend.mentor_booking_service.entity.MentorAvailability;
import com.exe.skillverse_backend.mentor_booking_service.repository.MentorAvailabilityRepository;
import com.exe.skillverse_backend.mentor_booking_service.service.MentorAvailabilityService;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MentorAvailabilityServiceImpl implements MentorAvailabilityService {
    private final MentorAvailabilityRepository repository;

    @Transactional
    public List<MentorAvailability> addAvailability(Long mentorId, AvailabilityRequest request) {
        List<MentorAvailability> availabilities = new ArrayList<>();

        // Convert request times to VN LocalDateTime (same timezone as bookings)
        LocalDateTime startVn = request.getStartTime().withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime();
        LocalDateTime endVn = request.getEndTime().withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime();
        LocalDateTime recurrenceEndVn = request.getRecurrenceEndDate() != null
                ? request.getRecurrenceEndDate().withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime()
                : null;

        if (!request.isRecurring()) {
            // Check for overlapping availability
            List<MentorAvailability> overlapping = repository.findOverlapping(mentorId, startVn, endVn);
            if (!overlapping.isEmpty()) {
                throw new IllegalStateException("Lịch rảnh đã tồn tại trong khoảng thời gian này.");
            }
            availabilities.add(createEntity(mentorId, startVn, endVn));
        } else {
            LocalDateTime currentStart = startVn;
            LocalDateTime currentEnd = endVn;
            LocalDateTime endDate = recurrenceEndVn;

            if (endDate == null) {
                // Default to 3 months if not specified
                endDate = currentStart.plusMonths(3);
            }

            while (currentStart.isBefore(endDate)) {
                // Check for overlapping availability for each occurrence
                List<MentorAvailability> overlapping = repository.findOverlapping(mentorId, currentStart, currentEnd);
                if (overlapping.isEmpty()) {
                    availabilities.add(createEntity(mentorId, currentStart, currentEnd));
                }

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

        if (availabilities.isEmpty()) {
            throw new IllegalStateException("Tất cả các lịch trong khoảng này đã bị trùng hoặc không hợp lệ.");
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
        if (from == null)
            from = LocalDateTime.now().minusDays(1);
        if (to == null)
            to = from.plusMonths(1);
        return repository.findByMentorIdAndDateRange(mentorId, from, to);
    }

    @Transactional
    public void deleteAvailability(Long id) {
        repository.deleteById(id);
    }
}
