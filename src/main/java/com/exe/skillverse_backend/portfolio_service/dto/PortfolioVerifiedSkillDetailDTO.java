package com.exe.skillverse_backend.portfolio_service.dto;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PortfolioVerifiedSkillDetailDTO {
    private Long id;
    private String skillName;
    private String displaySkillName;
    private String verificationSource;
    private Instant verifiedAt;
    private Long reviewerId;
    private String reviewerName;
    private String reviewerRole;
    private String reviewerSlug;
    private String reviewNote;
    private Long journeyId;
    private Long bookingId;
    private Long verificationRequestId;
    private List<PortfolioVerifiedSkillEvidenceDTO> evidences;
}
