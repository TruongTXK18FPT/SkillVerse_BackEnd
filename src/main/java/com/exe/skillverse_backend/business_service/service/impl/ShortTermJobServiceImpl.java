package com.exe.skillverse_backend.business_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.ApplyShortTermJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.CreateShortTermJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.RequestCancellationReviewRequest;
import com.exe.skillverse_backend.business_service.dto.request.RequestRevisionRequest;
import com.exe.skillverse_backend.business_service.dto.request.SubmitDeliverableRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateShortTermApplicationStatusRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateShortTermJobRequest;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermApplicationResponse;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermJobResponse;
import com.exe.skillverse_backend.business_service.entity.Dispute;
import com.exe.skillverse_backend.business_service.entity.JobDeliverable;
import com.exe.skillverse_backend.business_service.entity.JobEscrow;
import com.exe.skillverse_backend.business_service.entity.JobEscrow.EscrowStatus;
import com.exe.skillverse_backend.business_service.entity.JobStatusAuditLog;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.ReviewWindow;
import com.exe.skillverse_backend.business_service.entity.RevisionNote;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobMilestone;
import com.exe.skillverse_backend.business_service.entity.enums.JobUrgency;
import com.exe.skillverse_backend.business_service.entity.enums.PaymentMethod;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.DisputeRepository;
import com.exe.skillverse_backend.business_service.repository.JobDeliverableRepository;
import com.exe.skillverse_backend.business_service.repository.JobEscrowRepository;
import com.exe.skillverse_backend.business_service.repository.JobReviewRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.repository.ReviewWindowRepository;
import com.exe.skillverse_backend.business_service.repository.RevisionNoteRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobMilestoneRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.EscrowService;
import com.exe.skillverse_backend.business_service.service.JobAuditService;
import com.exe.skillverse_backend.business_service.service.ShortTermJobService;
import com.exe.skillverse_backend.business_service.service.TrustScoreService;
import com.exe.skillverse_backend.notification_service.entity.NotificationType;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.portfolio_service.entity.PortfolioExtendedProfile;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.premium_service.service.RecruiterSubscriptionService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.user_service.entity.UserProfile;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
    private final JobEscrowRepository jobEscrowRepository;
    private final ReviewWindowRepository reviewWindowRepository;
    private final ShortTermJobMilestoneRepository milestoneRepository;
    private final JobDeliverableRepository deliverableRepository;
    private final RevisionNoteRepository revisionNoteRepository;
    private final RecruiterProfileRepository recruiterProfileRepository;
    private final UserRepository userRepository;
    private final JobReviewRepository reviewRepository;
    private final PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;
    private final UserProfileRepository userProfileRepository;
    private final JobAuditService auditService;
    private final DisputeRepository disputeRepository;
    private final ObjectMapper objectMapper;
    private final RecruiterSubscriptionService recruiterSubscriptionService;
    private final WalletService walletService;
    private final EscrowService escrowService;
    private final TrustScoreService trustScoreService;
    private final NotificationService notificationService;
    private final EmailService emailService;

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
            ShortTermJobStatus.SUBMITTED, ShortTermJobStatus.CANCELLATION_REQUESTED
            // NOTE: CANCELLED removed — recruiter cannot cancel directly from IN_PROGRESS
            // Must go through revision flow (5 revisions → cancellation request)
    );
    private static final List<ShortTermJobStatus> SUBMITTED_TRANSITIONS = List.of(
            ShortTermJobStatus.UNDER_REVIEW, ShortTermJobStatus.CANCELLATION_REQUESTED
    );
    private static final List<ShortTermJobStatus> UNDER_REVIEW_TRANSITIONS = Arrays.asList(
            ShortTermJobStatus.APPROVED, ShortTermJobStatus.REJECTED
    );
    private static final List<ShortTermJobStatus> CANCELLATION_REQUESTED_TRANSITIONS = List.of(
            ShortTermJobStatus.CANCELLED, ShortTermJobStatus.DISPUTED
    );
    private static final List<ShortTermJobStatus> APPROVED_TRANSITIONS = List.of(
            ShortTermJobStatus.COMPLETED
    );
    private static final List<ShortTermJobStatus> REJECTED_TRANSITIONS = List.of(
            ShortTermJobStatus.IN_PROGRESS
    );
    private static final List<ShortTermJobStatus> COMPLETED_TRANSITIONS = Arrays.asList(
            ShortTermJobStatus.PAID, ShortTermJobStatus.CLOSED, ShortTermJobStatus.CANCELLED
    );
    private static final List<ShortTermJobStatus> PAID_TRANSITIONS = Arrays.asList(
            ShortTermJobStatus.CLOSED, ShortTermJobStatus.CANCELLED
    );
    private static final List<ShortTermJobStatus> DISPUTED_TRANSITIONS = List.of(
            ShortTermJobStatus.CLOSED
    );

    // ==================== JOB POSTING (RECRUITER) ====================

    @Override
    public ShortTermJobResponse createJob(Long userId, CreateShortTermJobRequest request) {
        log.info("Creating short-term job for user ID: {}", userId);

        RecruiterProfile recruiterProfile = getRecruiterProfile(userId);

        // Force correct values before validation so the service always produces valid short-term jobs
        request.setIsNegotiable(false);
        request.setPaymentMethod(PaymentMethod.FIXED);
        request.setIsRemote(true);

        validateCreateJobRequest(request);

        ShortTermJob job = ShortTermJob.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .requiredSkills(toJson(request.getRequiredSkills()))
                .budget(request.getBudget())
                .isNegotiable(false)
                .paymentMethod(PaymentMethod.FIXED)
                .deadline(request.getDeadline())
                .estimatedDuration(request.getEstimatedDuration())
                .urgency(request.getUrgency() != null ? request.getUrgency() : JobUrgency.NORMAL)
                .startTime(request.getStartTime())
                .isRemote(true)
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
        if (request.getDeadline() != null) {
            validateDeadline(request.getDeadline());
            job.setDeadline(request.getDeadline());
        }
        if (request.getEstimatedDuration() != null) job.setEstimatedDuration(request.getEstimatedDuration());
        if (request.getUrgency() != null) job.setUrgency(request.getUrgency());
        if (request.getStartTime() != null) job.setStartTime(request.getStartTime());
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
                // Check wallet has enough balance for posting fee
                if (!walletService.hasAvailableCash(userId, SHORT_TERM_JOB_POSTING_FEE)) {
                    throw new BadRequestException(
                            "Số dư ví không đủ để thanh toán phí đăng tin (30,000 VND). Vui lòng nạp thêm tiền vào ví."
                    );
                }
                // No active subscription — charge 30k from wallet
                walletService.deductCash(
                        userId,
                        SHORT_TERM_JOB_POSTING_FEE,
                        "Phí đăng tin tuyển dụng ngắn hạn",
                    WalletTransaction.TransactionType.JOB_POSTING_FEE,
                        "JOB_POSTING",
                        String.valueOf(jobId)
                );
            }
            job.setPaidViaSubscription(usedSubscription);
            job.setIsHighlighted(usedSubscription && recruiterSubscriptionService.canHighlightJob(userId));

            // Check wallet has enough frozen-capable balance for job budget (escrow will be funded later)
            // We require available balance >= budget so recruiter can fund escrow when worker is selected
            BigDecimal requiredForEscrow = job.getBudget();
            if (!walletService.hasAvailableCash(userId, requiredForEscrow)) {
                throw new BadRequestException(
                        "Số dư ví không đủ để ký quỹ cho công việc này ("
                                + requiredForEscrow + " VND). Vui lòng nạp thêm tiền vào ví để đảm bảo có thể ký quỹ khi chọn được ứng viên."
                );
            }
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

        // Handle escrow refund when job is cancelled with a selected candidate
        // Only refund if escrow has not been released yet (not FULLY_RELEASED)
        // Note: recruiter cannot cancel IN_PROGRESS/SUBMITTED/UNDER_REVIEW directly — must go through revision flow
        if (newStatus == ShortTermJobStatus.CANCELLED && job.getSelectedApplicantId() != null) {
            try {
                JobEscrow escrow = escrowService.getEscrowByJobId(jobId);
                if (escrow != null && escrow.getStatus() != JobEscrow.EscrowStatus.FULLY_RELEASED
                        && escrow.getStatus() != JobEscrow.EscrowStatus.REFUNDED) {
                    escrowService.refundEscrow(jobId, userId, reason != null ? reason : "Job cancelled");
                    log.info("Escrow refunded for cancelled job {}", jobId);
                } else {
                    log.info("Escrow already released or not found for job {}, skipping refund", jobId);
                }
            } catch (Exception e) {
                log.warn("Could not refund escrow for cancelled job {}: {}", jobId, e.getMessage());
            }
        }

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

        // Send confirmation email to candidate
        String fullName = user.getFirstName() != null ? user.getFirstName() : user.getEmail();
        String deadline = job.getDeadline() != null ? job.getDeadline().toLocalDate().toString() : "N/A";
        String budget = job.getBudget() != null ? job.getBudget().toString() + " VND" : "Thỏa thuận";
        String recruiterName = job.getRecruiterProfile() != null ? job.getRecruiterProfile().getCompanyName() : "Nhà tuyển dụng";
        emailService.sendShortTermApplicationSubmitted(user.getEmail(), fullName, job.getTitle(), recruiterName, deadline, budget);

        // In-app notification to recruiter
        Long recruiterUserId = job.getRecruiterProfile() != null ? job.getRecruiterProfile().getUserId() : null;
        if (recruiterUserId != null) {
            notificationService.createNotification(
                    recruiterUserId,
                    "Ứng viên mới ứng tuyển",
                    fullName + " đã ứng tuyển công việc \"" + job.getTitle() + "\"",
                    NotificationType.SHORT_TERM_APPLICATION_SUBMITTED,
                    job.getId().toString()
            );
        }

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
        return applicationRepository.findByUserIdOrderByAppliedAtDescWithRevisionNotes(userId)
                .stream()
                .map(this::mapToApplicationResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ShortTermApplicationResponse> getMyApplicationsPaged(Long userId, Pageable pageable) {
        return applicationRepository.findByUserIdWithRevisionNotes(userId, pageable)
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

        // Check if job has escrow funded before selecting candidate
        JobEscrow escrow = escrowService.getEscrowByJobId(jobId);
        if (escrow == null) {
            throw new BadRequestException("Job must be funded before selecting a candidate. Please fund the escrow first.");
        }
        // Set workerId on existing escrow
        escrow.setWorkerId(application.getUser().getId());
        // Update workerId in the job escrow
        jobEscrowRepository.save(escrow);

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

                // Send rejection email to rejected candidates
                User rejectedUser = other.getUser();
                if (rejectedUser != null) {
                    String rejectedName = rejectedUser.getFirstName() != null ? rejectedUser.getFirstName() : rejectedUser.getEmail();
                    String recruiterName = job.getRecruiterProfile() != null ? job.getRecruiterProfile().getCompanyName() : "Nhà tuyển dụng";
                    emailService.sendShortTermApplicationRejected(
                            rejectedUser.getEmail(), rejectedName, job.getTitle(), recruiterName, null);
                }
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

        // Send acceptance email to selected candidate
        User acceptedUser = application.getUser();
        String acceptedName = acceptedUser.getFirstName() != null ? acceptedUser.getFirstName() : acceptedUser.getEmail();
        String recruiterName = job.getRecruiterProfile() != null ? job.getRecruiterProfile().getCompanyName() : "Nhà tuyển dụng";
        String deadline = job.getDeadline() != null ? job.getDeadline().toLocalDate().toString() : "N/A";
        String budget = job.getBudget() != null ? job.getBudget().toString() + " VND" : "Thỏa thuận";
        emailService.sendShortTermApplicationAccepted(
                acceptedUser.getEmail(), acceptedName, job.getTitle(), recruiterName, budget, deadline);

        // In-app notification to accepted candidate
        notificationService.createNotification(
                acceptedUser.getId(),
                "Bạn đã được nhận!",
                "Chúc mừng! Bạn đã được chọn cho công việc \"" + job.getTitle() + "\"",
                NotificationType.SHORT_TERM_APPLICATION_ACCEPTED,
                applicationId.toString()
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
                application.getStatus() != ShortTermApplicationStatus.REVISION_RESPONSE_OVERDUE &&
                application.getStatus() != ShortTermApplicationStatus.ACCEPTED) {
            throw new BadRequestException("Can only submit deliverables when ACCEPTED, WORKING, REVISION_REQUIRED, or REVISION_RESPONSE_OVERDUE");
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
        application.setReviewDeadlineAt(LocalDateTime.now().plusHours(48)); // 48h SLA
        application.setLastActivityAt(LocalDateTime.now());
        if (application.getStartedAt() == null) {
            application.setStartedAt(LocalDateTime.now());
        }

        ShortTermApplicationStatus previousStatus = application.getStatus();
        application.setStatus(ShortTermApplicationStatus.SUBMITTED);
        application = applicationRepository.save(application);

        // Update job status
        job.setStatus(ShortTermJobStatus.SUBMITTED);
        shortTermJobRepository.save(job);

        // Create review window with 72-hour deadline (for auto-approval)
        LocalDateTime reviewDeadline = LocalDateTime.now().plusHours(72);
        ReviewWindow reviewWindow = ReviewWindow.builder()
                .applicationId(application.getId())
                .jobId(job.getId())
                .deadline(reviewDeadline)
                .autoActionAt(reviewDeadline)
                .status(ReviewWindow.ReviewStatus.ACTIVE)
                .build();
        reviewWindowRepository.save(reviewWindow);
        log.info("Created review window {} for application {} with deadline {}",
                reviewWindow.getId(), application.getId(), reviewDeadline);

        auditService.logApplicationStatusChange(
                request.getApplicationId(), previousStatus, ShortTermApplicationStatus.SUBMITTED,
                userId, JobStatusAuditLog.AuditRole.CANDIDATE, "Deliverables submitted"
        );

        // Notify recruiter that work has been submitted
        User submitter = getUserById(userId);
        String submitterName = submitter.getFirstName() != null ? submitter.getFirstName() : submitter.getEmail();
        String recruiterName = job.getRecruiterProfile() != null ? job.getRecruiterProfile().getCompanyName() : "Nhà tuyển dụng";
        if (job.getRecruiterProfile() != null && job.getRecruiterProfile().getUser() != null) {
            emailService.sendShortTermWorkSubmitted(
                    job.getRecruiterProfile().getUser().getEmail(), recruiterName, job.getTitle(), submitterName);
        }

        Long recruiterUserId = job.getRecruiterProfile() != null ? job.getRecruiterProfile().getUserId() : null;
        if (recruiterUserId != null) {
            notificationService.createNotification(
                    recruiterUserId,
                    "Sản phẩm đã được nộp — SLA 48 giờ",
                    submitterName + " đã nộp sản phẩm cho công việc \"" + job.getTitle() + "\". Bạn có 48 giờ để review.",
                    NotificationType.SHORT_TERM_WORK_SUBMITTED,
                    request.getApplicationId().toString()
            );
        }

        return mapToApplicationResponse(application);
    }

    // ==================== WORK REVIEW (RECRUITER) ====================

    @Override
    public ShortTermApplicationResponse approveWork(Long userId, Long applicationId, String message) {
        log.info("Approving work for application ID: {} by user ID: {}", applicationId, userId);

        ShortTermJobApplication application = getApplicationById(applicationId);
        ShortTermJob job = application.getShortTermJob();
        validateJobOwnership(job, userId);

        if (application.getStatus() != ShortTermApplicationStatus.SUBMITTED &&
                application.getStatus() != ShortTermApplicationStatus.SUBMITTED_OVERDUE) {
            throw new BadRequestException("Can only approve SUBMITTED or SUBMITTED_OVERDUE work");
        }

        ShortTermApplicationStatus previousStatus = application.getStatus();
        application.setStatus(ShortTermApplicationStatus.APPROVED);
        application = applicationRepository.save(application);

        // Update job status
        job.setStatus(ShortTermJobStatus.APPROVED);
        shortTermJobRepository.save(job);

        // Mark review window as manually approved
        reviewWindowRepository.findByApplicationId(applicationId).ifPresent(window -> {
            window.setStatus(ReviewWindow.ReviewStatus.MANUAL_APPROVED);
            window.setApprovedAt(LocalDateTime.now());
            reviewWindowRepository.save(window);
            log.info("Review window {} marked as MANUAL_APPROVED", window.getId());
        });

        auditService.logApplicationStatusChange(
                applicationId, previousStatus, ShortTermApplicationStatus.APPROVED,
                userId, JobStatusAuditLog.AuditRole.RECRUITER, message
        );

        // Notify worker that work has been approved
        User worker = application.getUser();
        if (worker != null) {
            String workerName = worker.getFirstName() != null ? worker.getFirstName() : worker.getEmail();
            String budget = job.getBudget() != null ? job.getBudget().toString() + " VND" : "Thỏa thuận";
            emailService.sendShortTermWorkApproved(worker.getEmail(), workerName, job.getTitle(), budget);

            notificationService.createNotification(
                    worker.getId(),
                    "Công việc đã được nghiệm thu!",
                    "Nhà tuyển dụng đã nghiệm thu sản phẩm cho công việc \"" + job.getTitle() + "\"",
                    NotificationType.SHORT_TERM_WORK_APPROVED,
                    applicationId.toString()
            );
        }

        return mapToApplicationResponse(application);
    }

    @Override
    public ShortTermApplicationResponse requestRevision(Long userId, RequestRevisionRequest request) {
        log.info("Requesting revision for application ID: {} by user ID: {}",
                request.getApplicationId(), userId);

        ShortTermJobApplication application = getApplicationById(request.getApplicationId());
        ShortTermJob job = application.getShortTermJob();
        validateJobOwnership(job, userId);

        if (application.getStatus() != ShortTermApplicationStatus.SUBMITTED &&
                application.getStatus() != ShortTermApplicationStatus.SUBMITTED_OVERDUE) {
            throw new BadRequestException("Can only request revision for SUBMITTED or SUBMITTED_OVERDUE work");
        }

        int currentRevisionCount = application.getRevisionCount() == null ? 0 : application.getRevisionCount();

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
        int nextRevisionCount = currentRevisionCount + 1;
        LocalDateTime now = LocalDateTime.now();

        application.setStatus(ShortTermApplicationStatus.REVISION_REQUIRED);
        application.setRevisionCount(nextRevisionCount);
        application.setResponseDeadlineAt(now.plusHours(72));
        application.setLastActivityAt(now);
        application.setDisputeEligibilityUnlocked(nextRevisionCount >= 5);
        application.setCancellationRequestedAt(null);
        application.setCancellationRequestedBy(null);
        application = applicationRepository.save(application);

        // Keep job status as IN_PROGRESS so candidate can continue working after revision.
        job.setStatus(ShortTermJobStatus.IN_PROGRESS);
        shortTermJobRepository.save(job);

        log.info("Revision {} requested for application ID: {}, job ID: {} remains IN_PROGRESS for candidate to fix",
                application.getRevisionCount(), request.getApplicationId(), job.getId());

        auditService.logApplicationStatusChange(
                request.getApplicationId(), previousStatus, ShortTermApplicationStatus.REVISION_REQUIRED,
                userId, JobStatusAuditLog.AuditRole.RECRUITER, request.getNote()
        );

        // Notify worker
        User worker = application.getUser();
        if (worker != null) {
            if (nextRevisionCount >= 5) {
                notificationService.createNotification(
                        worker.getId(),
                        "Đã đạt ngưỡng 5 lần sửa",
                        "Công việc '" + job.getTitle() + "' đã chạm mốc 5 lần yêu cầu sửa. "
                                + "Nhà tuyển dụng không được tự hủy; nếu phát sinh tranh chấp bạn có thể gửi dispute kèm bằng chứng để admin xem xét.",
                        NotificationType.DISPUTE_ELIGIBILITY_UNLOCKED,
                        application.getId().toString()
                );
            } else {
                notificationService.createNotification(
                        worker.getId(),
                        "Nhà tuyển dụng yêu cầu sửa đổi — SLA 72 giờ",
                        "Công việc '" + job.getTitle() + "' cần được sửa theo feedback. Bạn có 72 giờ để phản hồi.",
                        NotificationType.WARNING,
                        application.getId().toString()
                );
            }
        }

        return mapToApplicationResponse(application);
    }

    @Override
    public ShortTermApplicationResponse requestCancellationReview(
            Long userId,
            RequestCancellationReviewRequest request) {
        log.info("Recruiter {} requesting admin cancellation review for application {}", userId, request.getApplicationId());

        ShortTermJobApplication application = getApplicationById(request.getApplicationId());
        ShortTermJob job = application.getShortTermJob();
        validateJobOwnership(job, userId);

        int revisionCount = application.getRevisionCount() == null ? 0 : application.getRevisionCount();
        if (revisionCount < 5) {
            throw new BadRequestException("Cancellation review is only available after 5 revision requests");
        }

        if (application.getStatus() != ShortTermApplicationStatus.REVISION_REQUIRED
                && application.getStatus() != ShortTermApplicationStatus.SUBMITTED
                && application.getStatus() != ShortTermApplicationStatus.SUBMITTED_OVERDUE) {
            throw new BadRequestException(
                    "Can only request admin cancellation review when the application is awaiting rework or freshly submitted");
        }

        disputeRepository.findByShortTermJobId(job.getId())
                .stream()
                .filter(dispute -> dispute.getStatus() != Dispute.DisputeStatus.RESOLVED
                        && dispute.getStatus() != Dispute.DisputeStatus.DISMISSED)
                .findFirst()
                .ifPresent(dispute -> {
                    throw new BadRequestException("This job already has an active admin review/dispute");
                });

        ShortTermApplicationStatus previousApplicationStatus = application.getStatus();
        ShortTermJobStatus previousJobStatus = job.getStatus();
        LocalDateTime now = LocalDateTime.now();

        application.setStatus(ShortTermApplicationStatus.CANCELLATION_REQUESTED);
        application.setCancellationRequestedAt(now);
        application.setCancellationRequestedBy(userId);
        application.setDisputeEligibilityUnlocked(true);
        application.setResponseDeadlineAt(null);
        application.setLastActivityAt(now);
        application = applicationRepository.save(application);

        job.setStatus(ShortTermJobStatus.CANCELLATION_REQUESTED);
        job.setCancellationRequestCount((job.getCancellationRequestCount() == null ? 0 : job.getCancellationRequestCount()) + 1);
        job.setLastCancellationRequestAt(now);
        shortTermJobRepository.save(job);

        Dispute dispute = Dispute.builder()
                .shortTermJob(job)
                .application(application)
                .initiatorId(userId)
                .respondentId(application.getUser().getId())
                .disputeType(Dispute.DisputeType.CANCELLATION_REVIEW)
                .reason(request.getReason())
                .status(Dispute.DisputeStatus.OPEN)
                .createdAt(now)
                .adminResolutionDeadlineAt(now.plusDays(5))
                .build();
        disputeRepository.save(dispute);

        auditService.logApplicationStatusChange(
                application.getId(),
                previousApplicationStatus,
                ShortTermApplicationStatus.CANCELLATION_REQUESTED,
                userId,
                JobStatusAuditLog.AuditRole.RECRUITER,
                request.getReason()
        );
        auditService.logShortTermJobStatusChange(
                job.getId(),
                previousJobStatus,
                ShortTermJobStatus.CANCELLATION_REQUESTED,
                userId,
                JobStatusAuditLog.AuditRole.RECRUITER,
                "Recruiter requested admin cancellation review after 5 revisions"
        );

        notificationService.createNotification(
                application.getUser().getId(),
                "Nhà tuyển dụng yêu cầu admin xem xét hủy job",
                "Nhà tuyển dụng đã gửi yêu cầu hủy cho công việc \"" + job.getTitle()
                        + "\". Admin sẽ xem audit log và bằng chứng trước khi quyết định. Bạn có thể bổ sung dispute/evidence nếu cần.",
                NotificationType.WORKER_CANCELLATION_REQUESTED,
                String.valueOf(dispute.getId())
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
            Optional<ShortTermJobApplication> approvedApp = applicationRepository.findByJobIdAndStatus(jobId, ShortTermApplicationStatus.APPROVED);
            if (approvedApp.isPresent()) {
                job.setStatus(ShortTermJobStatus.APPROVED);
                shortTermJobRepository.save(job);
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
        applicationRepository.findApprovedApplicationByJobId(jobId)
                .ifPresent(app -> {
                    app.setStatus(ShortTermApplicationStatus.COMPLETED);
                    app.setCompletedAt(LocalDateTime.now());
                    applicationRepository.save(app);
                });

        // Release escrow to worker (skip if already released)
        JobEscrow escrow = jobEscrowRepository.findByJobId(jobId).orElse(null);
        if (escrow != null && escrow.getStatus() != EscrowStatus.FULLY_RELEASED) {
            try {
                escrowService.releaseEscrow(jobId, userId, "Job completed");
            } catch (BadRequestException e) {
                log.warn("Escrow release skipped for job {}: {}", jobId, e.getMessage());
            }
        }

        // Recalculate trust scores for both parties
        Long workerId = job.getSelectedApplicantId();
        trustScoreService.triggerRecalculationOnJobComplete(userId, workerId);

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

        if (job.getStatus() == ShortTermJobStatus.PAID) {
            log.info("Job {} is already PAID, skipping duplicate markAsPaid", jobId);
            return mapToResponse(job);
        }
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

    // ==================== CANCELLATION / DISPUTE (WORKER) ====================

    @Override
    public ShortTermApplicationResponse acceptCancellation(Long userId, Long applicationId) {
        log.info("User {} attempted to accept cancellation for application {}", userId, applicationId);
        throw new BadRequestException(
                "Cancellation requests are now reviewed by admin. Please submit dispute evidence if you disagree, or wait for admin's decision.");
    }

    // ==================== VALIDATION HELPERS ====================

    private void validateCreateJobRequest(CreateShortTermJobRequest request) {
        validateDeadline(request.getDeadline());

        // Rule: Only FIXED payment method is allowed
        if (request.getPaymentMethod() != null && request.getPaymentMethod() != PaymentMethod.FIXED) {
            throw new BadRequestException("Chỉ cho phép phương thức thanh toán trả một lần (FIXED) cho công việc ngắn hạn");
        }

        // Rule: Price negotiation is not allowed
        if (Boolean.TRUE.equals(request.getIsNegotiable())) {
            throw new BadRequestException("Không cho phép thương lượng giá cả cho công việc ngắn hạn");
        }

        // Rule: Only remote work is allowed
        if (Boolean.FALSE.equals(request.getIsRemote())) {
            throw new BadRequestException("Chỉ cho phép làm việc từ xa cho công việc ngắn hạn");
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
            case AUTO_APPROVED: allowed = APPROVED_TRANSITIONS; break;
            case CANCELLATION_REQUESTED: allowed = CANCELLATION_REQUESTED_TRANSITIONS; break;
            case AUTO_CANCELLED: allowed = List.of(); break;
            case DISPUTED: allowed = DISPUTED_TRANSITIONS; break;
            case ESCALATED: allowed = DISPUTED_TRANSITIONS; break;
            case APPROVED: allowed = APPROVED_TRANSITIONS; break;
            case REJECTED: allowed = REJECTED_TRANSITIONS; break;
            case COMPLETED: allowed = COMPLETED_TRANSITIONS; break;
            case PAID: allowed = PAID_TRANSITIONS; break;
            case CLOSED: allowed = List.of(); break;
            default: allowed = List.of();
        }

        if (!allowed.contains(target)) {
            throw new BadRequestException(
                    String.format("Cannot transition from %s to %s", current, target)
            );
        }
    }

    private void validateApplicationStatusTransition(ShortTermApplicationStatus current, ShortTermApplicationStatus target) {
        boolean valid = switch (current) {
            case PENDING -> target == ShortTermApplicationStatus.ACCEPTED ||
                    target == ShortTermApplicationStatus.REJECTED ||
                    target == ShortTermApplicationStatus.WITHDRAWN;
            case ACCEPTED -> target == ShortTermApplicationStatus.WORKING;
            case WORKING -> target == ShortTermApplicationStatus.SUBMITTED ||
                    target == ShortTermApplicationStatus.CANCELLED;
            case SUBMITTED, SUBMITTED_OVERDUE -> target == ShortTermApplicationStatus.REVISION_REQUIRED ||
                    target == ShortTermApplicationStatus.APPROVED;
            case REVISION_REQUIRED, REVISION_RESPONSE_OVERDUE -> target == ShortTermApplicationStatus.SUBMITTED;
            case CANCELLATION_REQUESTED -> target == ShortTermApplicationStatus.CANCELLED ||
                    target == ShortTermApplicationStatus.DISPUTE_OPENED;
            case APPROVED -> target == ShortTermApplicationStatus.COMPLETED;
            case DISPUTE_OPENED -> target == ShortTermApplicationStatus.COMPLETED ||
                    target == ShortTermApplicationStatus.CANCELLED;
            case AUTO_CANCELLED, CANCELLED, WITHDRAWN, COMPLETED, REJECTED -> false;
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

        // Rule: No more applications allowed after a candidate is selected
        if (job.getSelectedApplicantId() != null) {
            throw new BadRequestException("This job already has a selected candidate and is no longer accepting applications");
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
                    .companyLogoUrl(resolveRecruiterCompanyLogo(rp))
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
                .revisionNotes(app.getRevisionNotes() != null ? app.getRevisionNotes().stream()
                        .map(n -> {
                            List<String> issues = null;
                            if (n.getSpecificIssues() != null && !n.getSpecificIssues().isBlank()) {
                                try {
                                    issues = objectMapper.readValue(n.getSpecificIssues(), new TypeReference<List<String>>() {});
                                } catch (JsonProcessingException ignored) {}
                            }
                            return ShortTermApplicationResponse.RevisionNoteResponse.builder()
                                    .id(n.getId())
                                    .note(n.getNote())
                                    .specificIssues(issues)
                                    .requestedById(n.getRequestedBy().getId())
                                    .requestedByName(getDisplayName(n.getRequestedBy()))
                                    .requestedAt(n.getRequestedAt())
                                    .resolvedAt(n.getResolvedAt())
                                    .build();
                        })
                        .collect(java.util.stream.Collectors.toList()) : null)
                .reviewDeadlineAt(app.getReviewDeadlineAt())
                .responseDeadlineAt(app.getResponseDeadlineAt())
                .disputeEligibilityUnlocked(app.getDisputeEligibilityUnlocked())
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

    private String resolveRecruiterCompanyLogo(RecruiterProfile recruiterProfile) {
        if (recruiterProfile == null) {
            return null;
        }
        if (recruiterProfile.getCompanyLogoUrl() != null && !recruiterProfile.getCompanyLogoUrl().isBlank()) {
            return recruiterProfile.getCompanyLogoUrl();
        }
        return recruiterProfile.getUser() != null ? recruiterProfile.getUser().getAvatarUrl() : null;
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
