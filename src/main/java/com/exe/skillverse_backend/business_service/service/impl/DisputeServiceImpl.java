package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.admin_service.dto.request.ResolveDisputeAdminRequest;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.OpenDisputeRequest;
import com.exe.skillverse_backend.business_service.dto.request.ResolveDisputeRequest;
import com.exe.skillverse_backend.business_service.dto.request.SubmitEvidenceRequest;
import com.exe.skillverse_backend.business_service.dto.response.UserSubmittedDisputeResponse;
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
import com.exe.skillverse_backend.business_service.repository.DisputeRepository;
import com.exe.skillverse_backend.business_service.repository.DisputeResponseRepository;
import com.exe.skillverse_backend.business_service.repository.EscrowTransactionRepository;
import com.exe.skillverse_backend.business_service.repository.JobEscrowRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.DisputeService;
import com.exe.skillverse_backend.business_service.service.TrustScoreService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.EnumSet;
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
@Transactional
public class DisputeServiceImpl implements DisputeService {

    private static final Set<DisputeStatus> ACTIVE_DISPUTE_STATUSES = EnumSet.of(
            DisputeStatus.OPEN,
            DisputeStatus.UNDER_INVESTIGATION,
            DisputeStatus.AWAITING_RESPONSE,
            DisputeStatus.ESCALATED
    );

    private static final Set<ShortTermApplicationStatus> OPENABLE_APPLICATION_STATUSES = EnumSet.of(
            ShortTermApplicationStatus.REVISION_REQUIRED,
            ShortTermApplicationStatus.CANCELLATION_REQUESTED,
            ShortTermApplicationStatus.SUBMITTED,
            ShortTermApplicationStatus.SUBMITTED_OVERDUE,
            ShortTermApplicationStatus.DISPUTE_OPENED
    );

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

        ShortTermJob preloadedJob = null;
        if (request.getApplicationId() == null && request.getJobId() != null) {
            preloadedJob = shortTermJobRepository.findById(request.getJobId())
                    .orElseThrow(() -> new NotFoundException("Cong viec khong ton tai hoac da bi huy. Khong the mo khieu nai cho cong viec nay."));

            if (preloadedJob.getRecruiterProfile() != null
                    && preloadedJob.getRecruiterProfile().getUserId().equals(userId)) {
                throw new ForbiddenException("Recruiters cannot open disputes directly. Please contact admin support for assistance.");
            }
        }

        ShortTermJobApplication application = resolveApplicationForDispute(userId, request, preloadedJob);
        ShortTermJob job = application.getShortTermJob();

        if (job == null || job.getId() == null) {
            throw new NotFoundException("Cong viec khong ton tai hoac da bi huy. Khong the mo khieu nai cho cong viec nay.");
        }

        if (job.getStatus() == ShortTermJobStatus.CANCELLED || job.getStatus() == ShortTermJobStatus.CLOSED) {
            throw new NotFoundException("Cong viec khong ton tai hoac da bi huy. Khong the mo khieu nai cho cong viec nay.");
        }

        if (job.getRecruiterProfile() != null && job.getRecruiterProfile().getUserId().equals(userId)) {
            throw new ForbiddenException("Recruiters cannot open disputes directly. Please contact admin support for assistance.");
        }

        if (job.getSelectedApplicantId() == null || !job.getSelectedApplicantId().equals(userId)) {
            throw new ForbiddenException("Only assigned worker can open a dispute for this job.");
        }

        if (application.getUser() == null || !userId.equals(application.getUser().getId())) {
            throw new ForbiddenException("You can only open disputes for your own application.");
        }

        Dispute existingDispute = disputeRepository.findFirstByShortTermJobId(job.getId()).orElse(null);
        if (existingDispute != null && ACTIVE_DISPUTE_STATUSES.contains(existingDispute.getStatus())) {
            return existingDispute;
        }

        if (!Boolean.TRUE.equals(application.getDisputeEligibilityUnlocked())) {
            throw new BadRequestException(
                    "Dispute eligibility has not been unlocked yet. Disputes can only be opened after 5 revision requests.");
        }

        if (!OPENABLE_APPLICATION_STATUSES.contains(application.getStatus())) {
            throw new BadRequestException(
                    "Dispute can only be opened after the 5-revision threshold while the job is still under review.");
        }

