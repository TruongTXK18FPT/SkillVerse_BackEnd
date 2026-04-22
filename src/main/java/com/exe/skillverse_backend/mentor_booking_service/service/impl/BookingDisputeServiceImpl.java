package com.exe.skillverse_backend.mentor_booking_service.service.impl;

import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDispute;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDisputeEvidence;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDisputeResponse;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingDisputeEvidenceRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingDisputeRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingDisputeResponseRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.mentor_booking_service.service.BookingDisputeService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingDisputeServiceImpl implements BookingDisputeService {

    private final BookingRepository bookingRepository;
    private final BookingDisputeRepository disputeRepository;
    private final BookingDisputeEvidenceRepository evidenceRepository;
    private final BookingDisputeResponseRepository responseRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final WalletService walletService;
    private final JourneyRepository journeyRepository;

    @Override
    @Transactional
    public BookingDispute openDispute(Long userId, Long bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));

        // Only learner can open dispute
        if (!booking.getLearner().getId().equals(userId)) {
            throw new IllegalArgumentException("Chỉ người học được phép mở dispute");
        }

        // V3 Phase 1: JOURNEY_MENTORING bookings have no fixed session window —
        // the dispute/refund flow is not applicable. Learner should contact admin directly.
        if ("JOURNEY_MENTORING".equals(booking.getBookingType())) {
            throw new IllegalStateException(
                    "Booking hỗ trợ hành trình không thể mở dispute theo quy trình thông thường. Vui lòng liên hệ admin.");
        }

        // Can only dispute when mentor has completed or when session time has passed
        BookingStatus status = booking.getStatus();
        boolean sessionEnded = LocalDateTime.now().isAfter(booking.getEndTime());
        boolean canDispute = status == BookingStatus.PENDING_COMPLETION
                || ((status == BookingStatus.ONGOING || status == BookingStatus.CONFIRMED) && sessionEnded);
        if (!canDispute) {
            throw new IllegalStateException("Không thể mở dispute ở trạng thái này");
        }

        // Check no existing dispute
        if (disputeRepository.existsByBooking_Id(bookingId)) {
            throw new IllegalStateException("Dispute đã tồn tại cho booking này");
        }

        // Set booking to DISPUTED
        booking.setStatus(BookingStatus.DISPUTED);
        booking.setMeetingLink(null);
        bookingRepository.save(booking);

        // Create dispute
        BookingDispute dispute = BookingDispute.builder()
                .booking(booking)
                .initiatorId(userId)
                .respondentId(booking.getMentor().getId())
                .reason(reason != null ? reason : "")
                .status(BookingDispute.DisputeStatus.OPEN)
                .build();
        dispute = disputeRepository.save(dispute);

        // Notify mentor
        notificationService.createNotification(
                booking.getMentor().getId(),
                "Learner mở dispute",
                "Learner đã từ chối hoàn tất và mở dispute: " + (reason != null ? reason : ""),
                NotificationType.DISPUTE_OPENED,
                dispute.getId().toString(),
                userId);

        return dispute;
    }

    @Override
    @Transactional
    public BookingDisputeEvidence submitEvidence(Long userId, Long disputeId,
            BookingDisputeEvidence.EvidenceType type, String content,
            String fileUrl, String fileName, String description) {

        BookingDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute không tồn tại"));

        // Validate user is participant
        if (!userId.equals(dispute.getInitiatorId()) && !userId.equals(dispute.getRespondentId())) {
            throw new IllegalArgumentException("Không có quyền submit evidence cho dispute này");
        }

        BookingDisputeEvidence evidence = BookingDisputeEvidence.builder()
                .dispute(dispute)
                .submittedBy(userId)
                .evidenceType(type)
                .content(content)
                .fileUrl(fileUrl)
                .fileName(fileName)
                .description(description)
                .isOfficial(false)
                .build();
        return evidenceRepository.save(evidence);
    }

    @Override
    public List<BookingDisputeEvidence> getEvidence(Long disputeId) {
        return evidenceRepository.findByDispute_IdOrderByCreatedAtAsc(disputeId);
    }

    @Override
    @Transactional
    public BookingDisputeResponse respondToEvidence(Long userId, Long disputeId, Long evidenceId, String content) {
        BookingDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute không tồn tại"));

        BookingDisputeEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence không tồn tại"));

        // Validate user is participant
        if (!userId.equals(dispute.getInitiatorId()) && !userId.equals(dispute.getRespondentId())) {
            throw new IllegalArgumentException("Không có quyền respond evidence này");
        }

        // Get user name
        String userName = userRepository.findById(userId)
                .map(u -> ((u.getFirstName() != null ? u.getFirstName() : "") + " " + (u.getLastName() != null ? u.getLastName() : "")).trim())
                .orElse("User #" + userId);

        BookingDisputeResponse response = BookingDisputeResponse.builder()
                .evidence(evidence)
                .respondedBy(userId)
                .respondedByName(userName)
                .content(content)
                .isAdminResponse(false)
                .build();
        return responseRepository.save(response);
    }

    @Override
    public BookingDispute getDispute(Long disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute không tồn tại"));
    }

    @Override
    public BookingDispute getDisputeByBooking(Long bookingId) {
        return disputeRepository.findByBooking_Id(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute không tồn tại cho booking này"));
    }

    @Override
    @Transactional
    public BookingDispute resolveDispute(Long adminId, Long disputeId,
            BookingDispute.DisputeResolution resolution, String notes, BigDecimal partialAmount) {

        BookingDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute không tồn tại"));

        if (dispute.getStatus() == BookingDispute.DisputeStatus.RESOLVED) {
            throw new IllegalStateException("Dispute đã được giải quyết trước đó");
        }
        if (resolution == null) {
            throw new IllegalArgumentException("Thiếu resolution");
        }

        Booking booking = dispute.getBooking();
        BigDecimal bookingAmount = booking.getPriceVnd();
        BigDecimal refundAmount = BigDecimal.ZERO;
        BigDecimal releasedAmount = BigDecimal.ZERO;
        BigDecimal mentorPayoutAmount = BigDecimal.ZERO;
        BigDecimal adminCommissionAmount = BigDecimal.ZERO;

        dispute.setStatus(BookingDispute.DisputeStatus.RESOLVED);
        dispute.setResolution(resolution);
        dispute.setResolutionNotes(notes);
        dispute.setResolvedBy(adminId);
        dispute.setResolvedAt(LocalDateTime.now());

        // Handle financial resolution
        switch (resolution) {
            case FULL_REFUND -> {
                walletService.unfreezeForBooking(
                        booking.getLearner().getId(),
                        bookingAmount,
                        booking.getId(),
                        "Dispute resolved: Full refund to learner");
                refundAmount = bookingAmount;
                booking.setStatus(BookingStatus.REFUNDED);
            }
            case FULL_RELEASE -> {
                walletService.chargeFrozenForBooking(
                        booking.getLearner().getId(),
                        bookingAmount,
                        booking.getId());
                releasedAmount = bookingAmount;
                mentorPayoutAmount = releasedAmount.multiply(new BigDecimal("0.80"));
                adminCommissionAmount = releasedAmount.subtract(mentorPayoutAmount);
                walletService.payMentorForBooking(
                        booking.getMentor().getId(),
                        mentorPayoutAmount,
                        booking.getId());
                booking.setStatus(BookingStatus.COMPLETED);
            }
            case PARTIAL_REFUND -> {
                BigDecimal refundAmt = partialAmount != null ? partialAmount : bookingAmount.multiply(new BigDecimal("0.5"));
                if (refundAmt.compareTo(BigDecimal.ZERO) <= 0 || refundAmt.compareTo(bookingAmount) > 0) {
                    throw new IllegalArgumentException("partialAmount không hợp lệ");
                }
                BigDecimal releaseAmt = bookingAmount.subtract(refundAmt);
                walletService.unfreezeForBooking(
                        booking.getLearner().getId(),
                        refundAmt,
                        booking.getId(),
                        "Dispute resolved: Partial refund");
                if (releaseAmt.compareTo(BigDecimal.ZERO) > 0) {
                    walletService.chargeFrozenForBooking(booking.getLearner().getId(), releaseAmt, booking.getId());
                    mentorPayoutAmount = releaseAmt.multiply(new BigDecimal("0.80"));
                    adminCommissionAmount = releaseAmt.subtract(mentorPayoutAmount);
                    walletService.payMentorForBooking(
                            booking.getMentor().getId(),
                            mentorPayoutAmount,
                            booking.getId());
                }
                refundAmount = refundAmt;
                releasedAmount = releaseAmt;
                booking.setStatus(BookingStatus.COMPLETED);
            }
            case PARTIAL_RELEASE -> {
                BigDecimal releaseAmt = partialAmount != null ? partialAmount : bookingAmount.multiply(new BigDecimal("0.5"));
                if (releaseAmt.compareTo(BigDecimal.ZERO) <= 0 || releaseAmt.compareTo(bookingAmount) > 0) {
                    throw new IllegalArgumentException("partialAmount không hợp lệ");
                }
                BigDecimal refundAmt = bookingAmount.subtract(releaseAmt);
                walletService.chargeFrozenForBooking(booking.getLearner().getId(), releaseAmt, booking.getId());
                if (refundAmt.compareTo(BigDecimal.ZERO) > 0) {
                    walletService.unfreezeForBooking(
                            booking.getLearner().getId(),
                            refundAmt,
                            booking.getId(),
                            "Dispute resolved: Partial refund");
                }
                refundAmount = refundAmt;
                releasedAmount = releaseAmt;
                mentorPayoutAmount = releaseAmt.multiply(new BigDecimal("0.80"));
                adminCommissionAmount = releaseAmt.subtract(mentorPayoutAmount);
                walletService.payMentorForBooking(
                        booking.getMentor().getId(),
                        mentorPayoutAmount,
                        booking.getId());
                booking.setStatus(BookingStatus.COMPLETED);
            }
        }

        dispute.setRefundAmount(refundAmount);
        dispute.setReleasedAmount(releasedAmount);
        dispute.setMentorPayoutAmount(mentorPayoutAmount);
        dispute.setAdminCommissionAmount(adminCommissionAmount);

        booking.setMeetingLink(null);
        bookingRepository.save(booking);
        dispute = disputeRepository.save(dispute);

        notificationService.createNotification(
                booking.getLearner().getId(),
                "Dispute đã được giải quyết",
                "Dispute đã được giải quyết: " + resolution.name(),
                NotificationType.DISPUTE_RESOLVED,
                dispute.getId().toString(),
                adminId);
        notificationService.createNotification(
                booking.getMentor().getId(),
                "Dispute đã được giải quyết",
                "Dispute đã được giải quyết: " + resolution.name(),
                NotificationType.DISPUTE_RESOLVED,
                dispute.getId().toString(),
                adminId);

        return dispute;
    }

    @Override
    @Transactional
    public BookingDisputeEvidence reviewEvidenceAndResolve(Long adminId, Long disputeId, Long evidenceId,
            BookingDisputeEvidence.EvidenceReviewStatus reviewStatus,
            BookingDispute.DisputeResolution mappedResolution,
            String notes) {
        BookingDispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new IllegalArgumentException("Dispute không tồn tại"));

        BookingDisputeEvidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new IllegalArgumentException("Evidence không tồn tại"));

        if (!evidence.getDisputeId().equals(disputeId)) {
            throw new IllegalArgumentException("Evidence không thuộc dispute này");
        }
        if (dispute.getStatus() == BookingDispute.DisputeStatus.RESOLVED) {
            throw new IllegalStateException("Dispute đã được giải quyết trước đó");
        }
        if (reviewStatus == null) {
            throw new IllegalArgumentException("Thiếu review status");
        }

        evidence.setReviewStatus(reviewStatus);
        evidence.setReviewedBy(adminId);
        evidence.setReviewedAt(LocalDateTime.now());
        evidence.setReviewNotes(notes);
        evidence.setIsOfficial(reviewStatus == BookingDisputeEvidence.EvidenceReviewStatus.ACCEPTED);
        evidence = evidenceRepository.save(evidence);

        if (reviewStatus == BookingDisputeEvidence.EvidenceReviewStatus.UNDER_REVIEW) {
            dispute.setStatus(BookingDispute.DisputeStatus.UNDER_INVESTIGATION);
            disputeRepository.save(dispute);
            return evidence;
        }

        if (mappedResolution != null) {
            resolveDispute(adminId, disputeId, mappedResolution, notes, null);
        }

        return evidence;
    }

    private void resetFinalVerificationIfNoActiveBooking(Booking booking) {
        if (booking.getJourneyId() == null || !"JOURNEY_MENTORING".equals(booking.getBookingType())) {
            return;
        }
        List<BookingStatus> activeStatuses = List.of(
                BookingStatus.PENDING, BookingStatus.CONFIRMED,
                BookingStatus.ONGOING, BookingStatus.PENDING_COMPLETION);
        boolean hasActiveBooking = bookingRepository.existsActiveJourneyBookingForAnyMentor(
                booking.getJourneyId(), activeStatuses);
        if (!hasActiveBooking) {
            journeyRepository.findById(booking.getJourneyId()).ifPresent(journey -> {
                journey.setFinalVerificationRequired(false);
                journeyRepository.save(journey);
            });
        }
    }

    @Override
    public Page<BookingDispute> getAllDisputes(BookingDispute.DisputeStatus status, Pageable pageable) {
        if (status != null) {
            return disputeRepository.findByStatus(status, pageable);
        }
        return disputeRepository.findAll(pageable);
    }
}
