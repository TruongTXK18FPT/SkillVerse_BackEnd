package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.mentor_booking_service.dto.BookingReviewDTO;

import java.util.List;

public interface BookingReviewService {
    BookingReviewDTO createReview(Long userId, Long bookingId, Integer rating, String comment, boolean isAnonymous);

    BookingReviewDTO replyToReview(Long userId, Long reviewId, String reply);

    List<BookingReviewDTO> getMentorReviews(Long mentorId);

    List<BookingReviewDTO> getStudentReviews(Long studentId);

    BookingReviewDTO getReviewByBookingId(Long bookingId);
}