package com.exe.skillverse_backend.mentor_verification_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.auth_service.repository.UserRepository;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.CreateMentorVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.ReviewMentorVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.response.MentorVerificationResponse;
import com.exe.skillverse_backend.mentor_verification_service.entity.EvidenceType;
import com.exe.skillverse_backend.mentor_verification_service.entity.MentorSkillVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.entity.MentorVerificationEvidence;
import com.exe.skillverse_backend.mentor_verification_service.entity.VerificationStatus;
import com.exe.skillverse_backend.mentor_verification_service.repository.MentorSkillVerificationRequestRepository;
import com.exe.skillverse_backend.mentor_verification_service.repository.MentorVerificationEvidenceRepository;
import com.exe.skillverse_backend.mentor_verification_service.service.MentorVerificationService;
import com.exe.skillverse_backend.portfolio_service.entity.ExternalCertificate;
import com.exe.skillverse_backend.portfolio_service.repository.ExternalCertificateRepository;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
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
 * [Nghiệp vụ] Service xử lý luồng xác thực skill mentor.
 * 
 * Flow: Mentor submit request kèm chứng chỉ → Admin review → Approve/Reject.
 * Khi approve, chứng chỉ linked sẽ được mark is_verified=true trên portfolio.
 * Mỗi skill chỉ cho phép 1 request PENDING tại 1 thời điểm.
 * Nếu skill đã APPROVED, không cho submit lại.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MentorVerificationServiceImpl implements MentorVerificationService {

    private final MentorSkillVerificationRequestRepository requestRepository;
    private final MentorVerificationEvidenceRepository evidenceRepository;
    private final ExternalCertificateRepository certificateRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public MentorVerificationResponse submitVerification(User mentor, CreateMentorVerificationRequest request) {
        String normalizedSkill = SkillNameUtils.normalizeRequired(request.getSkillName());

        // [Nghiệp vụ] Không cho gửi request nếu skill đã PENDING hoặc APPROVED
        requestRepository.findByMentorAndSkillAndStatusIn(
                mentor.getId(), normalizedSkill,
                List.of(VerificationStatus.PENDING, VerificationStatus.APPROVED)
        ).ifPresent(existing -> {
            if (existing.getStatus() == VerificationStatus.APPROVED) {
                throw new BadRequestException("Skill '" + normalizedSkill + "' đã được xác thực.");
            }
            throw new BadRequestException("Đã có yêu cầu đang chờ duyệt cho skill '" + normalizedSkill + "'.");
        });

        // Tạo verification request
        MentorSkillVerificationRequest verificationRequest = MentorSkillVerificationRequest.builder()
                .mentor(mentor)
                .skillName(normalizedSkill)
                .githubUrl(request.getGithubUrl())
                .portfolioUrl(request.getPortfolioUrl())
                .additionalNotes(request.getAdditionalNotes())
                .build();

        verificationRequest = requestRepository.save(verificationRequest);

        // [Nghiệp vụ] Link chứng chỉ từ portfolio làm evidence
        List<MentorVerificationEvidence> evidences = new ArrayList<>();

        if (request.getCertificateIds() != null) {
            for (Long certId : request.getCertificateIds()) {
                ExternalCertificate cert = certificateRepository.findById(certId)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Certificate not found: " + certId));

                // Verify certificate belongs to this mentor
                if (!cert.getUser().getId().equals(mentor.getId())) {
                    throw new BadRequestException("Certificate " + certId + " does not belong to you.");
                }

                MentorVerificationEvidence evidence = MentorVerificationEvidence.builder()
                        .verificationRequest(verificationRequest)
                        .evidenceType(EvidenceType.CERTIFICATE)
                        .evidenceUrl(cert.getCredentialUrl() != null ? cert.getCredentialUrl() : cert.getCertificateImageUrl())
                        .description(cert.getTitle() + " - " + cert.getIssuingOrganization())
                        .certificate(cert)
                        .build();
                evidences.add(evidence);
            }
        }

        // [Nghiệp vụ] Thêm evidence bổ sung (github, work experience, etc.)
        if (request.getEvidences() != null) {
            for (CreateMentorVerificationRequest.EvidenceItem item : request.getEvidences()) {
                EvidenceType type;
                try {
                    type = EvidenceType.valueOf(item.getEvidenceType());
                } catch (IllegalArgumentException e) {
                    throw new BadRequestException("Invalid evidence type: " + item.getEvidenceType());
                }

                MentorVerificationEvidence evidence = MentorVerificationEvidence.builder()
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

        log.info("Mentor {} submitted skill verification for '{}'", mentor.getId(), normalizedSkill);
        return mapToResponse(verificationRequest);
    }

    @Override
    public List<MentorVerificationResponse> getMyVerifications(User mentor) {
        return requestRepository.findByMentorIdOrderByRequestedAtDesc(mentor.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getMyVerifiedSkills(User mentor) {
        return requestRepository.findApprovedByMentorId(mentor.getId())
                .stream()
                .map(MentorSkillVerificationRequest::getSkillName)
                .collect(Collectors.toList());
    }

    @Override
    public Page<MentorVerificationResponse> getPendingVerifications(Pageable pageable) {
        return requestRepository.findByStatusOrderByRequestedAtAsc(VerificationStatus.PENDING, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public Page<MentorVerificationResponse> getAllVerifications(List<String> statuses, Pageable pageable) {
        List<VerificationStatus> statusEnums;
        if (statuses == null || statuses.isEmpty()) {
            statusEnums = List.of(VerificationStatus.values());
        } else {
            statusEnums = statuses.stream()
                    .map(s -> {
                        try {
                            return VerificationStatus.valueOf(s.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            if (statusEnums.isEmpty()) {
                statusEnums = List.of(VerificationStatus.values());
            }
        }
        return requestRepository.findByStatusInOrderByRequestedAtDesc(statusEnums, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public MentorVerificationResponse getVerificationById(Long requestId) {
        MentorSkillVerificationRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Verification request not found: " + requestId));
        return mapToResponse(request);
    }

    @Override
    @Transactional
    public MentorVerificationResponse reviewVerification(Long requestId, User admin,
                                                          ReviewMentorVerificationRequest reviewRequest) {
        MentorSkillVerificationRequest verificationReq = requestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Verification request not found: " + requestId));

        if (verificationReq.getStatus() != VerificationStatus.PENDING) {
            throw new BadRequestException("Request đã được xử lý (status: " + verificationReq.getStatus() + ").");
        }

        if (Boolean.TRUE.equals(reviewRequest.getApproved())) {
            verificationReq.setStatus(VerificationStatus.APPROVED);

            // [Nghiệp vụ] Mark linked certificates as verified trên portfolio
            for (MentorVerificationEvidence evidence : verificationReq.getEvidences()) {
                if (evidence.getCertificate() != null) {
                    ExternalCertificate cert = evidence.getCertificate();
                    cert.setIsVerified(true);
                    certificateRepository.save(cert);
                }
            }

            log.info("Admin {} APPROVED skill '{}' for mentor {}",
                    admin.getId(), verificationReq.getSkillName(), verificationReq.getMentor().getId());
        } else {
            verificationReq.setStatus(VerificationStatus.REJECTED);
            log.info("Admin {} REJECTED skill '{}' for mentor {}",
                    admin.getId(), verificationReq.getSkillName(), verificationReq.getMentor().getId());
        }

        verificationReq.setReviewNote(reviewRequest.getReviewNote());
        verificationReq.setReviewedBy(admin);
        verificationReq.setReviewedAt(LocalDateTime.now());

        requestRepository.save(verificationReq);
        return mapToResponse(verificationReq);
    }

    @Override
    public long countPending() {
        return requestRepository.countByStatus(VerificationStatus.PENDING);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private MentorVerificationResponse mapToResponse(MentorSkillVerificationRequest request) {
        User mentor = request.getMentor();
        User reviewer = request.getReviewedBy();

        List<MentorVerificationResponse.EvidenceResponse> evidenceResponses =
                request.getEvidences() != null
                        ? request.getEvidences().stream().map(this::mapEvidence).collect(Collectors.toList())
                        : List.of();

        return MentorVerificationResponse.builder()
                .id(request.getId())
                .mentorId(mentor.getId())
                .mentorName(mentor.getFullName())
                .mentorEmail(mentor.getEmail())
                .mentorAvatarUrl(mentor.getAvatarUrl())
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

    private MentorVerificationResponse.EvidenceResponse mapEvidence(MentorVerificationEvidence evidence) {
        ExternalCertificate cert = evidence.getCertificate();
        return MentorVerificationResponse.EvidenceResponse.builder()
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
