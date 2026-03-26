package com.exe.skillverse_backend.mentor_booking_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
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
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
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
    private final WalletTransactionRepository transactionRepository;

    @Override
    @Transactional
    public BookingDispute openDispute(Long userId, Long bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking không tồn tại"));

        // Only learner can open dispute
        if (!booking.getLearner().getId().equals(userId)) {
            throw new IllegalArgumentException("Chỉ người học được phép mở dispute");
        }

        // Can only dispute when mentor has completed or when session time has passed
        BookingStatus status = booking.getStatus();
        boolean sessionEnded = LocalDateTime.now().isAfter(booking.getEndTime());
        boolean canDispute = status == BookingStatus.MENTOR_COMPLETED
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
                // Refund learner full amount (unfreeze)
                walletService.unfreezeForBooking(
                        booking.getLearner().getId(),
                        bookingAmount,
                        booking.getId(),
                        "Dispute resolved: Full refund to learner");
                refundAmount = bookingAmount;
                booking.setStatus(BookingStatus.REFUNDED);
            }
            case FULL_RELEASE -> {
                // Release to mentor (normal completion flow)
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
                // Split: partial refund to learner, rest to mentor
                BigDecimal refundAmt = partialAmount != null ? partialAmount : bookingAmount.multiply(new BigDecimal("0.5"));
                BigDecimal releaseAmt = bookingAmount.subtract(refundAmt);
                walletService.unfreezeForBooking(
                        booking.getLearner().getId(),
                        refundAmt,
                        booking.getId(),
                        "Dispute resolved: Partial refund");
                walletService.chargeFrozenForBooking(booking.getLearner().getId(), releaseAmt, booking.getId());
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
            case PARTIAL_RELEASE -> {
                // Partial to mentor, partial refund
                BigDecimal releaseAmt = partialAmount != null ? partialAmount : bookingAmount.multiply(new BigDecimal("0.5"));
                BigDecimal refundAmt = bookingAmount.subtract(releaseAmt);
                walletService.chargeFrozenForBooking(booking.getLearner().getId(), releaseAmt, booking.getId());
                refundAmount = refundAmt;
                releasedAmount = releaseAmt;
                mentorPayoutAmount = releaseAmt.multiply(new BigDecimal("0.80"));
                adminCommissionAmount = releaseAmt.subtract(mentorPayoutAmount);
                walletService.payMentorForBooking(
                        booking.getMentor().getId(),
                        mentorPayoutAmount,
                        booking.getId());
                walletService.unfreezeForBooking(
                        booking.getLearner().getId(),
                        refundAmt,
                        booking.getId(),
                        "Dispute resolved: Partial refund");
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

        // Notify both parties
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
    public Page<BookingDispute> getAllDisputes(BookingDispute.DisputeStatus status, Pageable pageable) {
        if (status != null) {
            return disputeRepository.findByStatus(status, pageable);
        }
        return disputeRepository.findAll(pageable);
    }
}
