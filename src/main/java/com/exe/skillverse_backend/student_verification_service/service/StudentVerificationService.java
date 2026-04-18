package com.exe.skillverse_backend.student_verification_service.service;

import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationDetailResponse;
import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationEligibilityResponse;
import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationListItemResponse;
import com.exe.skillverse_backend.student_verification_service.dto.response.StudentVerificationStartResponse;
import com.exe.skillverse_backend.student_verification_service.enums.StudentVerificationStatus;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface StudentVerificationService {

    StudentVerificationStartResponse startVerification(Long userId, String schoolEmail, MultipartFile studentCardImage);

    StudentVerificationStartResponse resendOtp(Long userId, Long requestId);

    StudentVerificationDetailResponse verifyOtpAndSubmit(Long userId, Long requestId, String otp);

    StudentVerificationDetailResponse getLatestMyRequest(Long userId);

    StudentVerificationDetailResponse getMyRequestDetail(Long userId, Long requestId);

    StudentVerificationEligibilityResponse getMyEligibility(Long userId);

    Page<StudentVerificationListItemResponse> getRequestsForAdmin(StudentVerificationStatus status, Pageable pageable);

    StudentVerificationDetailResponse getRequestDetailForAdmin(Long requestId);

    StudentVerificationDetailResponse approveRequest(Long adminId, Long requestId, String reviewNote);

    StudentVerificationDetailResponse rejectRequest(Long adminId, Long requestId, String reason);

    StudentVerificationDetailResponse expireApprovedEmail(Long adminId, Long requestId, String reason);

    LocalImagePayload getLocalImageForUser(Long userId, Long requestId);

    LocalImagePayload getLocalImageForAdmin(Long requestId);

    boolean hasApprovedStudentVerification(Long userId);

    record LocalImagePayload(Resource resource, String contentType, String fileName) {
    }
}
