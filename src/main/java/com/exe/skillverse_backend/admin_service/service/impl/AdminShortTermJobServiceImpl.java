package com.exe.skillverse_backend.admin_service.service.impl;

import com.exe.skillverse_backend.admin_service.dto.request.ResolveDisputeAdminRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminJobStatsResponse;
import com.exe.skillverse_backend.admin_service.service.AdminShortTermJobService;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermJobResponse;
import com.exe.skillverse_backend.business_service.entity.Dispute;
import com.exe.skillverse_backend.business_service.entity.JobEscrow;
import com.exe.skillverse_backend.business_service.entity.JobStatusAuditLog;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.DisputeRepository;
import com.exe.skillverse_backend.business_service.repository.JobEscrowRepository;
import com.exe.skillverse_backend.business_service.repository.JobStatusAuditLogRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.DisputeService;
import com.exe.skillverse_backend.business_service.service.EscrowService;
import com.exe.skillverse_backend.business_service.service.ShortTermJobService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminShortTermJobServiceImpl implements AdminShortTermJobService {

    private final ShortTermJobRepository shortTermJobRepository;
    private final ShortTermJobService shortTermJobService;
    private final DisputeRepository disputeRepository;
    private final DisputeService disputeService;
    private final ShortTermJobApplicationRepository applicationRepository;
    private final JobStatusAuditLogRepository auditLogRepository;
    private final JobEscrowRepository jobEscrowRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletService walletService;
    private final EscrowService escrowService;
    private final NotificationService notificationService;
    private final EmailService emailService;

    private static final BigDecimal SHORT_TERM_JOB_POSTING_FEE = new BigDecimal("30000");

    // ==================== EXISTING APPROVAL METHODS ====================

    @Override
    @Transactional(readOnly = true)
    public List<ShortTermJobResponse> getPendingJobs() {
        log.info("Fetching all pending short-term jobs for admin");
        List<ShortTermJob> jobs = shortTermJobRepository.findByStatus(ShortTermJobStatus.PENDING_APPROVAL);
        return jobs.stream()
                .map(job -> shortTermJobService.getJobDetails(job.getId()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ShortTermJobResponse approveJob(Long jobId) {
        log.info("Admin approving short-term job ID: {}", jobId);

        ShortTermJob job = shortTermJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Short-term job not found with ID: " + jobId));

        if (job.getStatus() != ShortTermJobStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Job is not in PENDING_APPROVAL status. Current: " + job.getStatus());
        }

        job.setStatus(ShortTermJobStatus.PUBLISHED);
        job.setPublishedAt(LocalDateTime.now());
        ShortTermJob savedJob = shortTermJobRepository.save(job);

        Long recruiterId = job.getRecruiterProfile().getUser().getId();
        String recruiterEmail = job.getRecruiterProfile().getUser().getEmail();
        String recruiterName = job.getRecruiterProfile().getUser().getFullName() != null
                ? job.getRecruiterProfile().getUser().getFullName() : "Recruiter";

        try {
            notificationService.createNotification(
                    recruiterId,
                    "Công việc đã được duyệt",
                    "Công việc \"" + job.getTitle() + "\" đã được duyệt và đăng thành công trên SkillVerse.",
                    NotificationType.JOB_APPROVED,
                    String.valueOf(jobId)
            );
        } catch (Exception e) {
            log.warn("Failed to send in-app notification for approved job {}: {}", jobId, e.getMessage());
        }

        try {
            emailService.sendJobApprovalNotification(
                    recruiterEmail,
                    job.getTitle(),
                    "Công việc của bạn đã được duyệt và đăng thành công. Ứng viên có thể nộp đơn ứng tuyển ngay bây giờ."
            );
        } catch (Exception e) {
            log.warn("Failed to send approval email for job {}: {}", jobId, e.getMessage());
        }

        log.info("Short-term job ID: {} approved and published. Notified recruiter {}.", jobId, recruiterId);
        return shortTermJobService.getJobDetails(savedJob.getId());
    }

    @Override
    @Transactional
    public ShortTermJobResponse rejectJob(Long jobId, String reason) {
        log.info("Admin rejecting short-term job ID: {} with reason: {}", jobId, reason);

        ShortTermJob job = shortTermJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Short-term job not found with ID: " + jobId));

        if (job.getStatus() != ShortTermJobStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Job is not in PENDING_APPROVAL status. Current: " + job.getStatus());
        }

        if (job.getPaidViaSubscription() == null || !job.getPaidViaSubscription()) {
            Long recruiterId = job.getRecruiterProfile().getUser().getId();
            walletService.processRefund(
                    recruiterId,
                    SHORT_TERM_JOB_POSTING_FEE,
                    "Hoàn tiền phí đăng tin ngắn hạn bị từ chối",
                    "JOB_POSTING_REFUND",
                    String.valueOf(jobId)
            );
            log.info("Refunded 30,000 VND to recruiter user ID: {} for rejected short-term job ID: {}", recruiterId, jobId);
        }

        job.setStatus(ShortTermJobStatus.DRAFT);
        ShortTermJob savedJob = shortTermJobRepository.save(job);

        Long recruiterId = job.getRecruiterProfile().getUser().getId();
        String recruiterEmail = job.getRecruiterProfile().getUser().getEmail();

        try {
            notificationService.createNotification(
                    recruiterId,
                    "Công việc bị từ chối",
                    "Công việc \"" + job.getTitle() + "\" đã bị từ chối. Lý do: " + (reason != null ? reason : "Không có lý do cụ thể."),
                    NotificationType.JOB_REJECTED,
                    String.valueOf(jobId)
            );
        } catch (Exception e) {
            log.warn("Failed to send notification for rejected job {}: {}", jobId, e.getMessage());
        }

        try {
            emailService.sendJobRejectionNotification(
                    recruiterEmail,
                    job.getTitle(),
                    reason != null ? reason : "Không có lý do cụ thể."
            );
        } catch (Exception e) {
            log.warn("Failed to send rejection email for job {}: {}", jobId, e.getMessage());
        }

        log.info("Short-term job ID: {} rejected, reverted to DRAFT. Notified recruiter {}.", jobId, recruiterId);
        return shortTermJobService.getJobDetails(savedJob.getId());
    }

    // ==================== FULL JOB MANAGEMENT ====================

    @Override
    @Transactional(readOnly = true)
    public Page<ShortTermJobResponse> getAllJobs(ShortTermJobStatus status, Pageable pageable) {
        log.info("Fetching all short-term jobs for admin. Status filter: {}", status);
        Page<ShortTermJob> jobs;
        if (status != null) {
            jobs = shortTermJobRepository.findByStatus(status, pageable);
        } else {
            jobs = shortTermJobRepository.findAll(pageable);
        }
        return jobs.map(job -> shortTermJobService.getJobDetails(job.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public ShortTermJobResponse getJobDetail(Long jobId) {
        ShortTermJob job = shortTermJobRepository.findByIdWithRecruiter(jobId)
                .orElseThrow(() -> new NotFoundException("Short-term job not found with ID: " + jobId));
        return shortTermJobService.getJobDetails(job.getId());
    }

    @Override
    @Transactional
    public ShortTermJobResponse deleteJob(Long adminId, Long jobId) {
        log.info("Admin {} deleting short-term job ID: {}", adminId, jobId);

        ShortTermJob job = shortTermJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Short-term job not found with ID: " + jobId));

        // Only allow delete for non-terminal statuses
        if (job.getStatus() == ShortTermJobStatus.PAID || job.getStatus() == ShortTermJobStatus.CLOSED) {
            throw new BadRequestException("Cannot delete a job that is already PAID or CLOSED");
        }

        ShortTermJobStatus oldStatus = job.getStatus();
        job.setStatus(ShortTermJobStatus.CANCELLED);
        ShortTermJob savedJob = shortTermJobRepository.save(job);

        // Create audit log
        createAuditLog(savedJob, oldStatus, ShortTermJobStatus.CANCELLED, adminId,
                "ADMIN", "Admin deleted job");

        // Notify recruiter
        Long recruiterId = job.getRecruiterProfile().getUser().getId();
        try {
            notificationService.createNotification(
                    recruiterId,
                    "Công việc bị xóa",
                    "Công việc \"" + job.getTitle() + "\" đã bị admin xóa khỏi hệ thống.",
                    NotificationType.JOB_REJECTED,
                    String.valueOf(jobId)
            );
        } catch (Exception e) {
            log.warn("Failed to notify recruiter for deleted job {}: {}", jobId, e.getMessage());
        }

        log.info("Admin {} deleted short-term job ID: {}. Status changed from {} to CANCELLED",
                adminId, jobId, oldStatus);
        return shortTermJobService.getJobDetails(savedJob.getId());
    }

    @Override
    @Transactional
    public ShortTermJobResponse banJob(Long adminId, Long jobId, String reason) {
        log.info("Admin {} banning short-term job ID: {} with reason: {}", adminId, jobId, reason);

        ShortTermJob job = shortTermJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Short-term job not found with ID: " + jobId));

        if (Boolean.TRUE.equals(job.getIsBanned())) {
            throw new BadRequestException("Job is already banned");
        }

        ShortTermJobStatus oldStatus = job.getStatus();

        // Set ban fields
        job.setIsBanned(true);
        job.setBanReason(reason != null ? reason : "Vi phạm quy định");
        job.setBannedAt(LocalDateTime.now());
        job.setBannedBy(adminId);

        // Close the job if still active
        if (job.getStatus() == ShortTermJobStatus.PUBLISHED
                || job.getStatus() == ShortTermJobStatus.APPLIED
                || job.getStatus() == ShortTermJobStatus.IN_PROGRESS) {
            job.setStatus(ShortTermJobStatus.CLOSED);
        }

        ShortTermJob savedJob = shortTermJobRepository.save(job);

        // Create audit log
        createAuditLog(savedJob, oldStatus, savedJob.getStatus(), adminId,
                "ADMIN", "Admin banned job: " + reason);

        // Notify recruiter
        Long recruiterId = job.getRecruiterProfile().getUser().getId();
        try {
            notificationService.createNotification(
                    recruiterId,
                    "Công việc bị khóa",
                    "Công việc \"" + job.getTitle() + "\" đã bị admin khóa. Lý do: " + (reason != null ? reason : "Vi phạm quy định"),
                    NotificationType.JOB_REJECTED,
                    String.valueOf(jobId)
            );
        } catch (Exception e) {
            log.warn("Failed to notify recruiter for banned job {}: {}", jobId, e.getMessage());
        }

        log.info("Admin {} banned short-term job ID: {}. Status changed to {}", adminId, jobId, savedJob.getStatus());
        return shortTermJobService.getJobDetails(savedJob.getId());
    }

    @Override
    @Transactional
    public ShortTermJobResponse unbanJob(Long adminId, Long jobId) {
        log.info("Admin {} unbanning short-term job ID: {}", adminId, jobId);

        ShortTermJob job = shortTermJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Short-term job not found with ID: " + jobId));

        if (!Boolean.TRUE.equals(job.getIsBanned())) {
            throw new BadRequestException("Job is not banned");
        }

        // Clear ban fields
        job.setIsBanned(false);
        job.setBanReason(null);
        job.setBannedAt(null);
        job.setBannedBy(null);

        // If job was closed due to ban, revert to DRAFT so recruiter can re-submit
        if (job.getStatus() == ShortTermJobStatus.CLOSED) {
            job.setStatus(ShortTermJobStatus.DRAFT);
        }

        ShortTermJob savedJob = shortTermJobRepository.save(job);

        // Create audit log
        createAuditLog(savedJob, ShortTermJobStatus.CLOSED, savedJob.getStatus(), adminId,
                "ADMIN", "Admin unbanned job");

        // Notify recruiter
        Long recruiterId = job.getRecruiterProfile().getUser().getId();
        try {
            notificationService.createNotification(
                    recruiterId,
                    "Công việc được mở khóa",
                    "Công việc \"" + job.getTitle() + "\" đã được admin mở khóa.",
                    NotificationType.JOB_APPROVED,
                    String.valueOf(jobId)
            );
        } catch (Exception e) {
            log.warn("Failed to notify recruiter for unbanned job {}: {}", jobId, e.getMessage());
        }

        log.info("Admin {} unbanned short-term job ID: {}", adminId, jobId);
        return shortTermJobService.getJobDetails(savedJob.getId());
    }

    // ==================== DISPUTE MANAGEMENT ====================

    @Override
    @Transactional(readOnly = true)
    public Page<Dispute> getAllDisputes(Dispute.DisputeStatus status, Pageable pageable) {
        log.info("Fetching disputes for admin. Status filter: {}", status);
        if (status != null) {
            return disputeRepository.findByStatusInPaginated(
                    List.of(status), pageable);
        }
        return disputeRepository.findByStatusInPaginated(
                Arrays.asList(
                        Dispute.DisputeStatus.OPEN,
                        Dispute.DisputeStatus.UNDER_INVESTIGATION,
                        Dispute.DisputeStatus.AWAITING_RESPONSE,
                        Dispute.DisputeStatus.ESCALATED
                ),
                pageable
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Dispute getDisputeDetail(Long disputeId) {
        return disputeRepository.findById(disputeId)
                .orElseThrow(() -> new NotFoundException("Dispute not found with ID: " + disputeId));
    }

    @Override
    @Transactional
    public Dispute resolveDispute(Long adminId, Long disputeId, ResolveDisputeAdminRequest request) {
        log.info("Admin {} resolving dispute ID: {} with resolution: {}", adminId, disputeId, request.getResolution());

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new NotFoundException("Dispute not found with ID: " + disputeId));

        if (dispute.getStatus() == Dispute.DisputeStatus.RESOLVED
                || dispute.getStatus() == Dispute.DisputeStatus.DISMISSED) {
            throw new BadRequestException("Dispute is already resolved or dismissed");
        }

        // Set resolution fields
        dispute.setResolution(request.getResolution());
        dispute.setResolutionNotes(request.getResolutionNotes());
        dispute.setPartialRefundPct(request.getPartialRefundPct());
        dispute.setResolvedBy(adminId);
        dispute.setResolvedAt(LocalDateTime.now());
        dispute.setStatus(Dispute.DisputeStatus.RESOLVED);

        // Handle escrow based on resolution
        Long jobId = dispute.getJobId();
        try {
            switch (request.getResolution()) {
                case FULL_REFUND -> {
                    escrowService.refundEscrow(jobId, adminId, "Admin resolved dispute: Full refund - " + request.getResolutionNotes());
                    log.info("Admin resolved dispute {} with FULL_REFUND for job {}", disputeId, jobId);
                }
                case FULL_RELEASE -> {
                    escrowService.releaseEscrow(jobId, adminId, "Admin resolved dispute: Full release - " + request.getResolutionNotes());
                    log.info("Admin resolved dispute {} with FULL_RELEASE for job {}", disputeId, jobId);
                }
                case PARTIAL_REFUND, PARTIAL_RELEASE -> {
                    // For partial refund/release, we need a partial release method
                    // For now, log and handle in escrow service
                    log.info("Admin resolved dispute {} with {} for job {}. Partial pct: {}",
                            disputeId, request.getResolution(), jobId, request.getPartialRefundPct());
                    // TODO: Implement partial release/refund in escrow service if needed
                }
                case RESUBMIT_REQUIRED -> {
                    // Change job status back to IN_PROGRESS
                    ShortTermJob job = shortTermJobRepository.findById(jobId).orElse(null);
                    if (job != null) {
                        job.setStatus(ShortTermJobStatus.IN_PROGRESS);
                        shortTermJobRepository.save(job);
                        log.info("Admin resolved dispute {} with RESUBMIT_REQUIRED, job {} status reverted to IN_PROGRESS", disputeId, jobId);
                    }
                }
                case NO_ACTION -> {
                    dispute.setStatus(Dispute.DisputeStatus.DISMISSED);
                    log.info("Admin dismissed dispute {} with NO_ACTION for job {}", disputeId, jobId);
                }
                default -> log.warn("Unknown resolution type for dispute {}: {}", disputeId, request.getResolution());
            }
        } catch (Exception e) {
            log.error("Failed to process escrow for dispute {}: {}", disputeId, e.getMessage());
            // Continue - dispute is still resolved even if escrow fails
        }

        Dispute savedDispute = disputeRepository.save(dispute);

        // Notify both parties
        try {
            notificationService.createNotification(
                    dispute.getInitiatorId(),
                    "Khiếu nại đã được giải quyết",
                    "Khiếu nại cho công việc đã được admin giải quyết. Xem chi tiết trong hệ thống.",
                    NotificationType.DISPUTE_RESOLVED,
                    String.valueOf(jobId)
            );
            notificationService.createNotification(
                    dispute.getRespondentId(),
                    "Khiếu nại đã được giải quyết",
                    "Khiếu nại cho công việc đã được admin giải quyết. Xem chi tiết trong hệ thống.",
                    NotificationType.DISPUTE_RESOLVED,
                    String.valueOf(jobId)
            );
        } catch (Exception e) {
            log.warn("Failed to send dispute resolution notifications for dispute {}: {}", disputeId, e.getMessage());
        }

        log.info("Admin {} resolved dispute ID: {} with resolution {}", adminId, disputeId, request.getResolution());
        return savedDispute;
    }

    // ==================== DASHBOARD STATS ====================

    @Override
    @Transactional(readOnly = true)
    public AdminJobStatsResponse getJobStats() {
        log.info("Generating admin job statistics");

        long draftCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.DRAFT);
        long pendingCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.PENDING_APPROVAL);
        long publishedCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.PUBLISHED);
        long appliedCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.APPLIED);
        long inProgressCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.IN_PROGRESS);
        long submittedCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.SUBMITTED);
        long underReviewCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.UNDER_REVIEW);
        long approvedCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.APPROVED);
        long completedCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.COMPLETED);
        long paidCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.PAID);
        long cancelledCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.CANCELLED);
        long disputedCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.DISPUTED);
        long closedCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.CLOSED);
        long rejectedCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.REJECTED);

        long totalJobs = draftCount + pendingCount + publishedCount + appliedCount + inProgressCount
                + submittedCount + underReviewCount + approvedCount + completedCount + paidCount
                + cancelledCount + disputedCount + closedCount + rejectedCount;

        Map<String, Long> byStatus = new HashMap<>();
        byStatus.put("DRAFT", draftCount);
        byStatus.put("PENDING_APPROVAL", pendingCount);
        byStatus.put("PUBLISHED", publishedCount);
        byStatus.put("APPLIED", appliedCount);
        byStatus.put("IN_PROGRESS", inProgressCount);
        byStatus.put("SUBMITTED", submittedCount);
        byStatus.put("UNDER_REVIEW", underReviewCount);
        byStatus.put("APPROVED", approvedCount);
        byStatus.put("COMPLETED", completedCount);
        byStatus.put("PAID", paidCount);
        byStatus.put("CANCELLED", cancelledCount);
        byStatus.put("DISPUTED", disputedCount);
        byStatus.put("CLOSED", closedCount);
        byStatus.put("REJECTED", rejectedCount);

        // Dispute stats
        long openDisputes = disputeRepository.countByStatus(Dispute.DisputeStatus.OPEN);
        long investigatingDisputes = disputeRepository.countByStatus(Dispute.DisputeStatus.UNDER_INVESTIGATION);
        long awaitingDisputes = disputeRepository.countByStatus(Dispute.DisputeStatus.AWAITING_RESPONSE);
        byStatus.put("DISPUTE_OPEN", openDisputes);
        byStatus.put("DISPUTE_INVESTIGATING", investigatingDisputes);
        byStatus.put("DISPUTE_AWAITING", awaitingDisputes);

        // Earnings stats from escrow and wallet transactions
        BigDecimal totalPlatformFee = jobEscrowRepository.getTotalPlatformFee();
        BigDecimal totalEscrowVolume = jobEscrowRepository.getTotalEscrowVolume();
        BigDecimal totalRecruiterEarnings = walletTransactionRepository.getTotalJobPayouts();
        long activeEscrows = jobEscrowRepository.countByStatus(JobEscrow.EscrowStatus.FUNDED)
                + jobEscrowRepository.countByStatus(JobEscrow.EscrowStatus.PARTIALLY_RELEASED);

        return AdminJobStatsResponse.builder()
                .totalJobs(totalJobs)
                .draftCount(draftCount)
                .pendingApprovalCount(pendingCount)
                .publishedCount(publishedCount)
                .inProgressCount(inProgressCount)
                .completedCount(completedCount)
                .paidCount(paidCount)
                .cancelledCount(cancelledCount)
                .disputedCount(disputedCount + openDisputes + investigatingDisputes + awaitingDisputes)
                .closedCount(closedCount)
                .rejectedCount(rejectedCount)
                .byStatus(byStatus)
                .byUrgency(new HashMap<>())
                .totalPlatformEarnings(totalPlatformFee != null ? totalPlatformFee.longValue() : 0L)
                .totalRecruiterEarnings(totalRecruiterEarnings != null ? totalRecruiterEarnings.longValue() : 0L)
                .totalEscrowVolume(totalEscrowVolume != null ? totalEscrowVolume.longValue() : 0L)
                .activeEscrows(activeEscrows)
                .build();
    }

    // ==================== HELPER METHODS ====================

    private void createAuditLog(ShortTermJob job, ShortTermJobStatus previousStatus,
                                ShortTermJobStatus newStatus, Long adminId, String role, String reason) {
        try {
            JobStatusAuditLog auditLog = JobStatusAuditLog.builder()
                    .shortTermJobId(job.getId())
                    .previousStatus(previousStatus.name())
                    .newStatus(newStatus.name())
                    .changedBy(null) // Admin entity not available here
                    .changedByRole(JobStatusAuditLog.AuditRole.ADMIN)
                    .reason(reason)
                    .metadata("{\"adminId\":" + adminId + "}")
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to create audit log for job {} status change: {}", job.getId(), e.getMessage());
        }
    }
}
