package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.business_service.dto.request.ApplyJobRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobApplicationResponse;
import com.exe.skillverse_backend.business_service.entity.JobApplication;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.enums.JobApplicationStatus;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.service.impl.JobApplicationServiceImpl;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for JobApplicationService
 * 
 * Mục đích: Kiểm thử các logic nghiệp vụ liên quan đến việc Ứng tuyển (Apply)
 * và Quản lý đơn ứng tuyển.
 * Sử dụng Mockito để giả lập Database (Repository), giúp test chạy nhanh và cô
 * lập logic.
 */
@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    @Mock
    private JobApplicationRepository jobApplicationRepository; // Giả lập DB cho JobApplication

    @Mock
    private JobPostingRepository jobPostingRepository; // Giả lập DB cho JobPosting

    @Mock
    private UserRepository userRepository; // Giả lập DB cho User

    @InjectMocks
    private JobApplicationServiceImpl jobApplicationService; // Service cần test (được tiêm các Mock vào)

    // Các biến dữ liệu mẫu dùng chung cho các test case
    private User applicant;
    private User recruiter;
    private RecruiterProfile recruiterProfile;
    private JobPosting jobPosting;
    private JobApplication jobApplication;
    private ApplyJobRequest applyRequest;

    /**
     * Cài đặt dữ liệu mẫu trước mỗi lần chạy test (@BeforeEach).
     * Đảm bảo môi trường test luôn sạch sẽ và nhất quán.
     */
    @BeforeEach
    void setUp() {
        // 1. Tạo User ứng viên giả (ID: 1)
        applicant = new User();
        applicant.setId(1L);
        applicant.setEmail("applicant@test.com");
        applicant.setFirstName("John");
        applicant.setLastName("Doe");

        // 2. Tạo User nhà tuyển dụng giả (ID: 2)
        recruiter = new User();
        recruiter.setId(2L);
        recruiter.setEmail("recruiter@test.com");

        // 3. Tạo Profile công ty giả cho nhà tuyển dụng
        recruiterProfile = new RecruiterProfile();
        recruiterProfile.setUser(recruiter);
        recruiterProfile.setCompanyName("Test Company");

        // 4. Tạo Job giả (ID: 100)
        jobPosting = JobPosting.builder()
                .id(100L)
                .title("Java Developer")
                .status(JobStatus.OPEN) // Job đang mở
                .recruiterProfile(recruiterProfile)
                .applicantCount(0)
                .minBudget(java.math.BigDecimal.valueOf(1000))
                .maxBudget(java.math.BigDecimal.valueOf(2000))
                .isRemote(true)
                .build();

        // 5. Tạo Đơn ứng tuyển giả (ID: 500)
        jobApplication = JobApplication.builder()
                .id(500L)
                .user(applicant)
                .jobPosting(jobPosting)
                .status(JobApplicationStatus.PENDING)
                .coverLetter("I am interested")
                .appliedAt(LocalDateTime.now())
                .build();

        // 6. Tạo Request body giả
        applyRequest = new ApplyJobRequest();
        applyRequest.setCoverLetter("I am interested");
    }

    /**
     * Test Case 1: Ứng tuyển thành công (Happy Path)
     * Kịch bản: Job tồn tại, đang OPEN, user chưa từng apply.
     * Mong đợi: Lưu thành công, trả về thông tin đơn, tăng số lượng applicantCount
     * của Job.
     */
    @Test
    void applyToJob_Success() {
        // Arrange (Chuẩn bị)
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.of(jobPosting));
        when(jobApplicationRepository.existsByJobPostingIdAndUserId(100L, 1L)).thenReturn(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(applicant));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenReturn(jobApplication);

        // Act (Thực hiện)
        JobApplicationResponse response = jobApplicationService.applyToJob(1L, 100L, applyRequest);

        // Assert (Kiểm tra)
        assertNotNull(response);
        assertEquals(100L, response.getJobId());
        assertEquals(1L, response.getUserId());
        assertEquals(JobApplicationStatus.PENDING, response.getStatus());

        // Verify: Kiểm tra xem job đã được lưu lại với số lượng ứng viên tăng lên chưa
        verify(jobPostingRepository).save(jobPosting);
        assertEquals(1, jobPosting.getApplicantCount());
    }

    /**
     * Test Case 2: Lỗi khi Job không tồn tại
     * Mong đợi: Ném lỗi NotFoundException.
     */
    @Test
    void applyToJob_Fail_JobNotFound() {
        when(jobPostingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> jobApplicationService.applyToJob(1L, 999L, applyRequest));
    }

    /**
     * Test Case 3: Lỗi khi Job đã đóng (CLOSED)
     * Mong đợi: Ném lỗi IllegalStateException "Can only apply to OPEN jobs".
     */
    @Test
    void applyToJob_Fail_JobNotOpen() {
        jobPosting.setStatus(JobStatus.CLOSED); // Giả lập job đã đóng
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.of(jobPosting));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jobApplicationService.applyToJob(1L, 100L, applyRequest));
        assertEquals("Can only apply to OPEN jobs", exception.getMessage());
    }

    /**
     * Test Case 4: Lỗi khi User đã ứng tuyển trước đó (Duplicate)
     * Mong đợi: Ném lỗi IllegalStateException "You have already applied...".
     */
    @Test
    void applyToJob_Fail_AlreadyApplied() {
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.of(jobPosting));
        when(jobApplicationRepository.existsByJobPostingIdAndUserId(100L, 1L)).thenReturn(true); // Đã apply rồi

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jobApplicationService.applyToJob(1L, 100L, applyRequest));
        assertEquals("You have already applied to this job", exception.getMessage());
    }

    /**
     * Test Case 5: Lỗi khi Nhà tuyển dụng tự ứng tuyển vào job của mình
     * Mong đợi: Ném lỗi IllegalStateException "Recruiters cannot apply to their
     * own...".
     */
    @Test
    void applyToJob_Fail_RecruiterSelfApply() {
        // User ID 2 là recruiter sở hữu job này
        when(jobPostingRepository.findById(100L)).thenReturn(Optional.of(jobPosting));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> jobApplicationService.applyToJob(2L, 100L, applyRequest));
        assertEquals("Recruiters cannot apply to their own job postings", exception.getMessage());
    }

    /**
     * Test Case 6: Lấy danh sách đơn ứng tuyển của User (Applicant)
     * Mong đợi: Trả về danh sách chứa đơn ứng tuyển ID 500.
     */
    @Test
    void getMyApplications_Success() {
        when(jobApplicationRepository.findByUserIdWithJobAndRecruiterOrderByAppliedAtDesc(1L))
                .thenReturn(List.of(jobApplication));

        List<JobApplicationResponse> responses = jobApplicationService.getMyApplications(1L);

        assertFalse(responses.isEmpty());
        assertEquals(1, responses.size());
        assertEquals(100L, responses.get(0).getJobId());
    }

    /**
     * Test Case 7: Nhà tuyển dụng xem danh sách ứng viên (Happy Path)
     * Mong đợi: Trả về danh sách ứng viên nếu user là chủ sở hữu job.
     */
    @Test
    void getJobApplicants_Success() {
        // User ID 2 là chủ job -> Có quyền xem
        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(100L, 2L))
                .thenReturn(Optional.of(jobPosting));
        when(jobApplicationRepository.findByJobPostingIdWithUserOrderByAppliedAtDesc(100L))
                .thenReturn(List.of(jobApplication));

        List<JobApplicationResponse> responses = jobApplicationService.getJobApplicants(2L, 100L);

        assertFalse(responses.isEmpty());
        assertEquals(1L, responses.get(0).getUserId()); // Kiểm tra ID người ứng tuyển
    }

    /**
     * Test Case 8: Người lạ cố tình xem danh sách ứng viên (Security Check)
     * Mong đợi: Ném lỗi NotFoundException (Giả vờ không tìm thấy job hoặc không có
     * quyền).
     */
    @Test
    void getJobApplicants_Fail_NotOwner() {
        // User ID 1 (Applicant) cố xem danh sách ứng viên -> Không tìm thấy
        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(100L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> jobApplicationService.getJobApplicants(1L, 100L));
    }
}
