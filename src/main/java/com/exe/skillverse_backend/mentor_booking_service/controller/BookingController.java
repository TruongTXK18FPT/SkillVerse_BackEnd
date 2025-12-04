package com.exe.skillverse_backend.mentor_booking_service.controller;

import com.exe.skillverse_backend.mentor_booking_service.dto.request.ApprovalRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.CreateBookingIntentRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.BookingResponse;
import com.exe.skillverse_backend.mentor_booking_service.service.BookingService;
import com.exe.skillverse_backend.payment_service.dto.response.CreatePaymentResponse;
import com.exe.skillverse_backend.payment_service.service.InvoiceService;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.RatingRequest;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mentor-bookings")
@RequiredArgsConstructor
@Tag(name = "Mentor Booking", description = "Đặt lịch mentor 1:1")
public class BookingController {

    private final BookingService bookingService;
    private final InvoiceService invoiceService;
    private final MentorProfileRepository mentorProfileRepository;

    @PostMapping("/intent")
    @Operation(summary = "Tạo intent thanh toán cho booking")
    public ResponseEntity<CreatePaymentResponse> createIntent(
            @Valid @RequestBody CreateBookingIntentRequest request,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long learnerId = Long.valueOf(jwt.getClaimAsString("userId"));
        return ResponseEntity.ok(bookingService.createBookingIntent(learnerId, request));
    }

    @PostMapping("/wallet")
    @Operation(summary = "Tạo booking và đóng băng tiền trong ví")
    public ResponseEntity<BookingResponse> createWithWallet(
            @Valid @RequestBody CreateBookingIntentRequest request,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long learnerId = Long.valueOf(jwt.getClaimAsString("userId"));
        var booking = bookingService.createBookingWithWallet(learnerId, request);
        return ResponseEntity.ok(toResponse(booking));
    }

    @PutMapping("/{id}/approve")
    @Operation(summary = "Mentor duyệt booking")
    public ResponseEntity<BookingResponse> approve(
            @PathVariable Long id,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long mentorId = Long.valueOf(jwt.getClaimAsString("userId"));
        var booking = bookingService.approve(mentorId, id);
        return ResponseEntity.ok(toResponse(booking));
    }

    @PutMapping("/{id}/reject")
    @Operation(summary = "Mentor từ chối booking")
    public ResponseEntity<BookingResponse> reject(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long mentorId = Long.valueOf(jwt.getClaimAsString("userId"));
        var booking = bookingService.reject(mentorId, id, reason);
        return ResponseEntity.ok(toResponse(booking));
    }

    @PutMapping("/{id}/start")
    @Operation(summary = "Bắt đầu buổi học")
    public ResponseEntity<BookingResponse> start(
            @PathVariable Long id,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long mentorId = Long.valueOf(jwt.getClaimAsString("userId"));
        var booking = bookingService.startMeeting(mentorId, id);
        return ResponseEntity.ok(toResponse(booking));
    }

    @PutMapping("/{id}/complete")
    @Operation(summary = "Hoàn tất buổi học (mentor nhận 80%)")
    public ResponseEntity<BookingResponse> complete(
            @PathVariable Long id,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long mentorId = Long.valueOf(jwt.getClaimAsString("userId"));
        var booking = bookingService.complete(mentorId, id);
        return ResponseEntity.ok(toResponse(booking));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Learner hủy booking trước 1 ngày và hoàn tiền")
    public ResponseEntity<BookingResponse> cancel(
            @PathVariable Long id,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long learnerId = Long.valueOf(jwt.getClaimAsString("userId"));
        var booking = bookingService.cancelByLearner(learnerId, id);
        return ResponseEntity.ok(toResponse(booking));
    }

    @PostMapping("/{id}/rating")
    @Operation(summary = "User đánh giá mentor sau buổi")
    public ResponseEntity<String> rate(
            @PathVariable Long id,
            @Valid @RequestBody RatingRequest req,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long learnerId = Long.valueOf(jwt.getClaimAsString("userId"));
        bookingService.rateAfterSession(learnerId, id, req.getStars(), req.getComment(), req.getSkillEndorsed());
        return ResponseEntity.ok("Đánh giá thành công");
    }

    @GetMapping("/me")
    @Operation(summary = "Danh sách booking của tôi")
    public ResponseEntity<Page<BookingResponse>> myBookings(
            @RequestParam(defaultValue = "false") boolean mentorView,
            Pageable pageable,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        return ResponseEntity.ok(bookingService.getUserBookings(userId, mentorView, pageable));
    }

    @GetMapping("/{id}/invoice")
    @Operation(summary = "Tải hóa đơn booking", description = "Sinh PDF hóa đơn cho buổi mentoring")
    public org.springframework.http.ResponseEntity<byte[]> downloadBookingInvoice(
            @PathVariable Long id,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        boolean allowed = authentication.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return "ROLE_USER".equals(r) || "ROLE_MENTOR".equals(r) || "ROLE_ADMIN".equals(r);
        });
        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException("Không có quyền tải hóa đơn");
        }

        var booking = bookingService.getBookingIfParticipant(userId, id);

        String role = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))
                ? "ADMIN"
                : (authentication.getAuthorities().stream().anyMatch(a -> "ROLE_MENTOR".equals(a.getAuthority()))
                    ? "MENTOR" : "USER");
        byte[] pdfBytes = invoiceService.generateBookingInvoice(booking, role);

        String filename = "booking-" + id + ".pdf";
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    private BookingResponse toResponse(com.exe.skillverse_backend.mentor_booking_service.entity.Booking booking) {
        String mentorName = booking.getMentor().getFullName();
        String mentorAvatar = booking.getMentor().getAvatarUrl();

        MentorProfile profile = mentorProfileRepository.findById(booking.getMentor().getId()).orElse(null);
        if (profile != null) {
            if (profile.getFullName() != null && !profile.getFullName().isEmpty()) {
                mentorName = profile.getFullName();
            }
            if (profile.getAvatarUrl() != null && !profile.getAvatarUrl().isEmpty()) {
                mentorAvatar = profile.getAvatarUrl();
            }
        }

        return BookingResponse.builder()
                .id(booking.getId())
                .mentorId(booking.getMentor().getId())
                .learnerId(booking.getLearner().getId())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .durationMinutes(booking.getDurationMinutes())
                .status(booking.getStatus())
                .priceVnd(booking.getPriceVnd())
                .meetingLink(booking.getMeetingLink())
                .paymentReference(booking.getPaymentReference())
                .mentorName(mentorName)
                .mentorAvatar(mentorAvatar)
                .learnerName(booking.getLearner().getFullName())
                .learnerAvatar(booking.getLearner().getAvatarUrl())
                .build();
    }
}
