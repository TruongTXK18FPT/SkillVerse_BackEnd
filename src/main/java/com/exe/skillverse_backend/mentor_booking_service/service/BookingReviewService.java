package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.mentor_booking_service.dto.BookingReviewDTO;
import com.exe.skillverse_backend.mentor_booking_service.dto.BookingReviewStatsDTO;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BookingReviewService {
    BookingReviewDTO createReview(Long userId, Long bookingId, Integer rating, String comment, boolean isAnonymous);

    BookingReviewDTO replyToReview(Long userId, Long reviewId, String reply);

    List<BookingReviewDTO> getMentorReviews(Long mentorId);
    Page<BookingReviewDTO> getMentorReviews(Long mentorId, Integer rating, Pageable pageable);
    BookingReviewStatsDTO getMentorReviewStats(Long mentorId);

    List<BookingReviewDTO> getStudentReviews(Long studentId);

    BookingReviewDTO getReviewByBookingId(Long bookingId);
}
