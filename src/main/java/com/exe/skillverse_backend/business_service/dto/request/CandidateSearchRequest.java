package com.exe.skillverse_backend.business_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for candidate search
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateSearchRequest {

    // Search query (skill, title, keyword)
    private String query;

    // Filters
    private String skills; // Comma-separated skills
    private Integer minExperience;
    private Integer maxExperience;
    private String jobType; // FULL_TIME, PART_TIME, etc.
    private Boolean isRemote;
    private String location;
    private String experienceLevel; // Junior, Senior, etc.
    private Integer minHourlyRate;
    private Integer maxHourlyRate;
    private Boolean openToOffers; // Only candidates open to job offers
    private Boolean hasPortfolio; // Only candidates with portfolio
    private Boolean hasCertificates; // Only candidates with certificates

    // For matching with specific job
    private Long jobId; // Match candidates to this long-term job posting
    private Long shortTermJobId; // Match candidates to this short-term job (gig/freelance)

    // Pagination
    @Builder.Default
    private Integer page = 0;
    @Builder.Default
    private Integer size = 20;

    // Sorting
    @Builder.Default
    private String sortBy = "totalScore"; // totalScore, experience, lastActive, matchQuality
    @Builder.Default
    private String sortOrder = "DESC"; // ASC, DESC

    // AI Enhancement
    @Builder.Default
    private Boolean enableAIMatching = false;
}
