package com.exe.skillverse_backend.admin_service.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminJourneyDashboardResponse {

    private Long totalJourneys;
    private Long totalAssessmentTests;
    private Long totalEvaluations;
    private Long totalQuestionBanks;
    private Long totalActiveQuestions;
    private Long bankLinkedTests;
    private Long aiGeneratedTests;
    private Long legacyUnlinkedTests;
    private Long recoveryReadyTests;
    private Long roadmapReadyJourneys;
    private Long questionSelectionsServed;
    private Double bankLinkedCoverageRate;
    private Double recoveryCoverageRate;
    private List<MetricBreakdownItem> assessmentFunnel;
    private List<MetricBreakdownItem> journeyStatusBreakdown;
    private List<MetricBreakdownItem> journeyTypeBreakdown;
    private List<MetricBreakdownItem> testSourceBreakdown;
    private List<MetricBreakdownItem> difficultyBreakdown;
    private List<MetricBreakdownItem> questionAuthoringSourceBreakdown;
    private List<MetricBreakdownItem> skillAreaBreakdown;
    private List<TopBankItem> topBanks;
    private List<TopQuestionItem> topQuestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricBreakdownItem {
        private String label;
        private Long value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopBankItem {
        private Long questionBankId;
        private String title;
        private String domain;
        private String industry;
        private String jobRole;
        private Long activeQuestionCount;
        private Long totalQuestionUsage;
        private Long linkedAssessmentTestCount;
        private Long linkedJourneyCount;
        private Long questionVolumeServed;
        private Double averageScore;
        private Instant lastUsedAt;
        private String readinessStatus;
        private String readinessReason;
        private Map<String, Long> difficultyBreakdown;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopQuestionItem {
        private Long questionId;
        private Long questionBankId;
        private String questionBankTitle;
        private String domain;
        private String industry;
        private String jobRole;
        private String questionText;
        private String difficulty;
        private String skillArea;
        private String category;
        private String source;
        private Integer usedCount;
        private Boolean isActive;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
