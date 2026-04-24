package com.exe.skillverse_backend.student_skill_verification.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.student_skill_verification.dto.request.CreateStudentVerificationRequest;
import com.exe.skillverse_backend.student_skill_verification.dto.request.ReviewStudentVerificationRequest;
import com.exe.skillverse_backend.student_skill_verification.dto.response.StudentVerificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface StudentSkillVerificationService {

    /** Student: gửi yêu cầu xác thực 1 skill */
    StudentVerificationResponse submitVerification(User student, CreateStudentVerificationRequest request);

    /** Student: xem danh sách request của mình */
    List<StudentVerificationResponse> getMyVerifications(User student);

    /** Student: xem danh sách skill đã được verified */
    List<String> getMyVerifiedSkills(User student);

    /** Admin: lấy danh sách request chờ duyệt */
    Page<StudentVerificationResponse> getPendingVerifications(Pageable pageable);

    /** Admin: lấy tất cả request (có filter) */
    Page<StudentVerificationResponse> getAllVerifications(List<String> statuses, Pageable pageable);

    /** Admin: xem chi tiết 1 request */
    StudentVerificationResponse getVerificationById(Long requestId);

    /** Admin: duyệt hoặc reject request */
    StudentVerificationResponse reviewVerification(Long requestId, User admin, ReviewStudentVerificationRequest request);

    /** Đếm request pending (cho admin badge) */
    long countPending();

    /** Public: lấy danh sách skill đã APPROVED kèm evidence cho 1 student */
    List<StudentVerificationResponse> getApprovedVerificationsByUserId(Long userId);
}
