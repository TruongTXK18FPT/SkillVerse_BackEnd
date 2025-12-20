package com.exe.skillverse_backend.business_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.service.UserCreationService;
import com.exe.skillverse_backend.business_service.dto.request.BusinessRegistrationRequest;
import com.exe.skillverse_backend.business_service.dto.response.BusinessRegistrationResponse;
import com.exe.skillverse_backend.business_service.entity.RecruiterProfile;
import com.exe.skillverse_backend.business_service.repository.RecruiterProfileRepository;
import com.exe.skillverse_backend.business_service.service.impl.BusinessRegistrationServiceImpl;
import com.exe.skillverse_backend.shared.service.CloudinaryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * =========================================================================================
 * HƯỚNG DẪN CHẠY TEST (TESTING GUIDE)
 * =========================================================================================
 *
 * 1. Chạy toàn bộ test trong file này:
 * mvn test "-Dtest=BusinessRegistrationServiceTest"
 *
 * 2. Chạy một test case cụ thể:
 * mvn test "-Dtest=BusinessRegistrationServiceTest#register_Success"
 *
 * 3. Chạy kiểm tra biên dịch (không chạy test):
 * mvn clean compile
 *
 * =========================================================================================
 * DANH SÁCH TEST CASES
 * =========================================================================================
 *
 * 1. register_Success (Happy Case):
 * - Mục đích: Kiểm tra luồng đăng ký doanh nghiệp thành công.
 * - Quan trọng: Xác minh rằng 'companyPhone' và 'contactPersonPhone' được map
 * đúng từ request vào entity.
 * - Input: BusinessRegistrationRequest hợp lệ với đầy đủ thông tin.
 * - Expected:
 * + UserCreationService.createUserForRecruiter được gọi.
 * + RecruiterProfileRepository.save được gọi với đúng dữ liệu (đặc biệt là 2 số
 * điện thoại riêng biệt).
 * + OTP được tạo ra.
 * + Trả về response thành công.
 *
 * 2. register_Fail_UserCreationError (Unhappy Case):
 * - Mục đích: Kiểm tra xử lý lỗi khi tạo User thất bại (ví dụ: email đã tồn
 * tại).
 * - Input: Request hợp lệ nhưng UserCreationService ném Exception.
 * - Expected:
 * + Service ném lại Exception.
 * + RecruiterProfile KHÔNG được lưu.
 * + OTP KHÔNG được tạo.
 *
 * 3. register_Fail_DatabaseError (Unhappy Case):
 * - Mục đích: Kiểm tra xử lý lỗi khi lưu RecruiterProfile xuống DB thất bại.
 * - Input: Request hợp lệ, User tạo thành công, nhưng Repository ném Exception.
 * - Expected:
 * + Service ném lại Exception.
 * + OTP KHÔNG được tạo.
 */
@ExtendWith(MockitoExtension.class)
public class BusinessRegistrationServiceTest {

        @Mock
        private UserCreationService userCreationService;

        @Mock
        private RecruiterProfileRepository recruiterProfileRepository;

        @Mock
        private CloudinaryService cloudinaryService;

        @InjectMocks
        private BusinessRegistrationServiceImpl businessRegistrationService;

        private BusinessRegistrationRequest validRequest;
        private User mockUser;

        @BeforeEach
        void setUp() {
                // Setup request data
                validRequest = new BusinessRegistrationRequest();
                validRequest.setEmail("business@test.com");
                validRequest.setPassword("Password123!");
                validRequest.setConfirmPassword("Password123!");
                validRequest.setFullName("Business Owner");
                validRequest.setPhone("0901234567"); // Company Phone / User Phone
                validRequest.setCompanyName("Tech Corp");
                validRequest.setCompanyWebsite("https://techcorp.com");
                validRequest.setCompanyAddress("123 Tech Street");
                validRequest.setTaxCodeOrBusinessRegistrationNumber("0123456789");
                validRequest.setCompanyDocumentsUrl("http://cloudinary.com/doc.pdf");
                validRequest.setContactPersonPhone("0909876543"); // Contact Person Phone
                validRequest.setContactPersonPosition("HR Manager");
                validRequest.setCompanySize("11-50");
                validRequest.setIndustry("IT");

                // Setup mock user
                mockUser = User.builder()
                                .id(1L)
                                .email("business@test.com")
                                .firstName("Business")
                                .lastName("Owner")
                                .phoneNumber("0901234567")
                                .build();
        }

