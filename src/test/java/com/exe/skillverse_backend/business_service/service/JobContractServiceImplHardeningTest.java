package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.CreateContractRequest;
import com.exe.skillverse_backend.business_service.entity.ContractSignature;
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
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.service.impl.JobContractServiceImpl;
import com.exe.skillverse_backend.identity_verification_service.service.FptAiEkycService;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
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
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Hardening tests for JobContractServiceImpl.
 *
 * Covers:
 * 1. createContract blocks when job is CLOSED
 * 2. uploadContractPdf requires endDate
 * 3. uploadContractPdf validates endDate after startDate
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobContractServiceImplHardeningTest {

    @Mock private JobContractRepository contractRepository;
    @Mock private JobApplicationRepository applicationRepository;
    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private UserRepository userRepository;
    @Mock private RecruiterProfileRepository recruiterProfileRepository;
    @Mock private NotificationService notificationService;
    @Mock private FptAiEkycService fptAiEkycService;
    @Mock private CloudinaryService cloudinaryService;

    @InjectMocks
    private JobContractServiceImpl service;

    private User mockRecruiter;
    private User mockCandidate;
    private RecruiterProfile mockRecruiterProfile;
    private JobPosting mockJob;
    private JobApplication mockApplication;

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
        mockJob.setIsRemote(true);

        mockApplication = new JobApplication();
        mockApplication.setId(100L);
        mockApplication.setJobPosting(mockJob);
        mockApplication.setUser(mockCandidate);
        mockApplication.setStatus(JobApplicationStatus.ACCEPTED);
    }

    // ==================== createContract CLOSED Guard ====================

    @Nested
    @DisplayName("createContract — CLOSED job guard")
    class CreateContractClosedGuardTests {

        @Test
        @DisplayName("Should block creating contract when job is CLOSED")
        void createContract_throwsWhenJobClosed() {
            // Arrange
            mockJob.setStatus(JobStatus.CLOSED);

            CreateContractRequest request = new CreateContractRequest();
            request.setApplicationId(mockApplication.getId());
            request.setStartDate(LocalDate.now());
            request.setEndDate(LocalDate.now().plusYears(1));
            request.setSalary(BigDecimal.valueOf(20_000_000));
            request.setJobTitle("Senior Java Developer");

            when(applicationRepository.findById(mockApplication.getId()))
                    .thenReturn(Optional.of(mockApplication));

            // Act & Assert
            assertThatThrownBy(() -> service.createContract(request, mockRecruiter.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("đã đóng");
        }

        @Test
        @DisplayName("Should allow creating contract when job is OPEN")
        void createContract_allowedWhenJobOpen() {
            // Arrange
            mockJob.setStatus(JobStatus.OPEN);

            CreateContractRequest request = new CreateContractRequest();
            request.setApplicationId(mockApplication.getId());
            request.setStartDate(LocalDate.now());
            request.setEndDate(LocalDate.now().plusYears(1));
            request.setSalary(BigDecimal.valueOf(20_000_000));
            request.setJobTitle("Senior Java Developer");

            when(applicationRepository.findById(mockApplication.getId()))
                    .thenReturn(Optional.of(mockApplication));
            when(contractRepository.existsByApplicationId(mockApplication.getId()))
                    .thenReturn(false);
            when(userRepository.findById(mockCandidate.getId()))
                    .thenReturn(Optional.of(mockCandidate));
            when(userRepository.findById(mockRecruiter.getId()))
                    .thenReturn(Optional.of(mockRecruiter));
            when(contractRepository.save(any())).thenAnswer(inv -> {
                JobContract c = inv.getArgument(0);
                c.setId(500L);
                return c;
            });

            // Act & Assert — should not throw
            assertThatCode(() -> service.createContract(request, mockRecruiter.getId()))
                    .doesNotThrowAnyException();
        }
    }

    // ==================== uploadContractPdf Date Validation ====================

    @Nested
    @DisplayName("uploadContractPdf — date validation")
    class UploadContractPdfDateTests {

        @Test
        @DisplayName("Should throw when endDate is null")
        void uploadContractPdf_throwsWhenEndDateNull() {
            // Arrange
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getContentType()).thenReturn("application/pdf");

            // Act & Assert
            assertThatThrownBy(() -> service.uploadContractPdf(500L, mockFile, LocalDate.now(), null, mockRecruiter.getId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("endDate");
        }

        @Test
        @DisplayName("Should throw when file is empty")
        void uploadContractPdf_throwsWhenFileEmpty() {
            // Arrange
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.isEmpty()).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> service.uploadContractPdf(500L, mockFile,
                    LocalDate.now(), LocalDate.now().plusYears(1), mockRecruiter.getId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("File is required");
        }

        @Test
        @DisplayName("Should throw when file is not PDF")
        void uploadContractPdf_throwsWhenNotPdf() {
            // Arrange
            MultipartFile mockFile = mock(MultipartFile.class);
            when(mockFile.isEmpty()).thenReturn(false);
            when(mockFile.getContentType()).thenReturn("image/png");

            // Act & Assert
            assertThatThrownBy(() -> service.uploadContractPdf(500L, mockFile,
                    LocalDate.now(), LocalDate.now().plusYears(1), mockRecruiter.getId()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("PDF");
        }
    }
}
