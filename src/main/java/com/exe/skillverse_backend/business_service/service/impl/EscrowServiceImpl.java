package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.entity.EscrowTransaction;
import com.exe.skillverse_backend.business_service.entity.EscrowTransaction.EscrowTransactionType;
import com.exe.skillverse_backend.business_service.entity.JobEscrow;
import com.exe.skillverse_backend.business_service.entity.JobEscrow.EscrowStatus;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.repository.EscrowTransactionRepository;
import com.exe.skillverse_backend.business_service.repository.JobEscrowRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.EscrowService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
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
public class EscrowServiceImpl implements EscrowService {

    private final JobEscrowRepository jobEscrowRepository;
    private final EscrowTransactionRepository escrowTransactionRepository;
    private final ShortTermJobRepository shortTermJobRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    private static final BigDecimal FEE_RATE = new BigDecimal("0.10");

    @Override
    public JobEscrow fundEscrow(Long jobId, Long userId) {
        log.info("Funding escrow for job ID: {} by user ID: {}", jobId, userId);

        ShortTermJob job = shortTermJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Short-term job not found with ID: " + jobId));

        // Validate recruiter owns the job
        RecruiterProfile profile = recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("You must be a recruiter to fund escrow"));
        if (!job.getRecruiterProfile().getUserId().equals(profile.getUserId())) {
            throw new ForbiddenException("You do not own this job");
        }

        // Check if escrow already exists
        if (jobEscrowRepository.existsByJobId(jobId)) {
            throw new BadRequestException("Escrow already exists for this job");
        }

        BigDecimal totalAmount = job.getBudget();
        BigDecimal platformFee = totalAmount.multiply(FEE_RATE).setScale(2, RoundingMode.HALF_UP);

        // Check wallet has sufficient available balance
        if (!walletService.hasAvailableCash(userId, totalAmount)) {
            throw new BadRequestException("Insufficient available balance in wallet to fund escrow");
        }

        // Freeze cash from recruiter's wallet
        String freezeDesc = "Đóng băng số tiền cho tuyển dụng công việc";
        walletService.freezeCashForBooking(userId, totalAmount, jobId, freezeDesc);
        log.info("Froze {} VND in wallet for job {}", totalAmount, jobId);

        // Create JobEscrow record
        JobEscrow escrow = JobEscrow.builder()
                .job(job)
                .recruiterId(userId)
                .workerId(job.getSelectedApplicantId())
                .totalAmount(totalAmount)
                .platformFee(platformFee)
                .feeRate(FEE_RATE)
                .escrowBalance(totalAmount)
                .pendingPayoutBalance(BigDecimal.ZERO)
                .status(EscrowStatus.FUNDED)
                .fundedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        escrow = jobEscrowRepository.save(escrow);

        // Update job: set recruiterProfile reference and funded timestamp
        job.setRecruiterProfile(profile);
        shortTermJobRepository.save(job);

        // Get actor name
        User actor = userRepository.findById(userId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Recruiter";

        // Create FUND transaction record
        EscrowTransaction transaction = EscrowTransaction.builder()
                .escrow(escrow)
                .transactionType(EscrowTransactionType.FUND)
                .amount(totalAmount)
                .feeAmount(BigDecimal.ZERO)
                .netAmount(totalAmount)
                .actorId(userId)
                .actorName(actorName)
                .reason("Escrow funded for job: " + job.getTitle())
                .metadata("{\"jobId\":" + jobId + "}")
                .build();
        escrowTransactionRepository.save(transaction);

        // Notify worker
        if (job.getSelectedApplicantId() != null) {
            try {
                notificationService.createNotification(
                        job.getSelectedApplicantId(),
                        "Escrow Funded",
                        "Recruiter has funded escrow for job: " + job.getTitle(),
                        NotificationType.ESCROW_FUNDED,
                        String.valueOf(jobId)
                );
            } catch (Exception e) {
                log.warn("Failed to send notification to worker: {}", e.getMessage());
            }
        }

        log.info("Escrow funded successfully. Escrow ID: {} for job: {}", escrow.getId(), jobId);
        return escrow;
    }

