package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.business_service.entity.JobEscrow;
import com.exe.skillverse_backend.business_service.entity.ReviewWindow;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.JobEscrowRepository;
import com.exe.skillverse_backend.business_service.repository.ReviewWindowRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.EscrowService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class EscrowSchedulerImpl {

    private final EscrowService escrowService;
    private final ReviewWindowRepository reviewWindowRepository;
    private final ShortTermJobRepository shortTermJobRepository;
    private final ShortTermJobApplicationRepository applicationRepository;
    private final NotificationService notificationService;

    private static final int REVIEW_WINDOW_HOURS = 72;

    /**
     * Release pending payouts from escrow to worker wallets.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void releasePendingPayouts() {
        log.info("Starting scheduled release pending payouts task...");
        escrowService.releasePendingPayouts();
    }

    /**
     * Auto-approve expired review windows.
     * When a review window expires (72 hours), automatically approve the work
     * and release the escrow.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000)
    @Transactional
    public void autoApproveExpiredReviews() {
        log.info("Starting auto-approve expired reviews task...");

        LocalDateTime now = LocalDateTime.now();
        List<ReviewWindow> expiredWindows = reviewWindowRepository.findActiveExpiredWindows(now);

        if (expiredWindows.isEmpty()) {
            log.info("No expired review windows found");
            return;
        }

        int approvedCount = 0;
        for (ReviewWindow window : expiredWindows) {
            try {
                autoApproveReviewWindow(window, now);
                approvedCount++;
            } catch (Exception e) {
                log.error("Failed to auto-approve review window {}: {}", window.getId(), e.getMessage());
            }
        }

        log.info("Auto-approve task completed. Approved {} review windows", approvedCount);
    }

    private void autoApproveReviewWindow(ReviewWindow window, LocalDateTime now) {
        // Mark window as auto-approved
        window.setStatus(ReviewWindow.ReviewStatus.AUTO_APPROVED);
        window.setAutoApprovedAt(now);
        reviewWindowRepository.save(window);

        // Update application status to APPROVED
        applicationRepository.findById(window.getApplicationId()).ifPresent(app -> {
            app.setStatus(ShortTermApplicationStatus.APPROVED);
            applicationRepository.save(app);
        });

        // Update job status to APPROVED
        shortTermJobRepository.findById(window.getJobId()).ifPresent(job -> {
            job.setStatus(ShortTermJobStatus.APPROVED);
            shortTermJobRepository.save(job);

            // Notify recruiter
            if (job.getRecruiterProfile() != null) {
                try {
                    notificationService.createNotification(
                            job.getRecruiterProfile().getUserId(),
                            "Work Auto-Approved",
                            "Work for job '" + job.getTitle() + "' has been auto-approved after the review period expired.",
                            NotificationType.REVIEW_WINDOW_EXPIRING,
                            String.valueOf(job.getId())
                    );
                } catch (Exception e) {
                    log.warn("Failed to send auto-approval notification: {}", e.getMessage());
                }
            }
        });

        log.info("Auto-approved review window {} for application {} and job {}",
                window.getId(), window.getApplicationId(), window.getJobId());
    }

    /**
     * Send reminders for expiring review windows.
     * Reminds recruiter 24 hours before expiry.
     * Runs every hour.
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void sendReviewReminders() {
        log.info("Starting review reminders task...");

        LocalDateTime reminderThreshold = LocalDateTime.now().plusHours(24);
        List<ReviewWindow> windowsNeedingReminder = reviewWindowRepository.findWindowsNeedingReminder(reminderThreshold);

        for (ReviewWindow window : windowsNeedingReminder) {
            try {
                shortTermJobRepository.findById(window.getJobId()).ifPresent(job -> {
                    if (job.getRecruiterProfile() != null) {
                        notificationService.createNotification(
                                job.getRecruiterProfile().getUserId(),
                                "Review Window Expiring Soon",
                                "Your review window for job '" + job.getTitle() + "' will expire in less than 24 hours. Please review the work.",
                                NotificationType.REVIEW_WINDOW_EXPIRING,
                                String.valueOf(job.getId())
                        );
                    }
                });

                window.setReminderSent(true);
                reviewWindowRepository.save(window);
                log.info("Sent reminder for review window {}", window.getId());
            } catch (Exception e) {
                log.error("Failed to send reminder for review window {}: {}", window.getId(), e.getMessage());
            }
        }

        log.info("Review reminders task completed. Sent {} reminders", windowsNeedingReminder.size());
    }
}
