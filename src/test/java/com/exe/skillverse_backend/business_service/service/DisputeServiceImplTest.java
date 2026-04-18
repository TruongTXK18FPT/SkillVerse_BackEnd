package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.OpenDisputeRequest;
import com.exe.skillverse_backend.business_service.dto.request.ResolveDisputeRequest;
import com.exe.skillverse_backend.business_service.entity.Dispute;
import com.exe.skillverse_backend.business_service.entity.JobEscrow;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.ShortTermJobApplication;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.DisputeEvidenceRepository;
import com.exe.skillverse_backend.business_service.repository.DisputeResponseRepository;
import com.exe.skillverse_backend.business_service.repository.DisputeRepository;
import com.exe.skillverse_backend.business_service.repository.EscrowTransactionRepository;
import com.exe.skillverse_backend.business_service.repository.JobEscrowRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.impl.DisputeServiceImpl;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisputeServiceImplTest {

    @Mock
    private DisputeRepository disputeRepository;

    @Mock
    private DisputeEvidenceRepository disputeEvidenceRepository;

    @Mock
    private DisputeResponseRepository disputeResponseRepository;

    @Mock
    private ShortTermJobRepository shortTermJobRepository;

    @Mock
    private ShortTermJobApplicationRepository applicationRepository;

    @Mock
    private JobEscrowRepository jobEscrowRepository;

    @Mock
    private TrustScoreService trustScoreService;

    @Mock
    private EscrowTransactionRepository escrowTransactionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    private DisputeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DisputeServiceImpl(
                disputeRepository,
                disputeEvidenceRepository,
                disputeResponseRepository,
                shortTermJobRepository,
                applicationRepository,
                jobEscrowRepository,
                trustScoreService,
                escrowTransactionRepository,
                userRepository,
                notificationService);

        lenient().when(disputeRepository.save(any(Dispute.class))).thenAnswer(invocation -> {
            Dispute dispute = invocation.getArgument(0);
            if (dispute.getId() == null) {
                dispute.setId(55L);
            }
            return dispute;
        });
        lenient().when(applicationRepository.save(any(ShortTermJobApplication.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(shortTermJobRepository.save(any(ShortTermJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(jobEscrowRepository.save(any(JobEscrow.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("openDispute should block recruiters from opening disputes directly")
    void openDispute_ShouldBlockRecruitersFromOpeningDisputesDirectly() {
        ShortTermJob job = job(100L, 1L, 2L);
        OpenDisputeRequest request = OpenDisputeRequest.builder()
                .jobId(job.getId())
                .disputeType(Dispute.DisputeType.POOR_QUALITY)
                .reason("Recruiter cannot do this")
                .build();
        when(shortTermJobRepository.findById(job.getId())).thenReturn(Optional.of(job));

        assertThrows(ForbiddenException.class, () -> service.openDispute(1L, request));
    }

    @Test
    @DisplayName("openDispute should create a worker dispute and freeze the escrow")
    void openDispute_ShouldCreateWorkerDisputeAndFreezeEscrow() {
        ShortTermJob job = job(100L, 1L, 2L);
        ShortTermJobApplication application = application(job, 2L, ShortTermApplicationStatus.SUBMITTED, true);
        JobEscrow escrow = JobEscrow.builder()
                .id(10L)
                .job(job)
                .recruiterId(1L)
                .workerId(2L)
                .status(JobEscrow.EscrowStatus.FUNDED)
                .escrowBalance(new BigDecimal("1000000"))
                .platformFee(new BigDecimal("100000"))
                .build();
        OpenDisputeRequest request = OpenDisputeRequest.builder()
                .jobId(job.getId())
                .disputeType(Dispute.DisputeType.POOR_QUALITY)
                .reason("Work quality below agreement")
                .build();

        when(shortTermJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(applicationRepository.findByShortTermJobIdAndUserId(job.getId(), 2L)).thenReturn(Optional.of(application));
        when(disputeRepository.findFirstByShortTermJobId(job.getId())).thenReturn(Optional.empty());
        when(jobEscrowRepository.findByJobId(job.getId())).thenReturn(Optional.of(escrow));

        Dispute dispute = service.openDispute(2L, request);

        assertEquals(Dispute.DisputeStatus.OPEN, dispute.getStatus());
        assertEquals(55L, dispute.getId());
        assertEquals(ShortTermApplicationStatus.DISPUTE_OPENED, application.getStatus());
        assertEquals(ShortTermJobStatus.DISPUTED, job.getStatus());
        assertEquals(JobEscrow.EscrowStatus.DISPUTED, escrow.getStatus());
        verify(notificationService).createNotification(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("resolveDispute should reject already resolved disputes")
    void resolveDispute_ShouldRejectAlreadyResolvedDisputes() {
        Dispute dispute = Dispute.builder()
                .id(99L)
                .status(Dispute.DisputeStatus.RESOLVED)
                .build();
        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));

        assertThrows(BadRequestException.class, () -> service.resolveDispute(900L, dispute.getId(),
                ResolveDisputeRequest.builder()
                        .resolution(Dispute.DisputeResolution.FULL_RELEASE)
                        .build()));
    }

    @Test
    @DisplayName("resolveDispute should fully release funds to the worker and recalculate trust scores")
    void resolveDispute_ShouldFullyReleaseFundsToWorkerAndRecalculateTrustScores() {
        ShortTermJob job = job(100L, 1L, 2L);
        ShortTermJobApplication application = application(job, 2L, ShortTermApplicationStatus.SUBMITTED, true);
        Dispute dispute = Dispute.builder()
                .id(77L)
                .shortTermJob(job)
                .application(application)
                .initiatorId(2L)
                .respondentId(1L)
                .status(Dispute.DisputeStatus.OPEN)
                .build();
        JobEscrow escrow = JobEscrow.builder()
                .id(10L)
                .job(job)
                .recruiterId(1L)
                .workerId(2L)
                .totalAmount(new BigDecimal("1000000"))
                .platformFee(new BigDecimal("100000"))
                .escrowBalance(new BigDecimal("1000000"))
                .status(JobEscrow.EscrowStatus.DISPUTED)
                .build();
        User admin = User.builder().id(900L).firstName("Admin").lastName("User").build();

        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));
        when(jobEscrowRepository.findByJobId(job.getId())).thenReturn(Optional.of(escrow));
        when(userRepository.findById(900L)).thenReturn(Optional.of(admin));

        Dispute resolved = service.resolveDispute(900L, dispute.getId(),
                ResolveDisputeRequest.builder()
                        .resolution(Dispute.DisputeResolution.FULL_RELEASE)
                        .resolutionNotes("Worker delivered correctly")
                        .build());

        assertEquals(Dispute.DisputeStatus.RESOLVED, resolved.getStatus());
        assertEquals(ShortTermApplicationStatus.COMPLETED, application.getStatus());
        assertEquals(ShortTermJobStatus.COMPLETED, job.getStatus());
        assertEquals(JobEscrow.EscrowStatus.FULLY_RELEASED, escrow.getStatus());
        assertEquals(new BigDecimal("900000"), escrow.getPendingPayoutBalance());
        verify(escrowTransactionRepository, times(2)).save(any());
        verify(trustScoreService).triggerRecalculationOnDispute(2L);
        verify(trustScoreService).triggerRecalculationOnDispute(1L);
    }

    @Test
    @DisplayName("resolveDispute should dismiss NO_ACTION disputes and restore workflow")
    void resolveDispute_ShouldDismissNoActionAndRestoreWorkflow() {
        ShortTermJob job = job(100L, 1L, 2L);
        job.setStatus(ShortTermJobStatus.DISPUTED);
        ShortTermJobApplication application = application(job, 2L, ShortTermApplicationStatus.DISPUTE_OPENED, true);
        Dispute dispute = Dispute.builder()
                .id(88L)
                .shortTermJob(job)
                .application(application)
                .initiatorId(2L)
                .respondentId(1L)
                .status(Dispute.DisputeStatus.OPEN)
                .build();
        JobEscrow escrow = JobEscrow.builder()
                .id(11L)
                .job(job)
                .recruiterId(1L)
                .workerId(2L)
                .totalAmount(new BigDecimal("1000000"))
                .platformFee(new BigDecimal("100000"))
                .escrowBalance(new BigDecimal("1000000"))
                .status(JobEscrow.EscrowStatus.DISPUTED)
                .build();

        when(disputeRepository.findById(dispute.getId())).thenReturn(Optional.of(dispute));
        when(jobEscrowRepository.findByJobId(job.getId())).thenReturn(Optional.of(escrow));

        Dispute resolved = service.resolveDispute(900L, dispute.getId(),
                ResolveDisputeRequest.builder()
                        .resolution(Dispute.DisputeResolution.NO_ACTION)
                        .resolutionNotes("Dismissed")
                        .build());

        assertEquals(Dispute.DisputeStatus.DISMISSED, resolved.getStatus());
        assertEquals(ShortTermApplicationStatus.REVISION_REQUIRED, application.getStatus());
        assertEquals(ShortTermJobStatus.IN_PROGRESS, job.getStatus());
        assertEquals(JobEscrow.EscrowStatus.FUNDED, escrow.getStatus());
    }

    @Test
    @DisplayName("openDispute should prefer applicationId when frontend sends the wrong jobId")
    void openDispute_ShouldUseApplicationIdWhenJobIdIsWrong() {
        ShortTermJob job = job(100L, 1L, 2L);
        ShortTermJobApplication application = application(job, 2L, ShortTermApplicationStatus.SUBMITTED, true);
        OpenDisputeRequest request = OpenDisputeRequest.builder()
                .jobId(999L)
                .applicationId(application.getId())
                .disputeType(Dispute.DisputeType.WORKER_PROTECTION)
                .reason("Worker needs protection")
                .build();

        when(applicationRepository.findById(application.getId())).thenReturn(Optional.of(application));
        when(disputeRepository.findFirstByShortTermJobId(job.getId())).thenReturn(Optional.empty());
        when(jobEscrowRepository.findByJobId(job.getId())).thenReturn(Optional.empty());

        Dispute dispute = service.openDispute(2L, request);

        assertEquals(job.getId(), dispute.getShortTermJob().getId());
        assertEquals(application.getId(), dispute.getApplication().getId());
        assertEquals(ShortTermApplicationStatus.DISPUTE_OPENED, application.getStatus());
        assertEquals(ShortTermJobStatus.DISPUTED, job.getStatus());
    }

    private ShortTermJob job(Long jobId, Long recruiterId, Long workerId) {
        RecruiterProfile recruiterProfile = RecruiterProfile.builder()
                .userId(recruiterId)
                .companyName("SkillVerse Hiring")
                .companyWebsite("https://skillverse.vn")
                .companyAddress("HCMC")
                .taxCodeOrBusinessRegistrationNumber("TAX-1")
                .companyDocumentsUrl("docs")
                .contactPersonPosition("CEO")
                .companySize("11-50")
                .industry("IT")
                .build();
        return ShortTermJob.builder()
                .id(jobId)
                .title("Backend API Refactor")
                .description("Refactor APIs")
                .requiredSkills("[\"Java\"]")
                .budget(new BigDecimal("1000000"))
                .deadline(LocalDateTime.now().plusDays(7))
                .recruiterProfile(recruiterProfile)
                .selectedApplicantId(workerId)
                .status(ShortTermJobStatus.IN_PROGRESS)
                .build();
    }

    private ShortTermJobApplication application(ShortTermJob job, Long userId, ShortTermApplicationStatus status,
            boolean unlocked) {
        return ShortTermJobApplication.builder()
                .id(33L)
                .shortTermJob(job)
                .user(User.builder().id(userId).build())
                .status(status)
                .disputeEligibilityUnlocked(unlocked)
                .build();
    }
}
