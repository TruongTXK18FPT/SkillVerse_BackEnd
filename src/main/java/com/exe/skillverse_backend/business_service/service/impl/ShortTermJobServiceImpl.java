package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.ApplyShortTermJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.CreateShortTermJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.RequestRevisionRequest;
import com.exe.skillverse_backend.business_service.dto.request.SubmitDeliverableRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateShortTermApplicationStatusRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateShortTermJobRequest;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermApplicationResponse;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermJobResponse;
import com.exe.skillverse_backend.business_service.entity.JobDeliverable;
import com.exe.skillverse_backend.business_service.entity.JobStatusAuditLog;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.RevisionNote;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobMilestone;
import com.exe.skillverse_backend.business_service.entity.enums.JobUrgency;
import com.exe.skillverse_backend.business_service.entity.enums.PaymentMethod;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.JobDeliverableRepository;
import com.exe.skillverse_backend.business_service.repository.JobReviewRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.repository.RevisionNoteRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobMilestoneRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.JobAuditService;
import com.exe.skillverse_backend.business_service.service.ShortTermJobService;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.premium_service.service.RecruiterSubscriptionService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
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
@Transactional
public class ShortTermJobServiceImpl implements ShortTermJobService {

    private final ShortTermJobRepository shortTermJobRepository;
    private final ShortTermJobApplicationRepository applicationRepository;
    private final ShortTermJobMilestoneRepository milestoneRepository;
    private final JobDeliverableRepository deliverableRepository;
    private final RevisionNoteRepository revisionNoteRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final UserRepository userRepository;
    private final JobReviewRepository reviewRepository;
    private final PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;
    private final UserProfileRepository userProfileRepository;
    private final JobAuditService auditService;
    private final ObjectMapper objectMapper;
    private final RecruiterSubscriptionService recruiterSubscriptionService;
    private final WalletService walletService;

    private static final BigDecimal SHORT_TERM_JOB_POSTING_FEE = new BigDecimal("30000");

    // ==================== STATUS TRANSITION RULES ====================

    private static final List<ShortTermJobStatus> DRAFT_TRANSITIONS = Arrays.asList(
            ShortTermJobStatus.PENDING_APPROVAL, ShortTermJobStatus.PUBLISHED, ShortTermJobStatus.CANCELLED
    );
    private static final List<ShortTermJobStatus> PENDING_APPROVAL_TRANSITIONS = Arrays.asList(
            ShortTermJobStatus.PUBLISHED, ShortTermJobStatus.REJECTED, ShortTermJobStatus.CANCELLED
    );
    private static final List<ShortTermJobStatus> PUBLISHED_TRANSITIONS = Arrays.asList(
            ShortTermJobStatus.APPLIED, ShortTermJobStatus.CANCELLED
    );
    private static final List<ShortTermJobStatus> APPLIED_TRANSITIONS = Arrays.asList(
            ShortTermJobStatus.IN_PROGRESS, ShortTermJobStatus.CANCELLED
    );
    private static final List<ShortTermJobStatus> IN_PROGRESS_TRANSITIONS = Arrays.asList(
            ShortTermJobStatus.SUBMITTED, ShortTermJobStatus.CANCELLED, ShortTermJobStatus.DISPUTED
    );
    private static final List<ShortTermJobStatus> SUBMITTED_TRANSITIONS = List.of(
            ShortTermJobStatus.UNDER_REVIEW
    );
    private static final List<ShortTermJobStatus> UNDER_REVIEW_TRANSITIONS = Arrays.asList(
            ShortTermJobStatus.APPROVED, ShortTermJobStatus.REJECTED
    );
    private static final List<ShortTermJobStatus> APPROVED_TRANSITIONS = List.of(
            ShortTermJobStatus.COMPLETED
    );
    private static final List<ShortTermJobStatus> REJECTED_TRANSITIONS = List.of(
            ShortTermJobStatus.IN_PROGRESS
    );
    private static final List<ShortTermJobStatus> COMPLETED_TRANSITIONS = List.of(
            ShortTermJobStatus.PAID
    );

    // ==================== JOB POSTING (RECRUITER) ====================

    @Override
    public ShortTermJobResponse createJob(Long userId, CreateShortTermJobRequest request) {
        log.info("Creating short-term job for user ID: {}", userId);

        RecruiterProfile recruiterProfile = getRecruiterProfile(userId);
        validateCreateJobRequest(request);

        ShortTermJob job = ShortTermJob.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .requiredSkills(toJson(request.getRequiredSkills()))
                .budget(request.getBudget())
                .isNegotiable(request.getIsNegotiable() != null ? request.getIsNegotiable() : false)
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : PaymentMethod.FIXED)
                .deadline(request.getDeadline())
                .estimatedDuration(request.getEstimatedDuration())
                .urgency(request.getUrgency() != null ? request.getUrgency() : JobUrgency.NORMAL)
                .startTime(request.getStartTime())
                .isRemote(request.getIsRemote())
                .location(request.getLocation())
                .maxApplicants(request.getMaxApplicants())
                .minRating(request.getMinRating())
                .recruiterProfile(recruiterProfile)
                .status(ShortTermJobStatus.DRAFT)
                .applicantCount(0)
                .build();

