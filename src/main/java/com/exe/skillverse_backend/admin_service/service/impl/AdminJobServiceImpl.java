package com.exe.skillverse_backend.admin_service.service.impl;

import com.exe.skillverse_backend.admin_service.service.AdminJobService;
import com.exe.skillverse_backend.business_service.dto.response.JobPostingResponse;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdminJobServiceImpl implements AdminJobService {

    private final JobPostingRepository jobPostingRepository;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<JobPostingResponse> getPendingJobs() {
        log.info("Fetching all pending jobs for admin");
        List<JobPosting> jobs = jobPostingRepository.findByStatusWithRecruiterOrderByCreatedAtDesc(JobStatus.PENDING_APPROVAL);
        return jobs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public JobPostingResponse approveJob(Long jobId) {
        log.info("Approving job ID: {}", jobId);
        
        JobPosting job = jobPostingRepository.findByIdWithRecruiter(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found with ID: " + jobId));

        if (job.getStatus() != JobStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Job is not in pending status");
        }

        // Just change status to OPEN, money was already deducted
        job.setStatus(JobStatus.OPEN);
        JobPosting savedJob = jobPostingRepository.save(job);
        
        log.info("Job ID: {} approved successfully", jobId);
        return mapToResponse(savedJob);
    }

    @Override
    @Transactional
    public JobPostingResponse rejectJob(Long jobId, String reason) {
        log.info("Rejecting job ID: {} with reason: {}", jobId, reason);

        JobPosting job = jobPostingRepository.findByIdWithRecruiter(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found with ID: " + jobId));

        if (job.getStatus() != JobStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Job is not in pending status");
        }

        // Refund 50,000 VND
        Long userId = job.getRecruiterProfile().getUser().getId();
        try {
            walletService.processRefund(
                userId,
                new BigDecimal("50000"),
                "Refund Job Posting Fee (Rejected): " + job.getTitle() + ". Reason: " + reason,
                String.valueOf(job.getId())
            );
        } catch (Exception e) {
            log.error("Failed to refund job fee", e);
            throw new IllegalStateException("Failed to refund job fee: " + e.getMessage());
        }

        job.setStatus(JobStatus.REJECTED);
        JobPosting savedJob = jobPostingRepository.save(job);

        log.info("Job ID: {} rejected successfully", jobId);
        return mapToResponse(savedJob);
    }

    // Helper method (copied from JobPostingServiceImpl to avoid circular dependency or code duplication)
    private JobPostingResponse mapToResponse(JobPosting job) {
        return JobPostingResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .description(job.getDescription())
                .requiredSkills(convertJsonToSkills(job.getRequiredSkills()))
                .minBudget(job.getMinBudget())
                .maxBudget(job.getMaxBudget())
                .deadline(job.getDeadline())
                .isRemote(job.getIsRemote())
                .location(job.getLocation())
                .status(job.getStatus())
                .applicantCount(job.getApplicantCount())
                .experienceLevel(job.getExperienceLevel())
                .jobType(job.getJobType())
                .hiringQuantity(job.getHiringQuantity())
                .benefits(job.getBenefits())
                .recruiterCompanyName(job.getRecruiterProfile().getCompanyName())
                .recruiterEmail(job.getRecruiterProfile().getUser().getEmail())
                .recruiterUserId(job.getRecruiterProfile().getUser().getId())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private List<String> convertJsonToSkills(String json) {
        try {
            return Arrays.asList(objectMapper.readValue(json, String[].class));
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to skills", e);
            return List.of();
        }
    }
}
