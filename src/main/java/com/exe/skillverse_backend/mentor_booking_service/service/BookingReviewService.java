package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.mentor_booking_service.dto.BookingReviewDTO;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingReview;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingReviewRepository;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingReviewService {

    private final BookingReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    @Transactional
    public BookingReviewDTO createReview(Long userId, Long bookingId, Integer rating, String comment, boolean isAnonymous) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));

        if (!booking.getLearner().getId().equals(userId)) {
            throw new RuntimeException("You are not authorized to review this booking");
        }

        if (booking.getStatus() != BookingStatus.COMPLETED) {
            throw new RuntimeException("You can only review completed bookings");
        }

        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new RuntimeException("You have already reviewed this booking");
        }

        BookingReview review = BookingReview.builder()
                .booking(booking)
                .student(booking.getLearner())
                .mentor(booking.getMentor())
                .rating(rating)
                .comment(comment)
                .isAnonymous(isAnonymous)
                .build();

        review = reviewRepository.save(review);
        return mapToDTO(review);
    }

    @Transactional
    public BookingReviewDTO replyToReview(Long userId, Long reviewId, String reply) {
        BookingReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Review not found"));

        if (!review.getMentor().getId().equals(userId)) {
            throw new RuntimeException("You are not authorized to reply to this review");
        }

        review.setReply(reply);
        review = reviewRepository.save(review);
        return mapToDTO(review);
    }

    @Transactional(readOnly = true)
    public List<BookingReviewDTO> getMentorReviews(Long mentorId) {
        return reviewRepository.findByMentorIdOrderByCreatedAtDesc(mentorId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BookingReviewDTO> getStudentReviews(Long studentId) {
        return reviewRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BookingReviewDTO getReviewByBookingId(Long bookingId) {
        return reviewRepository.findByBookingId(bookingId)
                .map(this::mapToDTO)
                .orElse(null);
    }

    private BookingReviewDTO mapToDTO(BookingReview review) {
        boolean anonymous = Boolean.TRUE.equals(review.getIsAnonymous());
        String studentName = anonymous ? "Anonymous User" : review.getStudent().getFullName();
        String studentAvatar = anonymous ? null : review.getStudent().getAvatarUrl();

        return BookingReviewDTO.builder()
                .id(review.getId())
                .bookingId(review.getBooking().getId())
                .studentId(review.getStudent().getId())
                .studentName(studentName)
                .studentAvatar(studentAvatar)
                .mentorId(review.getMentor().getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .reply(review.getReply())
                .isAnonymous(anonymous)
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