        Dispute dispute = Dispute.builder()
                .shortTermJob(job)
                .application(application)
                .initiatorId(userId)
                .respondentId(job.getRecruiterProfile().getUserId())
                .disputeType(request.getDisputeType())
                .reason(request.getReason())
                .status(DisputeStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .adminResolutionDeadlineAt(LocalDateTime.now().plusDays(5))
                .build();

        dispute = disputeRepository.save(dispute);

        application.setStatus(ShortTermApplicationStatus.DISPUTE_OPENED);
        application.setLastActivityAt(LocalDateTime.now());
        applicationRepository.save(application);

        job.setStatus(ShortTermJobStatus.DISPUTED);
        shortTermJobRepository.save(job);

        jobEscrowRepository.findByJobId(job.getId()).ifPresent(escrow -> {
            escrow.setStatus(EscrowStatus.DISPUTED);
            jobEscrowRepository.save(escrow);
            log.info("Escrow for job {} frozen as DISPUTED", job.getId());
        });

        notificationService.createNotification(
                dispute.getRespondentId(),
                "Khieu nai da duoc gui len Admin",
                "Ung vien da gui khieu nai cho cong viec \"" + job.getTitle()
                        + "\". Admin se xem xet va giai quyet trong 5 ngay lam viec.",
                NotificationType.DISPUTE_OPENED,
                String.valueOf(dispute.getId())
        );

        log.info("Dispute {} opened for job {} by worker {}", dispute.getId(), job.getId(), userId);
        return dispute;
    }

    @Override
    public DisputeEvidence submitEvidence(Long userId, Long disputeId, SubmitEvidenceRequest request) {
        log.info("Submitting evidence for dispute {} by user {}", disputeId, userId);

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new NotFoundException("Dispute not found with ID: " + disputeId));

        if (!dispute.getInitiatorId().equals(userId) && !dispute.getRespondentId().equals(userId)) {
            throw new ForbiddenException("You are not a party in this dispute");
        }

        if (!ACTIVE_DISPUTE_STATUSES.contains(dispute.getStatus())) {
            throw new BadRequestException("Cannot submit evidence to a non-active dispute. Status: " + dispute.getStatus());
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

        if (!dispute.getInitiatorId().equals(userId) && !dispute.getRespondentId().equals(userId)) {
            throw new ForbiddenException("You are not a party in this dispute");
        }

        if (!ACTIVE_DISPUTE_STATUSES.contains(dispute.getStatus())) {
            throw new BadRequestException("Cannot respond to evidence in a non-active dispute. Status: " + dispute.getStatus());
        }

        DisputeEvidence evidence = disputeEvidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new NotFoundException("Evidence not found with ID: " + evidenceId));

        if (!evidence.getDispute().getId().equals(disputeId)) {
            throw new BadRequestException("Evidence does not belong to this dispute");
        }

        User user = userRepository.findById(userId).orElse(null);
        String userName = user != null && user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName()
                : "User";

        DisputeResponseEntity response = DisputeResponseEntity.builder()
                .evidence(evidence)
                .respondedBy(userId)
                .respondedByName(userName)
                .content(content)
                .isAdminResponse(false)
                .build();

        response = disputeResponseRepository.save(response);
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

        if (request.getResolution() == null) {
            throw new BadRequestException("Resolution is required");
        }

        DisputeResolution resolution = request.getResolution();
        BigDecimal validatedPartialPct = validateAndNormalizePartialPercentage(
            resolution,
            request.getPartialRefundPct()
        );

        dispute.setResolution(resolution);
        dispute.setResolutionNotes(request.getResolutionNotes());
        dispute.setPartialRefundPct(validatedPartialPct);
        dispute.setResolvedBy(adminId);
        dispute.setResolvedAt(LocalDateTime.now());
        dispute.setStatus(resolution == DisputeResolution.NO_ACTION
                ? DisputeStatus.DISMISSED
                : DisputeStatus.RESOLVED);

        dispute = disputeRepository.save(dispute);

        updateJobAndApplicationStatus(dispute, resolution);

        JobEscrow escrow = dispute.getShortTermJob() != null
                ? jobEscrowRepository.findByJobId(dispute.getShortTermJob().getId()).orElse(null)
                : null;
        if (escrow != null) {
            handleFinancialResolution(dispute, escrow, resolution);
        }

