package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDispute;
import java.math.BigDecimal;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDisputeEvidence;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDisputeResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BookingDisputeService {

    BookingDispute openDispute(Long userId, Long bookingId, String reason);

    BookingDisputeEvidence submitEvidence(Long userId, Long disputeId,
            BookingDisputeEvidence.EvidenceType type, String content,
            String fileUrl, String fileName, String description);

    List<BookingDisputeEvidence> getEvidence(Long disputeId);

    BookingDisputeResponse respondToEvidence(Long userId, Long disputeId, Long evidenceId, String content);

    BookingDispute getDispute(Long disputeId);

    BookingDispute getDisputeByBooking(Long bookingId);

    BookingDispute resolveDispute(Long adminId, Long disputeId,
            BookingDispute.DisputeResolution resolution, String notes, BigDecimal partialAmount);

    Page<BookingDispute> getAllDisputes(BookingDispute.DisputeStatus status, Pageable pageable);
}
