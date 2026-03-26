package com.exe.skillverse_backend.mentor_booking_service.controller;

import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDispute;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDisputeEvidence;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDisputeResponse;
import com.exe.skillverse_backend.mentor_booking_service.service.BookingDisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/booking-disputes")
@RequiredArgsConstructor
@Tag(name = "Booking Dispute", description = "Dispute management for mentor bookings")
public class BookingDisputeController {

    private final BookingDisputeService disputeService;

    @PostMapping
    @Operation(summary = "Learner opens a dispute for a booking")
    public ResponseEntity<BookingDispute> openDispute(
            @RequestParam Long bookingId,
            @RequestParam(required = false) String reason,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        return ResponseEntity.ok(disputeService.openDispute(userId, bookingId, reason));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get dispute by ID")
    public ResponseEntity<BookingDispute> getDispute(@PathVariable Long id) {
        return ResponseEntity.ok(disputeService.getDispute(id));
    }

    @GetMapping("/booking/{bookingId}")
    @Operation(summary = "Get dispute by booking ID")
    public ResponseEntity<BookingDispute> getDisputeByBooking(@PathVariable Long bookingId) {
        return ResponseEntity.ok(disputeService.getDisputeByBooking(bookingId));
    }

    @PostMapping("/{id}/evidence")
    @Operation(summary = "Submit evidence to a dispute (TEXT/FILE/LINK)")
    public ResponseEntity<BookingDisputeEvidence> submitEvidence(
            @PathVariable Long id,
            @RequestParam BookingDisputeEvidence.EvidenceType type,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String fileUrl,
            @RequestParam(required = false) String fileName,
            @RequestParam(required = false) String description,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        return ResponseEntity.ok(disputeService.submitEvidence(
                userId, id, type, content, fileUrl, fileName, description));
    }

    @GetMapping("/{id}/evidence")
    @Operation(summary = "List all evidence for a dispute")
    public ResponseEntity<List<BookingDisputeEvidence>> getEvidence(@PathVariable Long id) {
        return ResponseEntity.ok(disputeService.getEvidence(id));
    }

    @PostMapping("/{id}/respond")
    @Operation(summary = "Respond to evidence in a dispute")
    public ResponseEntity<BookingDisputeResponse> respondToEvidence(
            @PathVariable Long id,
            @RequestParam Long evidenceId,
            @RequestParam String content,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long userId = Long.valueOf(jwt.getClaimAsString("userId"));
        return ResponseEntity.ok(disputeService.respondToEvidence(userId, id, evidenceId, content));
    }
}
