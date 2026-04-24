package com.exe.skillverse_backend.mentor_booking_service.controller;

import com.exe.skillverse_backend.mentor_booking_service.dto.BookingReviewDTO;
import com.exe.skillverse_backend.mentor_booking_service.dto.BookingReviewStatsDTO;
import com.exe.skillverse_backend.mentor_booking_service.service.BookingReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
        Long userId = getUserId(authentication);
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
        Long userId = getUserId(authentication);
        String reply = request.get("reply");

        BookingReviewDTO review = reviewService.replyToReview(userId, reviewId, reply);
        return ResponseEntity.ok(review);
    }

    @GetMapping("/mentor/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my reviews (Mentor)")
    public ResponseEntity<?> getMyReviews(Authentication authentication) {
        Long userId = getUserId(authentication);
        List<BookingReviewDTO> reviews = reviewService.getMentorReviews(userId);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/mentor/me/paged")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get my reviews with pagination/filter/sort (Mentor)")
    public ResponseEntity<Page<BookingReviewDTO>> getMyReviewsPaged(
            Authentication authentication,
            @RequestParam(required = false) Integer rating,
            @PageableDefault(size = 10) Pageable pageable) {
        Long userId = getUserId(authentication);
        Page<BookingReviewDTO> reviews = reviewService.getMentorReviews(userId, rating, pageable);
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/mentor/me/stats")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get aggregate review stats for current mentor")
    public ResponseEntity<BookingReviewStatsDTO> getMyReviewStats(Authentication authentication) {
        Long userId = getUserId(authentication);
        BookingReviewStatsDTO stats = reviewService.getMentorReviewStats(userId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/mentor/{mentorId}/stats")
    @Operation(summary = "Get aggregate review stats for any mentor (Public)")
    public ResponseEntity<BookingReviewStatsDTO> getMentorReviewStatsPublic(@PathVariable Long mentorId) {
        BookingReviewStatsDTO stats = reviewService.getMentorReviewStats(mentorId);
        return ResponseEntity.ok(stats);
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
        Long userId = getUserId(authentication);
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

    private Long getUserId(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        return Long.valueOf(jwt.getClaimAsString("userId"));
    }
}
