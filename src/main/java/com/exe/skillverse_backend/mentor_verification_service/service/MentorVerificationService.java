package com.exe.skillverse_backend.mentor_verification_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.CreateBatchVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.CreateMentorVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.ReviewBatchVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.ReviewMentorVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.response.BatchVerificationResponse;
import com.exe.skillverse_backend.mentor_verification_service.dto.response.MentorVerificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MentorVerificationService {

    MentorVerificationResponse submitVerification(User mentor, CreateMentorVerificationRequest request);

    BatchVerificationResponse submitBatchVerification(User mentor, CreateBatchVerificationRequest request);

    List<MentorVerificationResponse> getMyVerifications(User mentor);

    List<BatchVerificationResponse> getMyBatchVerifications(User mentor);

    List<String> getMyVerifiedSkills(User mentor);

    void revokeVerifiedSkill(User mentor, String skillName);

    Page<MentorVerificationResponse> getPendingVerifications(Pageable pageable);

    Page<MentorVerificationResponse> getAllVerifications(List<String> statuses, Pageable pageable);

    Page<BatchVerificationResponse> getPendingBatchVerifications(Pageable pageable);

    Page<BatchVerificationResponse> getAllBatchVerifications(List<String> statuses, Pageable pageable);

    MentorVerificationResponse getVerificationById(Long requestId);

    MentorVerificationResponse reviewVerification(Long requestId, User admin, ReviewMentorVerificationRequest request);

    BatchVerificationResponse reviewBatchVerification(Long batchId, User admin, ReviewBatchVerificationRequest request);

    long countPending();

    List<MentorVerificationResponse> getApprovedVerificationsByMentorId(Long mentorId);
}
