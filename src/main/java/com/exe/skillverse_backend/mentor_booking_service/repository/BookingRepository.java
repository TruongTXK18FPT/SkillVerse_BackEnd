package com.exe.skillverse_backend.mentor_booking_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Page<Booking> findByLearnerAndStatusInOrderByStartTimeDesc(User learner, java.util.List<BookingStatus> statuses, Pageable pageable);
    Page<Booking> findByMentorAndStatusInOrderByStartTimeDesc(User mentor, java.util.List<BookingStatus> statuses, Pageable pageable);
    boolean existsByMentorAndStatusInAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(User mentor, java.util.List<BookingStatus> statuses, LocalDateTime start, LocalDateTime end);
    boolean existsByLearnerAndStatusInAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(User learner, java.util.List<BookingStatus> statuses, LocalDateTime start, LocalDateTime end);
    java.util.List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, LocalDateTime before);
    Booking findTopByMentorAndLearnerOrderByStartTimeDesc(User mentor, User learner);
    Booking findTopByMentorAndLearnerAndStatusNotInOrderByStartTimeDesc(User mentor, User learner, java.util.Collection<BookingStatus> statuses);
    long countByMentorAndStatus(User mentor, BookingStatus status);
}
