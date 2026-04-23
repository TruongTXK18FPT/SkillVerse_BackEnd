package com.exe.skillverse_backend.journey_service.dto.response;

import com.exe.skillverse_backend.journey_service.entity.Journey;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for journey summary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneySummaryResponse {

    private Long id;
    private String type;
    private String domain;
    private String industry;
    private String subCategory;
    private String jobRole;
    private String goal;
    private Journey.JourneyStatus status;
    private Journey.SkillLevel currentLevel;
    private Integer progressPercentage;
    private String aiSummaryReport;
    private Instant startedAt;
    private Instant completedAt;
    private Instant lastActivityAt;
    private Instant createdAt;

    // Related data
    private Long roadmapSessionId;
    private Integer totalNodesCompleted;
    private List<MilestoneResponse> milestones;
    private TestResultSummaryResponse latestTestResult;

    // V3 Phase 1 — single verified skill tied to this journey
    private String skillName;

    // V3 Phase 1 — final verification gate flag
    private Boolean finalVerificationRequired;

    // Latest test info for assessment flow
    private Long assessmentTestId;
    private String assessmentTestTitle;
    private Integer assessmentTestQuestionCount;
    private String assessmentTestStatus;
    private Integer assessmentAttemptCount;
    private Integer maxAssessmentAttempts;
    private Integer remainingAssessmentRetakes;

    // V3 Phase 3 — single-journey enforcement: whether journey has active mentor booking
    private Boolean hasActiveMentorBooking;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MilestoneResponse {
        private String milestone;
        private Boolean isCompleted;
        private Instant completedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestResultSummaryResponse {
        private Long resultId;
        private Integer scorePercentage;
        private Journey.SkillLevel evaluatedLevel;
        private Integer skillGapsCount;
        private Integer strengthsCount;
        private Instant evaluatedAt;
    }
}
