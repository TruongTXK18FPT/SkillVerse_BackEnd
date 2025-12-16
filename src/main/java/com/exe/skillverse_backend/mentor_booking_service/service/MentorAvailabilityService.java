package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.mentor_booking_service.dto.request.AvailabilityRequest;
import com.exe.skillverse_backend.mentor_booking_service.entity.MentorAvailability;

import java.time.LocalDateTime;
import java.util.List;

public interface MentorAvailabilityService {
    List<MentorAvailability> addAvailability(Long mentorId, AvailabilityRequest request);
    List<MentorAvailability> getAvailability(Long mentorId, LocalDateTime from, LocalDateTime to);
    void deleteAvailability(Long id);
}