    @Override
    public JobEscrow releaseEscrow(Long jobId, Long userId, String message) {
        log.info("Releasing escrow for job ID: {} by user ID: {}", jobId, userId);

        JobEscrow escrow = jobEscrowRepository.findByJobId(jobId)
                .orElseThrow(() -> new NotFoundException("Escrow not found for job ID: " + jobId));

        // Validate escrow is in correct state (DISPUTED allowed so admin can resolve)
        if (escrow.getStatus() != EscrowStatus.FUNDED
                && escrow.getStatus() != EscrowStatus.PARTIALLY_RELEASED
                && escrow.getStatus() != EscrowStatus.DISPUTED) {
            throw new BadRequestException("Escrow cannot be released in current status: " + escrow.getStatus());
        }

        // Validate recruiter owns the job
        ShortTermJob job = escrow.getJob();
        if (!job.getRecruiterProfile().getUserId().equals(userId)) {
            throw new ForbiddenException("You do not own this job");
        }

        if (escrow.getWorkerId() == null) {
            throw new BadRequestException("No worker assigned to this job");
        }

        BigDecimal netAmount = escrow.getEscrowBalance().subtract(escrow.getPlatformFee());
        if (netAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Insufficient escrow balance for release");
        }

        // Charge frozen cash from recruiter's wallet (complete the payment)
        String chargeDesc = "Thanh toán tuyển dụng từ số tiền đóng băng cho công việc";
        walletService.chargeFrozenForBooking(escrow.getRecruiterId(), escrow.getTotalAmount(), jobId, chargeDesc);
        log.info("Charged {} VND from recruiter {} wallet (frozen balance) for job {}",
                escrow.getTotalAmount(), escrow.getRecruiterId(), jobId);

        // Update escrow
        BigDecimal oldBalance = escrow.getEscrowBalance();
        escrow.setEscrowBalance(BigDecimal.ZERO);
        escrow.setPendingPayoutBalance(netAmount);
        escrow.setStatus(EscrowStatus.FULLY_RELEASED);
        escrow.setReleasedAt(LocalDateTime.now());

        // Get actor name
        User actor = userRepository.findById(userId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Recruiter";

        // Create RELEASE transaction for worker payout
        EscrowTransaction releaseTx = EscrowTransaction.builder()
                .escrow(escrow)
                .transactionType(EscrowTransactionType.RELEASE)
                .amount(netAmount)
                .feeAmount(BigDecimal.ZERO)
                .netAmount(netAmount)
                .actorId(userId)
                .actorName(actorName)
                .reason(message != null ? message : "Job completed, releasing escrow")
                .metadata("{\"workerId\":" + escrow.getWorkerId() + ",\"oldBalance\":" + oldBalance + "}")
                .build();
        escrowTransactionRepository.save(releaseTx);

        // Create FEE_DEDUCTION transaction for platform fee
        EscrowTransaction feeTx = EscrowTransaction.builder()
                .escrow(escrow)
                .transactionType(EscrowTransactionType.FEE_DEDUCTION)
                .amount(escrow.getPlatformFee())
                .feeAmount(BigDecimal.ZERO)
                .netAmount(BigDecimal.ZERO)
                .actorId(userId)
                .actorName(actorName)
                .reason("Platform fee (10%) from escrow release")
                .metadata("{\"feeRate\":\"0.10\"}")
                .build();
        escrowTransactionRepository.save(feeTx);

        escrow = jobEscrowRepository.save(escrow);

        // Transfer money to worker wallet immediately
        try {
            walletService.payMentorForJobPayout(escrow.getWorkerId(), netAmount, jobId);
            log.info("Transferred {} VND to worker {} wallet for job {}", netAmount, escrow.getWorkerId(), jobId);
        } catch (Exception e) {
            log.error("Failed to transfer payout to worker wallet: {}", e.getMessage());
        }

        // Notify worker
        try {
            notificationService.createNotification(
                    escrow.getWorkerId(),
                    "Escrow Released",
                    netAmount + " VND đã được chuyển vào ví cho công việc: " + job.getTitle(),
                    NotificationType.ESCROW_RELEASED,
                    String.valueOf(jobId)
            );
        } catch (Exception e) {
            log.warn("Failed to send notification to worker: {}", e.getMessage());
        }

        log.info("Escrow released for job {}. Net amount: {}, Platform fee: {}", jobId, netAmount, escrow.getPlatformFee());
        return escrow;
    }

    @Override
    public JobEscrow refundEscrow(Long jobId, Long userId, String reason) {
        log.info("Refunding escrow for job ID: {} by user ID: {}", jobId, userId);

        JobEscrow escrow = jobEscrowRepository.findByJobId(jobId)
                .orElseThrow(() -> new NotFoundException("Escrow not found for job ID: " + jobId));

        // Validate escrow is in refundable state (DISPUTED allowed so admin can resolve)
        if (escrow.getStatus() != EscrowStatus.FUNDED
                && escrow.getStatus() != EscrowStatus.PARTIALLY_RELEASED
                && escrow.getStatus() != EscrowStatus.DISPUTED) {
            throw new BadRequestException("Escrow cannot be refunded in current status: " + escrow.getStatus());
        }

        // Validate recruiter owns the job
        ShortTermJob job = escrow.getJob();
        if (!job.getRecruiterProfile().getUserId().equals(userId)) {
            throw new ForbiddenException("You do not own this job");
        }

        BigDecimal refundAmount = escrow.getEscrowBalance();
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("No balance available to refund");
        }

        // Unfreeze the frozen cash (the totalAmount that was frozen)
        String unfreezeDesc = "Hoàn tiền (giải phóng đóng băng) cho tuyển dụng công việc";
        walletService.unfreezeForBooking(userId, escrow.getTotalAmount(), jobId, unfreezeDesc);
        log.info("Unfroze {} VND from wallet for job {}", escrow.getTotalAmount(), jobId);

        // Update escrow
        escrow.setEscrowBalance(BigDecimal.ZERO);
        escrow.setPendingPayoutBalance(BigDecimal.ZERO);
        escrow.setStatus(EscrowStatus.REFUNDED);
        escrow.setRefundedAt(LocalDateTime.now());

        // Get actor name
        User actor = userRepository.findById(userId).orElse(null);
        String actorName = actor != null ? actor.getFullName() : "Recruiter";

        // Create REFUND transaction
        EscrowTransaction transaction = EscrowTransaction.builder()
                .escrow(escrow)
                .transactionType(EscrowTransactionType.REFUND)
                .amount(refundAmount)
                .feeAmount(escrow.getPlatformFee())
                .netAmount(refundAmount.subtract(escrow.getPlatformFee()))
                .actorId(userId)
                .actorName(actorName)
                .reason(reason != null ? reason : "Escrow refunded")
                .metadata("{\"totalAmount\":" + escrow.getTotalAmount() + ",\"platformFee\":" + escrow.getPlatformFee() + "}")
                .build();
        escrowTransactionRepository.save(transaction);

        escrow = jobEscrowRepository.save(escrow);

        // Notify worker
        if (escrow.getWorkerId() != null) {
            try {
                notificationService.createNotification(
                        escrow.getWorkerId(),
                        "Escrow Refunded",
                        "Escrow for job: " + job.getTitle() + " has been refunded by the recruiter.",
                        NotificationType.ESCROW_REFUNDED,
                        String.valueOf(jobId)
                );
            } catch (Exception e) {
                log.warn("Failed to send notification to worker: {}", e.getMessage());
            }
        }

        log.info("Escrow refunded for job {}. Refund amount: {}, Platform fee retained: {}",
                jobId, refundAmount.subtract(escrow.getPlatformFee()), escrow.getPlatformFee());
        return escrow;
    }

