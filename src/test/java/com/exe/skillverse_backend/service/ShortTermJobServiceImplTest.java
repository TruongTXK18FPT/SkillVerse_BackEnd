package com.exe.skillverse_backend.service;

import com.exe.skillverse_backend.business_service.dto.request.CreateShortTermJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.ApplyShortTermJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.SubmitDeliverableRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateShortTermApplicationStatusRequest;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermJobResponse;
import com.exe.skillverse_backend.business_service.dto.response.ShortTermApplicationResponse;
import com.exe.skillverse_backend.business_service.entity.*;
import com.exe.skillverse_backend.business_service.entity.enums.*;
import com.exe.skillverse_backend.business_service.repository.*;
import com.exe.skillverse_backend.business_service.service.impl.ShortTermJobServiceImpl;
import com.exe.skillverse_backend.business_service.service.JobAuditService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.user_service.repository.UserProfileRepository;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ShortTermJobServiceImpl validation rules
 *
 * Test categories:
 * 1. Job Creation Validation
 * 2. Job Status Transition Validation
 * 3. Application Validation
 * 4. Application Status Transition Validation
 * 5. Deliverable Submission Validation
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShortTermJobServiceImplTest {

    @Mock
    private ShortTermJobRepository shortTermJobRepository;

    @Mock
    private ShortTermJobApplicationRepository applicationRepository;

    @Mock
    private JobDeliverableRepository deliverableRepository;

    @Mock
    private ShortTermJobMilestoneRepository milestoneRepository;

    @Mock
    private RevisionNoteRepository revisionNoteRepository;

    @Mock
    private RecruiterProfileRepository recruiterProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JobReviewRepository reviewRepository;

    @Mock
    private PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;

    @Mock
    private JobAuditService auditService;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ShortTermJobServiceImpl shortTermJobService;

    private User mockRecruiter;
    private User mockApplicant;
    private RecruiterProfile mockRecruiterProfile;
    private ShortTermJob mockJob;
    private ShortTermJobApplication mockApplication;

    @BeforeEach
    void setUp() {
        // Setup mock recruiter
        mockRecruiter = new User();
        mockRecruiter.setId(1L);
        mockRecruiter.setEmail("recruiter@test.com");
        mockRecruiter.setFirstName("Test");
        mockRecruiter.setLastName("Recruiter");

        // Setup mock applicant
        mockApplicant = new User();
        mockApplicant.setId(2L);
        mockApplicant.setEmail("applicant@test.com");
        mockApplicant.setFirstName("Test");
        mockApplicant.setLastName("Applicant");

        // Setup recruiter profile
        mockRecruiterProfile = new RecruiterProfile();
        mockRecruiterProfile.setUserId(mockRecruiter.getId());
        mockRecruiterProfile.setUser(mockRecruiter);
        mockRecruiterProfile.setCompanyName("Test Company");

        // Setup mock job
        mockJob = new ShortTermJob();
        mockJob.setId(1L);
        mockJob.setTitle("Test Job Title");
        mockJob.setDescription("Test job description with enough characters for validation");
        mockJob.setRequiredSkills("[\"Java\",\"Spring Boot\"]");
        mockJob.setBudget(new BigDecimal("1000000"));
        mockJob.setStatus(ShortTermJobStatus.PUBLISHED);
        mockJob.setRecruiterProfile(mockRecruiterProfile);
        mockJob.setDeadline(LocalDateTime.now().plusDays(7));
        mockJob.setMaxApplicants(10);
        mockJob.setApplicantCount(0);
        mockJob.setIsRemote(true);
        mockJob.setIsNegotiable(false);
        mockJob.setPaymentMethod(PaymentMethod.FIXED);
        mockJob.setEstimatedDuration("1-2 weeks");
        mockJob.setUrgency(JobUrgency.NORMAL);

        // Setup mock application
        mockApplication = new ShortTermJobApplication();
        mockApplication.setId(1L);
        mockApplication.setShortTermJob(mockJob);
        mockApplication.setUser(mockApplicant);
        mockApplication.setStatus(ShortTermApplicationStatus.PENDING);
        mockApplication.setCoverLetter("Test cover letter");
        mockApplication.setAppliedAt(LocalDateTime.now());
        mockApplication.setRevisionCount(0);

        // Default mock for portfolio - assume user has portfolio
        when(portfolioExtendedProfileRepository.existsByUserId(anyLong())).thenReturn(true);

        // Stub userProfileRepository to avoid NPE in mapToResponse
        when(userProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());
    }

    // ==================== JOB CREATION VALIDATION TESTS ====================

    @Nested
    @DisplayName("Job Creation Validation Tests")
    class JobCreationValidationTests {

        @Test
        @DisplayName("Should create job successfully with valid data")
        void shouldCreateJobSuccessfullyWithValidData() {
            CreateShortTermJobRequest request = createValidJobRequest();

            when(recruiterProfileRepository.findByUserId(mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockRecruiterProfile));
            when(shortTermJobRepository.save(any())).thenAnswer(invocation -> {
                ShortTermJob job = invocation.getArgument(0);
                job.setId(1L);
                return job;
            });

            ShortTermJobResponse response = shortTermJobService.createJob(mockRecruiter.getId(), request);

            assertThat(response).isNotNull();
            verify(shortTermJobRepository).save(any());
        }

        @Test
        @DisplayName("Should fail when deadline is in the past")
        void shouldFailWhenDeadlineInPast() {
            CreateShortTermJobRequest request = createValidJobRequest();
            request.setDeadline(LocalDateTime.now().minusDays(1));

            when(recruiterProfileRepository.findByUserId(mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockRecruiterProfile));

            assertThatThrownBy(() -> shortTermJobService.createJob(mockRecruiter.getId(), request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Deadline must be in the future");
        }

        @Test
        @DisplayName("Should fail when location missing for non-remote job")
        void shouldFailWhenLocationMissingForNonRemoteJob() {
            CreateShortTermJobRequest request = createValidJobRequest();
            request.setIsRemote(false);
            request.setLocation(null);

            when(recruiterProfileRepository.findByUserId(mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockRecruiterProfile));

            assertThatThrownBy(() -> shortTermJobService.createJob(mockRecruiter.getId(), request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Location is required for non-remote jobs");
        }
    }

    // ==================== JOB STATUS TRANSITION VALIDATION TESTS ====================

    @Nested
    @DisplayName("Job Status Transition Validation Tests")
    class JobStatusTransitionTests {

        @Test
        @DisplayName("Should allow DRAFT to PUBLISHED transition")
        void shouldAllowDraftToPublishedTransition() {
            mockJob.setStatus(ShortTermJobStatus.DRAFT);

            when(shortTermJobRepository.findById(any())).thenReturn(Optional.of(mockJob));
            when(recruiterProfileRepository.findByUserId(mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockRecruiterProfile));
            when(shortTermJobRepository.save(any())).thenReturn(mockJob);

            ShortTermJobResponse response = shortTermJobService.changeJobStatus(
                    mockRecruiter.getId(), mockJob.getId(),
                    ShortTermJobStatus.PUBLISHED, "Publishing job");

            assertThat(response).isNotNull();
            verify(auditService).logShortTermJobStatusChange(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should not allow invalid status transitions")
        void shouldNotAllowInvalidStatusTransition() {
            mockJob.setStatus(ShortTermJobStatus.PAID);

            when(shortTermJobRepository.findById(any())).thenReturn(Optional.of(mockJob));
            when(recruiterProfileRepository.findByUserId(mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockRecruiterProfile));

            assertThatThrownBy(() -> shortTermJobService.changeJobStatus(
                    mockRecruiter.getId(), mockJob.getId(),
                    ShortTermJobStatus.PUBLISHED, "Invalid transition"))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Cannot transition");
        }

        @Test
        @DisplayName("Should only allow deleting DRAFT jobs")
        void shouldOnlyAllowDeletingDraftJobs() {
            mockJob.setStatus(ShortTermJobStatus.PUBLISHED);

            when(shortTermJobRepository.findById(any())).thenReturn(Optional.of(mockJob));
            when(recruiterProfileRepository.findByUserId(mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockRecruiterProfile));

            assertThatThrownBy(() -> shortTermJobService.deleteJob(mockRecruiter.getId(), mockJob.getId()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("'PUBLISHED'");
        }

        @Test
        @DisplayName("Should delete DRAFT job successfully")
        void shouldDeleteDraftJobSuccessfully() {
            mockJob.setStatus(ShortTermJobStatus.DRAFT);

            when(shortTermJobRepository.findById(any())).thenReturn(Optional.of(mockJob));
            when(recruiterProfileRepository.findByUserId(mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockRecruiterProfile));

            shortTermJobService.deleteJob(mockRecruiter.getId(), mockJob.getId());

            verify(shortTermJobRepository).delete(mockJob);
        }

        @Test
        @DisplayName("Should allow COMPLETED to PAID transition")
        void shouldAllowCompletedToPaidTransition() {
            mockJob.setStatus(ShortTermJobStatus.COMPLETED);

            when(shortTermJobRepository.findById(any())).thenReturn(Optional.of(mockJob));
            when(recruiterProfileRepository.findByUserId(mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockRecruiterProfile));
            when(shortTermJobRepository.save(any())).thenReturn(mockJob);

            ShortTermJobResponse response = shortTermJobService.markAsPaid(
                    mockRecruiter.getId(), mockJob.getId());

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Should not allow marking non-COMPLETED job as paid")
        void shouldNotAllowMarkingNonCompletedJobAsPaid() {
            mockJob.setStatus(ShortTermJobStatus.IN_PROGRESS);

            when(shortTermJobRepository.findById(any())).thenReturn(Optional.of(mockJob));
            when(recruiterProfileRepository.findByUserId(mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockRecruiterProfile));

            assertThatThrownBy(() -> shortTermJobService.markAsPaid(
                    mockRecruiter.getId(), mockJob.getId()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Can only mark COMPLETED jobs as paid");
        }
    }

    // ==================== APPLICATION VALIDATION TESTS ====================

    @Nested
    @DisplayName("Application Validation Tests")
    class ApplicationValidationTests {

        @Test
        @DisplayName("Should create application successfully with valid data")
        void shouldCreateApplicationSuccessfully() {
            ApplyShortTermJobRequest request = new ApplyShortTermJobRequest();
            request.setCoverLetter("Test cover letter with enough content");

            when(shortTermJobRepository.findById(mockJob.getId())).thenReturn(Optional.of(mockJob));
            when(userRepository.findById(mockApplicant.getId())).thenReturn(Optional.of(mockApplicant));
            when(applicationRepository.existsByShortTermJobIdAndUserId(any(), any())).thenReturn(false);
            when(applicationRepository.save(any())).thenAnswer(invocation -> {
                ShortTermJobApplication app = invocation.getArgument(0);
                app.setId(1L);
                return app;
            });
            when(shortTermJobRepository.save(any())).thenReturn(mockJob);
            when(portfolioExtendedProfileRepository.findByUserId(anyLong())).thenReturn(Optional.empty());

            ShortTermApplicationResponse response = shortTermJobService.applyToJob(
                    mockApplicant.getId(), mockJob.getId(), request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Should not allow applying to non-published job")
        void shouldNotAllowApplyingToNonPublishedJob() {
            mockJob.setStatus(ShortTermJobStatus.DRAFT);

            ApplyShortTermJobRequest request = new ApplyShortTermJobRequest();
            request.setCoverLetter("Test cover letter");

            when(shortTermJobRepository.findById(mockJob.getId())).thenReturn(Optional.of(mockJob));
            when(userRepository.findById(mockApplicant.getId())).thenReturn(Optional.of(mockApplicant));

            assertThatThrownBy(() -> shortTermJobService.applyToJob(
                    mockApplicant.getId(), mockJob.getId(), request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("not accepting applications");
        }

        @Test
        @DisplayName("Should not allow duplicate applications")
        void shouldNotAllowDuplicateApplications() {
            ApplyShortTermJobRequest request = new ApplyShortTermJobRequest();
            request.setCoverLetter("Test cover letter");

            when(shortTermJobRepository.findById(mockJob.getId())).thenReturn(Optional.of(mockJob));
            when(userRepository.findById(mockApplicant.getId())).thenReturn(Optional.of(mockApplicant));
            when(applicationRepository.existsByShortTermJobIdAndUserId(any(), any())).thenReturn(true);

            assertThatThrownBy(() -> shortTermJobService.applyToJob(
                    mockApplicant.getId(), mockJob.getId(), request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("already applied");
        }

        @Test
        @DisplayName("Should not allow applying when max applicants reached")
        void shouldNotAllowApplyingWhenMaxReached() {
            mockJob.setMaxApplicants(5);
            mockJob.setApplicantCount(5);

            ApplyShortTermJobRequest request = new ApplyShortTermJobRequest();
            request.setCoverLetter("Test cover letter");

            when(shortTermJobRepository.findById(mockJob.getId())).thenReturn(Optional.of(mockJob));
            when(userRepository.findById(mockApplicant.getId())).thenReturn(Optional.of(mockApplicant));
            when(applicationRepository.existsByShortTermJobIdAndUserId(any(), any())).thenReturn(false);

            assertThatThrownBy(() -> shortTermJobService.applyToJob(
                    mockApplicant.getId(), mockJob.getId(), request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("maximum applicants");
        }

        @Test
        @DisplayName("Should not allow withdrawing non-PENDING application")
        void shouldNotAllowWithdrawingNonPendingApplication() {
            mockApplication.setStatus(ShortTermApplicationStatus.ACCEPTED);

            when(applicationRepository.findById(mockApplication.getId()))
                    .thenReturn(Optional.of(mockApplication));

            assertThatThrownBy(() -> shortTermJobService.withdrawApplication(
                    mockApplicant.getId(), mockApplication.getId()))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("PENDING");
        }
    }

    // ==================== APPLICATION STATUS TRANSITION TESTS ====================

    @Nested
    @DisplayName("Application Status Transition Tests")
    class ApplicationStatusTransitionTests {

        @Test
        @DisplayName("Should allow updating application status")
        void shouldAllowUpdatingApplicationStatus() {
            mockApplication.setStatus(ShortTermApplicationStatus.PENDING);

            when(applicationRepository.findById(mockApplication.getId()))
                    .thenReturn(Optional.of(mockApplication));
            when(shortTermJobRepository.findById(mockJob.getId()))
                    .thenReturn(Optional.of(mockJob));
            when(recruiterProfileRepository.findByUserId(mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockRecruiterProfile));
            when(applicationRepository.save(any())).thenReturn(mockApplication);
            when(shortTermJobRepository.save(any())).thenReturn(mockJob);
            doNothing().when(auditService).logApplicationStatusChange(anyLong(), any(), any(), anyLong(), any(), anyString());

            UpdateShortTermApplicationStatusRequest request = new UpdateShortTermApplicationStatusRequest();
            request.setStatus(ShortTermApplicationStatus.ACCEPTED);
            request.setMessage("Accepted!");

            ShortTermApplicationResponse response = shortTermJobService.updateApplicationStatus(
                    mockRecruiter.getId(), mockApplication.getId(), request);

            assertThat(response).isNotNull();
        }

        @Test
        @DisplayName("Should not allow non-owner to change application status")
        void shouldNotAllowNonOwnerToChangeStatus() {
            mockApplication.setStatus(ShortTermApplicationStatus.PENDING);
            Long otherUserId = 99L;

            // Different recruiter profile for the non-owner
            RecruiterProfile otherProfile = new RecruiterProfile();
            otherProfile.setUserId(otherUserId);

            when(applicationRepository.findById(mockApplication.getId()))
                    .thenReturn(Optional.of(mockApplication));
            when(recruiterProfileRepository.findByUserId(otherUserId))
                    .thenReturn(Optional.of(otherProfile));

            UpdateShortTermApplicationStatusRequest request = new UpdateShortTermApplicationStatusRequest();
            request.setStatus(ShortTermApplicationStatus.ACCEPTED);

            assertThatThrownBy(() -> shortTermJobService.updateApplicationStatus(
                    otherUserId, mockApplication.getId(), request))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    // ==================== DELIVERABLE SUBMISSION VALIDATION TESTS ====================

    @Nested
    @DisplayName("Deliverable Submission Validation Tests")
    class DeliverableSubmissionTests {

        @Test
        @DisplayName("Should not allow submission by non-applicant")
        void shouldNotAllowSubmissionByNonApplicant() {
            mockApplication.setStatus(ShortTermApplicationStatus.WORKING);

            when(applicationRepository.findById(mockApplication.getId()))
                    .thenReturn(Optional.of(mockApplication));

            SubmitDeliverableRequest request = new SubmitDeliverableRequest();
            request.setApplicationId(mockApplication.getId());
            request.setDeliverables(Collections.emptyList());

            assertThatThrownBy(() -> shortTermJobService.submitDeliverables(
                    mockRecruiter.getId(), request))
                    .isInstanceOf(ForbiddenException.class);
        }
    }

    // ==================== HELPER METHODS ====================

    private CreateShortTermJobRequest createValidJobRequest() {
        CreateShortTermJobRequest request = new CreateShortTermJobRequest();
        request.setTitle("Valid Job Title Here");
        request.setDescription("This is a valid job description that has more than fifty characters to pass validation.");
        request.setRequiredSkills(Arrays.asList("Java", "Spring Boot"));
        request.setBudget(new BigDecimal("1000000"));
        request.setDeadline(LocalDateTime.now().plusDays(7));
        request.setEstimatedDuration("1-2 weeks");
        request.setUrgency(JobUrgency.NORMAL);
        request.setIsRemote(true);
        request.setMaxApplicants(10);
        request.setPaymentMethod(PaymentMethod.FIXED);
        request.setIsNegotiable(false);
        return request;
    }
}
