package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.ApplyJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateApplicationStatusRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobApplicationResponse;
import com.exe.skillverse_backend.business_service.entity.JobApplication;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.JobContractRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.service.impl.JobApplicationServiceImpl;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.premium_service.dto.response.UsageCheckResult;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for JobApplicationService
 * Covers: Apply, Get Applicants (Paginated), Update Status
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobApplicationServiceTest {

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private JobContractRepository jobContractRepository;

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private UsageLimitService usageLimitService;

    @Mock
    private PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private JobApplicationServiceImpl jobApplicationService;

    private User applicant;
    private User recruiter;
    private RecruiterProfile recruiterProfile;
    private JobPosting jobPosting;
    private JobApplication jobApplication;
    private ApplyJobRequest applyRequest;

    @BeforeEach
    void setUp() {
        // 1. Applicant
        applicant = new User();
        applicant.setId(1L);
        applicant.setEmail("applicant@test.com");
        applicant.setFirstName("John");
        applicant.setLastName("Doe");

        // 2. Recruiter
        recruiter = new User();
        recruiter.setId(2L);
        recruiter.setEmail("recruiter@test.com");
        recruiter.setFirstName("Jane");
        recruiter.setLastName("Smith");

        // 3. Recruiter Profile
        recruiterProfile = new RecruiterProfile();
        recruiterProfile.setUser(recruiter);
        recruiterProfile.setCompanyName("Test Company");

        // 4. Job
        jobPosting = JobPosting.builder()
                .id(100L)
                .title("Java Developer")
                .status(JobStatus.OPEN)
                .recruiterProfile(recruiterProfile)
                .applicantCount(0)
                .minBudget(java.math.BigDecimal.valueOf(1000))
                .maxBudget(java.math.BigDecimal.valueOf(2000))
                .isRemote(true)
                .build();

        // 5. Application
        jobApplication = JobApplication.builder()
                .id(500L)
                .user(applicant)
                .jobPosting(jobPosting)
                .status(JobApplicationStatus.PENDING)
                .coverLetter("I am interested")
                .appliedAt(LocalDateTime.now())
                .build();

        // 6. Request
        applyRequest = new ApplyJobRequest();
        applyRequest.setCoverLetter("I am interested");

        // Mock jobContractRepository to avoid NPE
        when(jobContractRepository.findByApplicationId(anyLong())).thenReturn(Optional.empty());
    }

    // ==================== APPLY TO JOB TESTS ====================

    @Test
    void applyToJob_Success() {
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.of(jobPosting));
        when(jobApplicationRepository.existsByJobPostingIdAndUserId(100L, 1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(applicant));
        when(portfolioExtendedProfileRepository.existsByUserId(1L)).thenReturn(true);
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.applyToJob(1L, 100L, applyRequest);

        assertNotNull(response);
        assertEquals(100L, response.getJobId());
        assertEquals(1L, response.getUserId());
        assertEquals(JobApplicationStatus.PENDING, response.getStatus());

        verify(jobPostingRepository).save(jobPosting);
        assertEquals(1, jobPosting.getApplicantCount());
    }

    @Test
    void applyToJob_Fail_JobNotFound() {
        when(jobPostingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> jobApplicationService.applyToJob(1L, 999L, applyRequest));
    }

    @Test
    void applyToJob_Fail_JobNotOpen() {
        jobPosting.setStatus(JobStatus.CLOSED);
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.of(jobPosting));
        when(portfolioExtendedProfileRepository.existsByUserId(1L)).thenReturn(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jobApplicationService.applyToJob(1L, 100L, applyRequest));
        assertEquals("Can only apply to OPEN jobs", exception.getMessage());
    }

    @Test
    void applyToJob_Fail_AlreadyApplied() {
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.of(jobPosting));
        when(jobApplicationRepository.existsByJobPostingIdAndUserId(100L, 1L)).thenReturn(true);
        when(portfolioExtendedProfileRepository.existsByUserId(1L)).thenReturn(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jobApplicationService.applyToJob(1L, 100L, applyRequest));
        assertEquals("You have already applied to this job", exception.getMessage());
    }

    @Test
    void applyToJob_Fail_RecruiterSelfApply() {
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.of(jobPosting));
        when(portfolioExtendedProfileRepository.existsByUserId(2L)).thenReturn(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jobApplicationService.applyToJob(2L, 100L, applyRequest));
        assertEquals("Recruiters cannot apply to their own job postings", exception.getMessage());
    }

    // ==================== GET APPLICANTS TESTS ====================

    @Test
    void getJobApplicants_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<JobApplication> page = new PageImpl<>(List.of(jobApplication));

        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(100L, 2L))
                .thenReturn(Optional.of(jobPosting));
        when(jobApplicationRepository.findByJobPostingIdWithUserOrderByAppliedAtDesc(100L, pageable))
                .thenReturn(page);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        Page<JobApplicationResponse> responses = jobApplicationService.getJobApplicants(2L, 100L, pageable);

        assertFalse(responses.isEmpty());
        assertEquals(1, responses.getTotalElements());
        assertEquals(1L, responses.getContent().get(0).getUserId());
    }

    @Test
    void getJobApplicants_Fail_NotOwner() {
        Pageable pageable = PageRequest.of(0, 10);
        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(100L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> jobApplicationService.getJobApplicants(1L, 100L, pageable));
    }

    // ==================== UPDATE STATUS TESTS ====================

    @Test
    void updateApplicationStatus_Reviewed_Success() {
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.REVIEWED);

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.updateApplicationStatus(2L, 500L, request);

        assertEquals(JobApplicationStatus.REVIEWED, response.getStatus());
        verify(emailService).sendJobApplicationReviewed(any(), any(), any());
    }

    @Test
    void updateApplicationStatus_Accepted_Success() {
        // REMOTE job: must go REVIEWED -> ACCEPTED (pipeline requires intermediate review step)
        jobApplication.setStatus(JobApplicationStatus.REVIEWED);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.ACCEPTED);
        request.setAcceptanceMessage("Welcome!");

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.updateApplicationStatus(2L, 500L, request);

        assertEquals(JobApplicationStatus.ACCEPTED, response.getStatus());
        assertEquals("Welcome!", response.getAcceptanceMessage());
        verify(emailService).sendJobApplicationAccepted(any(), any(), any(), eq("Welcome!"), any());
    }

    @Test
    void updateApplicationStatus_Rejected_Success() {
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.REJECTED);
        request.setRejectionReason("Not a fit.");

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.updateApplicationStatus(2L, 500L, request);

        assertEquals(JobApplicationStatus.REJECTED, response.getStatus());
        assertEquals("Not a fit.", response.getRejectionReason());
        verify(emailService).sendJobApplicationRejected(any(), any(), any(), eq("Not a fit."));
    }

    @Test
    void updateApplicationStatus_Fail_AcceptedWithoutMessage() {
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.ACCEPTED);
        request.setAcceptanceMessage(""); // Empty

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));

        assertThrows(IllegalArgumentException.class,
                () -> jobApplicationService.updateApplicationStatus(2L, 500L, request));
    }

    @Test
    void updateApplicationStatus_Fail_RejectedWithoutReason() {
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.REJECTED);
        request.setRejectionReason(null); // Null

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));

        assertThrows(IllegalArgumentException.class,
                () -> jobApplicationService.updateApplicationStatus(2L, 500L, request));
    }

    @Test
    void updateApplicationStatus_Fail_NotOwner() {
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.REVIEWED);

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));

        // User 1 (Applicant) tries to update status
        assertThrows(IllegalStateException.class,
                () -> jobApplicationService.updateApplicationStatus(1L, 500L, request));
    }

    // ==================== REMOTE JOB STATUS TRANSITION TESTS ====================

    @Test
    void updateApplicationStatus_Remote_AcceptedToInterviewScheduled_Fail() {
        // REMOTE job: cannot go directly from ACCEPTED to any status other than REJECTED
        // Must schedule interview first via InterviewScheduleService
        jobApplication.setStatus(JobApplicationStatus.ACCEPTED);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.INTERVIEWED);

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> jobApplicationService.updateApplicationStatus(2L, 500L, request));
        assertTrue(exception.getMessage().contains("After ACCEPTED, schedule an interview first"));
    }

    @Test
    void updateApplicationStatus_Remote_PendingToReviewed_Success() {
        jobApplication.setStatus(JobApplicationStatus.PENDING);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.REVIEWED);

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.updateApplicationStatus(2L, 500L, request);

        assertEquals(JobApplicationStatus.REVIEWED, response.getStatus());
    }

    @Test
    void updateApplicationStatus_Remote_PendingToRejected_Success() {
        jobApplication.setStatus(JobApplicationStatus.PENDING);
        jobApplication.setCoverLetter("I am interested");

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.REJECTED);
        request.setRejectionReason("Not enough experience");

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.updateApplicationStatus(2L, 500L, request);

        assertEquals(JobApplicationStatus.REJECTED, response.getStatus());
        assertEquals("Not enough experience", response.getRejectionReason());
    }

    @Test
    void updateApplicationStatus_Remote_PendingToAccepted_Fail() {
        // Cannot go from PENDING directly to ACCEPTED — must REVIEW first
        jobApplication.setStatus(JobApplicationStatus.PENDING);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.ACCEPTED);
        request.setAcceptanceMessage("Welcome!");

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> jobApplicationService.updateApplicationStatus(2L, 500L, request));
        assertTrue(exception.getMessage().contains("only REVIEWED or REJECTED transitions are allowed"));
    }

    @Test
    void updateApplicationStatus_Remote_ReviewedToAccepted_Success() {
        jobApplication.setStatus(JobApplicationStatus.REVIEWED);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.ACCEPTED);
        request.setAcceptanceMessage("Congratulations!");

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.updateApplicationStatus(2L, 500L, request);

        assertEquals(JobApplicationStatus.ACCEPTED, response.getStatus());
        assertEquals("Congratulations!", response.getAcceptanceMessage());
    }

    @Test
    void updateApplicationStatus_Remote_ReviewedToInterviewed_Fail() {
        jobApplication.setStatus(JobApplicationStatus.REVIEWED);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.INTERVIEWED);
        request.setInterviewResult("Great performance");

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> jobApplicationService.updateApplicationStatus(2L, 500L, request));
        assertTrue(exception.getMessage().contains("only ACCEPTED or REJECTED transitions are allowed"));
    }

    @Test
    void updateApplicationStatus_Remote_AcceptedToInterviewScheduled_Fail_DirectStatusUpdate() {
        jobApplication.setStatus(JobApplicationStatus.ACCEPTED);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.INTERVIEW_SCHEDULED);

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));

        assertThrows(IllegalArgumentException.class,
                () -> jobApplicationService.updateApplicationStatus(2L, 500L, request));
    }

    @Test
    void updateApplicationStatus_Remote_TerminalStatus_Fail() {
        // OFFER_ACCEPTED is terminal — no further transitions allowed
        jobApplication.setStatus(JobApplicationStatus.OFFER_ACCEPTED);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.CONTRACT_SIGNED);

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> jobApplicationService.updateApplicationStatus(2L, 500L, request));
        assertTrue(exception.getMessage().contains("terminal status"));
    }

    @Test
    void updateApplicationStatus_Remote_AcceptedToOfferSent_Fail() {
        // After ACCEPTED on REMOTE job, must go through interview pipeline
        // Cannot skip to OFFER_SENT directly
        jobApplication.setStatus(JobApplicationStatus.ACCEPTED);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.OFFER_SENT);
        request.setOfferDetails("We offer 50M VND/year");

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> jobApplicationService.updateApplicationStatus(2L, 500L, request));
        assertTrue(exception.getMessage().contains("After ACCEPTED, schedule an interview first"));
    }

    // ==================== ONSITE JOB STATUS RESTRICTION TESTS ====================

    // Note: PENDING -> INTERVIEW_SCHEDULED is blocked at the service layer by
    // validateRemoteStatusTransition (all jobs must go PENDING->REVIEWED first).
    // This transition is impossible to reach directly — frontend enforces ACCEPTED status
    // before showing the interview scheduling modal.

    @Test
    void updateApplicationStatus_Onsite_PendingToReviewed_Success() {
        jobPosting.setIsRemote(false);
        jobApplication.setStatus(JobApplicationStatus.PENDING);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.REVIEWED);

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.updateApplicationStatus(2L, 500L, request);

        assertEquals(JobApplicationStatus.REVIEWED, response.getStatus());
    }

    @Test
    void updateApplicationStatus_Onsite_AcceptedToOfferSent_Fail() {
        // ONSITE: after ACCEPTED, can only schedule interview (INTERVIEW_SCHEDULED) then mark INTERVIEWED
        // Cannot go to OFFER_SENT — ONSITE jobs have no offer step; contract is created after INTERVIEWED
        jobPosting.setIsRemote(false);
        jobApplication.setStatus(JobApplicationStatus.ACCEPTED);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.OFFER_SENT);
        request.setOfferDetails("Contract offer");

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> jobApplicationService.updateApplicationStatus(2L, 500L, request));
        assertTrue(exception.getMessage().contains("ONSITE jobs only support"));
    }

    @Test
    void updateApplicationStatus_Onsite_AcceptedToInterviewScheduled_Success() {
        // ONSITE: ACCEPTED -> INTERVIEW_SCHEDULED is allowed (via InterviewScheduleService)
        // After interview is completed -> INTERVIEWED, then contract can be created
        jobPosting.setIsRemote(false);
        jobApplication.setStatus(JobApplicationStatus.ACCEPTED);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.INTERVIEW_SCHEDULED);

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.updateApplicationStatus(2L, 500L, request);

        assertEquals(JobApplicationStatus.INTERVIEW_SCHEDULED, response.getStatus());
    }

    @Test
    void updateApplicationStatus_Onsite_Interviewed_CanCreateContract() {
        // ONSITE: after INTERVIEWED, contract can be created directly (no OFFER_SENT step)
        jobPosting.setIsRemote(false);
        jobApplication.setStatus(JobApplicationStatus.INTERVIEW_SCHEDULED);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.INTERVIEWED);
        request.setInterviewResult("Good performance, recommended for hire");

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.updateApplicationStatus(2L, 500L, request);

        assertEquals(JobApplicationStatus.INTERVIEWED, response.getStatus());
        assertEquals("Good performance, recommended for hire", response.getInterviewResult());
    }

    // ==================== INTERVIEW RESULT FIELD TESTS ====================

    @Test
    void updateApplicationStatus_Interviewed_SetsInterviewResult() {
        // Remote pipeline: Reviewed -> Accepted -> (interview scheduled) -> Interviewed
        jobApplication.setStatus(JobApplicationStatus.INTERVIEW_SCHEDULED);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.INTERVIEWED);
        request.setInterviewResult("Strong technical skills, recommended for offer");

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.updateApplicationStatus(2L, 500L, request);

        assertEquals(JobApplicationStatus.INTERVIEWED, response.getStatus());
        assertEquals("Strong technical skills, recommended for offer", response.getInterviewResult());
    }

    @Test
    void updateApplicationStatus_OfferSent_SetsOfferDetails() {
        // Remote pipeline: after INTERVIEWED -> OFFER_SENT
        jobApplication.setStatus(JobApplicationStatus.INTERVIEWED);

        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
        request.setStatus(JobApplicationStatus.OFFER_SENT);
        request.setOfferDetails("Annual salary: 80M VND, start date: 2026-05-01");

        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.updateApplicationStatus(2L, 500L, request);

        assertEquals(JobApplicationStatus.OFFER_SENT, response.getStatus());
        // Note: sendStatusEmail only sends for REVIEWED/ACCEPTED/REJECTED — OFFER_SENT has no email
    }

    @Test
    void getApplicationById_Success_AsApplicant() {
        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.getApplicationById(1L, 500L);

        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals(1L, response.getUserId());
    }

    @Test
    void getApplicationById_Success_AsRecruiter() {
        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));
        when(usageLimitService.canUseFeature(any(), any()))
                .thenReturn(UsageCheckResult.builder().allowed(false).build());

        JobApplicationResponse response = jobApplicationService.getApplicationById(2L, 500L);

        assertNotNull(response);
        assertEquals(500L, response.getId());
    }

    @Test
    void getApplicationById_Fail_Unauthorized() {
        when(jobApplicationRepository.findById(500L)).thenReturn(Optional.of(jobApplication));

        assertThrows(RuntimeException.class,
                () -> jobApplicationService.getApplicationById(999L, 500L));
    }
}
