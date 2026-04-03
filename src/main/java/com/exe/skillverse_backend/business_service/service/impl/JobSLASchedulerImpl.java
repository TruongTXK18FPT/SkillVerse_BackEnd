package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.business_service.entity.Dispute;
import com.exe.skillverse_backend.business_service.entity.Dispute.DisputeStatus;
import com.exe.skillverse_backend.business_service.entity.JobEscrow;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.DisputeRepository;
import com.exe.skillverse_backend.business_service.repository.JobEscrowRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.EscrowService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class JobSLASchedulerImpl {

    private final ShortTermJobApplicationRepository applicationRepository;
    private final ShortTermJobRepository shortTermJobRepository;
    private final DisputeRepository disputeRepository;
    private final JobEscrowRepository jobEscrowRepository;
    private final EscrowService escrowService;
    private final NotificationService notificationService;

    /**
     * Process all SLA deadlines.
     * Runs every 15 minutes.
     */
    @Scheduled(fixedRate = 900000)
    @Transactional
    public void processSLADeadlines() {
        log.info("Starting SLA deadline processing...");

        processRecruiterReviewOverdue();
        processDisputeEscalation();

        log.info("SLA deadline processing completed");
    }

    // ============================================================
    // 48h RECRUITER REVIEW OVERDUE → AUTO-APPROVE
    // ============================================================

    private void processRecruiterReviewOverdue() {
        List<ShortTermJobApplication> overdue = applicationRepository
                .findOverdueReviewApplications(LocalDateTime.now());

        if (overdue.isEmpty()) {
            return;
        }

        log.info("Found {} applications with overdue recruiter review (48h SLA)", overdue.size());

        for (ShortTermJobApplication app : overdue) {
            try {
                processRecruiterReviewOverdueSingle(app);
            } catch (Exception e) {
                log.error("Failed to auto-approve overdue application {}: {}", app.getId(), e.getMessage(), e);
            }
        }
    }

    private void processRecruiterReviewOverdueSingle(ShortTermJobApplication app) {
        // Idempotency: skip if already approved/completed
        if (app.getStatus() != ShortTermApplicationStatus.SUBMITTED) {
            log.info("Skipping application {} — status is {}, not SUBMITTED", app.getId(), app.getStatus());
            return;
        }

        log.info("Auto-approving application {}: recruiter exceeded 48h review SLA", app.getId());

        // Update application
        app.setStatus(ShortTermApplicationStatus.APPROVED);
        app.setLastActivityAt(LocalDateTime.now());
        // Unlock dispute eligibility — recruiter abused by not reviewing
        app.setDisputeEligibilityUnlocked(true);
        applicationRepository.save(app);

        // Update job
        ShortTermJob job = app.getShortTermJob();
        job.setStatus(ShortTermJobStatus.AUTO_APPROVED);
        shortTermJobRepository.save(job);

        // Release escrow to worker
        jobEscrowRepository.findByJobId(job.getId()).ifPresent(escrow -> {
            if (escrow.getStatus() == JobEscrow.EscrowStatus.FUNDED) {
                try {
                    escrowService.releaseEscrow(job.getId(), app.getUser().getId(),
                            "Auto-approved: recruiter exceeded 48h review SLA");
                    log.info("Escrow released for job {} via auto-approve SLA", job.getId());
                } catch (Exception e) {
                    log.warn("Could not release escrow for job {}: {}", job.getId(), e.getMessage());
                }
            }
        });

        // Notify worker
        notificationService.createNotification(
                app.getUser().getId(),
                "✅ Bàn giao được tự động duyệt",
                "Nhà tuyển dụng không phản hồi trong 48 giờ. Bàn giao đã được tự động duyệt. Tiền sẽ được chuyển.",
                NotificationType.SHORT_TERM_WORK_APPROVED,
                app.getId().toString()
        );

        // Notify recruiter
        notificationService.createNotification(
                job.getRecruiterProfile().getUserId(),
                "⚠️ Bàn giao tự động duyệt",
                "Bạn không phản hồi trong 48 giờ. Bàn giao đã được tự động duyệt và tiền đã được chuyển cho ứng viên.",
                NotificationType.WARNING,
                app.getId().toString()
        );

        log.info("Auto-approve SLA completed for application {}", app.getId());
    }

    // ============================================================
    // Cancellation requests are now reviewed by admin manually.
    // No more auto-cancel based on worker response timeout.
    // ============================================================

    /**
     * @deprecated Cancellation requests are now reviewed by admin manually.
     *             Worker response timeout for cancellation is no longer auto-processed.
     *             This method is kept for backward compatibility and will be removed in a future release.
     */
    @Deprecated
    private void processCancellationResponseOverdue() {
        List<ShortTermJobApplication> overdue = applicationRepository
                .findOverdueCancellationResponse(LocalDateTime.now());

        if (overdue.isEmpty()) {
            return;
        }

        log.info("Found {} applications with overdue cancellation response (72h SLA)", overdue.size());

        for (ShortTermJobApplication app : overdue) {
            try {
                processCancellationResponseOverdueSingle(app);
            } catch (Exception e) {
                log.error("Failed to auto-cancel overdue application {}: {}", app.getId(), e.getMessage(), e);
            }
        }
    }

    private void processCancellationResponseOverdueSingle(ShortTermJobApplication app) {
        // Idempotency: skip if already cancelled
        if (app.getStatus() != ShortTermApplicationStatus.CANCELLATION_REQUESTED) {
            log.info("Skipping application {} — status is {}, not CANCELLATION_REQUESTED",
                    app.getId(), app.getStatus());
            return;
        }

        log.info("Auto-cancelling application {}: user exceeded 72h response SLA for cancellation", app.getId());

        // Update application
        app.setStatus(ShortTermApplicationStatus.AUTO_CANCELLED);
        app.setLastActivityAt(LocalDateTime.now());
        applicationRepository.save(app);

        // Update job
        ShortTermJob job = app.getShortTermJob();
        job.setStatus(ShortTermJobStatus.AUTO_CANCELLED);
        shortTermJobRepository.save(job);

        // Refund escrow to recruiter
        jobEscrowRepository.findByJobId(job.getId()).ifPresent(escrow -> {
            if (escrow.getStatus() == JobEscrow.EscrowStatus.FUNDED ||
                    escrow.getStatus() == JobEscrow.EscrowStatus.PARTIALLY_RELEASED) {
                try {
                    escrowService.refundEscrow(job.getId(), job.getRecruiterProfile().getUserId(),
                            "Auto-cancelled: user did not respond within 72 hours of cancellation request");
                    log.info("Escrow refunded to recruiter for job {} via auto-cancel SLA", job.getId());
                } catch (Exception e) {
                    log.warn("Could not refund escrow for job {}: {}", job.getId(), e.getMessage());
                }
            }
        });

        // Notify worker
        notificationService.createNotification(
                app.getUser().getId(),
                "⚠️ Công việc bị hủy tự động",
                "Bạn không phản hồi yêu cầu hủy trong 72 giờ. Công việc đã bị hủy tự động và tiền escrow đã được hoàn cho nhà tuyển dụng.",
                NotificationType.WORKER_AUTO_CANCELLED,
                job.getId().toString()
        );

        // Notify recruiter
        notificationService.createNotification(
                job.getRecruiterProfile().getUserId(),
                "✅ Công việc bị hủy tự động",
                "Ứng viên không phản hồi trong 72 giờ. Tiền escrow đã được hoàn cho bạn.",
                NotificationType.ESCROW_REFUNDED,
                job.getId().toString()
        );

        log.info("Auto-cancel SLA completed for application {}", app.getId());
    }

    // ============================================================
    // 5-DAY DISPUTE RESOLUTION OVERDUE → ESCALATE
    // ============================================================

    private void processDisputeEscalation() {
        List<Dispute> overdue = disputeRepository.findOverdueDisputes(LocalDateTime.now());

        if (overdue.isEmpty()) {
            return;
        }

        log.warn("Found {} disputes with overdue admin resolution (5-day SLA)", overdue.size());

        for (Dispute dispute : overdue) {
            try {
                processDisputeEscalationSingle(dispute);
            } catch (Exception e) {
                log.error("Failed to escalate dispute {}: {}", dispute.getId(), e.getMessage(), e);
            }
        }
    }

    private void processDisputeEscalationSingle(Dispute dispute) {
        // Idempotency: skip if already escalated
        if (dispute.getStatus() == DisputeStatus.ESCALATED) {
            log.info("Skipping dispute {} — already escalated", dispute.getId());
            return;
        }

        log.warn("Escalating dispute {}: admin exceeded 5-day resolution SLA", dispute.getId());

        dispute.setStatus(DisputeStatus.ESCALATED);
        dispute.setEscalationLevel(1);
        dispute.setPriority("HIGH");
        dispute.setEscalatedAt(LocalDateTime.now());
        disputeRepository.save(dispute);

        // Update job status to ESCALATED
        if (dispute.getShortTermJob() != null) {
            ShortTermJob job = dispute.getShortTermJob();
            job.setStatus(ShortTermJobStatus.ESCALATED);
            shortTermJobRepository.save(job);
        }

        // Note: Super-admin notifications for escalated disputes should be handled
        // via the admin dashboard, which queries disputes with status = ESCALATED.
        // A role-based notification method can be added to NotificationService if needed.
        log.warn("Dispute {} escalated — super-admin should be notified via admin dashboard", dispute.getId());
    }
}
