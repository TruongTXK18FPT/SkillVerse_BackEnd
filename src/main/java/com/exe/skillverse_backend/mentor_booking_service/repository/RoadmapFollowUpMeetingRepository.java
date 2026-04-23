package com.exe.skillverse_backend.mentor_booking_service.repository;

import com.exe.skillverse_backend.mentor_booking_service.entity.RoadmapFollowUpMeeting;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoadmapFollowUpMeetingRepository extends JpaRepository<RoadmapFollowUpMeeting, Long> {
    List<RoadmapFollowUpMeeting> findByBookingIdOrderByScheduledAtAsc(Long bookingId);

    Optional<RoadmapFollowUpMeeting> findByIdAndBookingId(Long id, Long bookingId);
}
