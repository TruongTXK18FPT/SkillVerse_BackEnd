package com.exe.skillverse_backend.mentor_verification_service.service.impl;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.CreateBatchVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.CreateMentorVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.ReviewBatchVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.request.ReviewMentorVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.dto.response.BatchVerificationResponse;
import com.exe.skillverse_backend.mentor_verification_service.dto.response.MentorVerificationResponse;
import com.exe.skillverse_backend.mentor_verification_service.entity.EvidenceType;
import com.exe.skillverse_backend.mentor_verification_service.entity.MentorBatchVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.entity.MentorSkillVerificationRequest;
import com.exe.skillverse_backend.mentor_verification_service.entity.MentorVerificationEvidence;
import com.exe.skillverse_backend.mentor_verification_service.entity.VerificationStatus;
import com.exe.skillverse_backend.mentor_verification_service.repository.MentorBatchVerificationRequestRepository;
import com.exe.skillverse_backend.mentor_verification_service.repository.MentorSkillVerificationRequestRepository;
import com.exe.skillverse_backend.mentor_verification_service.repository.MentorVerificationEvidenceRepository;
import com.exe.skillverse_backend.mentor_verification_service.service.MentorVerificationService;
import com.exe.skillverse_backend.portfolio_service.entity.ExternalCertificate;
import com.exe.skillverse_backend.portfolio_service.entity.UserVerifiedSkill;
import com.exe.skillverse_backend.portfolio_service.repository.ExternalCertificateRepository;
import com.exe.skillverse_backend.portfolio_service.repository.UserVerifiedSkillRepository;
import com.exe.skillverse_backend.portfolio_service.repository.PortfolioExtendedProfileRepository;
import com.exe.skillverse_backend.shared.exception.BadRequestException;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
    private final MentorBatchVerificationRequestRepository batchRepository;
    private final MentorVerificationEvidenceRepository evidenceRepository;
    private final ExternalCertificateRepository certificateRepository;
    private final UserVerifiedSkillRepository userVerifiedSkillRepository;
    private final PortfolioExtendedProfileRepository portfolioExtendedProfileRepository;
    private final ObjectMapper objectMapper;

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
    @Transactional
    public BatchVerificationResponse submitBatchVerification(User mentor, CreateBatchVerificationRequest request) {
        Set<String> normalizedSkills = request.getSkillNames() == null
                ? Set.of()
                : request.getSkillNames().stream()
                .map(SkillNameUtils::normalizeRequired)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (normalizedSkills.isEmpty()) {
            throw new BadRequestException("Phải chọn ít nhất 1 kỹ năng để xác thực.");
        }

        for (String skillName : normalizedSkills) {
            requestRepository.findByMentorAndSkillAndStatusIn(
                    mentor.getId(), skillName,
                    List.of(VerificationStatus.PENDING, VerificationStatus.APPROVED)
            ).ifPresent(existing -> {
                if (existing.getStatus() == VerificationStatus.APPROVED) {
                    throw new BadRequestException("Skill '" + skillName + "' đã được xác thực.");
                }
                throw new BadRequestException("Đã có yêu cầu đang chờ duyệt cho skill '" + skillName + "'.");
            });
        }

        MentorBatchVerificationRequest batch = MentorBatchVerificationRequest.builder()
                .mentor(mentor)
                .githubUrl(request.getGithubUrl())
                .portfolioUrl(request.getPortfolioUrl())
                .additionalNotes(request.getAdditionalNotes())
                .status(VerificationStatus.PENDING)
                .build();

        for (String skillName : normalizedSkills) {
            MentorSkillVerificationRequest skillRequest = MentorSkillVerificationRequest.builder()
                    .mentor(mentor)
                    .batchRequest(batch)
                    .skillName(skillName)
                    .githubUrl(request.getGithubUrl())
                    .portfolioUrl(request.getPortfolioUrl())
                    .additionalNotes(request.getAdditionalNotes())
                    .status(VerificationStatus.PENDING)
                    .build();
            batch.getSkillRequests().add(skillRequest);
        }

        List<MentorVerificationEvidence> evidences = buildEvidencesForBatch(mentor, batch, request);
        if (evidences.isEmpty()) {
            throw new BadRequestException("Phải cung cấp ít nhất 1 bằng chứng cho lô xác thực.");
        }
        batch.getEvidences().addAll(evidences);

        MentorBatchVerificationRequest saved = batchRepository.save(batch);
        log.info("Mentor {} submitted batch verification {} with {} skills",
                mentor.getId(), saved.getId(), normalizedSkills.size());
        return mapBatchToResponse(saved);
    }

    @Override
    public List<MentorVerificationResponse> getMyVerifications(User mentor) {
        return requestRepository.findByMentorIdOrderByRequestedAtDesc(mentor.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BatchVerificationResponse> getMyBatchVerifications(User mentor) {
        return batchRepository.findByMentorIdOrderBySubmittedAtDesc(mentor.getId())
                .stream()
                .map(this::mapBatchToResponse)
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
    @Transactional
    public void revokeVerifiedSkill(User mentor, String skillName) {
        String normalizedSkill = SkillNameUtils.normalizeRequired(skillName);
        List<MentorSkillVerificationRequest> approvedRequests =
                requestRepository.findApprovedByMentorIdAndSkillName(mentor.getId(), normalizedSkill);

        if (approvedRequests.isEmpty()) {
            throw new BadRequestException("Skill '" + normalizedSkill + "' chưa được xác thực hoặc đã được gỡ.");
        }

        for (MentorSkillVerificationRequest request : approvedRequests) {
            request.setStatus(VerificationStatus.REVOKED);
            request.setReviewNote("Mentor removed this verified skill from profile.");
            request.setReviewedAt(LocalDateTime.now());

            MentorBatchVerificationRequest batch = request.getBatchRequest();
            if (batch != null) {
                batch.setStatus(resolveBatchStatus(batch.getSkillRequests()));
                batch.setUpdatedAt(LocalDateTime.now());
                batchRepository.save(batch);
            }
        }

        requestRepository.saveAll(approvedRequests);
        userVerifiedSkillRepository.findByUserIdAndSkillName(mentor.getId(), normalizedSkill)
                .ifPresent(userVerifiedSkillRepository::delete);
        log.info("Mentor {} revoked verified skill '{}'", mentor.getId(), normalizedSkill);
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
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            if (statusEnums.isEmpty()) {
                statusEnums = List.of(VerificationStatus.values());
            }
        }
        return requestRepository.findByStatusInOrderByRequestedAtDesc(statusEnums, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BatchVerificationResponse> getPendingBatchVerifications(Pageable pageable) {
        return batchRepository.findByStatusOrderBySubmittedAtAsc(VerificationStatus.PENDING, pageable)
                .map(this::mapBatchToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BatchVerificationResponse> getAllBatchVerifications(List<String> statuses, Pageable pageable) {
        List<VerificationStatus> statusEnums = parseStatuses(statuses);
        return batchRepository.findByStatusInOrderBySubmittedAtDesc(statusEnums, pageable)
                .map(this::mapBatchToResponse);
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
            syncPortfolioVerifiedSkill(verificationReq, admin);

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
    @Transactional
    public BatchVerificationResponse reviewBatchVerification(Long batchId, User admin,
                                                             ReviewBatchVerificationRequest reviewRequest) {
        MentorBatchVerificationRequest batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Batch verification request not found: " + batchId));

        if (batch.getStatus() != VerificationStatus.PENDING) {
            throw new BadRequestException("Batch đã được xử lý (status: " + batch.getStatus() + ").");
        }

        Map<Long, MentorSkillVerificationRequest> skillById = batch.getSkillRequests().stream()
                .collect(Collectors.toMap(MentorSkillVerificationRequest::getId, Function.identity()));

        Set<Long> reviewedSkillIds = reviewRequest.getSkillsReview().stream()
                .map(ReviewBatchVerificationRequest.SkillReviewItem::getSkillVerificationId)
                .collect(Collectors.toSet());

        if (!reviewedSkillIds.equals(skillById.keySet())) {
            throw new BadRequestException("Danh sách skill review phải khớp toàn bộ skill trong batch.");
        }

        for (ReviewBatchVerificationRequest.SkillReviewItem item : reviewRequest.getSkillsReview()) {
            MentorSkillVerificationRequest skillRequest = skillById.get(item.getSkillVerificationId());
            if (skillRequest == null) {
                throw new BadRequestException("Skill verification không thuộc batch này: " + item.getSkillVerificationId());
            }

            if (Boolean.TRUE.equals(item.getApproved())) {
                skillRequest.setStatus(VerificationStatus.APPROVED);
                markCertificatesVerified(batch.getEvidences());
                syncPortfolioVerifiedSkill(skillRequest, admin);
            } else {
                skillRequest.setStatus(VerificationStatus.REJECTED);
            }
            skillRequest.setReviewNote(item.getReviewNote());
            skillRequest.setReviewedBy(admin);
            skillRequest.setReviewedAt(LocalDateTime.now());
        }

        batch.setGeneralReviewNote(reviewRequest.getGeneralReviewNote());
        batch.setReviewedBy(admin);
        batch.setReviewedAt(LocalDateTime.now());
        batch.setStatus(resolveBatchStatus(batch.getSkillRequests()));

        MentorBatchVerificationRequest saved = batchRepository.save(batch);
        log.info("Admin {} reviewed batch {} with status {}", admin.getId(), saved.getId(), saved.getStatus());
        return mapBatchToResponse(saved);
    }

    @Override
    public long countPending() {
        return requestRepository.countByStatus(VerificationStatus.PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MentorVerificationResponse> getApprovedVerificationsByMentorId(Long mentorId) {
        return requestRepository.findApprovedByMentorId(mentorId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private List<MentorVerificationEvidence> buildEvidencesForBatch(User mentor,
                                                                     MentorBatchVerificationRequest batch,
                                                                     CreateBatchVerificationRequest request) {
        List<MentorVerificationEvidence> evidences = new ArrayList<>();

        if (request.getCertificateIds() != null) {
            for (Long certId : request.getCertificateIds()) {
                ExternalCertificate cert = certificateRepository.findById(certId)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Certificate not found: " + certId));

                if (!cert.getUser().getId().equals(mentor.getId())) {
                    throw new BadRequestException("Certificate " + certId + " does not belong to you.");
                }

                evidences.add(MentorVerificationEvidence.builder()
                        .batchRequest(batch)
                        .evidenceType(EvidenceType.CERTIFICATE)
                        .evidenceUrl(cert.getCredentialUrl() != null ? cert.getCredentialUrl() : cert.getCertificateImageUrl())
                        .description(cert.getTitle() + " - " + cert.getIssuingOrganization())
                        .certificate(cert)
                        .build());
            }
        }

        if (request.getEvidences() != null) {
            for (CreateMentorVerificationRequest.EvidenceItem item : request.getEvidences()) {
                evidences.add(MentorVerificationEvidence.builder()
                        .batchRequest(batch)
                        .evidenceType(parseEvidenceType(item.getEvidenceType()))
                        .evidenceUrl(item.getEvidenceUrl())
                        .description(item.getDescription())
                        .build());
            }
        }

        return evidences;
    }

    private EvidenceType parseEvidenceType(String value) {
        try {
            return EvidenceType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Invalid evidence type: " + value);
        }
    }

    private List<VerificationStatus> parseStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of(VerificationStatus.values());
        }

        List<VerificationStatus> parsed = statuses.stream()
                .map(status -> {
                    try {
                        return VerificationStatus.valueOf(status.toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return parsed.isEmpty() ? List.of(VerificationStatus.values()) : parsed;
    }

    private void markCertificatesVerified(List<MentorVerificationEvidence> evidences) {
        if (evidences == null) {
            return;
        }
        for (MentorVerificationEvidence evidence : evidences) {
            if (evidence.getCertificate() != null) {
                ExternalCertificate cert = evidence.getCertificate();
                cert.setIsVerified(true);
                certificateRepository.save(cert);
            }
        }
    }

    private void syncPortfolioVerifiedSkill(MentorSkillVerificationRequest request, User admin) {
        String skillName = SkillNameUtils.normalizeRequired(request.getSkillName());
        Long mentorId = request.getMentor().getId();
        UserVerifiedSkill skill = userVerifiedSkillRepository
                .findByUserIdAndSkillName(mentorId, skillName)
                .orElseGet(() -> UserVerifiedSkill.builder()
                        .userId(mentorId)
                        .skillName(skillName)
                        .build());

        skill.setVerifiedByMentorId(admin.getId());
        skill.setVerificationNote(request.getReviewNote());
        if (skill.getVerifiedAt() == null) {
            skill.setVerifiedAt(Instant.now());
        }
        userVerifiedSkillRepository.save(skill);
        
        appendToPortfolioTopSkills(mentorId, skillName);
    }
    
    private void appendToPortfolioTopSkills(Long userId, String skillName) {
        portfolioExtendedProfileRepository.findByUserId(userId).ifPresent(profile -> {
            try {
                List<String> skills = new ArrayList<>();
                if (profile.getTopSkills() != null && !profile.getTopSkills().isBlank()) {
                    skills = objectMapper.readValue(profile.getTopSkills(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
                }
                if (!skills.contains(skillName)) {
                    skills.add(skillName);
                    profile.setTopSkills(objectMapper.writeValueAsString(skills));
                    portfolioExtendedProfileRepository.save(profile);
                }
            } catch (Exception e) {
                log.error("Failed to append topSkills for user {}", userId, e);
            }
        });
    }

    private VerificationStatus resolveBatchStatus(List<MentorSkillVerificationRequest> skillRequests) {
        long approvedCount = skillRequests.stream()
                .filter(skill -> skill.getStatus() == VerificationStatus.APPROVED)
                .count();
        long rejectedCount = skillRequests.stream()
                .filter(skill -> skill.getStatus() == VerificationStatus.REJECTED)
                .count();

        if (approvedCount == skillRequests.size()) {
            return VerificationStatus.COMPLETED;
        }
        if (rejectedCount == skillRequests.size()) {
            return VerificationStatus.REJECTED;
        }
        if (approvedCount > 0) {
            return VerificationStatus.PARTIAL_APPROVED;
        }
        if (skillRequests.stream().allMatch(skill -> skill.getStatus() == VerificationStatus.REVOKED)) {
            return VerificationStatus.REVOKED;
        }
        return VerificationStatus.REJECTED;
    }

    private BatchVerificationResponse mapBatchToResponse(MentorBatchVerificationRequest batch) {
        User mentor = batch.getMentor();
        User reviewer = batch.getReviewedBy();

        List<MentorVerificationResponse.EvidenceResponse> evidenceResponses =
                batch.getEvidences() != null
                        ? batch.getEvidences().stream().map(this::mapEvidence).collect(Collectors.toList())
                        : List.of();

        List<MentorVerificationResponse> skillResponses =
                batch.getSkillRequests() != null
                        ? batch.getSkillRequests().stream()
                        .map(this::mapToResponse)
                        .collect(Collectors.toList())
                        : List.of();

        return BatchVerificationResponse.builder()
                .id(batch.getId())
                .mentorId(mentor.getId())
                .mentorName(mentor.getFullName())
                .mentorEmail(mentor.getEmail())
                .mentorAvatarUrl(mentor.getAvatarUrl())
                .status(batch.getStatus())
                .githubUrl(batch.getGithubUrl())
                .portfolioUrl(batch.getPortfolioUrl())
                .additionalNotes(batch.getAdditionalNotes())
                .generalReviewNote(batch.getGeneralReviewNote())
                .reviewedById(reviewer != null ? reviewer.getId() : null)
                .reviewedByName(reviewer != null ? reviewer.getFullName() : null)
                .submittedAt(batch.getSubmittedAt())
                .reviewedAt(batch.getReviewedAt())
                .evidences(evidenceResponses)
                .skills(skillResponses)
                .build();
    }

    private MentorVerificationResponse mapToResponse(MentorSkillVerificationRequest request) {
        User mentor = request.getMentor();
        User reviewer = request.getReviewedBy();

        List<MentorVerificationResponse.EvidenceResponse> evidenceResponses =
                request.getEvidences() != null
                        ? request.getEvidences().stream().map(this::mapEvidence).collect(Collectors.toList())
                        : List.of();
        if (evidenceResponses.isEmpty()
                && request.getBatchRequest() != null
                && request.getBatchRequest().getEvidences() != null) {
            evidenceResponses = request.getBatchRequest().getEvidences().stream()
                    .map(this::mapEvidence)
                    .collect(Collectors.toList());
        }

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
