package com.exe.skillverse_backend.mentor_verification_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.CreateMentorVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.ReviewMentorVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.response.MentorVerificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MentorVerificationService {

    /** Mentor: gửi yêu cầu xác thực 1 skill */
    MentorVerificationResponse submitVerification(User mentor, CreateMentorVerificationRequest request);

    /** Mentor: xem danh sách request của mình */
    List<MentorVerificationResponse> getMyVerifications(User mentor);

    /** Mentor: xem danh sách skill đã được verified */
    List<String> getMyVerifiedSkills(User mentor);

    /** Admin: lấy danh sách request chờ duyệt */
    Page<MentorVerificationResponse> getPendingVerifications(Pageable pageable);

    /** Admin: lấy tất cả request (có filter) */
    Page<MentorVerificationResponse> getAllVerifications(List<String> statuses, Pageable pageable);

    /** Admin: xem chi tiết 1 request */
    MentorVerificationResponse getVerificationById(Long requestId);

    /** Admin: duyệt hoặc reject request */
    MentorVerificationResponse reviewVerification(Long requestId, User admin, ReviewMentorVerificationRequest request);

    /** Đếm request pending (cho admin badge) */
    long countPending();
}
