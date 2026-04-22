package com.exe.skillverse_backend.portfolio_service.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateSummaryDTO {
    private Long userId;
    private String fullName;
    private String professionalTitle;
    private String avatarUrl;
    private String customUrlSlug;
    private String topSkills; // Keep as JSON string or convert to List<String> if preferred, matching entity
                              // for now
    private boolean isHighlighted;
    private Double hourlyRate;
    private String preferredCurrency;
    private Integer totalProjects;

    // Matching/scoring fields (for recruiter search)
    private Double matchScore;
    private String matchQuality; // EXCELLENT, GOOD, FAIR, POOR
    private Integer skillMatchPercent;
    private String aiFitSummary; // AI-generated match explanation

    // Deterministic ranking breakdown
    private Boolean primarySkillMatch;
    private Double skillMatchScore;
    private Double projectMatchScore;
    private Double certMatchScore;
    private Double missionMatchScore;

    // Shortlist fields
    private Long shortlistId;
    private String shortlistStatus;
    private String shortlistNotes;
}
