package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.business_service.dto.request.CreateJobRequest;
import com.exe.skillverse_backend.business_service.dto.request.UpdateJobRequest;
import com.exe.skillverse_backend.business_service.dto.response.JobPostingResponse;
import com.exe.skillverse_backend.business_service.entity.JobPosting;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.entity.enums.JobStatus;
import com.exe.skillverse_backend.business_service.repository.JobApplicationRepository;
import com.exe.skillverse_backend.business_service.repository.JobPostingRepository;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.service.impl.JobPostingServiceImpl;
import com.exe.skillverse_backend.shared.exception.NotFoundException;
import com.exe.skillverse_backend.wallet_service.service.WalletService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
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
 * mvn test -Dtest=JobPostingServiceTest
 * 
 * 2. Chạy một test case cụ thể (ví dụ: test tạo job thành công):
 * mvn test -Dtest=JobPostingServiceTest#createJob_Success
 * 
 * 3. Chạy test với debug logs (chi tiết lỗi):
 * mvn test -Dtest=JobPostingServiceTest -X
 * 
 * =========================================================================================
 * DANH SÁCH TEST CASES (20 Cases)
 * =========================================================================================
 * 
 * Happy Path (Luồng chính):
 * - createJob_Success: Tạo tin thành công, trừ tiền ví.
 * - updateJob_Success: Cập nhật tin thành công.
 * - changeStatus_Success: Đổi trạng thái tin thành công.
 * - createJob_NullSkills: Tạo tin với danh sách kỹ năng rỗng (vẫn hợp lệ).
 * - changeStatus_OpenToClosed: Đóng tin tuyển dụng (OPEN -> CLOSED).
 * 
 * Unhappy Path (Các lỗi có thể xảy ra):
 * - createJob_InsufficientFunds: Ví không đủ tiền.
 * - createJob_RecruiterNotFound: Không tìm thấy profile nhà tuyển dụng.
 * - createJob_InvalidBudget: Ngân sách min > max.
 * - createJob_MissingLocation: Thiếu địa điểm khi job không phải remote.
 * - createJob_EmptyLocation: Địa điểm là chuỗi rỗng.
 * - createJob_JsonError: Lỗi xử lý JSON.
 * - createJob_WalletGenericError: Lỗi hệ thống ví.
 * - updateJob_NotFound: Không tìm thấy job để update.
 * - updateJob_WhileOpen: Không được update khi job đang OPEN.
 * - changeStatus_ClosedJob: Không được đổi trạng thái khi job đã CLOSED.
 * - updateJob_InvalidBudget: Update ngân sách không hợp lệ.
 * - updateJob_MissingLocation: Update thiếu địa điểm.
 * - changeStatus_NotFound: Không tìm thấy job để đổi trạng thái.
 * 
 * Advanced Edge Cases (Nâng cao):
 * - createJob_SkillsNormalization: Chuẩn hóa kỹ năng (trim, lower case).
 * - updateJob_PartialUpdate: Cập nhật một phần (chỉ update field có gửi lên).
 * =========================================================================================
 */
@ExtendWith(MockitoExtension.class)
public class JobPostingServiceTest {

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private RecruiterProfileRepository recruiterProfileRepository;

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private WalletService walletService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private JobPostingServiceImpl jobPostingService;

    private RecruiterProfile recruiterProfile;
    private CreateJobRequest createJobRequest;

    @BeforeEach
    void setUp() {
        recruiterProfile = new RecruiterProfile();
        recruiterProfile.setUserId(100L);
        recruiterProfile.setCompanyName("Test Company");

        User user = new User();
        user.setId(100L);
        user.setEmail("test@company.com");
        recruiterProfile.setUser(user);

        createJobRequest = CreateJobRequest.builder()
                .title("Java Developer")
                .description("Java Job")
                .requiredSkills(List.of("Java", "Spring"))
                .minBudget(new BigDecimal("1000"))
                .maxBudget(new BigDecimal("2000"))
                .deadline(LocalDate.now().plusDays(30))
                .isRemote(true)
                .experienceLevel("Junior")
                .jobType("FULL_TIME")
                .hiringQuantity(5)
                .benefits("Free lunch")
                .genderRequirement("ANY")
                .isNegotiable(false)
                .build();
    }