        @Test
        @DisplayName("Happy Case: Đăng ký doanh nghiệp thành công và kiểm tra mapping số điện thoại")
        void register_Success() {
                // Given
                when(userCreationService.createUserForRecruiter(anyString(), anyString(), anyString(), anyString()))
                                .thenReturn(mockUser);

                // When
                BusinessRegistrationResponse response = businessRegistrationService.register(validRequest);

                // Then
                // 1. Verify UserCreationService call
                verify(userCreationService).createUserForRecruiter(
                                eq(validRequest.getEmail()),
                                eq(validRequest.getPassword()),
                                eq(validRequest.getFullName()),
                                eq(validRequest.getPhone()) // Verify phone is passed to user creation
                );

                // 2. Verify RecruiterProfile save and check fields
                ArgumentCaptor<RecruiterProfile> profileCaptor = ArgumentCaptor.forClass(RecruiterProfile.class);
                verify(recruiterProfileRepository).save(profileCaptor.capture());

                RecruiterProfile savedProfile = profileCaptor.getValue();

                assertNotNull(savedProfile);
                assertEquals(mockUser, savedProfile.getUser());
                assertEquals(validRequest.getCompanyName(), savedProfile.getCompanyName());

                // CRITICAL: Verify phone mapping separation
                assertEquals(validRequest.getPhone(), savedProfile.getCompanyPhone(),
                                "Company Phone must match request phone");
                assertEquals(validRequest.getContactPersonPhone(), savedProfile.getContactPersonPhone(),
                                "Contact Person Phone must match request contact phone");
                assertNotEquals(savedProfile.getCompanyPhone(), savedProfile.getContactPersonPhone(),
                                "Company Phone and Contact Person Phone should be distinct in this test case");

                // 3. Verify OTP generation
                verify(userCreationService).generateOtpForUser(eq(validRequest.getEmail()));

                // 4. Verify Response
                assertTrue(response.isSuccess());
                assertEquals("RECRUITER", response.getRole());
        }

        @Test
        @DisplayName("Unhappy Case: Đăng ký thất bại do lỗi tạo User (ví dụ: email tồn tại)")
        void register_Fail_UserCreationError() {
                // Given
                String errorMessage = "Email already registered";
                when(userCreationService.createUserForRecruiter(anyString(), anyString(), anyString(), anyString()))
                                .thenThrow(new RuntimeException(errorMessage));

                // When & Then
                Exception exception = assertThrows(RuntimeException.class, () -> {
                        businessRegistrationService.register(validRequest);
                });

                assertEquals(errorMessage, exception.getMessage());

                // Verify that profile was NOT saved
                verify(recruiterProfileRepository, never()).save(any(RecruiterProfile.class));

                // Verify that OTP was NOT generated
                verify(userCreationService, never()).generateOtpForUser(anyString());
        }

        @Test
        @DisplayName("Unhappy Case: Đăng ký thất bại do lỗi Database khi lưu Profile")
        void register_Fail_DatabaseError() {
                // Given
                when(userCreationService.createUserForRecruiter(anyString(), anyString(), anyString(), anyString()))
                                .thenReturn(mockUser);

                // Simulate DB error
                doThrow(new RuntimeException("Database connection failed"))
                                .when(recruiterProfileRepository).save(any(RecruiterProfile.class));

                // When & Then
                Exception exception = assertThrows(RuntimeException.class, () -> {
                        businessRegistrationService.register(validRequest);
                });

                assertEquals("Database connection failed", exception.getMessage());

                // Verify OTP was NOT generated (transaction rollback handled by @Transactional
                // in real app, here checking flow)
                verify(userCreationService, never()).generateOtpForUser(anyString());
        }
}
