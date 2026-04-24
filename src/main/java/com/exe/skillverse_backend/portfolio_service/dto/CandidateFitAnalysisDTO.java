package com.exe.skillverse_backend.portfolio_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateFitAnalysisDTO {

    private Double overallScore;
    private String band;
    private String recommendation;
    private Double confidenceScore;
    private Double riskPenalty;

    @Builder.Default
    private List<ComponentScoreDTO> components = new ArrayList<>();

    @Builder.Default
    private List<SkillBreakdownDTO> skillBreakdown = new ArrayList<>();

    @Builder.Default
    private List<EvidenceHighlightDTO> evidenceHighlights = new ArrayList<>();

    @Builder.Default
    private List<MissingRequirementDTO> missingRequirements = new ArrayList<>();

    @Builder.Default
    private List<String> riskFlags = new ArrayList<>();

    @Builder.Default
    private List<String> interviewQuestions = new ArrayList<>();

    @Builder.Default
    private List<String> nextActions = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComponentScoreDTO {
        private String key;
        private String label;
        private Double score;
        private Double weight;
        private Double weightedScore;
        private String explanation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillBreakdownDTO {
        private String skill;
        private Boolean primary;
        private Boolean required;
        private Boolean matched;
        private String matchType;
        private Double relevanceScore;
        private Double confidenceScore;

        @Builder.Default
        private List<String> evidenceSources = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceHighlightDTO {
        private String type;
        private String title;
        private Double relevanceScore;

        @Builder.Default
        private List<String> matchedSkills = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingRequirementDTO {
        private String skill;
        private String severity;
        private String suggestion;
    }
}