    // 1. Case: Tạo tin tuyển dụng thành công
    // Input: Dữ liệu hợp lệ, ví đủ tiền
    // Expected: Trả về tin tuyển dụng với trạng thái PENDING_APPROVAL, ví bị trừ
    // tiền
    @Test
    void createJob_Success() throws Exception {
        when(recruiterProfileRepository.findByUserId(100L)).thenReturn(Optional.of(recruiterProfile));
        when(objectMapper.writeValueAsString(any())).thenReturn("[\"java\",\"spring\"]");

        JobPosting savedJob = new JobPosting();
        savedJob.setId(1L);
        savedJob.setTitle("Java Developer");
        savedJob.setRecruiterProfile(recruiterProfile);
        savedJob.setStatus(JobStatus.PENDING_APPROVAL);
        savedJob.setRequiredSkills("[\"java\",\"spring\"]");
        savedJob.setExperienceLevel("Junior");
        savedJob.setJobType("FULL_TIME");
        savedJob.setHiringQuantity(5);
        savedJob.setBenefits("Free lunch");
        savedJob.setGenderRequirement("ANY");
        savedJob.setIsNegotiable(false);

        when(jobPostingRepository.save(any(JobPosting.class))).thenReturn(savedJob);
        when(objectMapper.readValue(anyString(), eq(String[].class))).thenReturn(new String[] { "java", "spring" });

        JobPostingResponse response = jobPostingService.createJob(100L, createJobRequest);

        assertNotNull(response);
        assertEquals(JobStatus.PENDING_APPROVAL, response.getStatus());
        assertEquals("Junior", response.getExperienceLevel());
        assertEquals("FULL_TIME", response.getJobType());
        assertEquals(5, response.getHiringQuantity());
        assertEquals("Free lunch", response.getBenefits());
        verify(walletService).deductCash(eq(100L), eq(new BigDecimal("50000")), anyString(), eq("JOB_POSTING"),
                anyString());
    }

    // 2. Case: Tạo tin thất bại do không đủ tiền
    // Input: Ví không đủ số dư
    // Expected: Ném ra IllegalStateException
    @Test
    void createJob_InsufficientFunds() {
        when(recruiterProfileRepository.findByUserId(100L)).thenReturn(Optional.of(recruiterProfile));
        JobPosting savedJob = new JobPosting();
        savedJob.setId(1L);
        when(jobPostingRepository.save(any(JobPosting.class))).thenReturn(savedJob);

        doThrow(new IllegalStateException("Insufficient funds"))
                .when(walletService).deductCash(any(), any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> jobPostingService.createJob(100L, createJobRequest));
    }

    // 3. Case: Không tìm thấy hồ sơ nhà tuyển dụng
    // Input: userId không tồn tại trong bảng RecruiterProfile
    // Expected: Ném ra NotFoundException
    @Test
    void createJob_RecruiterNotFound() {
        when(recruiterProfileRepository.findByUserId(999L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> jobPostingService.createJob(999L, createJobRequest));
    }

    // 4. Case: Ngân sách không hợp lệ
    // Input: Min budget > Max budget
    // Expected: Ném ra IllegalArgumentException
    @Test
    void createJob_InvalidBudget() {
        createJobRequest.setMinBudget(new BigDecimal("3000"));
        createJobRequest.setMaxBudget(new BigDecimal("2000"));
        assertThrows(IllegalArgumentException.class, () -> jobPostingService.createJob(100L, createJobRequest));
    }

    // 5. Case: Thiếu địa điểm cho việc làm offline
    // Input: isRemote = false, location = null
    // Expected: Ném ra IllegalArgumentException
    @Test
    void createJob_MissingLocation() {
        createJobRequest.setIsRemote(false);
        createJobRequest.setLocation(null);
        assertThrows(IllegalArgumentException.class, () -> jobPostingService.createJob(100L, createJobRequest));
    }

    // 6. Case: Địa điểm rỗng
    // Input: isRemote = false, location = " "
    // Expected: Ném ra IllegalArgumentException
    @Test
    void createJob_EmptyLocation() {
        createJobRequest.setIsRemote(false);
        createJobRequest.setLocation("   ");
        assertThrows(IllegalArgumentException.class, () -> jobPostingService.createJob(100L, createJobRequest));
    }

    // 7. Case: Danh sách kỹ năng rỗng
    // Input: requiredSkills = empty list
    // Expected: Tạo thành công với danh sách kỹ năng rỗng
    @Test
    void createJob_NullSkills() throws Exception {
        createJobRequest.setRequiredSkills(Collections.emptyList());

        when(recruiterProfileRepository.findByUserId(100L)).thenReturn(Optional.of(recruiterProfile));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        JobPosting savedJob = new JobPosting();
        savedJob.setId(1L);
        savedJob.setRecruiterProfile(recruiterProfile);
        savedJob.setRequiredSkills("[]");
        when(jobPostingRepository.save(any(JobPosting.class))).thenReturn(savedJob);
        when(objectMapper.readValue(anyString(), eq(String[].class))).thenReturn(new String[] {});

        assertDoesNotThrow(() -> jobPostingService.createJob(100L, createJobRequest));
    }

    // 8. Case: Lỗi xử lý JSON
    // Input: ObjectMapper ném lỗi khi convert
    // Expected: Ném ra RuntimeException
    @Test
    void createJob_JsonError() throws Exception {
        when(recruiterProfileRepository.findByUserId(100L)).thenReturn(Optional.of(recruiterProfile));
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("Error") {
                });

        assertThrows(RuntimeException.class, () -> jobPostingService.createJob(100L, createJobRequest));
    }

