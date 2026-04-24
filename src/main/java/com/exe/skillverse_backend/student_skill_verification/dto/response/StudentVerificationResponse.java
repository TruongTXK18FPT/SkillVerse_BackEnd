package com.exe.skillverse_backend.student_skill_verification.dto.response;

import com.exe.skillverse_backend.mentor_verification_service.entity.EvidenceType;
import com.exe.skillverse_backend.student_skill_verification.entity.StudentVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * [Nghiệp vụ] Response DTO cho student skill verification request.
 * Gồm đầy đủ thông tin request + danh sách evidence + thông tin student.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentVerificationResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userAvatarUrl;

    private String skillName;
    private StudentVerificationStatus status;

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
