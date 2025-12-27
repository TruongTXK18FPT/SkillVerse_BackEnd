package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.business_service.dto.request.CreateJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateJobRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobPostingResponse;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.service.JobPostingService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.exe.skillverse_backend.wallet_service.service.WalletService;
import java.math.BigDecimal;

import com.exe.skillverse_backend.business_service.dto.request.ReopenJobRequest;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobPostingServiceImpl implements JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ObjectMapper objectMapper;
    private final WalletService walletService;

    /**
     * Create a new job posting (status = PENDING_APPROVAL, fee = 50k)
     */
    @Transactional
    public JobPostingResponse createJob(Long userId, CreateJobRequest request) {
        log.info("Creating job for recruiter user ID: {}", userId);

        // Validate budget
        if (request.getMaxBudget().compareTo(request.getMinBudget()) < 0) {
            throw new IllegalArgumentException("Maximum budget cannot be less than minimum budget");
        }

        // Validate location if not remote
        if (!request.getIsRemote() && (request.getLocation() == null || request.getLocation().trim().isEmpty())) {
            throw new IllegalArgumentException("Location is required for non-remote jobs");
        }

        // Validate deadline (Max 90 days)
        if (request.getDeadline().isAfter(LocalDate.now().plusDays(90))) {
            throw new IllegalArgumentException("Deadline cannot be more than 90 days from today");
        }

        // Find recruiter profile
        RecruiterProfile recruiterProfile = recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Recruiter profile not found for user ID: " + userId));

        // Normalize skills: lowercase, trim, remove duplicates
        List<String> normalizedSkills = request.getRequiredSkills().stream()
                .map(String::toLowerCase)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());

        // Convert skills to JSON
        String skillsJson = convertSkillsToJson(normalizedSkills);

        // Build job posting
        JobPosting job = JobPosting.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .requiredSkills(skillsJson)
                .minBudget(request.getMinBudget())
                .maxBudget(request.getMaxBudget())
                .deadline(request.getDeadline())
                .isRemote(request.getIsRemote())
                .location(request.getLocation())
                .status(JobStatus.PENDING_APPROVAL) // Default status for approval
                .applicantCount(0)
                .experienceLevel(request.getExperienceLevel())
                .jobType(request.getJobType())
                .hiringQuantity(request.getHiringQuantity())
                .benefits(request.getBenefits())
                .genderRequirement(request.getGenderRequirement())
                .isNegotiable(request.getIsNegotiable() != null ? request.getIsNegotiable() : false)
                .recruiterProfile(recruiterProfile)
                .build();

        JobPosting savedJob = jobPostingRepository.save(job);

        // Deduct Job Posting Fee (50,000 VND)
        try {
            BigDecimal fee = new BigDecimal("50000");
            walletService.deductCash(
                    userId,
                    fee,
                    "Phí đăng tin tuyển dụng: " + savedJob.getTitle(),
                    "JOB_POSTING",
                    String.valueOf(savedJob.getId()));
            log.info("Deducted 50,000 VND for job ID: {}", savedJob.getId());
        } catch (Exception e) {
            log.error("Failed to deduct job posting fee", e);
            throw new IllegalStateException(
                    "Số dư ví không đủ 50.000 VNĐ để đăng tin tuyển dụng. Vui lòng nạp thêm tiền.");
        }

        log.info("Job created successfully with ID: {}", savedJob.getId());

        return mapToResponse(savedJob);
    }

    /**
     * Update job posting (only allowed if status = IN_PROGRESS or CLOSED)
     */
    @Transactional
    public JobPostingResponse updateJob(Long userId, Long jobId, UpdateJobRequest request) {
        log.info("Updating job ID: {} by user ID: {}", jobId, userId);

        // Find job and validate ownership
        JobPosting job = jobPostingRepository.findByIdAndRecruiterProfileUserId(jobId, userId)
                .orElseThrow(() -> new NotFoundException("Job not found or you don't have permission to edit it"));

        // Validate status - only allow edit if IN_PROGRESS or CLOSED
        // UPDATE: Allow editing CLOSED jobs (User can edit then reopen for a fee)
        if (job.getStatus() == JobStatus.OPEN) {
            throw new IllegalStateException(
                    "Cannot edit job while it is OPEN. Close it first or change only the status.");
        }

        // Update fields if provided
        if (request.getTitle() != null) {
            job.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            job.setDescription(request.getDescription());
        }
        if (request.getRequiredSkills() != null && !request.getRequiredSkills().isEmpty()) {
            List<String> normalizedSkills = request.getRequiredSkills().stream()
                    .map(String::toLowerCase)
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());
            job.setRequiredSkills(convertSkillsToJson(normalizedSkills));
        }
        if (request.getMinBudget() != null) {
            job.setMinBudget(request.getMinBudget());
        }
        if (request.getMaxBudget() != null) {
            job.setMaxBudget(request.getMaxBudget());
        }
        if (request.getDeadline() != null) {
            // Validate deadline (Max 90 days)
            if (request.getDeadline().isAfter(LocalDate.now().plusDays(90))) {
                throw new IllegalArgumentException("Deadline cannot be more than 90 days from today");
            }
            job.setDeadline(request.getDeadline());
        }
        if (request.getIsRemote() != null) {
            job.setIsRemote(request.getIsRemote());
        }
        if (request.getLocation() != null) {
            job.setLocation(request.getLocation());
        }

        // Enhanced fields update
        if (request.getExperienceLevel() != null)
            job.setExperienceLevel(request.getExperienceLevel());
        if (request.getJobType() != null)
            job.setJobType(request.getJobType());
        if (request.getHiringQuantity() != null)
            job.setHiringQuantity(request.getHiringQuantity());
        if (request.getBenefits() != null)
            job.setBenefits(request.getBenefits());
        if (request.getGenderRequirement() != null)
            job.setGenderRequirement(request.getGenderRequirement());
        if (request.getIsNegotiable() != null)
            job.setIsNegotiable(request.getIsNegotiable());

        // Validate budget
        if (job.getMaxBudget().compareTo(job.getMinBudget()) < 0) {
            throw new IllegalArgumentException("Maximum budget cannot be less than minimum budget");
        }

        // Validate location if not remote
        if (!job.getIsRemote() && (job.getLocation() == null || job.getLocation().trim().isEmpty())) {
            throw new IllegalArgumentException("Location is required for non-remote jobs");
        }

        JobPosting updatedJob = jobPostingRepository.save(job);
        log.info("Job updated successfully: {}", jobId);

        return mapToResponse(updatedJob);
    }

    /**
     * Change job status (IN_PROGRESS -> OPEN -> CLOSED)
     */
    @Transactional
    public JobPostingResponse changeStatus(Long userId, Long jobId, JobStatus newStatus) {
        log.info("Changing job ID: {} status to {} by user ID: {}", jobId, newStatus, userId);

        // Find job and validate ownership
        JobPosting job = jobPostingRepository.findByIdAndRecruiterProfileUserId(jobId, userId)
                .orElseThrow(
                        () -> new NotFoundException("Job not found or you don't have permission to change its status"));

        // Validate status transition
        JobStatus currentStatus = job.getStatus();
        if (currentStatus == JobStatus.CLOSED && newStatus != JobStatus.CLOSED) {
            throw new IllegalStateException("Cannot change status of a CLOSED job. Use reopen instead.");
        }

        // Update closedAt if status is changing to CLOSED
        if (newStatus == JobStatus.CLOSED) {
            job.setClosedAt(LocalDateTime.now());
        }

        job.setStatus(newStatus);
        JobPosting updatedJob = jobPostingRepository.save(job);
        log.info("Job status changed from {} to {}", currentStatus, newStatus);

        return mapToResponse(updatedJob);
    }

    /**
     * Get all jobs for current recruiter
     * OPTIMIZED: Uses JOIN FETCH to prevent N+1 queries (91 queries → 1 query for
     * 30 jobs)
     */
    @Transactional(readOnly = true)
    public List<JobPostingResponse> getMyJobs(Long userId) {
        log.info("Fetching jobs for recruiter user ID: {}", userId);

        List<JobPosting> jobs = jobPostingRepository.findByRecruiterUserIdWithRecruiterOrderByCreatedAtDesc(userId);
        return jobs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all public jobs (status = OPEN)
     * OPTIMIZED: Uses JOIN FETCH to prevent N+1 queries (201 queries → 1 query for
     * 100 jobs)
     */
    @Transactional(readOnly = true)
    public List<JobPostingResponse> getPublicJobs() {
        log.info("Fetching public jobs (status = OPEN)");

        List<JobPosting> jobs = jobPostingRepository.findByStatusWithRecruiterOrderByCreatedAtDesc(JobStatus.OPEN);
        return jobs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get job details by ID
     * OPTIMIZED: Uses JOIN FETCH to prevent lazy loading of recruiter profile
     */
    @Transactional(readOnly = true)
    public JobPostingResponse getJobDetails(Long jobId) {
        log.info("Fetching job details for ID: {}", jobId);

        JobPosting job = jobPostingRepository.findByIdWithRecruiter(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found with ID: " + jobId));

        return mapToResponse(job);
    }

    /**
     * Delete job (only if status = IN_PROGRESS)
     * UPDATE: Allow delete for any status if force delete is requested (for
     * cleanup)
     * NOTE: This will also delete associated applications due to CascadeType or
     * manual cleanup
     */
    @Transactional
    public void deleteJob(Long userId, Long jobId) {
        log.info("Deleting job ID: {} by user ID: {}", jobId, userId);

        // Find job and validate ownership
        JobPosting job = jobPostingRepository.findByIdAndRecruiterProfileUserId(jobId, userId)
                .orElseThrow(() -> new NotFoundException("Job not found or you don't have permission to delete it"));

        // Manual cleanup of applications before deleting job
        // This is necessary if CascadeType.REMOVE is not set on the entity relationship
        jobApplicationRepository.deleteByJobPostingId(jobId);

        jobPostingRepository.delete(job);
        log.info("Job deleted successfully: {}", jobId);
    }

    /**
     * Reopen job (optionally delete applications, set status to OPEN)
     * FEE: 20,000 VND (unless reopened within 5 mins of closing - GRACE PERIOD)
     */
    @Transactional
    public JobPostingResponse reopenJob(Long userId, Long jobId, ReopenJobRequest request) {
        log.info("Reopening job ID: {} by user ID: {}", jobId, userId);

        // Find job and validate ownership
        JobPosting job = jobPostingRepository.findByIdAndRecruiterProfileUserId(jobId, userId)
                .orElseThrow(() -> new NotFoundException("Job not found or you don't have permission to reopen it"));

        // Only allow reopen if CLOSED
        if (job.getStatus() != JobStatus.CLOSED) {
            throw new IllegalStateException("Can only reopen CLOSED jobs");
        }

        // Check Grace Period (5 minutes from CLOSING time)
        // STRICT RULE: If job was EDITED after closing (updatedAt > closedAt), Grace
        // Period is VOIDED.
        boolean isFreeReopen = false;
        if (job.getClosedAt() != null) {
            LocalDateTime now = LocalDateTime.now();
            long secondsSinceClose = ChronoUnit.SECONDS.between(job.getClosedAt(), now);
            log.info("Grace Period Check - JobID: {}, ClosedAt: {}, Now: {}, SecondsDiff: {}",
                    jobId, job.getClosedAt(), now, secondsSinceClose);

            // Check if edited after closing (allow 1 second buffer for execution time diff)
            boolean wasEditedAfterClose = false;
            if (job.getUpdatedAt().isAfter(job.getClosedAt().plusSeconds(1))) {
                wasEditedAfterClose = true;
                log.info("Job ID {} was edited after closing. Grace period voided. UpdatedAt: {}", jobId,
                        job.getUpdatedAt());
            }

            // Grace period: 5 minutes (300 seconds)
            if (secondsSinceClose >= 0 && secondsSinceClose <= 300 && !wasEditedAfterClose) {
                isFreeReopen = true;
                log.info("DECISION: FREE REOPEN (Within 300s grace period and no edits)");
            } else {
                log.info("DECISION: PAID REOPEN (Time > 300s or Edited). Seconds: {}, Edited: {}", secondsSinceClose,
                        wasEditedAfterClose);
            }
        } else {
            // Fallback for legacy jobs without closedAt (treat as expired grace period)
            log.info("Job ID {} has no closedAt timestamp. Treating as paid reopen.", jobId);
        }

        // Deduct Reopen Fee (20,000 VND) if not free
        if (!isFreeReopen) {
            try {
                BigDecimal fee = new BigDecimal("20000");
                log.info("Attempting to deduct fee: {} for User: {}", fee, userId);

                walletService.deductCash(
                        userId,
                        fee,
                        "Phí mở lại tin tuyển dụng: " + job.getTitle(),
                        "JOB_REOPEN",
                        String.valueOf(job.getId()));

                log.info("SUCCESS: Deducted 20,000 VND for reopening job ID: {}", job.getId());
            } catch (Exception e) {
                log.error("Failed to deduct job reopen fee", e);
                throw new IllegalStateException(
                        "Số dư ví không đủ 20.000 VNĐ để mở lại tin tuyển dụng. Vui lòng nạp thêm tiền.");
            }
        }

        // Handle applications (delete or keep)
        if (Boolean.TRUE.equals(request.getClearApplications())) {
            jobApplicationRepository.deleteByJobPostingId(jobId);
            log.info("Deleted all applications for job ID: {}", jobId);
            job.setApplicantCount(0);
        } else {
            log.info("Keeping existing applications for job ID: {}", jobId);
        }

        // Set status to OPEN and clear closedAt
        job.setStatus(JobStatus.OPEN);
        job.setClosedAt(null);

        // Handle Deadline
        if (request.getDeadline() != null) {
            // Validate deadline (Max 90 days)
            if (request.getDeadline().isAfter(LocalDate.now().plusDays(90))) {
                throw new IllegalArgumentException("Deadline cannot be more than 90 days from today");
            }
            if (request.getDeadline().isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Deadline must be in the future");
            }
            job.setDeadline(request.getDeadline());
        } else {
            // Default behavior: Auto-extend by 30 days if expired
            if (job.getDeadline().isBefore(LocalDate.now())) {
                job.setDeadline(LocalDate.now().plusDays(30));
                log.info("Deadline expired, auto-extended by 30 days for job ID: {}", jobId);
            }
        }

        JobPosting reopenedJob = jobPostingRepository.save(job);
        log.info("Job reopened successfully: {}", jobId);

        return mapToResponse(reopenedJob);
    }

    // ==================== HELPER METHODS ====================

    private String convertSkillsToJson(List<String> skills) {
        try {
            return objectMapper.writeValueAsString(skills);
        } catch (JsonProcessingException e) {
            log.error("Error converting skills to JSON", e);
            throw new RuntimeException("Failed to process skills", e);
        }
    }

    private List<String> convertJsonToSkills(String json) {
        try {
            return Arrays.asList(objectMapper.readValue(json, String[].class));
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to skills", e);
            return List.of();
        }
    }

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
                .recruiterCompanyName(job.getRecruiterProfile().getCompanyName())
                .recruiterEmail(job.getRecruiterProfile().getUser().getEmail())
                .recruiterUserId(job.getRecruiterProfile().getUser().getId())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
