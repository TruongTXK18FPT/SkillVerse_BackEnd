package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.OpenDisputeRequest;
import com.exe.skillverse_backend.business_service.dto.request.ResolveDisputeRequest;
import com.exe.skillverse_backend.business_service.dto.request.SubmitEvidenceRequest;
import com.exe.skillverse_backend.business_service.entity.Dispute;
import com.exe.skillverse_backend.business_service.entity.Dispute.DisputeResolution;
import com.exe.skillverse_backend.business_service.entity.Dispute.DisputeStatus;
import com.exe.skillverse_backend.business_service.entity.DisputeEvidence;
import com.exe.skillverse_backend.business_service.entity.DisputeResponseEntity;
import com.exe.skillverse_backend.business_service.entity.EscrowTransaction;
import com.exe.skillverse_backend.business_service.entity.EscrowTransaction.EscrowTransactionType;
import com.exe.skillverse_backend.business_service.entity.JobEscrow;
import com.exe.skillverse_backend.business_service.entity.JobEscrow.EscrowStatus;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.DisputeEvidenceRepository;
import com.exe.skillverse_backend.business_service.repository.DisputeResponseRepository;
import com.exe.skillverse_backend.business_service.repository.DisputeRepository;
import com.exe.skillverse_backend.business_service.service.TrustScoreService;
import com.exe.skillverse_backend.business_service.repository.EscrowTransactionRepository;
import com.exe.skillverse_backend.business_service.repository.JobEscrowRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.DisputeService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class DisputeServiceImpl implements DisputeService {

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository disputeEvidenceRepository;
    private final DisputeResponseRepository disputeResponseRepository;
    private final ShortTermJobRepository shortTermJobRepository;
    private final ShortTermJobApplicationRepository applicationRepository;
    private final JobEscrowRepository jobEscrowRepository;
    private final TrustScoreService trustScoreService;
    private final EscrowTransactionRepository escrowTransactionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Override
    public Dispute openDispute(Long userId, OpenDisputeRequest request) {
        log.info("Opening dispute for job {} by user {}", request.getJobId(), userId);

        ShortTermJob job = shortTermJobRepository.findById(request.getJobId())
                .orElseThrow(() -> new NotFoundException("Job not found with ID: " + request.getJobId()));

        // ================================================================
        // PROTECTED DISPUTE FLOW — Recruiter CANNOT open disputes directly
        // Recruiter must contact admin for any issues.
        // Workers can only open disputes after 5 revision requests + eligibility unlocked.
        // ================================================================

        // RECRUITER IS BLOCKED — recruiters cannot open disputes via this endpoint
        if (job.getRecruiterProfile().getUserId().equals(userId)) {
            throw new ForbiddenException(
                    "Recruiters cannot open disputes directly. Please contact admin support for assistance.");
        }

        // Verify user is the selected worker for this job
        if (job.getSelectedApplicantId() == null || !job.getSelectedApplicantId().equals(userId)) {
            throw new ForbiddenException("Only assigned worker can open a dispute for this job.");
        }

        // Worker MUST have eligibility unlocked (triggered at revision >= 5)
        ShortTermJobApplication app = applicationRepository
                .findByShortTermJobIdAndUserId(job.getId(), userId)
                .orElseThrow(() -> new NotFoundException("Application not found for this job and user"));

        if (!Boolean.TRUE.equals(app.getDisputeEligibilityUnlocked())) {
            throw new BadRequestException(
                    "Dispute eligibility has not been unlocked yet. Disputes can only be opened after " +
                    "5 revision requests. Please complete the revision process first.");
        }

        if (app.getStatus() != ShortTermApplicationStatus.REVISION_REQUIRED
                && app.getStatus() != ShortTermApplicationStatus.CANCELLATION_REQUESTED
                && app.getStatus() != ShortTermApplicationStatus.SUBMITTED
                && app.getStatus() != ShortTermApplicationStatus.SUBMITTED_OVERDUE) {
            throw new BadRequestException(
                    "Dispute can only be opened after the 5-revision threshold while the job is still under review.");
        }

        // Reuse the active admin review/dispute if one already exists for this job.
        Dispute existingDispute = disputeRepository.findByJobId(request.getJobId()).orElse(null);
        if (existingDispute != null
                && existingDispute.getStatus() != DisputeStatus.RESOLVED
                && existingDispute.getStatus() != DisputeStatus.DISMISSED) {
            return existingDispute;
        }

        // Determine respondent (recruiter)
        Long respondentId = job.getRecruiterProfile().getUserId();
        Long initiatorId = userId;

        // Create dispute with admin resolution deadline (5 days SLA)
        Dispute dispute = Dispute.builder()
                .jobId(request.getJobId())
                .applicationId(request.getApplicationId())
                .initiatorId(initiatorId)
                .respondentId(respondentId)
                .disputeType(request.getDisputeType())
                .reason(request.getReason())
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .adminResolutionDeadlineAt(LocalDateTime.now().plusDays(5))
                .build();

        dispute = disputeRepository.save(dispute);

        // Update application and job status to DISPUTED
        app.setStatus(ShortTermApplicationStatus.DISPUTE_OPENED);
        app.setLastActivityAt(LocalDateTime.now());
        applicationRepository.save(app);

        job.setStatus(com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus.DISPUTED);
        shortTermJobRepository.save(job);

        // Mark escrow as DISPUTED — frozen until admin resolves
        jobEscrowRepository.findByJobId(request.getJobId()).ifPresent(escrow -> {
            escrow.setStatus(EscrowStatus.DISPUTED);
            jobEscrowRepository.save(escrow);
            log.info("Escrow for job {} frozen as DISPUTED", request.getJobId());
        });

        // Notify respondent (recruiter)
        notificationService.createNotification(
                respondentId,
                "Khiếu nại đã được gửi lên Admin",
                "Ứng viên đã gửi khiếu nại cho công việc \"" + job.getTitle() +
                "\". Admin sẽ xem xét và giải quyết trong 5 ngày làm việc.",
                NotificationType.DISPUTE_OPENED,
                String.valueOf(dispute.getId())
        );

        log.info("Dispute {} opened for job {} by worker {}", dispute.getId(), request.getJobId(), userId);
        return dispute;
    }

    @Override
    public DisputeEvidence submitEvidence(Long userId, Long disputeId, SubmitEvidenceRequest request) {
        log.info("Submitting evidence for dispute {} by user {}", disputeId, userId);

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new NotFoundException("Dispute not found with ID: " + disputeId));

        // Validate user is a party in the dispute
        if (!dispute.getInitiatorId().equals(userId) && !dispute.getRespondentId().equals(userId)) {
            throw new ForbiddenException("You are not a party in this dispute");
        }

        // Validate dispute is still open
        if (dispute.getStatus() == DisputeStatus.RESOLVED || dispute.getStatus() == DisputeStatus.DISMISSED) {
            throw new BadRequestException("Cannot submit evidence to a resolved or dismissed dispute");
        }

        DisputeEvidence evidence = DisputeEvidence.builder()
                .dispute(dispute)
                .submittedBy(userId)
                .evidenceType(request.getEvidenceType())
                .content(request.getContent())
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .description(request.getDescription())
                .isOfficial(false)
                .build();

        evidence = disputeEvidenceRepository.save(evidence);

        log.info("Evidence {} submitted for dispute {}", evidence.getId(), disputeId);
        return evidence;
    }

    @Override
    public DisputeResponseEntity respondToEvidence(Long userId, Long disputeId, Long evidenceId, String content) {
        log.info("Responding to evidence {} for dispute {} by user {}", evidenceId, disputeId, userId);

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new NotFoundException("Dispute not found with ID: " + disputeId));

        // Validate user is a party
        if (!dispute.getInitiatorId().equals(userId) && !dispute.getRespondentId().equals(userId)) {
            throw new ForbiddenException("You are not a party in this dispute");
        }

        // Find the evidence
        DisputeEvidence evidence = disputeEvidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new NotFoundException("Evidence not found with ID: " + evidenceId));

        if (!evidence.getDispute().getId().equals(disputeId)) {
            throw new BadRequestException("Evidence does not belong to this dispute");
        }

        // Get user name
        User user = userRepository.findById(userId).orElse(null);
        String userName = user != null && user.getFullName() != null ? user.getFullName() : "User";

        DisputeResponseEntity response = DisputeResponseEntity.builder()
                .evidence(evidence)
                .respondedBy(userId)
                .respondedByName(userName)
                .content(content)
                .isAdminResponse(false)
                .build();

        disputeResponseRepository.save(response);

        log.info("Response added to evidence {} for dispute {}", evidenceId, disputeId);
        return response;
    }

    @Override
    public Dispute resolveDispute(Long adminId, Long disputeId, ResolveDisputeRequest request) {
        log.info("Resolving dispute {} by admin {}", disputeId, adminId);

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new NotFoundException("Dispute not found with ID: " + disputeId));

        if (dispute.getStatus() == DisputeStatus.RESOLVED || dispute.getStatus() == DisputeStatus.DISMISSED) {
            throw new BadRequestException("Dispute is already resolved or dismissed");
        }

        dispute.setResolution(request.getResolution());
        dispute.setResolutionNotes(request.getResolutionNotes());
        if (request.getPartialRefundPct() != null) {
            dispute.setPartialRefundPct(request.getPartialRefundPct());
        }
        dispute.setResolvedBy(adminId);
        dispute.setResolvedAt(LocalDateTime.now());
        dispute.setStatus(DisputeStatus.RESOLVED);

        dispute = disputeRepository.save(dispute);

        // Update job and application status based on resolution
        updateJobAndApplicationStatus(dispute, request.getResolution());

        // Handle financial resolution
        JobEscrow escrow = jobEscrowRepository.findByJobId(dispute.getJobId()).orElse(null);
        if (escrow != null) {
            handleFinancialResolution(dispute, escrow, request.getResolution());
        }

        // Notify both parties
        notifyDisputeResolved(dispute);

        // Trigger trust score recalculation for both parties
        trustScoreService.triggerRecalculationOnDispute(dispute.getInitiatorId());
        trustScoreService.triggerRecalculationOnDispute(dispute.getRespondentId());

        log.info("Dispute {} resolved with resolution: {}", disputeId, request.getResolution());
        return dispute;
    }

    private void handleFinancialResolution(Dispute dispute, JobEscrow escrow, DisputeResolution resolution) {
        BigDecimal totalAmount = escrow.getTotalAmount();
        BigDecimal platformFee = escrow.getPlatformFee();
        BigDecimal refundAmount = escrow.getEscrowBalance();

        User admin = userRepository.findById(dispute.getResolvedBy()).orElse(null);
        String adminName = admin != null && admin.getFullName() != null ? admin.getFullName() : "Admin";

        switch (resolution) {
            case FULL_REFUND:
                // Refund recruiter (minus platform fee)
                escrow.setEscrowBalance(BigDecimal.ZERO);
                escrow.setStatus(EscrowStatus.REFUNDED);
                escrow.setRefundedAt(LocalDateTime.now());
                jobEscrowRepository.save(escrow);

                EscrowTransaction refundTx = EscrowTransaction.builder()
                        .escrow(escrow)
                        .transactionType(EscrowTransactionType.REFUND)
                        .amount(refundAmount.subtract(platformFee))
                        .feeAmount(platformFee)
                        .netAmount(refundAmount.subtract(platformFee))
                        .actorId(dispute.getResolvedBy())
                        .actorName(adminName)
                        .reason("Full refund via dispute resolution")
                        .metadata("{\"disputeId\":" + dispute.getId() + ",\"resolution\":\"FULL_REFUND\"}")
                        .build();
                escrowTransactionRepository.save(refundTx);
                break;

            case FULL_RELEASE:
                // Release full amount to worker (minus platform fee)
                escrow.setEscrowBalance(BigDecimal.ZERO);
                escrow.setPendingPayoutBalance(totalAmount.subtract(platformFee));
                escrow.setStatus(EscrowStatus.FULLY_RELEASED);
                escrow.setReleasedAt(LocalDateTime.now());
                jobEscrowRepository.save(escrow);

                EscrowTransaction releaseTx = EscrowTransaction.builder()
                        .escrow(escrow)
                        .transactionType(EscrowTransactionType.RELEASE)
                        .amount(totalAmount.subtract(platformFee))
                        .feeAmount(platformFee)
                        .netAmount(totalAmount.subtract(platformFee))
                        .actorId(dispute.getResolvedBy())
                        .actorName(adminName)
                        .reason("Full release via dispute resolution")
                        .metadata("{\"disputeId\":" + dispute.getId() + ",\"resolution\":\"FULL_RELEASE\"}")
                        .build();
                escrowTransactionRepository.save(releaseTx);

                EscrowTransaction feeTx = EscrowTransaction.builder()
                        .escrow(escrow)
                        .transactionType(EscrowTransactionType.FEE_DEDUCTION)
                        .amount(platformFee)
                        .feeAmount(BigDecimal.ZERO)
                        .netAmount(BigDecimal.ZERO)
                        .actorId(dispute.getResolvedBy())
                        .actorName(adminName)
                        .reason("Platform fee from dispute resolution")
                        .metadata("{\"disputeId\":" + dispute.getId() + "}")
                        .build();
                escrowTransactionRepository.save(feeTx);
                break;

            case PARTIAL_REFUND:
                // Split between recruiter and worker based on partialRefundPct
                BigDecimal refundPct = dispute.getPartialRefundPct() != null
                        ? dispute.getPartialRefundPct().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                        : new BigDecimal("0.50");
                BigDecimal recruiterRefund = escrow.getEscrowBalance().multiply(refundPct).setScale(2, RoundingMode.HALF_UP);
                BigDecimal workerAmount = escrow.getEscrowBalance().subtract(recruiterRefund);

                escrow.setEscrowBalance(BigDecimal.ZERO);
                escrow.setPendingPayoutBalance(workerAmount);
                escrow.setStatus(EscrowStatus.PARTIALLY_RELEASED);
                escrow.setReleasedAt(LocalDateTime.now());
                jobEscrowRepository.save(escrow);

                EscrowTransaction partialTx = EscrowTransaction.builder()
                        .escrow(escrow)
                        .transactionType(EscrowTransactionType.PARTIAL_RELEASE)
                        .amount(workerAmount)
                        .feeAmount(BigDecimal.ZERO)
                        .netAmount(workerAmount)
                        .actorId(dispute.getResolvedBy())
                        .actorName(adminName)
                        .reason("Partial release (" + refundPct.multiply(new BigDecimal("100")).setScale(0) + "%) via dispute resolution")
                        .metadata("{\"disputeId\":" + dispute.getId() + ",\"resolution\":\"PARTIAL_REFUND\",\"refundPct\":" + refundPct + "}")
                        .build();
                escrowTransactionRepository.save(partialTx);
                break;

            case PARTIAL_RELEASE:
            case WORKER_PARTIAL:
                // Split between recruiter and worker based on partialRefundPct
                BigDecimal partialPct = dispute.getPartialRefundPct() != null
                        ? dispute.getPartialRefundPct().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
                        : new BigDecimal("0.50");
                BigDecimal workerPartialAmount = escrow.getEscrowBalance().multiply(partialPct).setScale(2, RoundingMode.HALF_UP);

                escrow.setEscrowBalance(BigDecimal.ZERO);
                escrow.setPendingPayoutBalance(workerPartialAmount);
                escrow.setStatus(EscrowStatus.PARTIALLY_RELEASED);
                escrow.setReleasedAt(LocalDateTime.now());
                jobEscrowRepository.save(escrow);

                EscrowTransaction partialReleaseTx = EscrowTransaction.builder()
                        .escrow(escrow)
                        .transactionType(EscrowTransactionType.PARTIAL_RELEASE)
                        .amount(workerPartialAmount)
                        .feeAmount(BigDecimal.ZERO)
                        .netAmount(workerPartialAmount)
                        .actorId(dispute.getResolvedBy())
                        .actorName(adminName)
                        .reason("Partial release via dispute resolution")
                        .metadata("{\"disputeId\":" + dispute.getId() + ",\"resolution\":\"" + resolution + "\"}")
                        .build();
                escrowTransactionRepository.save(partialReleaseTx);
                break;

            case WORKER_WINS:
                // Full release to worker
                escrow.setEscrowBalance(BigDecimal.ZERO);
                escrow.setPendingPayoutBalance(totalAmount.subtract(platformFee));
                escrow.setStatus(EscrowStatus.FULLY_RELEASED);
                escrow.setReleasedAt(LocalDateTime.now());
                jobEscrowRepository.save(escrow);

                EscrowTransaction workerWinsTx = EscrowTransaction.builder()
                        .escrow(escrow)
                        .transactionType(EscrowTransactionType.RELEASE)
                        .amount(totalAmount.subtract(platformFee))
                        .feeAmount(platformFee)
                        .netAmount(totalAmount.subtract(platformFee))
                        .actorId(dispute.getResolvedBy())
                        .actorName(adminName)
                        .reason("Worker wins via dispute resolution")
                        .metadata("{\"disputeId\":" + dispute.getId() + ",\"resolution\":\"WORKER_WINS\"}")
                        .build();
                escrowTransactionRepository.save(workerWinsTx);
                break;

            case RECRUITER_WINS:
                // Full refund to recruiter
                escrow.setEscrowBalance(BigDecimal.ZERO);
                escrow.setStatus(EscrowStatus.REFUNDED);
                escrow.setRefundedAt(LocalDateTime.now());
                jobEscrowRepository.save(escrow);

                EscrowTransaction recruiterWinsTx = EscrowTransaction.builder()
                        .escrow(escrow)
                        .transactionType(EscrowTransactionType.REFUND)
                        .amount(refundAmount.subtract(platformFee))
                        .feeAmount(platformFee)
                        .netAmount(refundAmount.subtract(platformFee))
                        .actorId(dispute.getResolvedBy())
                        .actorName(adminName)
                        .reason("Recruiter wins via dispute resolution")
                        .metadata("{\"disputeId\":" + dispute.getId() + ",\"resolution\":\"RECRUITER_WINS\"}")
                        .build();
                escrowTransactionRepository.save(recruiterWinsTx);
                break;

            case RESUBMIT_REQUIRED:
            case NO_ACTION:
                // No financial action
                escrow.setStatus(EscrowStatus.FUNDED);
                jobEscrowRepository.save(escrow);
                break;

            default:
                break;
        }
    }

    private void updateJobAndApplicationStatus(Dispute dispute, DisputeResolution resolution) {
        ShortTermJob job = shortTermJobRepository.findById(dispute.getJobId()).orElse(null);
        if (job == null) return;

        applicationRepository.findByJobIdAndUserId(dispute.getJobId(), dispute.getInitiatorId())
                .ifPresent(app -> {
                    switch (resolution) {
                        case FULL_RELEASE:
                        case WORKER_WINS:
                        case WORKER_PARTIAL:
                            app.setStatus(ShortTermApplicationStatus.COMPLETED);
                            job.setStatus(ShortTermJobStatus.COMPLETED);
                            break;
                        case FULL_REFUND:
                        case RECRUITER_WINS:
                            app.setStatus(ShortTermApplicationStatus.CANCELLED);
                            job.setStatus(ShortTermJobStatus.CANCELLED);
                            break;
                        case RESUBMIT_REQUIRED:
                            app.setStatus(ShortTermApplicationStatus.REVISION_REQUIRED);
                            job.setStatus(ShortTermJobStatus.IN_PROGRESS);
                            break;
                        default:
                            // PARTIAL_REFUND, PARTIAL_RELEASE, NO_ACTION — keep as-is
                            break;
                    }
                    applicationRepository.save(app);
                    shortTermJobRepository.save(job);
                    log.info("Updated job {} and application status based on resolution {}", job.getId(), resolution);
                });
    }

    private void notifyDisputeResolved(Dispute dispute) {
        try {
            notificationService.createNotification(
                    dispute.getInitiatorId(),
                    "Dispute Resolved",
                    "Your dispute for job has been resolved with outcome: " + dispute.getResolution(),
                    NotificationType.DISPUTE_RESOLVED,
                    String.valueOf(dispute.getId())
            );
            notificationService.createNotification(
                    dispute.getRespondentId(),
                    "Dispute Resolved",
                    "A dispute for one of your jobs has been resolved with outcome: " + dispute.getResolution(),
                    NotificationType.DISPUTE_RESOLVED,
                    String.valueOf(dispute.getId())
            );
        } catch (Exception e) {
            log.warn("Failed to send dispute resolution notifications: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Dispute getDispute(Long disputeId) {
        return disputeRepository.findById(disputeId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dispute> getDisputesByJob(Long jobId) {
        return disputeRepository.findByJobId(jobId)
                .map(List::of)
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Dispute> getMyDisputes(Long userId, Pageable pageable) {
        return disputeRepository.findByInitiatorIdOrRespondentId(userId, userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Dispute> getAllDisputes(Pageable pageable) {
        return disputeRepository.findByStatusIn(
                Arrays.asList(DisputeStatus.OPEN, DisputeStatus.UNDER_INVESTIGATION, DisputeStatus.AWAITING_RESPONSE),
                pageable
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeEvidence> getDisputeEvidence(Long disputeId) {
        return disputeEvidenceRepository.findByDisputeIdOrderByCreatedAtDesc(disputeId);
    }
}
