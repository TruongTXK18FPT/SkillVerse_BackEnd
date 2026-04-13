package com.exe.skillverse_backend.admin_service.service.impl;

import com.exe.skillverse_backend.admin_service.dto.response.AdminFullTimeJobStatsResponse;
import com.exe.skillverse_backend.admin_service.service.AdminJobService;
import com.exe.skillverse_backend.business_service.dto.response.JobPostingResponse;
import com.exe.skillverse_backend.business_service.entity.JobApplication;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
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
public class AdminJobServiceImpl implements AdminJobService {

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ObjectMapper objectMapper;
    private final WalletService walletService;

    private static final BigDecimal JOB_POSTING_FEE = new BigDecimal("50000");

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
    @Transactional(readOnly = true)
    public Page<JobPostingResponse> getAllJobs(JobStatus status, Pageable pageable) {
        log.info("Fetching full-time admin jobs. status={}, page={}, size={}", status, pageable.getPageNumber(),
                pageable.getPageSize());
        return jobPostingRepository.findAllForAdmin(status, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminFullTimeJobStatsResponse getJobStats() {
        log.info("Fetching full-time admin job stats");

        List<JobPosting> jobs = jobPostingRepository.findAll();
        List<JobApplication> applications = jobApplicationRepository.findAll();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Long> byJobType = new LinkedHashMap<>();
        Map<String, Long> byExperienceLevel = new LinkedHashMap<>();
        Map<String, Long> byWorkMode = new LinkedHashMap<>();
        Map<String, Long> applicationByStatus = new LinkedHashMap<>();

        long totalApplicants = 0L;
        long totalBudgetFloor = 0L;
        long totalBudgetCeiling = 0L;
        long remoteCount = 0L;
        long onsiteCount = 0L;
        long highlightedCount = 0L;
        long negotiableCount = 0L;
        long draftCount = 0L;
        long pendingApprovalCount = 0L;
        long openCount = 0L;
        long closedCount = 0L;
        long rejectedCount = 0L;

        for (JobPosting job : jobs) {
            String statusKey = job.getStatus() != null ? job.getStatus().name() : "UNKNOWN";
            byStatus.merge(statusKey, 1L, Long::sum);

            if (job.getStatus() == JobStatus.IN_PROGRESS) {
                draftCount++;
            } else if (job.getStatus() == JobStatus.PENDING_APPROVAL) {
                pendingApprovalCount++;
            } else if (job.getStatus() == JobStatus.OPEN) {
                openCount++;
            } else if (job.getStatus() == JobStatus.CLOSED) {
                closedCount++;
            } else if (job.getStatus() == JobStatus.REJECTED) {
                rejectedCount++;
            }

            if (Boolean.TRUE.equals(job.getIsRemote())) {
                remoteCount++;
                byWorkMode.merge("REMOTE", 1L, Long::sum);
            } else {
                onsiteCount++;
                byWorkMode.merge("ON_SITE", 1L, Long::sum);
            }

            if (Boolean.TRUE.equals(job.getIsHighlighted())) {
                highlightedCount++;
            }
            if (Boolean.TRUE.equals(job.getIsNegotiable())) {
                negotiableCount++;
            }

            if (job.getJobType() != null && !job.getJobType().isBlank()) {
                byJobType.merge(job.getJobType(), 1L, Long::sum);
            } else {
                byJobType.merge("UNSPECIFIED", 1L, Long::sum);
            }

            if (job.getExperienceLevel() != null && !job.getExperienceLevel().isBlank()) {
                byExperienceLevel.merge(job.getExperienceLevel(), 1L, Long::sum);
            } else {
                byExperienceLevel.merge("UNSPECIFIED", 1L, Long::sum);
            }

            totalApplicants += job.getApplicantCount() != null ? job.getApplicantCount() : 0;
            totalBudgetFloor += job.getMinBudget() != null ? job.getMinBudget().longValue() : 0L;
            totalBudgetCeiling += job.getMaxBudget() != null ? job.getMaxBudget().longValue() : 0L;
        }

        for (JobApplication application : applications) {
            String applicationStatusKey = application.getStatus() != null ? application.getStatus().name() : "UNKNOWN";
            applicationByStatus.merge(applicationStatusKey, 1L, Long::sum);
        }

        double averageApplicantsPerJob = jobs.isEmpty() ? 0D : (double) totalApplicants / jobs.size();

        return AdminFullTimeJobStatsResponse.builder()
                .totalJobs(jobs.size())
                .draftCount(draftCount)
                .pendingApprovalCount(pendingApprovalCount)
                .openCount(openCount)
                .closedCount(closedCount)
                .rejectedCount(rejectedCount)
                .remoteCount(remoteCount)
                .onsiteCount(onsiteCount)
                .highlightedCount(highlightedCount)
                .negotiableCount(negotiableCount)
                .totalApplicants(totalApplicants)
                .averageApplicantsPerJob(averageApplicantsPerJob)
                .totalBudgetFloor(totalBudgetFloor)
                .totalBudgetCeiling(totalBudgetCeiling)
                .byStatus(byStatus)
                .byJobType(byJobType)
                .byExperienceLevel(byExperienceLevel)
                .byWorkMode(byWorkMode)
                .applicationByStatus(applicationByStatus)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public JobPostingResponse getJobDetail(Long jobId) {
        log.info("Fetching full-time admin job detail for ID: {}", jobId);

        JobPosting job = jobPostingRepository.findByIdWithRecruiter(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found with ID: " + jobId));

        return mapToResponse(job);
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

        // Just change status to OPEN — no fee to deduct (recruiter pays via subscription)
        job.setStatus(JobStatus.OPEN);
        JobPosting savedJob = jobPostingRepository.save(job);
        
        log.info("Job ID: {} approved successfully", jobId);
        return mapToResponse(savedJob);
    }

    @Override
    @Transactional
    public JobPostingResponse closeJob(Long adminId, Long jobId, String reason) {
        log.info("Admin {} closing full-time job ID: {}. reason={}", adminId, jobId, reason);

        JobPosting job = jobPostingRepository.findByIdWithRecruiter(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found with ID: " + jobId));

        if (job.getStatus() == JobStatus.CLOSED) {
            throw new IllegalStateException("Job is already closed");
        }

        job.setStatus(JobStatus.CLOSED);
        job.setClosedAt(java.time.LocalDateTime.now());

        JobPosting savedJob = jobPostingRepository.save(job);
        log.info("Full-time job ID: {} closed successfully by admin {}", jobId, adminId);
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

        // Refund if recruiter paid via wallet (postingFeeCharged=true && paidViaSubscription=false)
        if (job.getPostingFeeCharged() != null && job.getPostingFeeCharged()
                && (job.getPaidViaSubscription() == null || !job.getPaidViaSubscription())) {
            Long recruiterId = job.getRecruiterProfile().getUser().getId();
            try {
                walletService.processRefund(
                        recruiterId,
                        JOB_POSTING_FEE,
                        "Hoàn tiền phí đăng tin tuyển dụng bị từ chối",
                    "JOB_POSTING_REFUND",
                        String.valueOf(jobId)
                );
                log.info("Refunded 50,000 VND to recruiter user ID: {} for rejected job ID: {}", recruiterId, jobId);
            } catch (Exception ex) {
                throw new IllegalStateException("Refund failed", ex);
            }
        }
        // If paid via subscription quota, quota is consumed — no refund

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
                .genderRequirement(job.getGenderRequirement())
                .isNegotiable(job.getIsNegotiable())
                .isHighlighted(job.getIsHighlighted())
                .recruiterCompanyName(job.getRecruiterProfile().getCompanyName())
                .recruiterCompanyLogoUrl(resolveRecruiterCompanyLogo(job.getRecruiterProfile()))
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

    private String resolveRecruiterCompanyLogo(RecruiterProfile recruiterProfile) {
        if (recruiterProfile == null) {
            return null;
        }
        if (recruiterProfile.getCompanyLogoUrl() != null && !recruiterProfile.getCompanyLogoUrl().isBlank()) {
            return recruiterProfile.getCompanyLogoUrl();
        }
        return recruiterProfile.getUser() != null ? recruiterProfile.getUser().getAvatarUrl() : null;
    }
}
