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
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.service.impl.JobApplicationServiceImpl;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.premium_service.dto.response.UsageCheckResult;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.shared.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class JobApplicationServiceTest {

    @Mock
    private JobApplicationRepository jobApplicationRepository;

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
    }

    // ==================== APPLY TO JOB TESTS ====================

    @Test
    void applyToJob_Success() {
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.of(jobPosting));
        when(jobApplicationRepository.existsByJobPostingIdAndUserId(100L, 1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(applicant));
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

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jobApplicationService.applyToJob(1L, 100L, applyRequest));
        assertEquals("Can only apply to OPEN jobs", exception.getMessage());
    }

    @Test
    void applyToJob_Fail_AlreadyApplied() {
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.of(jobPosting));
        when(jobApplicationRepository.existsByJobPostingIdAndUserId(100L, 1L)).thenReturn(true);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jobApplicationService.applyToJob(1L, 100L, applyRequest));
        assertEquals("You have already applied to this job", exception.getMessage());
    }

    @Test
    void applyToJob_Fail_RecruiterSelfApply() {
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.of(jobPosting));

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
}