    // 9. Case: Lỗi hệ thống ví
    // Input: WalletService ném RuntimeException
    // Expected: Ném ra IllegalStateException
    @Test
    void createJob_WalletGenericError() {
        when(recruiterProfileRepository.findByUserId(100L)).thenReturn(Optional.of(recruiterProfile));
        JobPosting savedJob = new JobPosting();
        savedJob.setId(1L);
        when(jobPostingRepository.save(any(JobPosting.class))).thenReturn(savedJob);

        doThrow(new RuntimeException("Database error"))
                .when(walletService).deductCash(any(), any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> jobPostingService.createJob(100L, createJobRequest));
    }

    // 10. Case: Cập nhật tin tuyển dụng thành công
    // Input: Job tồn tại, status IN_PROGRESS/PENDING, user sở hữu job
    // Expected: Job được cập nhật thông tin mới
    @Test
    void updateJob_Success() throws Exception {
        UpdateJobRequest updateRequest = new UpdateJobRequest();
        updateRequest.setTitle("Updated Title");
        updateRequest.setMinBudget(new BigDecimal("1500"));
        updateRequest.setMaxBudget(new BigDecimal("2500"));
        updateRequest.setExperienceLevel("Senior");
        updateRequest.setIsNegotiable(true);

        JobPosting existingJob = new JobPosting();
        existingJob.setId(1L);
        existingJob.setTitle("Old Title");
        existingJob.setStatus(JobStatus.PENDING_APPROVAL);
        existingJob.setRecruiterProfile(recruiterProfile);
        existingJob.setMinBudget(new BigDecimal("1000"));
        existingJob.setMaxBudget(new BigDecimal("2000"));
        existingJob.setIsRemote(true);
        existingJob.setRequiredSkills("[]");
        existingJob.setExperienceLevel("Junior");
        existingJob.setIsNegotiable(false);

        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.of(existingJob));
        when(jobPostingRepository.save(any(JobPosting.class))).thenReturn(existingJob);
        when(objectMapper.readValue(anyString(), eq(String[].class))).thenReturn(new String[] {});

        JobPostingResponse response = jobPostingService.updateJob(100L, 1L, updateRequest);

