package com.exe.skillverse_backend.admin_service.service;

import com.exe.skillverse_backend.admin_service.service.impl.AdminJobServiceImpl;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.business_service.dto.response.JobPostingResponse;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * =========================================================================================
 * HƯỚNG DẪN CHẠY TEST (TESTING GUIDE)
 * =========================================================================================
 * 
 * 1. Chạy toàn bộ test trong file này:
 * mvn test -Dtest=AdminJobServiceTest
 * 
 * 2. Chạy một test case cụ thể (ví dụ: test duyệt job thành công):
 * mvn test -Dtest=AdminJobServiceTest#approveJob_Success
 * 
 * 3. Chạy test với debug logs:
 * mvn test -Dtest=AdminJobServiceTest -X
 * 
 * =========================================================================================
 * DANH SÁCH TEST CASES (9 Cases)
 * =========================================================================================
 * 
 * Happy Path (Luồng chính):
 * - approveJob_Success: Admin duyệt tin thành công (PENDING -> OPEN).
 * - rejectJob_Success: Admin từ chối tin thành công (PENDING -> REJECTED, hoàn
 * tiền).
 * - getPendingJobs_Success: Lấy danh sách tin chờ duyệt.
 * 
 * Unhappy Path (Các lỗi có thể xảy ra):
 * - approveJob_NotFound: Duyệt tin không tồn tại.
 * - approveJob_InvalidStatus: Duyệt tin sai trạng thái (ví dụ đã OPEN rồi).
 * - rejectJob_WalletError: Lỗi hoàn tiền khi từ chối tin.
 * - rejectJob_NotFound: Từ chối tin không tồn tại.
 * - rejectJob_InvalidStatus: Từ chối tin sai trạng thái.
 * 
 * Advanced Edge Cases (Nâng cao):
 * - getPendingJobs_Empty: Xử lý khi danh sách chờ duyệt rỗng (trả về list rỗng
 * thay vì null/lỗi).
 * =========================================================================================
 */
@ExtendWith(MockitoExtension.class)
public class AdminJobServiceTest {

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AdminJobServiceImpl adminJobService;

    private JobPosting job;
    private RecruiterProfile recruiterProfile;

    @BeforeEach
    void setUp() {
        recruiterProfile = new RecruiterProfile();
        recruiterProfile.setUserId(100L);
        recruiterProfile.setCompanyName("Test Corp");
        User user = new User();
        user.setId(100L);
        user.setEmail("test@corp.com");
        recruiterProfile.setUser(user);

        job = new JobPosting();
        job.setId(1L);
        job.setTitle("Test Job");
        job.setDescription("Description");
        job.setRequiredSkills("[\"java\"]");
        job.setRecruiterProfile(recruiterProfile);
        job.setStatus(JobStatus.PENDING_APPROVAL);
        job.setApplicantCount(0);
        job.setMinBudget(new BigDecimal("1000"));
        job.setMaxBudget(new BigDecimal("2000"));
        job.setDeadline(LocalDate.now().plusDays(10));
        job.setIsRemote(true);
    }

    // 1. Case: Duyệt tin tuyển dụng thành công
    // Input: Job tồn tại, status PENDING_APPROVAL
    // Expected: Job status chuyển sang OPEN
    @Test
    void approveJob_Success() throws Exception {
        when(jobPostingRepository.findByIdWithRecruiter(1L)).thenReturn(Optional.of(job));
        when(jobPostingRepository.save(any(JobPosting.class))).thenReturn(job);
        when(objectMapper.readValue(anyString(), eq(String[].class))).thenReturn(new String[] { "java" });

        JobPostingResponse response = adminJobService.approveJob(1L);

        assertNotNull(response);
        assertEquals(JobStatus.OPEN, job.getStatus());
        verify(jobPostingRepository).save(job);
    }