    @Override
    @Transactional(readOnly = true)
    public JobEscrow getEscrowByJobId(Long jobId) {
        return jobEscrowRepository.findByJobId(jobId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EscrowTransaction> getEscrowTransactions(Long jobId) {
        return jobEscrowRepository.findByJobId(jobId)
                .map(escrow -> escrowTransactionRepository.findByEscrowIdOrderByCreatedAtDesc(escrow.getId()))
                .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EscrowTransaction> getEscrowTransactionsPaged(Long jobId, Pageable pageable) {
        return jobEscrowRepository.findByJobId(jobId)
                .map(escrow -> escrowTransactionRepository.findByEscrowIdPaged(escrow.getId(), pageable))
                .orElse(Page.empty(pageable));
    }

    @Override
    @Transactional
    public void releasePendingPayouts() {
        log.info("Starting release pending payouts task...");

        List<JobEscrow> escrowsWithPayouts = jobEscrowRepository.findByStatusIn(
                Arrays.asList(EscrowStatus.FULLY_RELEASED, EscrowStatus.PARTIALLY_RELEASED));

        int releasedCount = 0;
        for (JobEscrow escrow : escrowsWithPayouts) {
            if (escrow.getPendingPayoutBalance().compareTo(BigDecimal.ZERO) > 0 && escrow.getWorkerId() != null) {
                try {
                    BigDecimal amount = escrow.getPendingPayoutBalance();
                    // Check if already paid via payMentorForJobPayout (uses JOB_PAYOUT reference)
                    // to prevent double payout. Only pay if not already transferred.
                    String payoutRefType = "JOB_PAYOUT";
                    String payoutRefId = "JOB_" + escrow.getJob().getId();
                    boolean alreadyPaid = walletTransactionRepository.existsByReferenceIdAndReferenceTypeAndStatus(
                            payoutRefId, payoutRefType, WalletTransaction.TransactionStatus.COMPLETED);
                    if (alreadyPaid) {
                        log.info("Payout for job {} already processed via payMentorForJobPayout, skipping scheduled payout",
                                escrow.getJob().getId());
                        // Reset pendingPayoutBalance since money was already transferred
                        escrow.setPendingPayoutBalance(BigDecimal.ZERO);
                        jobEscrowRepository.save(escrow);
                        continue;
                    }
                    walletService.payMentorForBooking(escrow.getWorkerId(), amount, escrow.getJob().getId());

                    // Create PAYOUT_RELEASE transaction
                    EscrowTransaction payoutTx = EscrowTransaction.builder()
                            .escrow(escrow)
                            .transactionType(EscrowTransactionType.PAYOUT_RELEASE)
                            .amount(amount)
                            .feeAmount(BigDecimal.ZERO)
                            .netAmount(amount)
                            .actorId(escrow.getWorkerId())
                            .actorName("System")
                            .reason("Pending payout released to worker wallet")
                            .metadata("{\"releasedAt\":\"" + LocalDateTime.now() + "\"}")
                            .build();
                    escrowTransactionRepository.save(payoutTx);

                    // Update escrow
                    escrow.setPendingPayoutBalance(BigDecimal.ZERO);
                    jobEscrowRepository.save(escrow);

                    releasedCount++;
                    log.info("Released pending payout of {} VND to worker {} for job {}",
                            amount, escrow.getWorkerId(), escrow.getJob().getId());
                } catch (Exception e) {
                    log.error("Failed to release payout for escrow ID: {} - {}", escrow.getId(), e.getMessage());
                }
            }
        }

        log.info("Release pending payouts task completed. Released {} payouts", releasedCount);
    }
}
