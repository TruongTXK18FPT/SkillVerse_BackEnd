package com.exe.skillverse_backend.ai_search_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for AI candidate match generation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AICandidateMatchRequest {

    private Long jobId;
    private Long candidateId;

    // Job details
    private String jobTitle;
    private String jobDescription;
    private String requiredSkills; // JSON array
    private String minBudget;
    private String maxBudget;
    private String experienceLevel;
    private String jobType;

    // Candidate details
    private String candidateName;
    private String professionalTitle;
    private String bio;
    private String topSkills; // JSON array
    private Integer yearsOfExperience;
    private String hourlyRate;
    private Integer totalProjects;
    private Integer totalCertificates;

    // Optional: User's skill wallet, roadmap progress for personalization
    private String skillWalletSkills;
    private String activeRoadmapSkills;
}
