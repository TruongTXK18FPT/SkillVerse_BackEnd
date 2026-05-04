package com.exe.skillverse_backend.admin_service.controller;

import com.exe.skillverse_backend.admin_service.dto.request.ResolveBookingDisputeRequest;
import com.exe.skillverse_backend.admin_service.dto.request.ReviewBookingDisputeEvidenceRequest;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDispute;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDisputeEvidence;
import com.exe.skillverse_backend.mentor_booking_service.service.BookingDisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/booking-disputes")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Admin Booking Dispute", description = "Admin management for booking disputes")
@PreAuthorize("hasRole('ADMIN') or hasRole('FINANCE_ADMIN')")
public class AdminBookingController {

    private final BookingDisputeService disputeService;

    @GetMapping
    @Operation(summary = "Get all booking disputes", description = "Paginated list with optional status filter")
    public ResponseEntity<Page<BookingDispute>> getAllDisputes(
            @RequestParam(required = false) BookingDispute.DisputeStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(disputeService.getAllDisputes(status, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get booking dispute by ID")
    public ResponseEntity<BookingDispute> getDispute(@PathVariable Long id) {
        return ResponseEntity.ok(disputeService.getDispute(id));
    }

    @GetMapping("/{id}/evidence")
    @Operation(summary = "Get all evidence for a dispute")
    public ResponseEntity<List<BookingDisputeEvidence>> getEvidence(@PathVariable Long id) {
        return ResponseEntity.ok(disputeService.getEvidence(id));
    }

    @PostMapping("/{id}/resolve")
    @Operation(summary = "Resolve booking dispute", description = "Admin resolves a dispute with FULL_REFUND, FULL_RELEASE, PARTIAL_REFUND, or PARTIAL_RELEASE")
    public ResponseEntity<BookingDispute> resolveDispute(
            @PathVariable Long id,
            @RequestBody ResolveBookingDisputeRequest request,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long adminId = Long.valueOf(jwt.getClaimAsString("userId"));
        log.info("Admin {} resolving booking dispute {} with resolution {}",
                adminId, id, request.getResolution());
        return ResponseEntity.ok(disputeService.resolveDispute(
                adminId, id, request.getResolution(),
                request.getNotes(), request.getPartialAmount()));
    }

    @PostMapping("/{id}/evidence/{evidenceId}/review")
    @Operation(summary = "Review dispute evidence and resolve by decision")
    public ResponseEntity<BookingDisputeEvidence> reviewEvidence(
            @PathVariable Long id,
            @PathVariable Long evidenceId,
            @RequestBody ReviewBookingDisputeEvidenceRequest request,
            Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        Long adminId = Long.valueOf(jwt.getClaimAsString("userId"));

        BookingDisputeEvidence.EvidenceReviewStatus reviewStatus;
        BookingDispute.DisputeResolution mappedResolution = null;

        switch (request.getDecision()) {
            case MARK_UNDER_REVIEW -> reviewStatus = BookingDisputeEvidence.EvidenceReviewStatus.UNDER_REVIEW;
            case ACCEPT_EVIDENCE_REFUND_USER -> {
                reviewStatus = BookingDisputeEvidence.EvidenceReviewStatus.ACCEPTED;
                mappedResolution = BookingDispute.DisputeResolution.FULL_REFUND;
            }
            case REJECT_EVIDENCE_RELEASE_MENTOR -> {
                reviewStatus = BookingDisputeEvidence.EvidenceReviewStatus.REJECTED;
                mappedResolution = BookingDispute.DisputeResolution.FULL_RELEASE;
            }
            default -> throw new IllegalArgumentException("Decision không hợp lệ");
        }

        return ResponseEntity.ok(disputeService.reviewEvidenceAndResolve(
                adminId, id, evidenceId, reviewStatus, mappedResolution, request.getNotes()));
    }
}
