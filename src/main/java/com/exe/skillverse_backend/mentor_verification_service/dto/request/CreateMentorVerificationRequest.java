package com.exe.skillverse_backend.mentor_verification_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * [Nghiệp vụ] DTO để mentor gửi yêu cầu xác thực skill.
 * Mentor phải cung cấp tên skill + ít nhất 1 bằng chứng (chứng chỉ, github, work experience).
 * certificateIds: IDs chứng chỉ đã tồn tại trong portfolio.
 * evidences: bằng chứng bổ sung (github link, portfolio link, work experience description).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMentorVerificationRequest {

    @NotBlank(message = "Skill name is required")
    @Size(max = 100, message = "Skill name must be at most 100 characters")
    private String skillName;

    @Size(max = 500, message = "GitHub URL must be at most 500 characters")
    private String githubUrl;

    @Size(max = 500, message = "Portfolio URL must be at most 500 characters")
    private String portfolioUrl;

    @Size(max = 2000, message = "Additional notes must be at most 2000 characters")
    private String additionalNotes;

    /** IDs of existing ExternalCertificates from portfolio */
    @Builder.Default
    private List<Long> certificateIds = new ArrayList<>();

    /** Additional evidence items (GitHub repos, work experience, etc.) */
    @Builder.Default
    private List<EvidenceItem> evidences = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EvidenceItem {
        @NotBlank(message = "Evidence type is required")
        private String evidenceType; // GITHUB, PORTFOLIO_LINK, WORK_EXPERIENCE

        @Size(max = 1000, message = "Evidence URL must be at most 1000 characters")
        private String evidenceUrl;

        @Size(max = 2000, message = "Description must be at most 2000 characters")
        private String description;
    }
}
