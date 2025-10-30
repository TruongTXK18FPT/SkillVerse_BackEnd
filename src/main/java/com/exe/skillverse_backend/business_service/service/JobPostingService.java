package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.CreateJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateJobRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobPostingResponse;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final JobApplicationRepository jobApplicationRepository;
    private final ObjectMapper objectMapper;

    /**
     * Create a new job posting (status = IN_PROGRESS by default)
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
                .status(JobStatus.IN_PROGRESS) // Default status
                .applicantCount(0)
                .recruiterProfile(recruiterProfile)
                .build();

        JobPosting savedJob = jobPostingRepository.save(job);
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
            job.setDeadline(request.getDeadline());
        }
        if (request.getIsRemote() != null) {
            job.setIsRemote(request.getIsRemote());
        }
        if (request.getLocation() != null) {
            job.setLocation(request.getLocation());
        }

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

        job.setStatus(newStatus);
        JobPosting updatedJob = jobPostingRepository.save(job);
        log.info("Job status changed from {} to {}", currentStatus, newStatus);

        return mapToResponse(updatedJob);
    }

    /**
     * Get all jobs for current recruiter
     */
    @Transactional(readOnly = true)
    public List<JobPostingResponse> getMyJobs(Long userId) {
        log.info("Fetching jobs for recruiter user ID: {}", userId);

        List<JobPosting> jobs = jobPostingRepository.findByRecruiterProfileUserIdOrderByCreatedAtDesc(userId);
        return jobs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all public jobs (status = OPEN)
     */
    @Transactional(readOnly = true)
    public List<JobPostingResponse> getPublicJobs() {
        log.info("Fetching public jobs (status = OPEN)");

        List<JobPosting> jobs = jobPostingRepository.findByStatusOrderByCreatedAtDesc(JobStatus.OPEN);
        return jobs.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get job details by ID
     */
    @Transactional(readOnly = true)
    public JobPostingResponse getJobDetails(Long jobId) {
        log.info("Fetching job details for ID: {}", jobId);

        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found with ID: " + jobId));

        return mapToResponse(job);
    }

    /**
     * Delete job (only if status = IN_PROGRESS)
     */
    @Transactional
    public void deleteJob(Long userId, Long jobId) {
        log.info("Deleting job ID: {} by user ID: {}", jobId, userId);

        // Find job and validate ownership
        JobPosting job = jobPostingRepository.findByIdAndRecruiterProfileUserId(jobId, userId)
                .orElseThrow(() -> new NotFoundException("Job not found or you don't have permission to delete it"));

        // Only allow delete if IN_PROGRESS
        if (job.getStatus() != JobStatus.IN_PROGRESS) {
            throw new IllegalStateException("Can only delete jobs that are IN_PROGRESS");
        }

        jobPostingRepository.delete(job);
        log.info("Job deleted successfully: {}", jobId);
    }

    /**
     * Reopen job (hard delete all applications, set status to OPEN)
     */
    @Transactional
    public JobPostingResponse reopenJob(Long userId, Long jobId) {
        log.info("Reopening job ID: {} by user ID: {}", jobId, userId);

        // Find job and validate ownership
        JobPosting job = jobPostingRepository.findByIdAndRecruiterProfileUserId(jobId, userId)
                .orElseThrow(() -> new NotFoundException("Job not found or you don't have permission to reopen it"));

        // Only allow reopen if CLOSED
        if (job.getStatus() != JobStatus.CLOSED) {
            throw new IllegalStateException("Can only reopen CLOSED jobs");
        }

        // Hard delete all applications
        jobApplicationRepository.deleteByJobPostingId(jobId);
        log.info("Deleted all applications for job ID: {}", jobId);

        // Reset applicant count and set status to OPEN
        job.setApplicantCount(0);
        job.setStatus(JobStatus.OPEN);

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
                .recruiterCompanyName(job.getRecruiterProfile().getCompanyName())
                .recruiterEmail(job.getRecruiterProfile().getUser().getEmail())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
