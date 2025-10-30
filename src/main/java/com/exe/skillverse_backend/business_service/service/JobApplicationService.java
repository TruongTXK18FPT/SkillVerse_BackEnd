package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.ApplyJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateApplicationStatusRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobApplicationResponse;
import com.exe.skillverse_backend.business_service.entity.JobApplication;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    /**
     * Apply to a job (duplicate prevention, increment applicant count)
     */
    @Transactional
    public JobApplicationResponse applyToJob(Long userId, Long jobId, ApplyJobRequest request) {
        log.info("User ID: {} applying to job ID: {}", userId, jobId);

        // Check if job exists and is OPEN
        JobPosting job = jobPostingRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Job not found with ID: " + jobId));

        if (job.getStatus() != JobStatus.OPEN) {
            throw new IllegalStateException("Can only apply to OPEN jobs");
        }

        // Check for duplicate application
        if (jobApplicationRepository.existsByJobPostingIdAndUserId(jobId, userId)) {
            throw new IllegalStateException("You have already applied to this job");
        }

        // Check if user is the recruiter who posted this job (cannot apply to own job)
        if (job.getRecruiterProfile().getUser().getId().equals(userId)) {
            throw new IllegalStateException("Recruiters cannot apply to their own job postings");
        }

        // Find user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));

        // Create application
        JobApplication application = JobApplication.builder()
                .jobPosting(job)
                .user(user)
                .coverLetter(request.getCoverLetter())
                .status(JobApplicationStatus.PENDING)
                .build();

        JobApplication savedApplication = jobApplicationRepository.save(application);

        // Increment applicant count
        job.setApplicantCount(job.getApplicantCount() + 1);
        jobPostingRepository.save(job);

        log.info("Application created successfully with ID: {}", savedApplication.getId());

        return mapToResponse(savedApplication);
    }

    /**
     * Get all applications for current user
     * OPTIMIZED: Uses JOIN FETCH to prevent N+1 queries (41 queries → 1 query for
     * 20 applications)
     */
    @Transactional(readOnly = true)
    public List<JobApplicationResponse> getMyApplications(Long userId) {
        log.info("Fetching applications for user ID: {}", userId);

        List<JobApplication> applications = jobApplicationRepository
                .findByUserIdWithJobAndRecruiterOrderByAppliedAtDesc(userId);
        return applications.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all applicants for a job (recruiter only)
     */
    @Transactional(readOnly = true)
    public List<JobApplicationResponse> getJobApplicants(Long userId, Long jobId) {
        log.info("Fetching applicants for job ID: {} by user ID: {}", jobId, userId);

        // Validate ownership
        JobPosting job = jobPostingRepository.findByIdAndRecruiterProfileUserId(jobId, userId)
                .orElseThrow(
                        () -> new NotFoundException("Job not found or you don't have permission to view applicants"));

        // OPTIMIZED: Uses JOIN FETCH to prevent N+1 queries when loading applicant user
        // details
        List<JobApplication> applications = jobApplicationRepository
                .findByJobPostingIdWithUserOrderByAppliedAtDesc(jobId);
        return applications.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update application status (REVIEWED, ACCEPTED, REJECTED) and send email
     */
    @Transactional
    public JobApplicationResponse updateApplicationStatus(Long userId, Long applicationId,
            UpdateApplicationStatusRequest request) {
        log.info("Updating application ID: {} status to {} by user ID: {}", applicationId, request.getStatus(), userId);

        // Find application
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found with ID: " + applicationId));

        // Validate ownership (recruiter owns the job)
        JobPosting job = application.getJobPosting();
        if (!job.getRecruiterProfile().getUser().getId().equals(userId)) {
            throw new IllegalStateException("You don't have permission to update this application");
        }

        // Validate required fields based on status
        JobApplicationStatus newStatus = request.getStatus();
        if (newStatus == JobApplicationStatus.ACCEPTED) {
            if (request.getAcceptanceMessage() == null || request.getAcceptanceMessage().trim().isEmpty()) {
                throw new IllegalArgumentException("Acceptance message is required when accepting an application");
            }
        }
        if (newStatus == JobApplicationStatus.REJECTED) {
            if (request.getRejectionReason() == null || request.getRejectionReason().trim().isEmpty()) {
                throw new IllegalArgumentException("Rejection reason is required when rejecting an application");
            }
        }

        // Update status
        application.setStatus(newStatus);

        // Update timestamps and messages based on status
        if (newStatus == JobApplicationStatus.REVIEWED) {
            application.setReviewedAt(LocalDateTime.now());
        } else if (newStatus == JobApplicationStatus.ACCEPTED) {
            application.setAcceptanceMessage(request.getAcceptanceMessage());
            application.setProcessedAt(LocalDateTime.now());
        } else if (newStatus == JobApplicationStatus.REJECTED) {
            application.setRejectionReason(request.getRejectionReason());
            application.setProcessedAt(LocalDateTime.now());
        }

        JobApplication updatedApplication = jobApplicationRepository.save(application);

        // Send email notification
        sendStatusEmail(application, newStatus, request);

        log.info("Application status updated to {}", newStatus);

        return mapToResponse(updatedApplication);
    }

    // ==================== HELPER METHODS ====================

    private void sendStatusEmail(JobApplication application, JobApplicationStatus status,
            UpdateApplicationStatusRequest request) {
        String userEmail = application.getUser().getEmail();
        String userFullName = getUserFullName(application.getUser());
        String jobTitle = application.getJobPosting().getTitle();

        try {
            if (status == JobApplicationStatus.REVIEWED) {
                emailService.sendJobApplicationReviewed(userEmail, userFullName, jobTitle);
                log.info("Sent REVIEWED email to {}", userEmail);
            } else if (status == JobApplicationStatus.ACCEPTED) {
                emailService.sendJobApplicationAccepted(userEmail, userFullName, jobTitle,
                        request.getAcceptanceMessage());
                log.info("Sent ACCEPTED email to {}", userEmail);
            } else if (status == JobApplicationStatus.REJECTED) {
                emailService.sendJobApplicationRejected(userEmail, userFullName, jobTitle,
                        request.getRejectionReason());
                log.info("Sent REJECTED email to {}", userEmail);
            }
        } catch (Exception e) {
            log.error("Failed to send email for application ID: {}, status: {}", application.getId(), status, e);
            // Don't throw - email failure shouldn't fail the transaction
        }
    }

    private String getUserFullName(User user) {
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }

    private JobApplicationResponse mapToResponse(JobApplication application) {
        JobPosting job = application.getJobPosting();
        return JobApplicationResponse.builder()
                .id(application.getId())
                .jobId(job.getId())
                .jobTitle(job.getTitle())
                .userId(application.getUser().getId())
                .userFullName(getUserFullName(application.getUser()))
                .userEmail(application.getUser().getEmail())
                .coverLetter(application.getCoverLetter())
                .status(application.getStatus())
                .appliedAt(application.getAppliedAt())
                .reviewedAt(application.getReviewedAt())
                .processedAt(application.getProcessedAt())
                .acceptanceMessage(application.getAcceptanceMessage())
                .rejectionReason(application.getRejectionReason())
                // Job details for user's application view
                .recruiterCompanyName(job.getRecruiterProfile().getCompanyName())
                .minBudget(job.getMinBudget())
                .maxBudget(job.getMaxBudget())
                .isRemote(job.getIsRemote())
                .location(job.getLocation())
                .build();
    }
}