    // 2. Case: Duyệt tin thất bại do không tìm thấy
    // Input: JobId không tồn tại
    // Expected: Ném ra NotFoundException
    @Test
    void approveJob_NotFound() {
        when(jobPostingRepository.findByIdWithRecruiter(999L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> adminJobService.approveJob(999L));
    }

    // 3. Case: Duyệt tin thất bại do sai trạng thái (không phải PENDING)
    // Input: Job status là OPEN (đã duyệt rồi)
    // Expected: Ném ra IllegalStateException
    @Test
    void approveJob_InvalidStatus() {
        job.setStatus(JobStatus.OPEN);
        when(jobPostingRepository.findByIdWithRecruiter(1L)).thenReturn(Optional.of(job));
        assertThrows(IllegalStateException.class, () -> adminJobService.approveJob(1L));
    }

    // 4. Case: Từ chối tin tuyển dụng thành công (hoàn tiền)
    // Input: Job status PENDING_APPROVAL
    // Expected: Job status chuyển sang REJECTED, gọi walletService hoàn tiền
    @Test
    void rejectJob_Success() throws Exception {
        when(jobPostingRepository.findByIdWithRecruiter(1L)).thenReturn(Optional.of(job));
        when(jobPostingRepository.save(any(JobPosting.class))).thenReturn(job);
        when(objectMapper.readValue(anyString(), eq(String[].class))).thenReturn(new String[] { "java" });

        JobPostingResponse response = adminJobService.rejectJob(1L, "Violation");

        assertNotNull(response);
        assertEquals(JobStatus.REJECTED, job.getStatus());
        verify(walletService).processRefund(eq(100L), eq(new BigDecimal("50000")), contains("Refund"), eq("1"));
        verify(jobPostingRepository).save(job);
    }

    // 5. Case: Từ chối tin thất bại do lỗi hoàn tiền
    // Input: WalletService ném lỗi khi hoàn tiền
    // Expected: Ném ra IllegalStateException
    @Test
    void rejectJob_WalletError() {
        when(jobPostingRepository.findByIdWithRecruiter(1L)).thenReturn(Optional.of(job));
        doThrow(new RuntimeException("Refund failed")).when(walletService).processRefund(any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> adminJobService.rejectJob(1L, "Violation"));
    }

    // 6. Case: Lấy danh sách tin chờ duyệt
    // Input: Có tin tuyển dụng trạng thái PENDING_APPROVAL
    // Expected: Trả về danh sách JobPostingResponse
    @Test
    void getPendingJobs_Success() {
        when(jobPostingRepository.findByStatusWithRecruiterOrderByCreatedAtDesc(JobStatus.PENDING_APPROVAL))
                .thenReturn(List.of(job));
        // Mock object mapper if needed inside mapToResponse -> convertJsonToSkills
        // mapToResponse calls convertJsonToSkills which calls objectMapper.readValue
        // We need to handle the potential checked exception in lambda or setup safe
        // mock
        try {
            when(objectMapper.readValue(anyString(), eq(String[].class))).thenReturn(new String[] { "java" });
        } catch (Exception e) {
            // Should not happen in setup
        }

        List<JobPostingResponse> responses = adminJobService.getPendingJobs();

        assertNotNull(responses);
        assertFalse(responses.isEmpty());
        assertEquals(1, responses.size());
        assertEquals(job.getId(), responses.get(0).getId());
    }

    // 7. Case: Từ chối tin thất bại do không tìm thấy
    // Input: JobId không tồn tại
    // Expected: Ném ra NotFoundException
    @Test
    void rejectJob_NotFound() {
        when(jobPostingRepository.findByIdWithRecruiter(999L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> adminJobService.rejectJob(999L, "Reason"));
    }

    // 8. Case: Từ chối tin thất bại do sai trạng thái
    // Input: Job status không phải PENDING_APPROVAL (ví dụ OPEN)
    // Expected: Ném ra IllegalStateException
    @Test
    void rejectJob_InvalidStatus() {
        job.setStatus(JobStatus.OPEN);
        when(jobPostingRepository.findByIdWithRecruiter(1L)).thenReturn(Optional.of(job));
        assertThrows(IllegalStateException.class, () -> adminJobService.rejectJob(1L, "Reason"));
    }

    // 9. Case: Danh sách chờ duyệt rỗng
    // Input: Không có job nào status PENDING_APPROVAL
    // Expected: Trả về danh sách rỗng
    @Test
    void getPendingJobs_Empty() {
        when(jobPostingRepository.findByStatusWithRecruiterOrderByCreatedAtDesc(JobStatus.PENDING_APPROVAL))
                .thenReturn(List.of());

        List<JobPostingResponse> responses = adminJobService.getPendingJobs();

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }
}
