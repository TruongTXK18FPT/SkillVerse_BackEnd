package com.exe.skillverse_backend.student_skill_verification.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.mentor_verification_service.entity.EvidenceType;
import com.exe.skillverse_backend.portfolio_service.entity.ExternalCertificate;
import com.exe.skillverse_backend.portfolio_service.entity.UserVerifiedSkill;
import com.exe.skillverse_backend.portfolio_service.repository.ExternalCertificateRepository;
import com.exe.skillverse_backend.portfolio_service.repository.UserVerifiedSkillRepository;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
import com.exe.skillverse_backend.student_skill_verification.dto.request.CreateStudentVerificationRequest;
import com.exe.skillverse_backend.student_skill_verification.dto.request.ReviewStudentVerificationRequest;
import com.exe.skillverse_backend.student_skill_verification.dto.response.StudentVerificationResponse;
import com.exe.skillverse_backend.student_skill_verification.entity.StudentSkillVerificationRequest;
import com.exe.skillverse_backend.student_skill_verification.entity.StudentVerificationEvidence;
import com.exe.skillverse_backend.student_skill_verification.entity.StudentVerificationStatus;
import com.exe.skillverse_backend.student_skill_verification.repository.StudentSkillVerificationRequestRepository;
import com.exe.skillverse_backend.student_skill_verification.repository.StudentVerificationEvidenceRepository;
import com.exe.skillverse_backend.student_skill_verification.service.StudentSkillVerificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * [Nghiệp vụ] Service xử lý luồng xác thực skill student.
 *
 * Flow: Student submit request kèm bằng chứng → Admin review → Approve/Reject.
 * Mỗi skill chỉ cho phép 1 request PENDING tại 1 thời điểm.
 * Nếu skill đã APPROVED, không cho submit lại.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StudentSkillVerificationServiceImpl implements StudentSkillVerificationService {

    private final StudentSkillVerificationRequestRepository requestRepository;
    private final StudentVerificationEvidenceRepository evidenceRepository;
    private final ExternalCertificateRepository certificateRepository;
    private final UserVerifiedSkillRepository userVerifiedSkillRepository;

    @Override
    @Transactional
    public StudentVerificationResponse submitVerification(User student, CreateStudentVerificationRequest request) {
        String normalizedSkill = SkillNameUtils.normalizeRequired(request.getSkillName());

        // [Nghiệp vụ] Không cho gửi request nếu skill đã PENDING hoặc APPROVED
        requestRepository.findByUserAndSkillAndStatusIn(
                student.getId(), normalizedSkill,
                List.of(StudentVerificationStatus.PENDING, StudentVerificationStatus.APPROVED)
        ).ifPresent(existing -> {
            if (existing.getStatus() == StudentVerificationStatus.APPROVED) {
                throw new BadRequestException("Skill '" + normalizedSkill + "' đã được xác thực.");
            }
            throw new BadRequestException("Đã có yêu cầu đang chờ duyệt cho skill '" + normalizedSkill + "'.");
        });

        // Tạo verification request
        StudentSkillVerificationRequest verificationRequest = StudentSkillVerificationRequest.builder()
                .user(student)
                .skillName(normalizedSkill)
                .githubUrl(request.getGithubUrl())
                .portfolioUrl(request.getPortfolioUrl())
                .additionalNotes(request.getAdditionalNotes())
                .build();

        verificationRequest = requestRepository.save(verificationRequest);

        // [Nghiệp vụ] Link chứng chỉ từ portfolio làm evidence
        List<StudentVerificationEvidence> evidences = new ArrayList<>();

        if (request.getCertificateIds() != null) {
            for (Long certId : request.getCertificateIds()) {
                ExternalCertificate cert = certificateRepository.findById(certId)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Certificate not found: " + certId));

                if (!cert.getUser().getId().equals(student.getId())) {
                    throw new BadRequestException("Certificate " + certId + " does not belong to you.");
                }

                StudentVerificationEvidence evidence = StudentVerificationEvidence.builder()
                        .verificationRequest(verificationRequest)
                        .evidenceType(EvidenceType.CERTIFICATE)
                        .evidenceUrl(cert.getCredentialUrl() != null ? cert.getCredentialUrl() : cert.getCertificateImageUrl())
                        .description(cert.getTitle() + " - " + cert.getIssuingOrganization())
                        .certificate(cert)
                        .build();
                evidences.add(evidence);
            }
        }

        // [Nghiệp vụ] Thêm evidence bổ sung
        if (request.getEvidences() != null) {
            for (CreateStudentVerificationRequest.EvidenceItem item : request.getEvidences()) {
                EvidenceType type;
                try {
                    type = EvidenceType.valueOf(item.getEvidenceType());
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid evidence type: " + item.getEvidenceType());
                }

                StudentVerificationEvidence evidence = StudentVerificationEvidence.builder()
                        .verificationRequest(verificationRequest)
                        .evidenceType(type)
                        .evidenceUrl(item.getEvidenceUrl())
                        .description(item.getDescription())
                        .build();
                evidences.add(evidence);
            }
        }

        if (evidences.isEmpty()) {
            throw new BadRequestException("Phải cung cấp ít nhất 1 bằng chứng (chứng chỉ, github, kinh nghiệm).");
        }

        evidenceRepository.saveAll(evidences);
        verificationRequest.setEvidences(evidences);

        log.info("Student {} submitted skill verification for '{}'", student.getId(), normalizedSkill);
        return mapToResponse(verificationRequest);
    }

    @Override
    public List<StudentVerificationResponse> getMyVerifications(User student) {
        return requestRepository.findByUserIdOrderByRequestedAtDesc(student.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getMyVerifiedSkills(User student) {
        return requestRepository.findApprovedByUserId(student.getId())
                .stream()
                .map(StudentSkillVerificationRequest::getSkillName)
                .collect(Collectors.toList());
    }

    @Override
    public Page<StudentVerificationResponse> getPendingVerifications(Pageable pageable) {
        return requestRepository.findByStatusOrderByRequestedAtAsc(StudentVerificationStatus.PENDING, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public Page<StudentVerificationResponse> getAllVerifications(List<String> statuses, Pageable pageable) {
        List<StudentVerificationStatus> statusEnums;
        if (statuses == null || statuses.isEmpty()) {
            statusEnums = List.of(StudentVerificationStatus.values());
        } else {
            statusEnums = statuses.stream()
                    .map(s -> {
                        try {
                            return StudentVerificationStatus.valueOf(s.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            if (statusEnums.isEmpty()) {
                statusEnums = List.of(StudentVerificationStatus.values());
            }
        }
        return requestRepository.findByStatusInOrderByRequestedAtDesc(statusEnums, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public StudentVerificationResponse getVerificationById(Long requestId) {
        StudentSkillVerificationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Verification request not found: " + requestId));
        return mapToResponse(request);
    }

    @Override
    @Transactional
    public StudentVerificationResponse reviewVerification(Long requestId, User admin,
                                                           ReviewStudentVerificationRequest reviewRequest) {
        StudentSkillVerificationRequest verificationReq = requestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Verification request not found: " + requestId));

        if (verificationReq.getStatus() != StudentVerificationStatus.PENDING) {
            throw new BadRequestException("Request đã được xử lý (status: " + verificationReq.getStatus() + ").");
        }

        if (Boolean.TRUE.equals(reviewRequest.getApproved())) {
            verificationReq.setStatus(StudentVerificationStatus.APPROVED);

            // [Nghiệp vụ] Mark linked certificates as verified trên portfolio
            for (StudentVerificationEvidence evidence : verificationReq.getEvidences()) {
                if (evidence.getCertificate() != null) {
                    ExternalCertificate cert = evidence.getCertificate();
                    cert.setIsVerified(true);
                    certificateRepository.save(cert);
                }
            }
            syncPortfolioVerifiedSkill(verificationReq, admin);

            log.info("Admin {} APPROVED skill '{}' for student {}",
                    admin.getId(), verificationReq.getSkillName(), verificationReq.getUser().getId());
        } else {
            verificationReq.setStatus(StudentVerificationStatus.REJECTED);
            log.info("Admin {} REJECTED skill '{}' for student {}",
                    admin.getId(), verificationReq.getSkillName(), verificationReq.getUser().getId());
        }

        verificationReq.setReviewNote(reviewRequest.getReviewNote());
        verificationReq.setReviewedBy(admin);
        verificationReq.setReviewedAt(LocalDateTime.now());

        requestRepository.save(verificationReq);
        return mapToResponse(verificationReq);
    }

    @Override
    public long countPending() {
        return requestRepository.countByStatus(StudentVerificationStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentVerificationResponse> getApprovedVerificationsByUserId(Long userId) {
        return requestRepository.findApprovedByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private StudentVerificationResponse mapToResponse(StudentSkillVerificationRequest request) {
        User user = request.getUser();
        User reviewer = request.getReviewedBy();

        List<StudentVerificationResponse.EvidenceResponse> evidenceResponses =
                request.getEvidences() != null
                        ? request.getEvidences().stream().map(this::mapEvidence).collect(Collectors.toList())
                        : List.of();

        return StudentVerificationResponse.builder()
                .id(request.getId())
                .userId(user.getId())
                .userName(user.getFullName())
                .userEmail(user.getEmail())
                .userAvatarUrl(user.getAvatarUrl())
                .skillName(request.getSkillName())
                .status(request.getStatus())
                .githubUrl(request.getGithubUrl())
                .portfolioUrl(request.getPortfolioUrl())
                .additionalNotes(request.getAdditionalNotes())
                .reviewNote(request.getReviewNote())
                .reviewedById(reviewer != null ? reviewer.getId() : null)
                .reviewedByName(reviewer != null ? reviewer.getFullName() : null)
                .requestedAt(request.getRequestedAt())
                .reviewedAt(request.getReviewedAt())
                .evidences(evidenceResponses)
                .build();
    }

    private void syncPortfolioVerifiedSkill(StudentSkillVerificationRequest request, User admin) {
        String skillName = SkillNameUtils.normalizeRequired(request.getSkillName());
        Long userId = request.getUser().getId();
        UserVerifiedSkill skill = userVerifiedSkillRepository
                .findByUserIdAndSkillName(userId, skillName)
                .orElseGet(() -> UserVerifiedSkill.builder()
                        .userId(userId)
                        .skillName(skillName)
                        .build());

        skill.setVerifiedByMentorId(admin.getId());
        skill.setVerificationNote(request.getReviewNote());
        if (skill.getVerifiedAt() == null) {
            skill.setVerifiedAt(java.time.Instant.now());
        }
        userVerifiedSkillRepository.save(skill);
    }

    private StudentVerificationResponse.EvidenceResponse mapEvidence(StudentVerificationEvidence evidence) {
        ExternalCertificate cert = evidence.getCertificate();
        return StudentVerificationResponse.EvidenceResponse.builder()
                .id(evidence.getId())
                .evidenceType(evidence.getEvidenceType())
                .evidenceUrl(evidence.getEvidenceUrl())
                .description(evidence.getDescription())
                .certificateId(cert != null ? cert.getId() : null)
                .certificateTitle(cert != null ? cert.getTitle() : null)
                .certificateImageUrl(cert != null ? cert.getCertificateImageUrl() : null)
                .issuingOrganization(cert != null ? cert.getIssuingOrganization() : null)
                .build();
    }
}
