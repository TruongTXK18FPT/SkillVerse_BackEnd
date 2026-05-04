package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.business_service.entity.JobApplication;
import com.exe.skillverse_backend.business_service.entity.JobContract;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.enums.ContractStatus;
import com.exe.skillverse_backend.business_service.exception.JobCloseBlockedException;
import com.exe.skillverse_backend.business_service.repository.*;
import com.exe.skillverse_backend.business_service.service.impl.JobPostingServiceImpl;
import com.exe.skillverse_backend.premium_service.service.RecruiterSubscriptionService;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
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
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Hardening tests for JobPostingServiceImpl.
 *
 * Covers:
 * 1. updateJob blocks CLOSED jobs
 * 2. changeStatus to CLOSED blocked when unresolved applicants exist
 * 3. changeStatus to CLOSED blocked when pending contracts exist
 * 4. changeStatus to CLOSED allowed when all resolved
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobPostingServiceImplHardeningTest {

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private RecruiterProfileRepository recruiterProfileRepository;
    @Mock private JobApplicationRepository jobApplicationRepository;
    @Mock private JobContractRepository jobContractRepository;
    @Mock private JobBoostRepository jobBoostRepository;
    @Mock private CandidateMatchScoreRepository candidateMatchScoreRepository;
    @Mock private RecruiterShortlistRepository recruiterShortlistRepository;
    @Mock private RecruitmentSessionRepository recruitmentSessionRepository;
    @Mock private RecruiterSubscriptionService recruiterSubscriptionService;
    @Mock private WalletService walletService;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private JobPostingServiceImpl service;

    private User mockRecruiter;
    private RecruiterProfile mockRecruiterProfile;
    private JobPosting mockJob;

    @BeforeEach
    void setUp() {
        mockRecruiter = new User();
        mockRecruiter.setId(1L);
        mockRecruiter.setEmail("recruiter@test.com");
        mockRecruiter.setFirstName("Recruiter");
        mockRecruiter.setLastName("Test");

        mockRecruiterProfile = new RecruiterProfile();
        mockRecruiterProfile.setUserId(mockRecruiter.getId());
        mockRecruiterProfile.setUser(mockRecruiter);
        mockRecruiterProfile.setCompanyName("Test Company");

        mockJob = new JobPosting();
        mockJob.setId(10L);
        mockJob.setTitle("Senior Java Developer");
        mockJob.setStatus(JobStatus.OPEN);
        mockJob.setRecruiterProfile(mockRecruiterProfile);
        mockJob.setMinBudget(BigDecimal.valueOf(15_000_000));
        mockJob.setMaxBudget(BigDecimal.valueOf(25_000_000));
        mockJob.setDeadline(LocalDate.now().plusDays(30));
        mockJob.setApplicantCount(0);
        mockJob.setIsRemote(true);
        mockJob.setIsNegotiable(false);
        mockJob.setRequiredSkills("[\"Java\",\"Spring Boot\"]");
        mockJob.setIsHighlighted(false);
    }

    // ==================== updateJob CLOSED guard ====================

    @Nested
    @DisplayName("updateJob — CLOSED status guard")
    class UpdateJobClosedGuardTests {

        @Test
        @DisplayName("Should block editing a CLOSED job (must reopen first)")
        void updateJob_closedJobBlocked() {
            // Arrange
            mockJob.setStatus(JobStatus.CLOSED);
            when(jobPostingRepository.findByIdAndRecruiterProfileUserId(mockJob.getId(), mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockJob));

            var request = new com.exe.skillverse_backend.business_service.dto.request.UpdateJobRequest();
            request.setTitle("Updated Title");

            // Act & Assert
            assertThatThrownBy(() -> service.updateJob(mockRecruiter.getId(), mockJob.getId(), request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CLOSED");
        }

        @Test
        @DisplayName("Should block editing an OPEN job (must close or reopen)")
        void updateJob_openJobBlocked() {
            // Arrange
            mockJob.setStatus(JobStatus.OPEN);
            when(jobPostingRepository.findByIdAndRecruiterProfileUserId(mockJob.getId(), mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockJob));

            var request = new com.exe.skillverse_backend.business_service.dto.request.UpdateJobRequest();
            request.setTitle("Updated Title");

            // Act & Assert
            assertThatThrownBy(() -> service.updateJob(mockRecruiter.getId(), mockJob.getId(), request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("OPEN");
        }
    }

    // ==================== changeStatus to CLOSED guard ====================

    @Nested
    @DisplayName("changeStatus — close-job guard")
    class CloseJobGuardTests {

        @Test
        @DisplayName("Should block closing when unresolved PENDING applicants exist")
        void changeStatus_closeBlockedByPendingApplicant() {
            // Arrange
            User candidate = new User();
            candidate.setId(2L);
            candidate.setFirstName("John");
            candidate.setLastName("Doe");
            candidate.setEmail("john@test.com");

            JobApplication pendingApp = new JobApplication();
            pendingApp.setId(100L);
            pendingApp.setStatus(JobApplicationStatus.PENDING);
            pendingApp.setUser(candidate);
            pendingApp.setJobPosting(mockJob);

            when(jobPostingRepository.findByIdAndRecruiterProfileUserId(mockJob.getId(), mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockJob));
            when(jobApplicationRepository.findByJobPostingIdAndStatusIn(eq(mockJob.getId()), anyList()))
                    .thenReturn(List.of(pendingApp));
            when(jobContractRepository.findByApplicationJobPostingIdAndStatusIn(eq(mockJob.getId()), anyList()))
                    .thenReturn(Collections.emptyList());

            // Act & Assert
            assertThatThrownBy(() -> service.changeStatus(mockRecruiter.getId(), mockJob.getId(), JobStatus.CLOSED))
                    .isInstanceOf(JobCloseBlockedException.class)
                    .satisfies(ex -> {
                        JobCloseBlockedException blocked = (JobCloseBlockedException) ex;
                        assertThat(blocked.getBlockingItems()).hasSize(1);
                        assertThat(blocked.getBlockingItems().get(0).getScope()).isEqualTo("APPLICATION");
                        assertThat(blocked.getBlockingItems().get(0).getCurrentStatus()).isEqualTo("PENDING");
                    });
        }

        @Test
        @DisplayName("Should block closing when INTERVIEWED applicant exists")
        void changeStatus_closeBlockedByInterviewedApplicant() {
            // Arrange
            User candidate = new User();
            candidate.setId(3L);
            candidate.setFirstName("Jane");
            candidate.setLastName("Smith");
            candidate.setEmail("jane@test.com");

            JobApplication interviewedApp = new JobApplication();
            interviewedApp.setId(101L);
            interviewedApp.setStatus(JobApplicationStatus.INTERVIEWED);
            interviewedApp.setUser(candidate);
            interviewedApp.setJobPosting(mockJob);

            when(jobPostingRepository.findByIdAndRecruiterProfileUserId(mockJob.getId(), mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockJob));
            when(jobApplicationRepository.findByJobPostingIdAndStatusIn(eq(mockJob.getId()), anyList()))
                    .thenReturn(List.of(interviewedApp));
            when(jobContractRepository.findByApplicationJobPostingIdAndStatusIn(eq(mockJob.getId()), anyList()))
                    .thenReturn(Collections.emptyList());

            // Act & Assert
            assertThatThrownBy(() -> service.changeStatus(mockRecruiter.getId(), mockJob.getId(), JobStatus.CLOSED))
                    .isInstanceOf(JobCloseBlockedException.class)
                    .satisfies(ex -> {
                        JobCloseBlockedException blocked = (JobCloseBlockedException) ex;
                        assertThat(blocked.getBlockingItems()).hasSize(1);
                        assertThat(blocked.getBlockingItems().get(0).getCurrentStatus()).isEqualTo("INTERVIEWED");
                        assertThat(blocked.getBlockingItems().get(0).getRequiredAction()).contains("Từ chối");
                    });
        }

        @Test
        @DisplayName("Should block closing when contract in PENDING_SIGNER status exists")
        void changeStatus_closeBlockedByPendingContract() {
            // Arrange
            JobApplication app = new JobApplication();
            app.setId(102L);
            app.setStatus(JobApplicationStatus.CONTRACT_SIGNED);
            app.setJobPosting(mockJob);

            JobContract pendingContract = new JobContract();
            pendingContract.setId(500L);
            pendingContract.setStatus(ContractStatus.PENDING_SIGNER);
            pendingContract.setCandidateName("Candidate X");
            pendingContract.setCandidateEmail("candidatex@test.com");
            pendingContract.setApplication(app);

            when(jobPostingRepository.findByIdAndRecruiterProfileUserId(mockJob.getId(), mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockJob));
            when(jobApplicationRepository.findByJobPostingIdAndStatusIn(eq(mockJob.getId()), anyList()))
                    .thenReturn(Collections.emptyList());
            when(jobContractRepository.findByApplicationJobPostingIdAndStatusIn(eq(mockJob.getId()), anyList()))
                    .thenReturn(List.of(pendingContract));

            // Act & Assert
            assertThatThrownBy(() -> service.changeStatus(mockRecruiter.getId(), mockJob.getId(), JobStatus.CLOSED))
                    .isInstanceOf(JobCloseBlockedException.class)
                    .satisfies(ex -> {
                        JobCloseBlockedException blocked = (JobCloseBlockedException) ex;
                        assertThat(blocked.getBlockingItems()).hasSize(1);
                        assertThat(blocked.getBlockingItems().get(0).getScope()).isEqualTo("CONTRACT");
                        assertThat(blocked.getBlockingItems().get(0).getCurrentStatus()).isEqualTo("PENDING_SIGNER");
                    });
        }

        @Test
        @DisplayName("Should allow closing when all applicants and contracts are resolved")
        void changeStatus_closeAllowedWhenAllResolved() {
            // Arrange — no unresolved apps, no pending contracts
            when(jobPostingRepository.findByIdAndRecruiterProfileUserId(mockJob.getId(), mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockJob));
            when(jobApplicationRepository.findByJobPostingIdAndStatusIn(eq(mockJob.getId()), anyList()))
                    .thenReturn(Collections.emptyList());
            when(jobContractRepository.findByApplicationJobPostingIdAndStatusIn(eq(mockJob.getId()), anyList()))
                    .thenReturn(Collections.emptyList());
            when(jobPostingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Act & Assert
            assertThatCode(() -> service.changeStatus(mockRecruiter.getId(), mockJob.getId(), JobStatus.CLOSED))
                    .doesNotThrowAnyException();

            verify(jobPostingRepository).save(argThat(job -> job.getStatus() == JobStatus.CLOSED));
        }

        @Test
        @DisplayName("Should aggregate both application and contract blockers in single exception")
        void changeStatus_closeBlockedByBothApplicantsAndContracts() {
            // Arrange
            User candidate = new User();
            candidate.setId(4L);
            candidate.setFirstName("Mixed");
            candidate.setLastName("Case");
            candidate.setEmail("mixed@test.com");

            JobApplication unresolvedApp = new JobApplication();
            unresolvedApp.setId(103L);
            unresolvedApp.setStatus(JobApplicationStatus.OFFER_SENT);
            unresolvedApp.setUser(candidate);
            unresolvedApp.setJobPosting(mockJob);

            JobApplication contractApp = new JobApplication();
            contractApp.setId(104L);
            contractApp.setJobPosting(mockJob);

            JobContract draftContract = new JobContract();
            draftContract.setId(501L);
            draftContract.setStatus(ContractStatus.DRAFT);
            draftContract.setCandidateName("Draft Candidate");
            draftContract.setCandidateEmail("draft@test.com");
            draftContract.setApplication(contractApp);

            when(jobPostingRepository.findByIdAndRecruiterProfileUserId(mockJob.getId(), mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockJob));
            when(jobApplicationRepository.findByJobPostingIdAndStatusIn(eq(mockJob.getId()), anyList()))
                    .thenReturn(List.of(unresolvedApp));
            when(jobContractRepository.findByApplicationJobPostingIdAndStatusIn(eq(mockJob.getId()), anyList()))
                    .thenReturn(List.of(draftContract));

            // Act & Assert
            assertThatThrownBy(() -> service.changeStatus(mockRecruiter.getId(), mockJob.getId(), JobStatus.CLOSED))
                    .isInstanceOf(JobCloseBlockedException.class)
                    .satisfies(ex -> {
                        JobCloseBlockedException blocked = (JobCloseBlockedException) ex;
                        assertThat(blocked.getBlockingItems()).hasSize(2);
                        assertThat(blocked.getBlockingItems())
                                .extracting("scope")
                                .containsExactlyInAnyOrder("APPLICATION", "CONTRACT");
                    });
        }
    }
}
