package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.entity.JobEscrow;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.ShortTermJob;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermJobStatus;
import com.exe.skillverse_backend.business_service.repository.EscrowTransactionRepository;
import com.exe.skillverse_backend.business_service.repository.JobEscrowRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.repository.ShortTermJobRepository;
import com.exe.skillverse_backend.business_service.service.impl.EscrowServiceImpl;
import com.exe.skillverse_backend.notification_service.service.NotificationService;
import com.exe.skillverse_backend.shared.exception.ForbiddenException;
import com.exe.skillverse_backend.wallet_service.entity.WalletTransaction;
import com.exe.skillverse_backend.wallet_service.repository.WalletTransactionRepository;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EscrowServiceImplTest {

    @Mock
    private JobEscrowRepository jobEscrowRepository;

    @Mock
    private EscrowTransactionRepository escrowTransactionRepository;

    @Mock
    private ShortTermJobRepository shortTermJobRepository;

    @Mock
    private RecruiterProfileRepository recruiterProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private NotificationService notificationService;

    private EscrowServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new EscrowServiceImpl(
                jobEscrowRepository,
                escrowTransactionRepository,
                shortTermJobRepository,
                recruiterProfileRepository,
                userRepository,
                walletTransactionRepository,
                walletService,
                notificationService);
        lenient().when(jobEscrowRepository.save(any(JobEscrow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(shortTermJobRepository.save(any(ShortTermJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("fundEscrow should reject recruiters who do not own the job")
    void fundEscrow_ShouldRejectRecruitersWhoDoNotOwnTheJob() {
        ShortTermJob job = job(101L, 1L, 2L);
        RecruiterProfile otherProfile = recruiterProfile(9L);
        when(shortTermJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(recruiterProfileRepository.findByUserId(9L)).thenReturn(Optional.of(otherProfile));

        assertThrows(ForbiddenException.class, () -> service.fundEscrow(job.getId(), 9L));
    }

    @Test
    @DisplayName("fundEscrow should freeze the wallet and create a funded escrow")
    void fundEscrow_ShouldFreezeWalletAndCreateFundedEscrow() {
        ShortTermJob job = job(101L, 1L, 2L);
        RecruiterProfile profile = recruiterProfile(1L);
        when(shortTermJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
        when(recruiterProfileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(jobEscrowRepository.existsByJobId(job.getId())).thenReturn(false);
        when(walletService.hasAvailableCash(1L, new BigDecimal("1000000"))).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).firstName("Recruiter").lastName("One").build()));

        JobEscrow escrow = service.fundEscrow(job.getId(), 1L);

        assertEquals(JobEscrow.EscrowStatus.FUNDED, escrow.getStatus());
        assertEquals(new BigDecimal("100000.00"), escrow.getPlatformFee());
        verify(walletService).freezeCashForBooking(eq(1L), eq(new BigDecimal("1000000")), eq(job.getId()), anyString());
        verify(notificationService).createNotification(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("releaseEscrow should charge frozen funds and transfer net payout to the worker")
    void releaseEscrow_ShouldChargeFrozenFundsAndTransferNetPayoutToWorker() {
        ShortTermJob job = job(101L, 1L, 2L);
        JobEscrow escrow = JobEscrow.builder()
                .id(20L)
                .job(job)
                .recruiterId(1L)
                .workerId(2L)
                .totalAmount(new BigDecimal("1000000"))
                .platformFee(new BigDecimal("100000"))
                .escrowBalance(new BigDecimal("1000000"))
                .status(JobEscrow.EscrowStatus.FUNDED)
                .build();

        when(jobEscrowRepository.findByJobId(job.getId())).thenReturn(Optional.of(escrow));
        when(userRepository.findById(1L)).thenReturn(Optional.of(User.builder().id(1L).firstName("Recruiter").lastName("One").build()));

        JobEscrow released = service.releaseEscrow(job.getId(), 1L, "Job completed");

        assertEquals(JobEscrow.EscrowStatus.FULLY_RELEASED, released.getStatus());
        assertEquals(new BigDecimal("0"), released.getEscrowBalance());
        assertEquals(new BigDecimal("900000"), released.getPendingPayoutBalance());
        verify(walletService).chargeFrozenForBooking(eq(1L), eq(new BigDecimal("1000000")), eq(job.getId()), anyString());
        verify(walletService).payMentorForJobPayout(2L, new BigDecimal("900000"), job.getId());
        verify(escrowTransactionRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("releasePendingPayouts should skip already-paid payouts and clear pending balance")
    void releasePendingPayouts_ShouldSkipAlreadyPaidPayoutsAndClearPendingBalance() {
        ShortTermJob job = job(101L, 1L, 2L);
        JobEscrow escrow = JobEscrow.builder()
                .id(30L)
                .job(job)
                .workerId(2L)
                .pendingPayoutBalance(new BigDecimal("900000"))
                .status(JobEscrow.EscrowStatus.FULLY_RELEASED)
                .build();

        when(jobEscrowRepository.findByStatusIn(anyList())).thenReturn(List.of(escrow));
        when(walletTransactionRepository.existsByReferenceIdAndReferenceTypeAndStatus(
                "JOB_" + job.getId(),
                "JOB_PAYOUT",
                WalletTransaction.TransactionStatus.COMPLETED))
                .thenReturn(true);

        service.releasePendingPayouts();

        assertEquals(new BigDecimal("0"), escrow.getPendingPayoutBalance());
        verify(walletService, never()).payMentorForBooking(any(), any(), any());
        verify(jobEscrowRepository).save(escrow);
    }

    private RecruiterProfile recruiterProfile(Long userId) {
        return RecruiterProfile.builder()
                .userId(userId)
                .companyName("SkillVerse Hiring")
                .companyWebsite("https://skillverse.vn")
                .companyAddress("HCMC")
                .taxCodeOrBusinessRegistrationNumber("TAX-1")
                .companyDocumentsUrl("docs")
                .contactPersonPosition("CEO")
                .companySize("11-50")
                .industry("IT")
                .build();
    }

    private ShortTermJob job(Long jobId, Long recruiterId, Long workerId) {
        return ShortTermJob.builder()
                .id(jobId)
                .title("Backend API Refactor")
                .description("Refactor APIs")
                .requiredSkills("[\"Java\"]")
                .budget(new BigDecimal("1000000"))
                .deadline(LocalDateTime.now().plusDays(7))
                .recruiterProfile(recruiterProfile(recruiterId))
                .selectedApplicantId(workerId)
                .status(ShortTermJobStatus.IN_PROGRESS)
                .build();
    }
}
