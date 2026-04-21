package com.exe.skillverse_backend.mentor_verification_service.dto.response;

import com.exe.skillverse_backend.mentor_verification_service.entity.EvidenceType;
import com.exe.skillverse_backend.mentor_verification_service.entity.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [Nghiệp vụ] Response DTO cho verification request.
 * Gồm đầy đủ thông tin request + danh sách evidence + thông tin mentor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorVerificationResponse {

    private Long id;
    private Long mentorId;
    private String mentorName;
    private String mentorEmail;
    private String mentorAvatarUrl;

    private String skillName;
    private VerificationStatus status;

    private String githubUrl;
    private String portfolioUrl;
    private String additionalNotes;

    private String reviewNote;
    private Long reviewedById;
    private String reviewedByName;

    private LocalDateTime requestedAt;
    private LocalDateTime reviewedAt;

    private List<EvidenceResponse> evidences;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EvidenceResponse {
        private Long id;
        private EvidenceType evidenceType;
        private String evidenceUrl;
        private String description;
        private Long certificateId;
        private String certificateTitle;
        private String certificateImageUrl;
        private String issuingOrganization;
    }
}
