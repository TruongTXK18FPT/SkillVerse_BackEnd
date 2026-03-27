package com.exe.skillverse_backend.mentor_booking_service.controller;

import com.exe.skillverse_backend.mentor_booking_service.dto.request.CreateBookingIntentRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.request.RatingRequest;
import com.exe.skillverse_backend.mentor_booking_service.dto.response.BookingResponse;
import com.exe.skillverse_backend.mentor_booking_service.service.BookingService;
import com.exe.skillverse_backend.mentor_service.entity.MentorProfile;
import com.exe.skillverse_backend.mentor_service.repository.MentorProfileRepository;
import com.exe.skillverse_backend.payment_service.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mentor-bookings")
@RequiredArgsConstructor
@Tag(name = "Mentor Booking", description = "Đặt lịch mentor 1:1")
public class BookingController {

    private final BookingService bookingService;
    private final com.exe.skillverse_backend.mentor_booking_service.service.BookingDisputeService disputeService;
    private final com.exe.skillverse_backend.mentor_booking_service.repository.BookingDisputeRepository disputeRepository;
    private final InvoiceService invoiceService;
    private final MentorProfileRepository mentorProfileRepository;

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
    @Operation(summary = "Mentor hoàn tất buổi học (chờ learner xác nhận)")
    public ResponseEntity<BookingResponse> complete(
            @PathVariable Long id,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long mentorId = Long.valueOf(jwt.getClaimAsString("userId"));
        var booking = bookingService.complete(mentorId, id);
        return ResponseEntity.ok(toResponse(booking));
    }

    @PutMapping("/{id}/confirm-complete")
    @Operation(summary = "Learner xác nhận hoàn tất buổi học")
    public ResponseEntity<BookingResponse> confirmComplete(
            @PathVariable Long id,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long learnerId = Long.valueOf(jwt.getClaimAsString("userId"));
        var booking = bookingService.learnerConfirmComplete(learnerId, id);
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

    @GetMapping("/{id}")
    @Operation(summary = "Lấy chi tiết một booking")
    public ResponseEntity<BookingResponse> getBookingDetail(
            @PathVariable Long id,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        return ResponseEntity.ok(bookingService.getBookingDetail(userId, id));
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

    @GetMapping("/mentor/{mentorId}/bookings")
    @Operation(summary = "Lấy danh sách booking đang active của mentor (dùng để đánh dấu slot đã đặt)")
    public ResponseEntity<List<BookingResponse>> getMentorActiveBookings(
            @PathVariable Long mentorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(bookingService.getMentorBookingsForDateRange(mentorId, from, to));
    }

    @GetMapping("/{id}/invoice")
    @Operation(summary = "Tải hóa đơn booking", description = "Sinh PDF hóa đơn cho buổi mentoring")
    public ResponseEntity<byte[]> downloadBookingInvoice(
            @PathVariable Long id,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));

        boolean allowed = authentication.getAuthorities().stream().anyMatch(a -> {
            String r = a.getAuthority();
            return "ROLE_USER".equals(r) || "ROLE_MENTOR".equals(r) || "ROLE_ADMIN".equals(r);
        });
        if (!allowed) {
            throw new AccessDeniedException("Không có quyền tải hóa đơn");
        }

        var booking = bookingService.getBookingIfParticipant(userId, id);

        String role = authentication.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))
                ? "ADMIN"
                : (authentication.getAuthorities().stream().anyMatch(a -> "ROLE_MENTOR".equals(a.getAuthority()))
                        ? "MENTOR"
                        : "USER");
        byte[] pdfBytes = invoiceService.generateBookingInvoice(booking, role);

        String filename = "booking-" + id + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    private BookingResponse toResponse(Booking booking) {
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
                .createdAt(booking.getCreatedAt())
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .durationMinutes(booking.getDurationMinutes())
                .status(booking.getStatus())
                .priceVnd(booking.getPriceVnd())
                .meetingLink(booking.getMeetingLink())
                .paymentReference(booking.getPaymentReference())
                .confirmedByLearner(booking.getConfirmedByLearner())
                .mentorCompletedAt(booking.getMentorCompletedAt())
                .learnerConfirmedAt(booking.getLearnerConfirmedAt())
                .mentorName(mentorName)
                .mentorAvatar(mentorAvatar)
                .learnerName(booking.getLearner().getFullName())
                .learnerAvatar(booking.getLearner().getAvatarUrl())
                .disputeId(disputeRepository.findByBooking_Id(booking.getId()).map(d -> d.getId()).orElse(null))
                .build();
    }
}
