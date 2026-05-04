package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.ApplyJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateApplicationStatusRequest;
import com.exe.skillverse_backend.business_service.entity.JobApplication;
import com.exe.skillverse_backend.business_service.entity.JobContract;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.enums.ContractStatus;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.JobContractRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.service.impl.JobApplicationServiceImpl;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.shared.service.EmailService;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.exe.skillverse_backend.premium_service.dto.response.UsageCheckResult;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Hardening tests for JobApplicationServiceImpl.
 *
 * Covers:
 * 1. Active SIGNED contract blocks new applications
 * 2. ONSITE HIRED status blocks new applications
 * 3. Ended contract allows applications
 * 4. CLOSED job status blocks application status updates
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobApplicationServiceImplHardeningTest {

    @Mock private JobApplicationRepository jobApplicationRepository;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private JobContractRepository jobContractRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmailService emailService;
    @Mock private UsageLimitService usageLimitService;
    @Mock private PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;
    @Mock private UserProfileRepository userProfileRepository;

    @InjectMocks
    private JobApplicationServiceImpl service;

    private User mockRecruiter;
    private User mockCandidate;
    private RecruiterProfile mockRecruiterProfile;
    private JobPosting mockJob;

    @BeforeEach
    void setUp() {
        mockRecruiter = new User();
        mockRecruiter.setId(1L);
        mockRecruiter.setEmail("recruiter@test.com");
        mockRecruiter.setFirstName("Recruiter");
        mockRecruiter.setLastName("Test");

        mockCandidate = new User();
        mockCandidate.setId(2L);
        mockCandidate.setEmail("candidate@test.com");
        mockCandidate.setFirstName("Candidate");
        mockCandidate.setLastName("Test");

        mockRecruiterProfile = new RecruiterProfile();
        mockRecruiterProfile.setUserId(mockRecruiter.getId());
        mockRecruiterProfile.setUser(mockRecruiter);
        mockRecruiterProfile.setCompanyName("Test Company");

        mockJob = new JobPosting();
        mockJob.setId(10L);
        mockJob.setTitle("Senior Java Developer");
        mockJob.setStatus(JobStatus.OPEN);
        mockJob.setRecruiterProfile(mockRecruiterProfile);
        mockJob.setApplicantCount(0);
        mockJob.setIsRemote(true);

        // Default stubs
        when(portfolioExtendedProfileRepository.existsByUserId(anyLong())).thenReturn(true);
        when(userProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
        when(portfolioExtendedProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());

        // Mock UsageLimitService to avoid NPE in mapToResponse
        UsageCheckResult usageCheckResult = new UsageCheckResult();
        usageCheckResult.setAllowed(false);
        when(usageLimitService.canUseFeature(anyLong(), any(FeatureType.class))).thenReturn(usageCheckResult);
    }

    // ==================== Active Employment Guard Tests ====================

    @Nested
    @DisplayName("Apply-to-job active-employment guard")
    class ActiveEmploymentGuardTests {

        @Test
        @DisplayName("Should block application when candidate has active SIGNED contract")
        void applyToJob_blockedWhenActiveSignedContractExists() {
            // Arrange
            JobContract activeContract = new JobContract();
            activeContract.setId(100L);
            activeContract.setJobTitle("Frontend Developer");
            activeContract.setStatus(ContractStatus.SIGNED);
            activeContract.setEndDate(LocalDate.now().plusMonths(6));

            when(jobPostingRepository.findById(mockJob.getId())).thenReturn(Optional.of(mockJob));
            when(jobApplicationRepository.existsByJobPostingIdAndUserId(mockJob.getId(), mockCandidate.getId()))
                    .thenReturn(false);
            when(jobContractRepository.findActiveContractsForCandidate(eq(mockCandidate.getId()), any(LocalDate.class)))
                    .thenReturn(List.of(activeContract));

            ApplyJobRequest request = new ApplyJobRequest();
            request.setCoverLetter("I want this job");

            // Act & Assert
            assertThatThrownBy(() -> service.applyToJob(mockCandidate.getId(), mockJob.getId(), request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("hợp đồng")
                    .hasMessageContaining("Frontend Developer");
        }

        @Test
        @DisplayName("Should block application when candidate has ONSITE HIRED status")
        void applyToJob_blockedWhenOnsiteHiredExists() {
            // Arrange
            JobPosting hiredJob = new JobPosting();
            hiredJob.setId(20L);
            hiredJob.setTitle("Backend Engineer (Onsite)");

            JobApplication hiredApp = new JobApplication();
            hiredApp.setId(200L);
            hiredApp.setStatus(JobApplicationStatus.HIRED);
            hiredApp.setJobPosting(hiredJob);

            when(jobPostingRepository.findById(mockJob.getId())).thenReturn(Optional.of(mockJob));
            when(jobApplicationRepository.existsByJobPostingIdAndUserId(mockJob.getId(), mockCandidate.getId()))
                    .thenReturn(false);
            when(jobContractRepository.findActiveContractsForCandidate(eq(mockCandidate.getId()), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(jobApplicationRepository.findByUserIdAndStatus(mockCandidate.getId(), JobApplicationStatus.HIRED))
                    .thenReturn(List.of(hiredApp));

            ApplyJobRequest request = new ApplyJobRequest();
            request.setCoverLetter("I want this job");

            // Act & Assert
            assertThatThrownBy(() -> service.applyToJob(mockCandidate.getId(), mockJob.getId(), request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Backend Engineer (Onsite)");
        }

        @Test
        @DisplayName("Should allow application after contract has ended (endDate < today)")
        void applyToJob_allowedAfterContractEnded() {
            // Arrange — no active contracts, no HIRED status
            when(jobPostingRepository.findById(mockJob.getId())).thenReturn(Optional.of(mockJob));
            when(jobApplicationRepository.existsByJobPostingIdAndUserId(mockJob.getId(), mockCandidate.getId()))
                    .thenReturn(false);
            when(jobContractRepository.findActiveContractsForCandidate(eq(mockCandidate.getId()), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());
            when(jobApplicationRepository.findByUserIdAndStatus(mockCandidate.getId(), JobApplicationStatus.HIRED))
                    .thenReturn(Collections.emptyList());
            when(userRepository.findById(mockCandidate.getId())).thenReturn(Optional.of(mockCandidate));
            when(jobApplicationRepository.save(any())).thenAnswer(inv -> {
                JobApplication app = inv.getArgument(0);
                app.setId(300L);
                return app;
            });
            when(jobPostingRepository.save(any())).thenReturn(mockJob);

            ApplyJobRequest request = new ApplyJobRequest();
            request.setCoverLetter("I want this job");

            // Act & Assert — no exception
            assertThatCode(() -> service.applyToJob(mockCandidate.getId(), mockJob.getId(), request))
                    .doesNotThrowAnyException();
        }
    }

    // ==================== CLOSED Job Guard Tests ====================

    @Nested
    @DisplayName("Application status update — CLOSED job guard")
    class ClosedJobGuardTests {

        @Test
        @DisplayName("Should block status update when job is CLOSED")
        void updateApplicationStatus_rejectedWhenJobClosed() {
            // Arrange
            mockJob.setStatus(JobStatus.CLOSED);

            JobApplication application = new JobApplication();
            application.setId(400L);
            application.setJobPosting(mockJob);
            application.setUser(mockCandidate);
            application.setStatus(JobApplicationStatus.PENDING);

            when(jobApplicationRepository.findById(application.getId()))
                    .thenReturn(Optional.of(application));

            UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest();
            request.setStatus(JobApplicationStatus.REVIEWED);

            // Act & Assert
            assertThatThrownBy(() -> service.updateApplicationStatus(
                    mockRecruiter.getId(), application.getId(), request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("đã đóng");
        }
    }
}
