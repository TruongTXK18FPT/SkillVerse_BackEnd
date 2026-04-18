package com.exe.skillverse_backend.admin_service.service.impl;

import com.exe.skillverse_backend.admin_service.dto.request.RejectCancellationRequest;
import com.exe.skillverse_backend.admin_service.dto.request.ResolveDisputeAdminRequest;
import com.exe.skillverse_backend.admin_service.dto.response.AdminDisputeResponse;
import com.exe.skillverse_backend.admin_service.dto.response.AdminJobStatsResponse;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.admin_service.service.AdminShortTermJobService;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermJobResponse;
import com.exe.skillverse_backend.business_service.entity.Dispute;
import com.exe.skillverse_backend.business_service.entity.DisputeEvidence;
import com.exe.skillverse_backend.business_service.entity.DisputeResponseEntity;
import com.exe.skillverse_backend.business_service.entity.EscrowTransaction;
import com.exe.skillverse_backend.business_service.entity.EscrowTransaction.EscrowTransactionType;
import com.exe.skillverse_backend.business_service.entity.JobEscrow;
import com.exe.skillverse_backend.business_service.entity.JobStatusAuditLog;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.DisputeRepository;
import com.exe.skillverse_backend.business_service.repository.EscrowTransactionRepository;
import com.exe.skillverse_backend.business_service.repository.JobEscrowRepository;
import com.exe.skillverse_backend.business_service.repository.JobStatusAuditLogRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.EscrowService;
import com.exe.skillverse_backend.business_service.service.JobAuditService;
import com.exe.skillverse_backend.business_service.service.ShortTermJobService;
import com.exe.skillverse_backend.business_service.service.DisputeService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final JobAuditService auditService;
    private final JobEscrowRepository jobEscrowRepository;
    private final EscrowTransactionRepository escrowTransactionRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletService walletService;
    private final EscrowService escrowService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final UserRepository userRepository;

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
    public ShortTermJobResponse deleteJob(Long adminId, Long jobId, String reason) {
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
        String normalizedReason = reason != null && !reason.isBlank()
                ? reason.trim()
                : "Admin deleted job";

        // Create audit log
        createAuditLog(savedJob, oldStatus, ShortTermJobStatus.CANCELLED, adminId,
                "ADMIN", normalizedReason);

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
    public Page<AdminDisputeResponse> getAllDisputes(Dispute.DisputeStatus status, Pageable pageable) {
        log.info("Fetching disputes for admin. Status filter: {}", status);
        Page<Dispute> disputes = status != null
                ? disputeRepository.findByStatusInPaginated(List.of(status), pageable)
                : disputeRepository.findAll(pageable);
        Map<Long, String> userNames = loadUserNames(disputes.getContent(), false);
        return disputes.map(dispute -> toAdminDisputeResponse(dispute, userNames, false));
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDisputeResponse getDisputeDetail(Long disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new NotFoundException("Dispute not found with ID: " + disputeId));
        return toAdminDisputeResponse(dispute, loadUserNames(List.of(dispute), true), true);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobStatusAuditLog> getDisputeAuditLogs(Long disputeId) {
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new NotFoundException("Dispute not found with ID: " + disputeId));

        List<JobStatusAuditLog> logs = new ArrayList<>();
        if (dispute.getApplication() != null) {
            logs.addAll(auditLogRepository.findByApplicationIdOrderByCreatedAtDesc(dispute.getApplication().getId()));
        }
        if (dispute.getShortTermJob() != null) {
            logs.addAll(auditLogRepository.findByShortTermJobIdOrderByCreatedAtDesc(dispute.getShortTermJob().getId()));
        }

        logs.sort(Comparator.comparing(JobStatusAuditLog::getCreatedAt).reversed());
        return logs;
    }

    @Override
    @Transactional
    public AdminDisputeResponse resolveDispute(Long adminId, Long disputeId, ResolveDisputeAdminRequest request) {
        log.info("Admin {} resolving dispute ID: {} with resolution: {}", adminId, disputeId, request.getResolution());

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new NotFoundException("Dispute not found with ID: " + disputeId));

        if (dispute.getStatus() == Dispute.DisputeStatus.RESOLVED
                || dispute.getStatus() == Dispute.DisputeStatus.DISMISSED) {
            throw new BadRequestException("Dispute is already resolved or dismissed");
        }

        if (request.getResolution() == null) {
            throw new BadRequestException("Resolution is required");
        }

        // B1: Delegate to DisputeServiceImpl so both admin and business paths
        // use identical financial logic, status updates, escrow transactions, and notifications.
        try {
            disputeService.resolveDisputeFromAdmin(adminId, disputeId, request);
        } catch (Exception e) {
            log.error("Failed to resolve dispute {} via DisputeService: {}", disputeId, e.getMessage());
            throw new BadRequestException("Failed to process dispute resolution: " + e.getMessage());
        }

        log.info("Admin {} resolved dispute ID: {} with resolution {}", adminId, disputeId, request.getResolution());
        Dispute updatedDispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new NotFoundException("Dispute not found with ID: " + disputeId));
        return toAdminDisputeResponse(updatedDispute, loadUserNames(List.of(updatedDispute), false), false);
    }

    @Override
    @Transactional
    public ShortTermJobResponse rejectCancellation(Long adminId, Long disputeId, RejectCancellationRequest request) {
        // ================================================================
        // [Nghiệp vụ] Admin từ chối yêu cầu hủy job từ recruiter.
        // Job quay về IN_PROGRESS để worker tiếp tục làm việc.
        // Admin cần ghi rõ lý do từ chối.
        // ================================================================
        log.info("Admin {} rejecting cancellation for dispute {}", adminId, disputeId);

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new NotFoundException("Dispute not found with ID: " + disputeId));

        // Chỉ từ chối được khi dispute đang ở trạng thái OPEN (chưa resolved/dismissed)
        if (dispute.getStatus() == Dispute.DisputeStatus.RESOLVED
                || dispute.getStatus() == Dispute.DisputeStatus.DISMISSED) {
            throw new BadRequestException("Dispute is already resolved or dismissed");
        }

        ShortTermJob job = dispute.getShortTermJob();
        ShortTermJobApplication application = dispute.getApplication();

        if (job == null || application == null) {
            throw new NotFoundException("Job or application not found for this dispute");
        }

        ShortTermJobStatus previousJobStatus = job.getStatus();
        ShortTermApplicationStatus previousAppStatus = application.getStatus();
        LocalDateTime now = LocalDateTime.now();

        // Job quay về IN_PROGRESS — worker tiếp tục làm việc được
        job.setStatus(ShortTermJobStatus.IN_PROGRESS);
        shortTermJobRepository.save(job);

        // Application quay về WORKING — worker có thể submit lại
        application.setStatus(ShortTermApplicationStatus.WORKING);
        application.setLastActivityAt(now);
        application.setResponseDeadlineAt(null);
        applicationRepository.save(application);

        // Dispute được dismiss — không ảnh hưởng tài chính
        dispute.setResolvedBy(adminId);
        dispute.setResolvedAt(now);
        dispute.setStatus(Dispute.DisputeStatus.DISMISSED);
        dispute.setResolutionNotes(
                "CANCELLATION_REJECTED: " + (request.getReason() != null ? request.getReason() : "No reason provided"));
        disputeRepository.save(dispute);

        // Audit log
        auditService.logShortTermJobStatusChange(
                job.getId(),
                previousJobStatus,
                ShortTermJobStatus.IN_PROGRESS,
                adminId,
                JobStatusAuditLog.AuditRole.ADMIN,
                "Admin rejected cancellation request: job returns to IN_PROGRESS"
        );
        auditService.logApplicationStatusChange(
                application.getId(),
                previousAppStatus,
                ShortTermApplicationStatus.WORKING,
                adminId,
                JobStatusAuditLog.AuditRole.ADMIN,
                "Admin rejected cancellation: application returns to WORKING"
        );

        // Notify recruiter
        String recruiterNote = request.getReason() != null && !request.getReason().isBlank()
                ? request.getReason()
                : "Admin đã từ chối yêu cầu hủy job. Công việc tiếp tục được thực hiện.";
        notificationService.createNotification(
                dispute.getInitiatorId(),
                "Yêu cầu hủy job bị từ chối",
                recruiterNote,
                NotificationType.ADMIN_CANCELLATION_REJECTED,
                String.valueOf(job.getId())
        );

        // Notify worker
        notificationService.createNotification(
                application.getUser().getId(),
                "Yêu cầu hủy job bị từ chối — tiếp tục làm việc",
                "Admin đã từ chối yêu cầu hủy của nhà tuyển dụng. Bạn vui lòng tiếp tục hoàn thành công việc.",
                NotificationType.WORKER_CANCELLATION_REJECTED,
                String.valueOf(job.getId())
        );

        log.info("Admin {} rejected cancellation for job {}. Job is now IN_PROGRESS", adminId, job.getId());
        return shortTermJobService.getJobDetails(job.getId());
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
        long escalatedCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.ESCALATED);
        long closedCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.CLOSED);
        long rejectedCount = shortTermJobRepository.countByStatus(ShortTermJobStatus.REJECTED);

        long totalJobs = draftCount + pendingCount + publishedCount + appliedCount + inProgressCount
                + submittedCount + underReviewCount + approvedCount + completedCount + paidCount
                + cancelledCount + disputedCount + escalatedCount + closedCount + rejectedCount;

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
        byStatus.put("ESCALATED", escalatedCount);
        byStatus.put("CLOSED", closedCount);
        byStatus.put("REJECTED", rejectedCount);

        // Dispute stats
        long openDisputes = disputeRepository.countByStatus(Dispute.DisputeStatus.OPEN);
        long investigatingDisputes = disputeRepository.countByStatus(Dispute.DisputeStatus.UNDER_INVESTIGATION);
        long awaitingDisputes = disputeRepository.countByStatus(Dispute.DisputeStatus.AWAITING_RESPONSE);
        long escalatedDisputes = disputeRepository.countByStatus(Dispute.DisputeStatus.ESCALATED);
        byStatus.put("DISPUTE_OPEN", openDisputes);
        byStatus.put("DISPUTE_INVESTIGATING", investigatingDisputes);
        byStatus.put("DISPUTE_AWAITING", awaitingDisputes);
        byStatus.put("DISPUTE_ESCALATED", escalatedDisputes);

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
                .disputedCount(disputedCount + openDisputes + investigatingDisputes + awaitingDisputes + escalatedDisputes)
                .escalatedCount(escalatedCount)
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
            auditService.logShortTermJobStatusChange(
                    job.getId(),
                    previousStatus,
                    newStatus,
                    adminId,
                    JobStatusAuditLog.AuditRole.ADMIN,
                    reason
            );
        } catch (Exception e) {
            log.warn("Failed to create audit log for job {} status change: {}", job.getId(), e.getMessage());
        }
    }

    private AdminDisputeResponse toAdminDisputeResponse(
            Dispute dispute,
            Map<Long, String> userNames,
            boolean includeEvidence) {
        ShortTermJob job = dispute.getShortTermJob();
        ShortTermJobApplication application = dispute.getApplication();

        return AdminDisputeResponse.builder()
                .id(dispute.getId())
                .jobId(job != null ? job.getId() : null)
                .applicationId(application != null ? application.getId() : null)
                .jobTitle(job != null ? job.getTitle() : null)
                .jobStatus(job != null && job.getStatus() != null ? job.getStatus().name() : null)
                .initiatorId(dispute.getInitiatorId())
                .initiatorName(resolveUserName(dispute.getInitiatorId(), userNames))
                .respondentId(dispute.getRespondentId())
                .respondentName(resolveUserName(dispute.getRespondentId(), userNames))
                .disputeType(dispute.getDisputeType())
                .reason(dispute.getReason())
                .status(dispute.getStatus())
                .resolution(dispute.getResolution())
                .partialRefundPct(dispute.getPartialRefundPct())
                .resolutionNotes(dispute.getResolutionNotes())
                .resolvedBy(dispute.getResolvedBy())
                .resolvedByName(resolveUserName(dispute.getResolvedBy(), userNames))
                .resolvedAt(dispute.getResolvedAt())
                .adminResolutionDeadlineAt(dispute.getAdminResolutionDeadlineAt())
                .escalationLevel(dispute.getEscalationLevel())
                .priority(dispute.getPriority())
                .escalatedAt(dispute.getEscalatedAt())
                .createdAt(dispute.getCreatedAt())
                .workerUserId(application != null && application.getUser() != null ? application.getUser().getId() : null)
                .applicationStatus(application != null && application.getStatus() != null
                        ? application.getStatus().name()
                        : null)
                .evidence(includeEvidence ? mapEvidence(dispute, userNames) : null)
                .build();
    }

    private List<AdminDisputeResponse.EvidenceInfo> mapEvidence(
            Dispute dispute,
            Map<Long, String> userNames) {
        if (dispute.getEvidence() == null || dispute.getEvidence().isEmpty()) {
            return List.of();
        }

        return dispute.getEvidence().stream()
                .sorted(Comparator.comparing(DisputeEvidence::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .reversed())
                .map(evidence -> AdminDisputeResponse.EvidenceInfo.builder()
                        .id(evidence.getId())
                        .disputeId(dispute.getId())
                        .submittedBy(evidence.getSubmittedBy())
                        .submittedByName(resolveUserName(evidence.getSubmittedBy(), userNames))
                        .evidenceType(evidence.getEvidenceType() != null ? evidence.getEvidenceType().name() : null)
                        .content(evidence.getContent())
                        .fileUrl(evidence.getFileUrl())
                        .fileName(evidence.getFileName())
                        .description(evidence.getDescription())
                        .isOfficial(evidence.getIsOfficial())
                        .createdAt(evidence.getCreatedAt())
                        .responses(mapResponses(dispute.getId(), evidence, userNames))
                        .build())
                .collect(Collectors.toList());
    }

    private List<AdminDisputeResponse.ResponseInfo> mapResponses(
            Long disputeId,
            DisputeEvidence evidence,
            Map<Long, String> userNames) {
        if (evidence.getResponses() == null || evidence.getResponses().isEmpty()) {
            return List.of();
        }

        return evidence.getResponses().stream()
                .sorted(Comparator.comparing(
                        DisputeResponseEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(response -> AdminDisputeResponse.ResponseInfo.builder()
                        .id(response.getId())
                        .disputeId(disputeId)
                        .evidenceId(evidence.getId())
                        .respondedBy(response.getRespondedBy())
                        .respondedByName(response.getRespondedByName() != null
                                && !response.getRespondedByName().isBlank()
                                        ? response.getRespondedByName()
                                        : resolveUserName(response.getRespondedBy(), userNames))
                        .content(response.getContent())
                        .isAdminResponse(response.getIsAdminResponse())
                        .createdAt(response.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private Map<Long, String> loadUserNames(List<Dispute> disputes, boolean includeEvidence) {
        Set<Long> userIds = new LinkedHashSet<>();

        for (Dispute dispute : disputes) {
            addUserId(userIds, dispute.getInitiatorId());
            addUserId(userIds, dispute.getRespondentId());
            addUserId(userIds, dispute.getResolvedBy());

            if (!includeEvidence || dispute.getEvidence() == null) {
                continue;
            }

            for (DisputeEvidence evidence : dispute.getEvidence()) {
                addUserId(userIds, evidence.getSubmittedBy());
                if (evidence.getResponses() == null) {
                    continue;
                }
                for (DisputeResponseEntity response : evidence.getResponses()) {
                    addUserId(userIds, response.getRespondedBy());
                }
            }
        }

        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, this::buildUserDisplayName));
    }

    private void addUserId(Set<Long> userIds, Long userId) {
        if (userId != null) {
            userIds.add(userId);
        }
    }

    private String resolveUserName(Long userId, Map<Long, String> userNames) {
        if (userId == null) {
            return null;
        }
        return userNames.getOrDefault(userId, "User #" + userId);
    }

    private String buildUserDisplayName(User user) {
        String fullName = user.getFullName();
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return "User #" + user.getId();
    }
}
