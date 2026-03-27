package com.exe.skillverse_backend.mentor_booking_service.repository;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Page<Booking> findByLearnerAndStatusInOrderByStartTimeDesc(User learner, List<BookingStatus> statuses, Pageable pageable);
    Page<Booking> findByMentorAndStatusInOrderByStartTimeDesc(User mentor, List<BookingStatus> statuses, Pageable pageable);
    Page<Booking> findByStatusOrderByStartTimeDesc(BookingStatus status, Pageable pageable);
    Page<Booking> findByStartTimeBetweenOrderByStartTimeDesc(LocalDateTime from, LocalDateTime to, Pageable pageable);
    Page<Booking> findByStatusAndStartTimeBetweenOrderByStartTimeDesc(BookingStatus status, LocalDateTime from, LocalDateTime to, Pageable pageable);
    boolean existsByMentorAndStatusInAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(User mentor, List<BookingStatus> statuses, LocalDateTime start, LocalDateTime end);
    boolean existsByLearnerAndStatusInAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(User learner, List<BookingStatus> statuses, LocalDateTime start, LocalDateTime end);
    List<Booking> findByStatusAndCreatedAtBefore(BookingStatus status, LocalDateTime before);
    List<Booking> findByStatusAndStartTimeLessThanEqual(BookingStatus status, LocalDateTime time);
    Booking findTopByMentorAndLearnerOrderByStartTimeDesc(User mentor, User learner);
    Booking findTopByMentorAndLearnerAndStatusNotInOrderByStartTimeDesc(User mentor, User learner, Collection<BookingStatus> statuses);
    long countByMentorAndStatus(User mentor, BookingStatus status);
    boolean existsByPaymentReference(String paymentReference);
    Optional<Booking> findByPaymentReference(String paymentReference);
    List<Booking> findByStatusAndMentorCompletedAtBefore(BookingStatus status, LocalDateTime deadline);
    List<Booking> findByStatusAndCompletionDeadlineBefore(BookingStatus status, LocalDateTime deadline);
    List<Booking> findByMentorAndStatusInAndStartTimeBetween(User mentor, List<BookingStatus> statuses, LocalDateTime from, LocalDateTime to);
}
