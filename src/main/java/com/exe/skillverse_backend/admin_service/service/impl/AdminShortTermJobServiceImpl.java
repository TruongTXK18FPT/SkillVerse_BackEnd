package com.exe.skillverse_backend.admin_service.service.impl;

import com.exe.skillverse_backend.admin_service.service.AdminShortTermJobService;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermJobResponse;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.ShortTermJobService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminShortTermJobServiceImpl implements AdminShortTermJobService {

    private final ShortTermJobRepository shortTermJobRepository;
    private final ShortTermJobService shortTermJobService;
    private final WalletService walletService;

    private static final BigDecimal SHORT_TERM_JOB_POSTING_FEE = new BigDecimal("30000");

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

        // No fee deduction — recruiter pays via RECRUITER_PRO subscription
        // Subscription and quota were already validated when submitting for approval

        // Change to PUBLISHED
        job.setStatus(ShortTermJobStatus.PUBLISHED);
        job.setPublishedAt(LocalDateTime.now());
        ShortTermJob savedJob = shortTermJobRepository.save(job);

        log.info("Short-term job ID: {} approved and published", jobId);
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

        // Change back to DRAFT so recruiter can edit and re-submit
        // Refund if recruiter paid via wallet (not subscription)
        if (job.getPaidViaSubscription() == null || !job.getPaidViaSubscription()) {
            Long recruiterId = job.getRecruiterProfile().getUser().getId();
            walletService.processRefund(
                    recruiterId,
                    SHORT_TERM_JOB_POSTING_FEE,
                    "Hoàn tiền phí đăng tin ngắn hạn bị từ chối",
                    String.valueOf(jobId)
            );
            log.info("Refunded 30,000 VND to recruiter user ID: {} for rejected short-term job ID: {}", recruiterId, jobId);
        }
        // If paid via subscription, quota is consumed — no refund
        job.setStatus(ShortTermJobStatus.DRAFT);
        ShortTermJob savedJob = shortTermJobRepository.save(job);

        log.info("Short-term job ID: {} rejected, reverted to DRAFT", jobId);
        return shortTermJobService.getJobDetails(savedJob.getId());
    }
}
