package com.exe.skillverse_backend.mentor_booking_service.service.impl;

import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingDisputeRepository;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.impl.NotificationServiceImpl;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.impl.WalletServiceImpl;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingAutoCancelSchedulerImpl {

    private final BookingRepository bookingRepository;
    private final BookingDisputeRepository disputeRepository;
    private final WalletServiceImpl walletService;
    private final NotificationServiceImpl notificationService;
    private final WalletTransactionRepository transactionRepository;

    /**
     * Part 3+5: Auto-cancel bookings PENDING for >24h from creation (mentor never responded).
     * Runs every 10 minutes.
     * Now with idempotency check to prevent double-refund.
     */
    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void autoCancelUnapproved() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(24);
        List<Booking> oldPendings = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, deadline);
        for (Booking booking : oldPendings) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setMeetingLink(null);
            bookingRepository.save(booking);

            // Part 3: Idempotency check before refund
            boolean alreadyCancelled = transactionRepository.existsByReferenceIdAndReferenceTypeAndStatus(
                    "BOOKING_" + booking.getId(),
                    "BOOKING_REFUND",
                    WalletTransaction.TransactionStatus.COMPLETED);
            if (!alreadyCancelled) {
                walletService.processRefund(
                        booking.getLearner().getId(),
                        booking.getPriceVnd(),
                        "Hoàn tiền do mentor không phản hồi",
                        "BOOKING_" + booking.getId());
            }

            notificationService.createNotification(
                    booking.getLearner().getId(),
                    "Booking bị hủy tự động",
                    "Mentor không phản hồi trong 24h. Hệ thống đã hoàn tiền.",
                    NotificationType.BOOKING_REFUND,
                    booking.getId().toString(),
                    booking.getMentor().getId());
        }
    }

    /**
     * Part 3+5: Auto-cancel PENDING bookings when start time has arrived.
     * If mentor hasn't approved by the session start time, auto-cancel + refund.
     * Runs every minute.
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void autoCancelAtStartTime() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> expiredPendings = bookingRepository
                .findByStatusAndStartTimeLessThanEqual(BookingStatus.PENDING, now);
        for (Booking booking : expiredPendings) {
            booking.setStatus(BookingStatus.CANCELLED);
            booking.setMeetingLink(null);
            bookingRepository.save(booking);

            // Idempotency check
            boolean alreadyRefunded = transactionRepository.existsByReferenceIdAndReferenceTypeAndStatus(
                    "BOOKING_" + booking.getId(),
                    "BOOKING_REFUND",
                    WalletTransaction.TransactionStatus.COMPLETED);
            if (!alreadyRefunded) {
                walletService.processRefund(
                        booking.getLearner().getId(),
                        booking.getPriceVnd(),
                        "Hoàn tiền do mentor không duyệt trước giờ học",
                        "BOOKING_" + booking.getId());
            }

            notificationService.createNotification(
                    booking.getLearner().getId(),
                    "Booking bị hủy tự động",
                    "Mentor không duyệt trước giờ học. Hệ thống đã hoàn tiền.",
                    NotificationType.BOOKING_REFUND,
                    booking.getId().toString(),
                    booking.getMentor().getId());
        }
    }

    /**
     * Part 6: Auto-complete MENTOR_COMPLETED bookings after 1 day past booking end time.
     * If learner doesn't confirm and there's no dispute, auto-complete + release payment.
     * Runs every 10 minutes.
     */
    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void autoCompleteOldMentorCompleted() {
        LocalDateTime deadline = LocalDateTime.now().minusDays(1);
        List<Booking> expired = bookingRepository
                .findByStatusAndMentorCompletedAtBefore(BookingStatus.MENTOR_COMPLETED, deadline);
        for (Booking booking : expired) {
            // Skip if dispute exists
            if (disputeRepository.existsByBooking_Id(booking.getId())) {
                continue;
            }

            // Auto-complete: release payment to mentor
            walletService.chargeFrozenForBooking(
                    booking.getLearner().getId(),
                    booking.getPriceVnd(),
                    booking.getId());
            java.math.BigDecimal mentorPay = booking.getPriceVnd().multiply(new java.math.BigDecimal("0.80"));
            walletService.payMentorForBooking(
                    booking.getMentor().getId(),
                    mentorPay,
                    booking.getId());

            booking.setStatus(BookingStatus.COMPLETED);
            booking.setLearnerConfirmedAt(LocalDateTime.now());
            bookingRepository.save(booking);

            notificationService.createNotification(
                    booking.getLearner().getId(),
                    "Booking tự động hoàn tất",
                    "Buổi học đã được tự động hoàn tất do bạn không phản hồi trong 24h.",
                    NotificationType.BOOKING_COMPLETED,
                    booking.getId().toString(),
                    booking.getMentor().getId());
            notificationService.createNotification(
                    booking.getMentor().getId(),
                    "Booking tự động hoàn tất",
                    "Buổi học đã được tự động hoàn tất do learner không phản hồi.",
                    NotificationType.BOOKING_COMPLETED,
                    booking.getId().toString(),
                    booking.getMentor().getId());
        }
    }
}
