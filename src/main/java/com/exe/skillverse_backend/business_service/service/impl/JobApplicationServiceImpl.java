package com.exe.skillverse_backend.business_service.service.impl;

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
import com.exe.skillverse_backend.business_service.service.JobApplicationService;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.premium_service.dto.response.UsageCheckResult;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
public class JobApplicationServiceImpl implements JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobPostingRepository jobPostingRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final UsageLimitService usageLimitService;
    private final PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;
    private final UserProfileRepository userProfileRepository;

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

        // Check if user has a portfolio before applying
        boolean hasPortfolio = portfolioExtendedProfileRepository.existsByUserId(userId);
        if (!hasPortfolio) {
            throw new IllegalStateException("You must create a portfolio before applying to jobs. Please create your portfolio first.");
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
    public Page<JobApplicationResponse> getJobApplicants(Long userId, Long jobId, Pageable pageable) {
        log.info("Fetching applicants for job ID: {} by user ID: {}", jobId, userId);

        // Validate ownership
        JobPosting job = jobPostingRepository.findByIdAndRecruiterProfileUserId(jobId, userId)
                .orElseThrow(
                        () -> new NotFoundException("Job not found or you don't have permission to view applicants"));

        // OPTIMIZED: Uses JOIN FETCH to prevent N+1 queries when loading applicant user
        // details
        Page<JobApplication> applications = jobApplicationRepository
                .findByJobPostingIdWithUserOrderByAppliedAtDesc(jobId, pageable);
        return applications.map(this::mapToResponse);
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
        // Get recruiter contact email from the profile associated with the job
        String contactEmail = application.getJobPosting().getRecruiterProfile().getUser().getEmail();

        try {
            if (status == JobApplicationStatus.REVIEWED) {
                emailService.sendJobApplicationReviewed(userEmail, userFullName, jobTitle);
                log.info("Sent REVIEWED email to {}", userEmail);
            } else if (status == JobApplicationStatus.ACCEPTED) {
                emailService.sendJobApplicationAccepted(userEmail, userFullName, jobTitle,
                        request.getAcceptanceMessage(), contactEmail);
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

    @Override
    @Transactional(readOnly = true)
    public JobApplicationResponse getApplicationById(Long userId, Long applicationId) {
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found"));

        // Check if user is the applicant OR the recruiter
        boolean isApplicant = application.getUser().getId().equals(userId);
        boolean isRecruiter = application.getJobPosting().getRecruiterProfile().getUser().getId().equals(userId);

        if (!isApplicant && !isRecruiter) {
            throw new RuntimeException("Unauthorized access to application");
        }

        return mapToResponse(application);
    }

    private String getUserFullName(User user) {
        String firstName = user.getFirstName() != null ? user.getFirstName() : "";
        String lastName = user.getLastName() != null ? user.getLastName() : "";
        return (firstName + " " + lastName).trim();
    }

    private String getDisplayName(User user) {
        String fullName = getUserFullName(user);
        if (!fullName.isBlank()) {
            return fullName;
        }

        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            String[] emailParts = user.getEmail().split("@", 2);
            if (emailParts.length > 0 && !emailParts[0].isBlank()) {
                return emailParts[0];
            }
        }

        return "Ứng viên SkillVerse";
    }

    private String resolveUserAvatar(User user, PortfolioExtendedProfile portfolioProfile) {
        if (portfolioProfile != null && portfolioProfile.getAvatarUrl() != null && !portfolioProfile.getAvatarUrl().isBlank()) {
            return portfolioProfile.getAvatarUrl();
        }

        Optional<UserProfile> basicProfile = userProfileRepository.findByUserId(user.getId());
        if (basicProfile.isPresent()
                && basicProfile.get().getAvatarMedia() != null
                && basicProfile.get().getAvatarMedia().getUrl() != null
                && !basicProfile.get().getAvatarMedia().getUrl().isBlank()) {
            return basicProfile.get().getAvatarMedia().getUrl();
        }

        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()) {
            return user.getAvatarUrl();
        }

        return null;
    }

    private JobApplicationResponse mapToResponse(JobApplication application) {
        JobPosting job = application.getJobPosting();
        User user = application.getUser();

        // Check premium status
        UsageCheckResult usageCheck = usageLimitService.canUseFeature(user.getId(),
                FeatureType.PRIORITY_SUPPORT);
        boolean isHighlighted = Boolean.TRUE.equals(usageCheck.getAllowed());

        Optional<PortfolioExtendedProfile> portfolioProfile = portfolioExtendedProfileRepository.findByUserId(user.getId());
        String portfolioSlug = portfolioProfile.map(PortfolioExtendedProfile::getCustomUrlSlug).orElse(null);
        String professionalTitle = portfolioProfile.map(PortfolioExtendedProfile::getProfessionalTitle).orElse(null);

        return JobApplicationResponse.builder()
                .id(application.getId())
                .jobId(job.getId())
                .jobTitle(job.getTitle())
                .userId(user.getId())
                .userFullName(getDisplayName(user))
                .userEmail(user.getEmail())
                .userAvatar(resolveUserAvatar(user, portfolioProfile.orElse(null)))
                .userProfessionalTitle(professionalTitle)
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
                .isHighlighted(isHighlighted)
                .portfolioSlug(portfolioSlug)
                .build();
    }
}
