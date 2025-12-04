package com.exe.skillverse_backend.mentor_booking_service.service;

import com.exe.skillverse_backend.mentor_booking_service.entity.Booking;
import com.exe.skillverse_backend.mentor_booking_service.entity.BookingStatus;
import com.exe.skillverse_backend.mentor_booking_service.repository.BookingRepository;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingAutoCancelScheduler {

    private final BookingRepository bookingRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void autoCancelUnapproved() {
        LocalDateTime deadline = LocalDateTime.now().minusHours(24);
        List<Booking> oldPendings = bookingRepository.findByStatusAndCreatedAtBefore(BookingStatus.PENDING, deadline);
        for (Booking booking : oldPendings) {
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);

            try {
                walletService.processRefund(
                        booking.getLearner().getId(),
                        booking.getPriceVnd(),
                        "Hoàn tiền do mentor không phản hồi",
                        "BOOKING_" + booking.getId()
                );
            } catch (Exception e) {
                log.warn("Refund failed for booking {}: {}", booking.getId(), e.getMessage());
            }

            notificationService.createNotification(
                    booking.getLearner().getId(),
                    "Booking bị hủy tự động",
                    "Mentor không phản hồi trong 24h. Hệ thống đã hoàn tiền.",
                    NotificationType.BOOKING_REFUND,
                    booking.getId().toString(),
                    booking.getMentor().getId()
            );
        }
    }
}