        assertEquals("Updated Title", existingJob.getTitle());
        assertEquals(new BigDecimal("1500"), existingJob.getMinBudget());
        assertEquals("Senior", existingJob.getExperienceLevel());
        assertTrue(existingJob.getIsNegotiable());
    }

    // 11. Case: Cập nhật tin thất bại do không tìm thấy
    // Input: JobId không tồn tại hoặc không thuộc user
    // Expected: Ném ra NotFoundException
    @Test
    void updateJob_NotFound() {
        UpdateJobRequest updateRequest = new UpdateJobRequest();
        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> jobPostingService.updateJob(100L, 1L, updateRequest));
    }

    // 12. Case: Cập nhật tin khi đang OPEN (không được phép)
    // Input: Job status là OPEN
    // Expected: Ném ra IllegalStateException
    @Test
    void updateJob_WhileOpen() {
        UpdateJobRequest updateRequest = new UpdateJobRequest();
        JobPosting existingJob = new JobPosting();
        existingJob.setStatus(JobStatus.OPEN);

        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.of(existingJob));

        assertThrows(IllegalStateException.class, () -> jobPostingService.updateJob(100L, 1L, updateRequest));
    }

    // 13. Case: Thay đổi trạng thái tin thành công
    // Input: Chuyển từ PENDING -> OPEN (hoặc status khác hợp lệ)
    // Expected: Trạng thái job thay đổi
    @Test
    void changeStatus_Success() throws Exception {
        JobPosting existingJob = new JobPosting();
        existingJob.setId(1L);
        existingJob.setStatus(JobStatus.PENDING_APPROVAL);
        existingJob.setRecruiterProfile(recruiterProfile);
        existingJob.setRequiredSkills("[]");

        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.of(existingJob));
        when(jobPostingRepository.save(any(JobPosting.class))).thenReturn(existingJob);
        when(objectMapper.readValue(anyString(), eq(String[].class))).thenReturn(new String[] {});

        JobPostingResponse response = jobPostingService.changeStatus(100L, 1L, JobStatus.OPEN);

        assertEquals(JobStatus.OPEN, response.getStatus());
    }

    // 14. Case: Thay đổi trạng thái tin đã đóng (CLOSED)
    // Input: Job đang ở trạng thái CLOSED
    // Expected: Ném ra IllegalStateException (trừ khi reopen)
    @Test
    void changeStatus_ClosedJob() {
        JobPosting existingJob = new JobPosting();
        existingJob.setStatus(JobStatus.CLOSED);

        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.of(existingJob));

        assertThrows(IllegalStateException.class, () -> jobPostingService.changeStatus(100L, 1L, JobStatus.OPEN));
    }

    // 15. Case: Cập nhật tin thất bại do ngân sách không hợp lệ
    // Input: Min budget > Max budget
    // Expected: Ném ra IllegalArgumentException
    @Test
    void updateJob_InvalidBudget() {
        UpdateJobRequest updateRequest = new UpdateJobRequest();
        updateRequest.setMinBudget(new BigDecimal("3000"));
        updateRequest.setMaxBudget(new BigDecimal("2000"));

        JobPosting existingJob = new JobPosting();
        existingJob.setId(1L);
        existingJob.setStatus(JobStatus.PENDING_APPROVAL);
        existingJob.setRecruiterProfile(recruiterProfile);
        existingJob.setMinBudget(new BigDecimal("1000"));
        existingJob.setMaxBudget(new BigDecimal("2000"));

        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.of(existingJob));

        assertThrows(IllegalArgumentException.class, () -> jobPostingService.updateJob(100L, 1L, updateRequest));
    }

    // 16. Case: Cập nhật tin thất bại do thiếu địa điểm (khi chuyển sang offline)
    // Input: isRemote = false, location = null/empty
    // Expected: Ném ra IllegalArgumentException
    @Test
    void updateJob_MissingLocation() {
        UpdateJobRequest updateRequest = new UpdateJobRequest();
        updateRequest.setIsRemote(false);
        updateRequest.setLocation(null);

        JobPosting existingJob = new JobPosting();
        existingJob.setId(1L);
        existingJob.setStatus(JobStatus.PENDING_APPROVAL);
        existingJob.setRecruiterProfile(recruiterProfile);
        existingJob.setIsRemote(true); // Was remote
        existingJob.setMinBudget(new BigDecimal("1000")); // Ensure budget is set to avoid NPE
        existingJob.setMaxBudget(new BigDecimal("2000"));

        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.of(existingJob));

        assertThrows(IllegalArgumentException.class, () -> jobPostingService.updateJob(100L, 1L, updateRequest));
    }

    // 17. Case: Thay đổi trạng thái tin thất bại do không tìm thấy
    // Input: JobId không tồn tại hoặc không thuộc user
    // Expected: Ném ra NotFoundException
    @Test
    void changeStatus_NotFound() {
        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> jobPostingService.changeStatus(100L, 1L, JobStatus.OPEN));
    }

    // 18. Case: Chuẩn hóa kỹ năng (Advanced)
    // Input: Skills = [" Java ", "java", "SPRING"]
    // Expected: Lưu vào DB là ["java", "spring"] (chữ thường, trim, không trùng
    // lặp)
    @Test
    void createJob_SkillsNormalization() throws Exception {
        createJobRequest.setRequiredSkills(List.of(" Java ", "java", "SPRING"));

        when(recruiterProfileRepository.findByUserId(100L)).thenReturn(Optional.of(recruiterProfile));

        // Mock mapper to return specific string when normalized list is passed
        // We capture the argument to verify normalization logic
        when(objectMapper.writeValueAsString(any())).thenAnswer(invocation -> {
            List<String> argument = invocation.getArgument(0);
            if (argument.contains("java") && argument.contains("spring") && argument.size() == 2) {
                return "[\"java\",\"spring\"]";
            }
            return "[]";
        });

        JobPosting savedJob = new JobPosting();
        savedJob.setId(1L);
        savedJob.setRequiredSkills("[\"java\",\"spring\"]");
        savedJob.setRecruiterProfile(recruiterProfile);

        when(jobPostingRepository.save(any(JobPosting.class))).thenReturn(savedJob);
        when(objectMapper.readValue(anyString(), eq(String[].class))).thenReturn(new String[] { "java", "spring" });

        jobPostingService.createJob(100L, createJobRequest);

        // Verify writeValueAsString was called with normalized list
        verify(objectMapper).writeValueAsString(
                argThat((List<String> list) -> list.size() == 2 && list.contains("java") && list.contains("spring")));
    }

    // 19. Case: Cập nhật một phần (Partial Update)
    // Input: Request chỉ chứa description mới, các field khác null
    // Expected: Chỉ description thay đổi, các field khác giữ nguyên
    @Test
    void updateJob_PartialUpdate() throws Exception {
        UpdateJobRequest updateRequest = new UpdateJobRequest();
        updateRequest.setDescription("New Description Only");

        JobPosting existingJob = new JobPosting();
        existingJob.setId(1L);
        existingJob.setTitle("Old Title");
        existingJob.setDescription("Old Description");
        existingJob.setStatus(JobStatus.PENDING_APPROVAL);
        existingJob.setRecruiterProfile(recruiterProfile);
        existingJob.setIsRemote(true);
        existingJob.setRequiredSkills("[]");
        existingJob.setMinBudget(new BigDecimal("1000"));
        existingJob.setMaxBudget(new BigDecimal("2000"));

        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.of(existingJob));
        when(jobPostingRepository.save(any(JobPosting.class))).thenReturn(existingJob);

        // Mock objectMapper for mapToResponse
        when(objectMapper.readValue(anyString(), eq(String[].class))).thenReturn(new String[] {});

        JobPostingResponse response = jobPostingService.updateJob(100L, 1L, updateRequest);

        assertEquals("New Description Only", existingJob.getDescription());
        assertEquals("Old Title", existingJob.getTitle()); // Should ensure other fields are untouched
    }

    // 20. Case: Đóng tin tuyển dụng (OPEN -> CLOSED)
    // Input: Job đang OPEN, chuyển sang CLOSED
    // Expected: Thành công
    @Test
    void changeStatus_OpenToClosed() throws Exception {
        JobPosting existingJob = new JobPosting();
        existingJob.setId(1L);
        existingJob.setStatus(JobStatus.OPEN);
        existingJob.setRecruiterProfile(recruiterProfile);
        existingJob.setRequiredSkills("[]");

        when(jobPostingRepository.findByIdAndRecruiterProfileUserId(1L, 100L))
                .thenReturn(Optional.of(existingJob));
        when(jobPostingRepository.save(any(JobPosting.class))).thenReturn(existingJob);

        // Mock objectMapper for mapToResponse
        when(objectMapper.readValue(anyString(), eq(String[].class))).thenReturn(new String[] {});

        JobPostingResponse response = jobPostingService.changeStatus(100L, 1L, JobStatus.CLOSED);

        assertEquals(JobStatus.CLOSED, response.getStatus());
    }
}