        job = shortTermJobRepository.save(job);

        // Create milestones if provided
        if (request.getMilestones() != null && !request.getMilestones().isEmpty()) {
            for (CreateShortTermJobRequest.CreateMilestoneRequest milestoneReq : request.getMilestones()) {
                ShortTermJobMilestone milestone = ShortTermJobMilestone.builder()
                        .shortTermJob(job)
                        .title(milestoneReq.getTitle())
                        .description(milestoneReq.getDescription())
                        .amount(milestoneReq.getAmount())
                        .deadline(milestoneReq.getDeadline())
                        .orderIndex(milestoneReq.getOrder())
                        .status(ShortTermJobMilestone.MilestoneStatus.PENDING)
                        .build();
                milestoneRepository.save(milestone);
            }
        }

        log.info("Short-term job created with ID: {}", job.getId());
        return mapToResponse(job);
    }

    @Override
    public ShortTermJobResponse updateJob(Long userId, Long jobId, UpdateShortTermJobRequest request) {
        log.info("Updating short-term job ID: {} by user ID: {}", jobId, userId);

        ShortTermJob job = getJobById(jobId);
        validateJobOwnership(job, userId);
        validateJobCanBeUpdated(job);

        if (request.getTitle() != null) job.setTitle(request.getTitle());
        if (request.getDescription() != null) job.setDescription(request.getDescription());
        if (request.getRequiredSkills() != null) job.setRequiredSkills(toJson(request.getRequiredSkills()));
        if (request.getBudget() != null) job.setBudget(request.getBudget());
        if (request.getIsNegotiable() != null) job.setIsNegotiable(request.getIsNegotiable());
        if (request.getPaymentMethod() != null) job.setPaymentMethod(request.getPaymentMethod());
        if (request.getDeadline() != null) {
            validateDeadline(request.getDeadline());
            job.setDeadline(request.getDeadline());
        }
        if (request.getEstimatedDuration() != null) job.setEstimatedDuration(request.getEstimatedDuration());
        if (request.getUrgency() != null) job.setUrgency(request.getUrgency());
        if (request.getStartTime() != null) job.setStartTime(request.getStartTime());
        if (request.getIsRemote() != null) job.setIsRemote(request.getIsRemote());
        if (request.getLocation() != null) job.setLocation(request.getLocation());
        if (request.getMaxApplicants() != null) job.setMaxApplicants(request.getMaxApplicants());
        if (request.getMinRating() != null) job.setMinRating(request.getMinRating());

        job = shortTermJobRepository.save(job);
        log.info("Short-term job updated: {}", jobId);
        return mapToResponse(job);
    }

    @Override
    public ShortTermJobResponse changeJobStatus(Long userId, Long jobId, ShortTermJobStatus newStatus, String reason) {
        log.info("Changing status of job ID: {} to {} by user ID: {}", jobId, newStatus, userId);

        ShortTermJob job = getJobById(jobId);
        validateJobOwnership(job, userId);
        validateStatusTransition(job.getStatus(), newStatus);

        ShortTermJobStatus previousStatus = job.getStatus();

        // Payment logic when submitting for approval
        if (previousStatus == ShortTermJobStatus.DRAFT && newStatus == ShortTermJobStatus.PENDING_APPROVAL) {
            boolean usedSubscription = recruiterSubscriptionService.tryUseShortTermJobQuota(userId);
            if (!usedSubscription) {
                // No active subscription — charge 30k from wallet
                walletService.deductCash(
                        userId,
                        SHORT_TERM_JOB_POSTING_FEE,
                        "Phí đăng tin tuyển dụng ngắn hạn",
                        "JOB_POSTING",
                        String.valueOf(jobId)
                );
            }
            job.setPaidViaSubscription(usedSubscription);
            job.setIsHighlighted(usedSubscription && recruiterSubscriptionService.canHighlightJob(userId));
        }

        job.setStatus(newStatus);

        if (newStatus == ShortTermJobStatus.PUBLISHED) {
            job.setPublishedAt(LocalDateTime.now());
        } else if (newStatus == ShortTermJobStatus.COMPLETED) {
            job.setCompletedAt(LocalDateTime.now());
        } else if (newStatus == ShortTermJobStatus.PAID) {
            job.setPaidAt(LocalDateTime.now());
        }

        job = shortTermJobRepository.save(job);

        // Log audit
        auditService.logShortTermJobStatusChange(
                jobId, previousStatus, newStatus, userId,
                JobStatusAuditLog.AuditRole.RECRUITER, reason
        );

        log.info("Job status changed from {} to {}", previousStatus, newStatus);
        return mapToResponse(job);
    }

    @Override
    public void deleteJob(Long userId, Long jobId) {
        log.info("Deleting job ID: {} by user ID: {}", jobId, userId);

        ShortTermJob job = getJobById(jobId);
        validateJobOwnership(job, userId);

        // Allow deletion for inactive statuses: DRAFT, REJECTED, CANCELLED
        // These are statuses where no active application/progress is happening
        ShortTermJobStatus status = job.getStatus();
        boolean canDelete = status == ShortTermJobStatus.DRAFT
                || status == ShortTermJobStatus.REJECTED
                || status == ShortTermJobStatus.CANCELLED;

        if (!canDelete) {
            throw new BadRequestException(
                    "Không thể xóa công việc đang trong trạng thái '" + status + "'. " +
                    "Chỉ có thể xóa công việc ở trạng thái nháp, bị từ chối hoặc đã hủy.");
        }

        shortTermJobRepository.delete(job);
        log.info("Job deleted: {}", jobId);
    }

    @Override
    public List<ShortTermJobResponse> getMyJobs(Long userId) {
        RecruiterProfile profile = getRecruiterProfile(userId);
        return shortTermJobRepository.findByRecruiterProfileUserIdOrderByCreatedAtDesc(profile.getUserId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ShortTermJobResponse> getMyJobsPaged(Long userId, Pageable pageable) {
        RecruiterProfile profile = getRecruiterProfile(userId);
        return shortTermJobRepository.findByRecruiterProfileUserId(profile.getUserId(), pageable)
                .map(this::mapToResponse);
    }

    // ==================== JOB BROWSING (PUBLIC) ====================

    @Override
    @Transactional(readOnly = true)
    public List<ShortTermJobResponse> getPublishedJobs() {
        return shortTermJobRepository.findPublishedJobs(LocalDateTime.now())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShortTermJobResponse> getPublishedJobsPaged(Pageable pageable) {
        return shortTermJobRepository.findPublishedJobs(LocalDateTime.now(), pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShortTermJobResponse> searchJobs(
            String search, BigDecimal minBudget, BigDecimal maxBudget,
            Boolean isRemote, String urgency, Pageable pageable) {
        return shortTermJobRepository.searchJobs(
                LocalDateTime.now(), search, minBudget, maxBudget, isRemote, urgency, pageable
        ).map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public ShortTermJobResponse getJobDetails(Long jobId) {
        return mapToResponse(getJobById(jobId));
    }

    // ==================== JOB APPLICATION (CANDIDATE) ====================

    @Override
    public ShortTermApplicationResponse applyToJob(Long userId, Long jobId, ApplyShortTermJobRequest request) {
        log.info("User ID: {} applying to job ID: {}", userId, jobId);

        User user = getUserById(userId);
        ShortTermJob job = getJobById(jobId);

        // Validate application
        validateCanApply(job, user);

        ShortTermJobApplication application = ShortTermJobApplication.builder()
                .shortTermJob(job)
                .user(user)
                .coverLetter(request.getCoverLetter())
                .proposedPrice(request.getProposedPrice())
                .proposedDuration(request.getProposedDuration())
                .portfolio(request.getPortfolio() != null ? toJson(request.getPortfolio()) : null)
                .status(ShortTermApplicationStatus.PENDING)
                .revisionCount(0)
                .build();

        application = applicationRepository.save(application);

        // Update job status and applicant count
        job.setApplicantCount(job.getApplicantCount() + 1);
        if (job.getStatus() == ShortTermJobStatus.PUBLISHED) {
            job.setStatus(ShortTermJobStatus.APPLIED);
        }
        shortTermJobRepository.save(job);

        log.info("Application created with ID: {}", application.getId());
        return mapToApplicationResponse(application);
    }

    @Override
    public void withdrawApplication(Long userId, Long applicationId) {
        log.info("User ID: {} withdrawing application ID: {}", userId, applicationId);

        ShortTermJobApplication application = getApplicationById(applicationId);
        validateApplicationOwnership(application, userId);

        if (application.getStatus() != ShortTermApplicationStatus.PENDING) {
            throw new BadRequestException("Can only withdraw PENDING applications");
        }

        ShortTermApplicationStatus previousStatus = application.getStatus();
        application.setStatus(ShortTermApplicationStatus.WITHDRAWN);
        applicationRepository.save(application);

        // Update job applicant count
        ShortTermJob job = application.getShortTermJob();
        job.setApplicantCount(Math.max(0, job.getApplicantCount() - 1));
        shortTermJobRepository.save(job);

        auditService.logApplicationStatusChange(
                applicationId, previousStatus, ShortTermApplicationStatus.WITHDRAWN,
                userId, JobStatusAuditLog.AuditRole.CANDIDATE, "Candidate withdrew application"
        );

        log.info("Application withdrawn: {}", applicationId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShortTermApplicationResponse> getMyApplications(Long userId) {
        return applicationRepository.findByUserIdOrderByAppliedAtDesc(userId)
                .stream()
                .map(this::mapToApplicationResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShortTermApplicationResponse> getMyApplicationsPaged(Long userId, Pageable pageable) {
        return applicationRepository.findByUserId(userId, pageable)
                .map(this::mapToApplicationResponse);
    }

    // ==================== APPLICATION MANAGEMENT (RECRUITER) ====================

    @Override
    @Transactional(readOnly = true)
    public Page<ShortTermApplicationResponse> getJobApplicants(Long userId, Long jobId, Pageable pageable) {
        ShortTermJob job = getJobById(jobId);
        validateJobOwnership(job, userId);
        return applicationRepository.findByShortTermJobId(jobId, pageable)
                .map(this::mapToApplicationResponse);
    }

    @Override
    public ShortTermApplicationResponse updateApplicationStatus(
            Long userId, Long applicationId, UpdateShortTermApplicationStatusRequest request) {
        log.info("Updating application ID: {} status to {} by user ID: {}",
                applicationId, request.getStatus(), userId);

        ShortTermJobApplication application = getApplicationById(applicationId);
        ShortTermJob job = application.getShortTermJob();
        validateJobOwnership(job, userId);

        if (request.getStatus() == ShortTermApplicationStatus.ACCEPTED
                && application.getStatus() == ShortTermApplicationStatus.PENDING) {
            return selectCandidate(userId, job.getId(), applicationId);
        }

        validateApplicationStatusTransition(application.getStatus(), request.getStatus());

        ShortTermApplicationStatus previousStatus = application.getStatus();
        application.setStatus(request.getStatus());

        // Update timestamps based on status
        switch (request.getStatus()) {
            case ACCEPTED:
                application.setAcceptedAt(LocalDateTime.now());
                break;
            case WORKING:
                application.setStartedAt(LocalDateTime.now());
                break;
            case SUBMITTED:
                application.setSubmittedAt(LocalDateTime.now());
                break;
            case COMPLETED:
                application.setCompletedAt(LocalDateTime.now());
                break;
            case PENDING:
            case REJECTED:
            case CANCELLED:
            case WITHDRAWN:
            case REVISION_REQUIRED:
            case APPROVED:
            default:
                // No special timestamp handling for these statuses
                break;
        }

        application = applicationRepository.save(application);

        auditService.logApplicationStatusChange(
                applicationId, previousStatus, request.getStatus(),
                userId, JobStatusAuditLog.AuditRole.RECRUITER, request.getReason()
        );

        return mapToApplicationResponse(application);
    }

    @Override
    public ShortTermApplicationResponse selectCandidate(Long userId, Long jobId, Long applicationId) {
        log.info("Selecting candidate for job ID: {} - application ID: {}", jobId, applicationId);

        ShortTermJob job = getJobById(jobId);
        validateJobOwnership(job, userId);

        if (job.getSelectedApplicantId() != null) {
            throw new BadRequestException("A candidate has already been selected for this job");
        }

        ShortTermJobApplication application = getApplicationById(applicationId);
        if (!application.getShortTermJob().getId().equals(jobId)) {
            throw new BadRequestException("Application does not belong to this job");
        }

        // Accept this application
        ShortTermApplicationStatus previousStatus = application.getStatus();
        application.setStatus(ShortTermApplicationStatus.ACCEPTED);
        application.setAcceptedAt(LocalDateTime.now());
        applicationRepository.save(application);

        // Reject other pending applications
        List<ShortTermJobApplication> otherApplications = applicationRepository
                .findByShortTermJobIdAndStatus(jobId, ShortTermApplicationStatus.PENDING);
        for (ShortTermJobApplication other : otherApplications) {
            if (!other.getId().equals(applicationId)) {
                other.setStatus(ShortTermApplicationStatus.REJECTED);
                applicationRepository.save(other);
            }
        }

        // Update job
        job.setSelectedApplicantId(application.getUser().getId());
        job.setStatus(ShortTermJobStatus.IN_PROGRESS);
        shortTermJobRepository.save(job);

        auditService.logApplicationStatusChange(
                applicationId, previousStatus, ShortTermApplicationStatus.ACCEPTED,
                userId, JobStatusAuditLog.AuditRole.RECRUITER, "Candidate selected"
        );

        return mapToApplicationResponse(application);
    }

    // ==================== WORK SUBMISSION (CANDIDATE) ====================

    @Override
    public ShortTermApplicationResponse submitDeliverables(Long userId, SubmitDeliverableRequest request) {
        log.info("Submitting deliverables for application ID: {} by user ID: {}",
                request.getApplicationId(), userId);

        ShortTermJobApplication application = getApplicationById(request.getApplicationId());
        validateApplicationOwnership(application, userId);

        if (application.getStatus() != ShortTermApplicationStatus.WORKING &&
                application.getStatus() != ShortTermApplicationStatus.REVISION_REQUIRED &&
                application.getStatus() != ShortTermApplicationStatus.ACCEPTED) {
            throw new BadRequestException("Can only submit deliverables when ACCEPTED, WORKING or REVISION_REQUIRED");
        }

        // Validate milestones if payment method is MILESTONE
        ShortTermJob job = application.getShortTermJob();
        if (job.getPaymentMethod() == PaymentMethod.MILESTONE && request.getMilestoneId() == null) {
            validateAllMilestonesCompleted(job);
        }

        // Save deliverables
        if (request.getDeliverables() != null) {
            User user = getUserById(userId);
            for (SubmitDeliverableRequest.DeliverableItem item : request.getDeliverables()) {
                JobDeliverable deliverable = JobDeliverable.builder()
                        .application(application)
                        .type(item.getType())
                        .fileName(item.getFileName())
                        .fileUrl(item.getFileUrl())
                        .fileSize(item.getFileSize())
                        .mimeType(item.getMimeType())
                        .description(item.getDescription())
                        .uploadedBy(user)
                        .build();

                if (request.getMilestoneId() != null) {
                    ShortTermJobMilestone milestone = milestoneRepository.findById(request.getMilestoneId())
                            .orElseThrow(() -> new NotFoundException("Milestone not found"));
                    deliverable.setMilestone(milestone);
                }

                deliverableRepository.save(deliverable);
            }
        }

        application.setWorkNote(request.getWorkNote());
        application.setSubmittedAt(LocalDateTime.now());
        if (application.getStartedAt() == null) {
            application.setStartedAt(LocalDateTime.now());
        }

        ShortTermApplicationStatus previousStatus = application.getStatus();
        application.setStatus(ShortTermApplicationStatus.SUBMITTED);
        application = applicationRepository.save(application);

        // Update job status
        job.setStatus(ShortTermJobStatus.SUBMITTED);
        shortTermJobRepository.save(job);

        auditService.logApplicationStatusChange(
                request.getApplicationId(), previousStatus, ShortTermApplicationStatus.SUBMITTED,
                userId, JobStatusAuditLog.AuditRole.CANDIDATE, "Deliverables submitted"
        );

        return mapToApplicationResponse(application);
    }

    // ==================== WORK REVIEW (RECRUITER) ====================

    @Override
    public ShortTermApplicationResponse approveWork(Long userId, Long applicationId, String message) {
        log.info("Approving work for application ID: {} by user ID: {}", applicationId, userId);

        ShortTermJobApplication application = getApplicationById(applicationId);
        ShortTermJob job = application.getShortTermJob();
        validateJobOwnership(job, userId);

        if (application.getStatus() != ShortTermApplicationStatus.SUBMITTED) {
            throw new BadRequestException("Can only approve SUBMITTED work");
        }

        ShortTermApplicationStatus previousStatus = application.getStatus();
        application.setStatus(ShortTermApplicationStatus.APPROVED);
        application = applicationRepository.save(application);

        // Update job status
        job.setStatus(ShortTermJobStatus.APPROVED);
        shortTermJobRepository.save(job);

        auditService.logApplicationStatusChange(
                applicationId, previousStatus, ShortTermApplicationStatus.APPROVED,
                userId, JobStatusAuditLog.AuditRole.RECRUITER, message
        );

        return mapToApplicationResponse(application);
    }

    @Override
    public ShortTermApplicationResponse requestRevision(Long userId, RequestRevisionRequest request) {
        log.info("Requesting revision for application ID: {} by user ID: {}",
                request.getApplicationId(), userId);

        ShortTermJobApplication application = getApplicationById(request.getApplicationId());
        ShortTermJob job = application.getShortTermJob();
        validateJobOwnership(job, userId);

        if (application.getStatus() != ShortTermApplicationStatus.SUBMITTED) {
            throw new BadRequestException("Can only request revision for SUBMITTED work");
        }

        // Create revision note
        User recruiter = getUserById(userId);
        RevisionNote revisionNote = RevisionNote.builder()
                .application(application)
                .note(request.getNote())
                .specificIssues(request.getSpecificIssues() != null ? toJson(request.getSpecificIssues()) : null)
                .requestedBy(recruiter)
                .build();
        revisionNoteRepository.save(revisionNote);

        ShortTermApplicationStatus previousStatus = application.getStatus();
        application.setStatus(ShortTermApplicationStatus.REVISION_REQUIRED);
        application.setRevisionCount(application.getRevisionCount() + 1);
        application = applicationRepository.save(application);

        // Keep job status as IN_PROGRESS so candidate can continue working after revision
        // Do NOT change job status to REJECTED - that would prevent candidate from submitting
        log.info("Revision requested for application ID: {}, job ID: {} remains IN_PROGRESS for candidate to fix",
                request.getApplicationId(), job.getId());

        auditService.logApplicationStatusChange(
                request.getApplicationId(), previousStatus, ShortTermApplicationStatus.REVISION_REQUIRED,
                userId, JobStatusAuditLog.AuditRole.RECRUITER, request.getNote()
        );

        return mapToApplicationResponse(application);
    }

    // ==================== COMPLETION ====================

    @Override
    public ShortTermJobResponse completeJob(Long userId, Long jobId) {
        log.info("Completing job ID: {} by user ID: {}", jobId, userId);

        ShortTermJob job = getJobById(jobId);
        validateJobOwnership(job, userId);

        // Auto-advance job status if application is already APPROVED
        if (job.getStatus() == ShortTermJobStatus.SUBMITTED) {
            Optional<ShortTermJobApplication> approvedApp = applicationRepository.findWorkingApplicationByJobId(jobId);
            if (approvedApp.isPresent() && approvedApp.get().getStatus() == ShortTermApplicationStatus.APPROVED) {
                job.setStatus(ShortTermJobStatus.APPROVED);
                log.info("Auto-advancing job {} to APPROVED before completion", jobId);
            }
        }

        if (job.getStatus() != ShortTermJobStatus.APPROVED) {
            throw new BadRequestException("Chỉ có thể hoàn tất khi bàn giao đã được duyệt. Vui lòng duyệt bàn giao trước.");
        }

        ShortTermJobStatus previousStatus = job.getStatus();
        job.setStatus(ShortTermJobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        job = shortTermJobRepository.save(job);

        // Update application status
        applicationRepository.findWorkingApplicationByJobId(jobId)
                .ifPresent(app -> {
                    app.setStatus(ShortTermApplicationStatus.COMPLETED);
                    app.setCompletedAt(LocalDateTime.now());
                    applicationRepository.save(app);
                });

        auditService.logShortTermJobStatusChange(
                jobId, previousStatus, ShortTermJobStatus.COMPLETED,
                userId, JobStatusAuditLog.AuditRole.RECRUITER, "Job completed"
        );

        return mapToResponse(job);
    }

    @Override
    public ShortTermJobResponse markAsPaid(Long userId, Long jobId) {
        log.info("Marking job ID: {} as paid by user ID: {}", jobId, userId);

        ShortTermJob job = getJobById(jobId);
        validateJobOwnership(job, userId);

        if (job.getStatus() != ShortTermJobStatus.COMPLETED) {
            throw new BadRequestException("Can only mark COMPLETED jobs as paid");
        }

        ShortTermJobStatus previousStatus = job.getStatus();
        job.setStatus(ShortTermJobStatus.PAID);
        job.setPaidAt(LocalDateTime.now());
        job = shortTermJobRepository.save(job);

        auditService.logShortTermJobStatusChange(
                jobId, previousStatus, ShortTermJobStatus.PAID,
                userId, JobStatusAuditLog.AuditRole.RECRUITER, "Payment completed"
        );

        return mapToResponse(job);
    }

    // ==================== VALIDATION HELPERS ====================

    private void validateCreateJobRequest(CreateShortTermJobRequest request) {
        validateDeadline(request.getDeadline());

        if (!request.getIsRemote() && (request.getLocation() == null || request.getLocation().isBlank())) {
            throw new BadRequestException("Location is required for non-remote jobs");
        }

        // Validate milestones total equals budget if payment method is MILESTONE
        if (request.getPaymentMethod() == PaymentMethod.MILESTONE && request.getMilestones() != null) {
            BigDecimal milestonesTotal = request.getMilestones().stream()
                    .map(CreateShortTermJobRequest.CreateMilestoneRequest::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (milestonesTotal.compareTo(request.getBudget()) != 0) {
                throw new BadRequestException("Milestones total must equal job budget");
            }
        }
    }

    private void validateDeadline(LocalDateTime deadline) {
        if (deadline != null && deadline.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Deadline must be in the future");
        }
    }

    private void validateJobCanBeUpdated(ShortTermJob job) {
        if (job.getStatus() != ShortTermJobStatus.DRAFT && job.getStatus() != ShortTermJobStatus.PUBLISHED
                && job.getStatus() != ShortTermJobStatus.PENDING_APPROVAL) {
            throw new BadRequestException("Can only update jobs in DRAFT, PENDING_APPROVAL or PUBLISHED status");
        }
    }

    private void validateStatusTransition(ShortTermJobStatus current, ShortTermJobStatus target) {
        List<ShortTermJobStatus> allowed;
        switch (current) {
            case DRAFT: allowed = DRAFT_TRANSITIONS; break;
            case PENDING_APPROVAL: allowed = PENDING_APPROVAL_TRANSITIONS; break;
            case PUBLISHED: allowed = PUBLISHED_TRANSITIONS; break;
            case APPLIED: allowed = APPLIED_TRANSITIONS; break;
            case IN_PROGRESS: allowed = IN_PROGRESS_TRANSITIONS; break;
            case SUBMITTED: allowed = SUBMITTED_TRANSITIONS; break;
            case UNDER_REVIEW: allowed = UNDER_REVIEW_TRANSITIONS; break;
            case APPROVED: allowed = APPROVED_TRANSITIONS; break;
            case REJECTED: allowed = REJECTED_TRANSITIONS; break;
            case COMPLETED: allowed = COMPLETED_TRANSITIONS; break;
            default: allowed = List.of();
        }

        if (!allowed.contains(target)) {
            throw new BadRequestException(
                    String.format("Cannot transition from %s to %s", current, target)
            );
        }
    }

    private void validateApplicationStatusTransition(ShortTermApplicationStatus current, ShortTermApplicationStatus target) {
        // Basic transition validation
        boolean valid = switch (current) {
            case PENDING -> target == ShortTermApplicationStatus.ACCEPTED ||
                    target == ShortTermApplicationStatus.REJECTED ||
                    target == ShortTermApplicationStatus.WITHDRAWN;
            case ACCEPTED -> target == ShortTermApplicationStatus.WORKING;
            case WORKING -> target == ShortTermApplicationStatus.SUBMITTED ||
                    target == ShortTermApplicationStatus.CANCELLED;
            case SUBMITTED -> target == ShortTermApplicationStatus.REVISION_REQUIRED ||
                    target == ShortTermApplicationStatus.APPROVED;
            case REVISION_REQUIRED -> target == ShortTermApplicationStatus.SUBMITTED;
            case APPROVED -> target == ShortTermApplicationStatus.COMPLETED;
            default -> false;
        };

        if (!valid) {
            throw new BadRequestException(
                    String.format("Cannot transition application from %s to %s", current, target)
            );
        }
    }

    private void validateCanApply(ShortTermJob job, User user) {
        // Check job status
        if (job.getStatus() != ShortTermJobStatus.PUBLISHED && job.getStatus() != ShortTermJobStatus.APPLIED) {
            throw new BadRequestException("This job is not accepting applications");
        }

        // Check if job is expired
        if (job.isExpired()) {
            throw new BadRequestException("This job has expired");
        }

        // Check if already applied
        if (applicationRepository.existsByShortTermJobIdAndUserId(job.getId(), user.getId())) {
            throw new BadRequestException("You have already applied to this job");
        }

        // Check max applicants
        if (job.getMaxApplicants() != null && job.getApplicantCount() >= job.getMaxApplicants()) {
            throw new BadRequestException("This job has reached maximum applicants");
        }

        // Check min rating requirement
        if (job.getMinRating() != null) {
            BigDecimal userRating = reviewRepository.getAverageRatingForUser(user.getId());
            if (userRating == null || userRating.compareTo(job.getMinRating()) < 0) {
                throw new BadRequestException(
                        String.format("This job requires a minimum rating of %.1f", job.getMinRating())
                );
            }
        }

        // Check if user has a portfolio before applying
        boolean hasPortfolio = portfolioExtendedProfileRepository.existsByUserId(user.getId());
        if (!hasPortfolio) {
            throw new BadRequestException("You must create a portfolio before applying to jobs. Please create your portfolio first.");
        }
    }

    private void validateAllMilestonesCompleted(ShortTermJob job) {
        long totalMilestones = milestoneRepository.countByShortTermJobId(job.getId());
        long completedMilestones = milestoneRepository.countByShortTermJobIdAndStatus(
                job.getId(), ShortTermJobMilestone.MilestoneStatus.APPROVED
        );

        if (completedMilestones < totalMilestones) {
            throw new BadRequestException("All milestones must be completed before final submission");
        }
    }

    // ==================== ENTITY HELPERS ====================

    private ShortTermJob getJobById(Long jobId) {
        return shortTermJobRepository.findById(jobId)
                .orElseThrow(() -> new NotFoundException("Short-term job not found with ID: " + jobId));
    }

    private ShortTermJobApplication getApplicationById(Long applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found with ID: " + applicationId));
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found with ID: " + userId));
    }

    private RecruiterProfile getRecruiterProfile(Long userId) {
        return recruiterProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ForbiddenException("You must be a recruiter to perform this action"));
    }

    private void validateJobOwnership(ShortTermJob job, Long userId) {
        RecruiterProfile profile = getRecruiterProfile(userId);
        if (!job.getRecruiterProfile().getUserId().equals(profile.getUserId())) {
            throw new ForbiddenException("You do not own this job");
        }
    }

    private void validateApplicationOwnership(ShortTermJobApplication application, Long userId) {
        if (!application.getUser().getId().equals(userId)) {
            throw new ForbiddenException("You do not own this application");
        }
    }

    // ==================== MAPPING HELPERS ====================

    @SuppressWarnings("unchecked")
    private ShortTermJobResponse mapToResponse(ShortTermJob job) {
        List<String> skills = fromJson(job.getRequiredSkills(), List.class);

        ShortTermJobResponse.RecruiterInfo recruiterInfo = null;
        if (job.getRecruiterProfile() != null) {
            RecruiterProfile rp = job.getRecruiterProfile();
            recruiterInfo = ShortTermJobResponse.RecruiterInfo.builder()
                    .id(rp.getUserId())
                    .companyName(rp.getCompanyName())
                    .build();
        }

        List<ShortTermJobResponse.MilestoneResponse> milestones = job.getMilestones() != null ?
                job.getMilestones().stream()
                        .map(this::mapMilestoneToResponse)
                        .collect(Collectors.toList()) : null;

        return ShortTermJobResponse.builder()
                .id(job.getId())
                .title(job.getTitle())
                .description(job.getDescription())
                .requiredSkills(skills)
                .budget(job.getBudget())
                .isNegotiable(job.getIsNegotiable())
                .paymentMethod(job.getPaymentMethod())
                .deadline(job.getDeadline())
                .estimatedDuration(job.getEstimatedDuration())
                .urgency(job.getUrgency())
                .startTime(job.getStartTime())
                .isRemote(job.getIsRemote())
                .location(job.getLocation())
                .isHighlighted(job.getIsHighlighted())
                .status(job.getStatus())
                .applicantCount(job.getApplicantCount())
                .selectedApplicantId(job.getSelectedApplicantId())
                .maxApplicants(job.getMaxApplicants())
                .minRating(job.getMinRating())
                .recruiterId(job.getRecruiterProfile() != null ? job.getRecruiterProfile().getUserId() : null)
                .recruiterInfo(recruiterInfo)
                .milestones(milestones)
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .publishedAt(job.getPublishedAt())
                .completedAt(job.getCompletedAt())
                .paidAt(job.getPaidAt())
                .isExpired(job.isExpired())
                .canApply(job.canApply())
                .build();
    }

    private ShortTermJobResponse.MilestoneResponse mapMilestoneToResponse(ShortTermJobMilestone milestone) {
        return ShortTermJobResponse.MilestoneResponse.builder()
                .id(milestone.getId())
                .title(milestone.getTitle())
                .description(milestone.getDescription())
                .amount(milestone.getAmount())
                .deadline(milestone.getDeadline())
                .status(milestone.getStatus().name())
                .order(milestone.getOrderIndex())
                .completedAt(milestone.getCompletedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private ShortTermApplicationResponse mapToApplicationResponse(ShortTermJobApplication app) {
        User user = app.getUser();
        ShortTermJob job = app.getShortTermJob();
        Optional<PortfolioExtendedProfile> portfolioProfile = portfolioExtendedProfileRepository.findByUserId(user.getId());

        List<ShortTermApplicationResponse.DeliverableResponse> deliverables = app.getDeliverables() != null ?
                app.getDeliverables().stream()
                        .map(d -> ShortTermApplicationResponse.DeliverableResponse.builder()
                                .id(d.getId())
                                .type(d.getType().name())
                                .fileName(d.getFileName())
                                .fileUrl(d.getFileUrl())
                                .fileSize(d.getFileSize())
                                .mimeType(d.getMimeType())
                                .description(d.getDescription())
                                .uploadedAt(d.getUploadedAt())
                                .build())
                        .collect(Collectors.toList()) : null;

        return ShortTermApplicationResponse.builder()
                .id(app.getId())
                .jobId(job.getId())
                .jobTitle(job.getTitle())
                .jobBudget(job.getBudget())
                .userId(user.getId())
                .userFullName(getDisplayName(user))
                .userEmail(user.getEmail())
                .userAvatar(resolveUserAvatar(user, portfolioProfile.orElse(null)))
                .userProfessionalTitle(portfolioProfile.map(PortfolioExtendedProfile::getProfessionalTitle).orElse(null))
                .coverLetter(app.getCoverLetter())
                .proposedPrice(app.getProposedPrice())
                .proposedDuration(app.getProposedDuration())
                .portfolio(fromJson(app.getPortfolio(), List.class))
                .portfolioSlug(portfolioProfile.map(PortfolioExtendedProfile::getCustomUrlSlug).orElse(null))
                .status(app.getStatus())
                .appliedAt(app.getAppliedAt())
                .acceptedAt(app.getAcceptedAt())
                .startedAt(app.getStartedAt())
                .submittedAt(app.getSubmittedAt())
                .completedAt(app.getCompletedAt())
                .deliverables(deliverables)
                .workNote(app.getWorkNote())
                .revisionCount(app.getRevisionCount())
                .jobDetails(ShortTermApplicationResponse.JobInfo.builder()
                        .title(job.getTitle())
                        .budget(job.getBudget())
                        .deadline(job.getDeadline())
                        .recruiterCompanyName(job.getRecruiterProfile() != null ?
                                job.getRecruiterProfile().getCompanyName() : null)
                        .build())
                .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Error converting to JSON", e);
            return null;
        }
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Error parsing JSON", e);
            return null;
        }
    }

    private String getDisplayName(User user) {
        String fullName = user.getFullName();
        if (fullName != null && !fullName.isBlank()) {
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
}
