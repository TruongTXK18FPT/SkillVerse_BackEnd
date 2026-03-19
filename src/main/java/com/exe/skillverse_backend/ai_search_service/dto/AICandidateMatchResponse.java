package com.exe.skillverse_backend.ai_search_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for AI candidate matching
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AICandidateMatchResponse {

    private Long jobId;
    private Long candidateId;

    // AI-generated fit summary (1-2 sentences)
    private String fitSummary;

    // Extracted skill signals from candidate profile
    private List<SkillSignal> skillSignals;

    // AI reasoning for the match
    private String reasoning;

    // Confidence score (0-1)
    private Double confidenceScore;

    // Overall match quality
    private MatchQuality matchQuality;

    // Processing metadata
    private String modelUsed;
    private Long processingTimeMs;
    private Boolean isFallback;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillSignal {
        private String skill;
        private String evidence; // Where the skill was found
        private Boolean isRequired; // Whether skill is required in JD
        private Double relevanceScore; // 0-1 relevance to job
    }

    public enum MatchQuality {
        EXCELLENT,  // 0.8-1.0
        GOOD,       // 0.6-0.8
        FAIR,       // 0.4-0.6
        POOR        // 0.0-0.4
    }
}
