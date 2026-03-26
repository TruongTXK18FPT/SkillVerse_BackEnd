package com.exe.skillverse_backend.mentor_booking_service.repository;

import com.exe.skillverse_backend.mentor_booking_service.entity.BookingReview;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingReviewRepository extends JpaRepository<BookingReview, Long> {
    List<BookingReview> findByMentorIdOrderByCreatedAtDesc(Long mentorId);
    List<BookingReview> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    Page<BookingReview> findByMentorIdOrderByCreatedAtDesc(Long mentorId, Pageable pageable);

    @Query("SELECT r FROM BookingReview r WHERE r.mentor.id = :mentorId AND (:rating IS NULL OR r.rating = :rating)")
    Page<BookingReview> findByMentorIdWithRatingFilter(
            @Param("mentorId") Long mentorId,
            @Param("rating") Integer rating,
            Pageable pageable
    );

    long countByMentorId(Long mentorId);
    long countByMentorIdAndRating(Long mentorId, Integer rating);

    @Query("SELECT AVG(r.rating) FROM BookingReview r WHERE r.mentor.id = :mentorId")
    Double averageRatingByMentorId(@Param("mentorId") Long mentorId);

    Optional<BookingReview> findByBookingId(Long bookingId);
    @Query("SELECT r FROM BookingReview r WHERE r.booking.id = :bookingId AND r.rating BETWEEN 1 AND 5")
    Optional<BookingReview> findValidByBookingId(@Param("bookingId") Long bookingId);
    boolean existsByBookingId(Long bookingId);
}
