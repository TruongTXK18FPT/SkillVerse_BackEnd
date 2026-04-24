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
    private Boolean isVerified;
    private Double hourlyRate;
    private String preferredCurrency;
    private Integer totalProjects;
    private Integer yearsOfExperience;
    private String location;
    private String availabilityStatus;

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
    private Double experienceMatchScore;
    private Double evidenceMatchScore;
    private Double deliveryMatchScore;
    private Double logisticsMatchScore;
    private Double confidenceMatchScore;
    private Double riskPenaltyScore;

    // Detailed breakdown context (for rich UI explanations)
    private List<String> matchedSkills;       // Skills the candidate has that match job requirements
    private List<String> unmatchedSkills;     // Required skills the candidate is missing
    private Integer totalRequiredSkills;       // Total number of required skills from the job
    private Integer totalCandidateSkills;      // Total number of skills the candidate has
    private Integer completedMissionsCount;    // Number of completed missions/journeys
    private Integer totalCertificatesCount;    // Number of certificates
    private Integer totalVerifiedSkillsCount;  // Number of mentor/admin verified skills
    private Integer relevantProjectsCount;     // Projects related to the job requirements
    private Integer relevantCertificatesCount; // Certificates related to the job requirements
    private Integer relevantMissionsCount;     // Completed missions related to the job requirements
    private Double averageMissionRating;       // Average short-term mission rating
    private String fitExplanation;             // Auto-generated explanation text
    private CandidateFitAnalysisDTO fitAnalysis; // Rich recruiter-facing analysis

    // Shortlist fields
    private Long shortlistId;
    private String shortlistStatus;
    private String shortlistNotes;
}
