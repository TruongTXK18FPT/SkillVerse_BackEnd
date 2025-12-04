package com.exe.skillverse_backend.mentor_booking_service.controller;

import com.exe.skillverse_backend.mentor_booking_service.dto.BookingReviewDTO;
import com.exe.skillverse_backend.mentor_booking_service.service.BookingReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Booking Reviews", description = "APIs for managing booking reviews")
public class BookingReviewController {

    private final BookingReviewService reviewService;

    @PostMapping("/booking/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a review for a booking")
    public ResponseEntity<?> createReview(
            @PathVariable Long bookingId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        Integer rating = (Integer) request.get("rating");
        String comment = (String) request.get("comment");
        boolean isAnonymous = request.containsKey("isAnonymous") ? (boolean) request.get("isAnonymous") : false;

        BookingReviewDTO review = reviewService.createReview(userId, bookingId, rating, comment, isAnonymous);
        return ResponseEntity.ok(review);
    }

    @PostMapping("/{reviewId}/reply")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Reply to a review (Mentor only)")
    public ResponseEntity<?> replyToReview(
            @PathVariable Long reviewId,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        String reply = request.get("reply");

        BookingReviewDTO review = reviewService.replyToReview(userId, reviewId, reply);
        return ResponseEntity.ok(review);
    }

    @GetMapping("/mentor/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my reviews (Mentor)")
    public ResponseEntity<?> getMyReviews(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        List<BookingReviewDTO> reviews = reviewService.getMentorReviews(userId);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/mentor/{mentorId}")
    @Operation(summary = "Get reviews by mentor (Public)")
    public ResponseEntity<?> getMentorReviewsPublic(@PathVariable Long mentorId) {
        List<BookingReviewDTO> reviews = reviewService.getMentorReviews(mentorId);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/student/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my reviews (Student)")
    public ResponseEntity<?> getMyStudentReviews(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        List<BookingReviewDTO> reviews = reviewService.getStudentReviews(userId);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get reviews by student (Public/Mentor)")
    public ResponseEntity<?> getStudentReviews(@PathVariable Long studentId) {
        List<BookingReviewDTO> reviews = reviewService.getStudentReviews(studentId);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get review for a specific booking")
    public ResponseEntity<?> getReviewForBooking(@PathVariable Long bookingId) {
        BookingReviewDTO review = reviewService.getReviewByBookingId(bookingId);
        return ResponseEntity.ok(review);
    }
}
