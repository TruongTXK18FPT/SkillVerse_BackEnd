package com.exe.skillverse_backend.mentor_booking_service.repository;

import com.exe.skillverse_backend.mentor_booking_service.entity.BookingReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingReviewRepository extends JpaRepository<BookingReview, Long> {
    List<BookingReview> findByMentorIdOrderByCreatedAtDesc(Long mentorId);
    List<BookingReview> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    Optional<BookingReview> findByBookingId(Long bookingId);
    boolean existsByBookingId(Long bookingId);
}