        notifyDisputeResolved(dispute);
        trustScoreService.triggerRecalculationOnDispute(dispute.getInitiatorId());
        trustScoreService.triggerRecalculationOnDispute(dispute.getRespondentId());

        log.info("Dispute {} resolved with resolution: {}", disputeId, resolution);
        return dispute;
    }

    @Override
    public Dispute resolveDisputeFromAdmin(
            Long adminId,
            Long disputeId,
            ResolveDisputeAdminRequest request) {
        ResolveDisputeRequest mappedRequest = ResolveDisputeRequest.builder()
                .resolution(request.getResolution())
                .resolutionNotes(request.getResolutionNotes())
                .partialRefundPct(request.getPartialRefundPct())
                .build();
        return resolveDispute(adminId, disputeId, mappedRequest);
    }

    private BigDecimal validateAndNormalizePartialPercentage(
            DisputeResolution resolution,
            BigDecimal partialRefundPct) {
        if (!requiresPartialPercentage(resolution)) {
            return null;
        }

        if (partialRefundPct == null) {
            throw new BadRequestException("partialRefundPct is required for partial resolutions");
        }

        if (partialRefundPct.compareTo(BigDecimal.ONE) < 0
                || partialRefundPct.compareTo(new BigDecimal("99")) > 0) {
            throw new BadRequestException("partialRefundPct must be in range 1-99");
        }

        return partialRefundPct.stripTrailingZeros();
    }

    private boolean requiresPartialPercentage(DisputeResolution resolution) {
        return resolution == DisputeResolution.WORKER_PARTIAL
                || resolution == DisputeResolution.PARTIAL_REFUND
                || resolution == DisputeResolution.PARTIAL_RELEASE;
    }

    private ShortTermJobApplication resolveApplicationForDispute(
            Long userId,
            OpenDisputeRequest request,
            ShortTermJob preloadedJob) {
        if (request.getApplicationId() != null) {
            ShortTermJobApplication application = applicationRepository.findById(request.getApplicationId())
                    .orElseThrow(() -> new NotFoundException("Application not found with ID: " + request.getApplicationId()));

            ShortTermJob job = application.getShortTermJob();
            if (job == null) {
                throw new NotFoundException("Application is not associated with a short-term job.");
            }

            if (request.getJobId() != null && !job.getId().equals(request.getJobId())) {
                log.warn("Open dispute payload mismatch for user {}: jobId {} does not match application {} job {}. Using application job.",
                        userId, request.getJobId(), request.getApplicationId(), job.getId());
            }

            return application;
        }

        ShortTermJob job = preloadedJob != null
                ? preloadedJob
                : shortTermJobRepository.findById(request.getJobId())
                .orElseThrow(() -> new NotFoundException("Cong viec khong ton tai hoac da bi huy. Khong the mo khieu nai cho cong viec nay."));

        return applicationRepository.findByShortTermJobIdAndUserId(job.getId(), userId)
                .orElseThrow(() -> new NotFoundException("Application not found for this job and user"));
    }

    private void handleFinancialResolution(Dispute dispute, JobEscrow escrow, DisputeResolution resolution) {
        BigDecimal totalAmount = escrow.getTotalAmount();
        BigDecimal platformFee = escrow.getPlatformFee();
        BigDecimal refundAmount = escrow.getEscrowBalance();

        User admin = userRepository.findById(dispute.getResolvedBy()).orElse(null);
        String adminName = admin != null && admin.getFullName() != null && !admin.getFullName().isBlank()
                ? admin.getFullName()
                : "Admin";

        switch (resolution) {
            case FULL_REFUND -> {
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
            }
            case FULL_RELEASE -> {
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
            }
            case PARTIAL_REFUND, PARTIAL_RELEASE, WORKER_PARTIAL -> {
                BigDecimal splitPct = dispute.getPartialRefundPct().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

                BigDecimal recruiterShare;
                BigDecimal workerShare;
                if (resolution == DisputeResolution.PARTIAL_REFUND) {
                    recruiterShare = escrow.getEscrowBalance().multiply(splitPct).setScale(2, RoundingMode.HALF_UP);
                    workerShare = escrow.getEscrowBalance().subtract(recruiterShare);
                } else {
                    workerShare = escrow.getEscrowBalance().multiply(splitPct).setScale(2, RoundingMode.HALF_UP);
                    recruiterShare = escrow.getEscrowBalance().subtract(workerShare);
                }

                escrow.setEscrowBalance(BigDecimal.ZERO);
                escrow.setPendingPayoutBalance(workerShare);
                escrow.setStatus(EscrowStatus.PARTIALLY_RELEASED);
                escrow.setReleasedAt(LocalDateTime.now());
                jobEscrowRepository.save(escrow);

                EscrowTransaction refundTx = EscrowTransaction.builder()
                        .escrow(escrow)
                        .transactionType(EscrowTransactionType.REFUND)
                        .amount(recruiterShare)
                        .feeAmount(BigDecimal.ZERO)
                        .netAmount(recruiterShare)
                        .actorId(dispute.getResolvedBy())
                        .actorName(adminName)
                        .reason("Partial refund to recruiter via dispute resolution")
                        .metadata("{\"disputeId\":" + dispute.getId() + ",\"resolution\":\"" + resolution + "\"}")
                        .build();
                escrowTransactionRepository.save(refundTx);

                EscrowTransaction workerTx = EscrowTransaction.builder()
                        .escrow(escrow)
                        .transactionType(EscrowTransactionType.PARTIAL_RELEASE)
                        .amount(workerShare)
                        .feeAmount(BigDecimal.ZERO)
                        .netAmount(workerShare)
                        .actorId(dispute.getResolvedBy())
                        .actorName(adminName)
                        .reason("Partial release to worker via dispute resolution")
                        .metadata("{\"disputeId\":" + dispute.getId() + ",\"resolution\":\"" + resolution + "\"}")
                        .build();
                escrowTransactionRepository.save(workerTx);
            }
            case CANCEL_JOB, RECRUITER_WINS -> {
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
                        .reason(resolution == DisputeResolution.CANCEL_JOB
                                ? "Job cancelled via dispute resolution"
                                : "Recruiter wins via dispute resolution")
                        .metadata("{\"disputeId\":" + dispute.getId() + ",\"resolution\":\"" + resolution + "\"}")
                        .build();
                escrowTransactionRepository.save(refundTx);
            }
            case WORKER_WINS -> {
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
                        .reason("Worker wins via dispute resolution")
                        .metadata("{\"disputeId\":" + dispute.getId() + ",\"resolution\":\"WORKER_WINS\"}")
                        .build();
                escrowTransactionRepository.save(releaseTx);
            }
            case RESUBMIT_REQUIRED, NO_ACTION, RECRUITER_WARNING -> {
                escrow.setStatus(EscrowStatus.FUNDED);
                jobEscrowRepository.save(escrow);
            }
            default -> {
            }
        }
    }

    private void updateJobAndApplicationStatus(Dispute dispute, DisputeResolution resolution) {
        ShortTermJob job = dispute.getShortTermJob();
        ShortTermJobApplication application = dispute.getApplication();
        if (job == null || application == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        switch (resolution) {
            case FULL_RELEASE, WORKER_WINS, PARTIAL_REFUND, PARTIAL_RELEASE, WORKER_PARTIAL -> {
                application.setStatus(ShortTermApplicationStatus.COMPLETED);
                application.setCompletedAt(now);
                job.setStatus(ShortTermJobStatus.COMPLETED);
                job.setCompletedAt(now);
            }
            case FULL_REFUND, RECRUITER_WINS, CANCEL_JOB -> {
                application.setStatus(ShortTermApplicationStatus.CANCELLED);
                job.setStatus(ShortTermJobStatus.CANCELLED);
            }
            case RESUBMIT_REQUIRED, NO_ACTION -> {
                application.setStatus(ShortTermApplicationStatus.REVISION_REQUIRED);
                application.setLastActivityAt(now);
                job.setStatus(ShortTermJobStatus.IN_PROGRESS);
            }
            case RECRUITER_WARNING -> {
                application.setStatus(ShortTermApplicationStatus.SUBMITTED);
                application.setReviewDeadlineAt(now.plusHours(48));
                application.setLastActivityAt(now);
                job.setStatus(ShortTermJobStatus.SUBMITTED);
            }
            default -> {
            }
        }

        applicationRepository.save(application);
        shortTermJobRepository.save(job);
        log.info("Updated job {} and application status based on resolution {}", job.getId(), resolution);
    }

    private void notifyDisputeResolved(Dispute dispute) {
        try {
            notificationService.createNotification(
                    dispute.getInitiatorId(),
                    "Dispute Resolved",
                    "Your dispute has been resolved with outcome: " + dispute.getResolution(),
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
        return disputeRepository.findByShortTermJobId(jobId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Dispute> getMyDisputes(Long userId, Pageable pageable) {
        return disputeRepository.findByInitiatorIdOrRespondentId(userId, userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserSubmittedDisputeResponse> getMySubmittedDisputes(Long userId, Pageable pageable) {
        // [Nghiệp vụ] Tab "Disputes đã gửi" chỉ hiển thị các khiếu nại mà user hiện tại là người khởi tạo.
        Page<Dispute> disputes = disputeRepository.findByInitiatorId(userId, pageable);
        Map<Long, String> userNames = loadUserDisplayNames(disputes.getContent());
        return disputes.map(dispute -> toUserSubmittedDisputeResponse(dispute, userNames));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Dispute> getAllDisputes(Pageable pageable) {
        return disputeRepository.findByStatusIn(List.copyOf(ACTIVE_DISPUTE_STATUSES), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DisputeEvidence> getDisputeEvidence(Long disputeId) {
        return disputeEvidenceRepository.findByDisputeIdOrderByCreatedAtDesc(disputeId);
    }

    // [Nghiệp vụ] Tổng hợp thông tin dispute theo góc nhìn người gửi để FE hiển thị tab quản lý dạng read-only.
    private UserSubmittedDisputeResponse toUserSubmittedDisputeResponse(
            Dispute dispute,
            Map<Long, String> userNames) {
        ShortTermJob job = dispute.getShortTermJob();
        ShortTermJobApplication application = dispute.getApplication();

        return UserSubmittedDisputeResponse.builder()
                .id(dispute.getId())
                .jobId(job != null ? job.getId() : null)
                .applicationId(application != null ? application.getId() : null)
                .jobTitle(job != null ? job.getTitle() : null)
                .jobStatus(job != null && job.getStatus() != null ? job.getStatus().name() : null)
                .applicationStatus(application != null && application.getStatus() != null
                        ? application.getStatus().name()
                        : null)
                .initiatorId(dispute.getInitiatorId())
                .initiatorName(resolveDisplayName(dispute.getInitiatorId(), userNames))
                .respondentId(dispute.getRespondentId())
                .respondentName(resolveDisplayName(dispute.getRespondentId(), userNames))
                .disputeType(dispute.getDisputeType())
                .reason(dispute.getReason())
                .status(dispute.getStatus())
                .resolution(dispute.getResolution())
                .partialRefundPct(dispute.getPartialRefundPct())
                .resolutionNotes(dispute.getResolutionNotes())
                .resolvedBy(dispute.getResolvedBy())
                .resolvedByName(resolveDisplayName(dispute.getResolvedBy(), userNames))
                .resolvedAt(dispute.getResolvedAt())
                .createdAt(dispute.getCreatedAt())
                .adminResolutionDeadlineAt(dispute.getAdminResolutionDeadlineAt())
                .escalationLevel(dispute.getEscalationLevel())
                .priority(dispute.getPriority())
                .build();
    }

    private Map<Long, String> loadUserDisplayNames(List<Dispute> disputes) {
        Set<Long> userIds = new LinkedHashSet<>();
        for (Dispute dispute : disputes) {
            addUserId(userIds, dispute.getInitiatorId());
            addUserId(userIds, dispute.getRespondentId());
            addUserId(userIds, dispute.getResolvedBy());
        }

        if (userIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, this::buildDisplayName));
    }

    private void addUserId(Set<Long> userIds, Long userId) {
        if (userId != null) {
            userIds.add(userId);
        }
    }

    private String resolveDisplayName(Long userId, Map<Long, String> userNames) {
        if (userId == null) {
            return null;
        }
        return userNames.getOrDefault(userId, "User #" + userId);
    }

    private String buildDisplayName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return "User #" + user.getId();
    }
}
