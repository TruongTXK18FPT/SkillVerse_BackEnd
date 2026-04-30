package com.exe.skillverse_backend.student_learning_report_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StudentLearningReportResponse {

    private Long id;
    private Long reportId;
    private String reportName;
    private LocalDateTime generatedAt;
    private Long studentId;
    private String studentName;
    private String reportType;
    private String range;
    private Boolean snapshot;

    private Overview overview;
    private StudyStats studyStats;
    private RoadmapStats roadmapStats;
    private TaskStats taskStats;
    private CourseStats courseStats;
    private ShortTermJobStats jobStats;
    private List<RoadmapBreakdownItem> roadmapBreakdown;
    private List<CourseBreakdownItem> courseBreakdown;
    private List<JobBreakdownItem> jobBreakdown;
    private List<TimelinePoint> timeline;
    private Map<String, List<TimelinePoint>> timelineByRange;

    // Compatibility aliases for existing callers during transition.
    private String reportContent;
    private ReportSections sections;
    private StudentMetrics metrics;
    private Integer overallProgress;
    private String learningTrend;
    private String recommendedFocus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Overview {
        private Integer overallProgress;
        private String learningTrend;

        @JsonDeserialize(contentUsing = RecommendationDeserializer.class)
        private List<Recommendation> recommendations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Recommendation {
        /** Stable identifier for the rule that produced this recommendation. */
        private String id;
        /** Tier: CRITICAL | IMPROVE | NEXT_STEP | STRENGTH. */
        private String tier;
        /** Category: STUDY | ROADMAP | TASK | COURSE | JOB | GROWTH. */
        private String category;
        /** Short headline for the recommendation card. */
        private String title;
        /** Data-driven observation that supports the recommendation. */
        private String analysis;
        /** Concrete, measurable action the learner should take. */
        private String action;
        /** Optional metric label, e.g. "On-time delivery". */
        private String metricLabel;
        /** Current metric value (number). */
        private Number metricValue;
        /** Target metric value (number). */
        private Number metricTarget;
        /** Unit shown next to metric, e.g. "%", "phút/tuần". */
        private String metricUnit;
        /** Optional deep-link path on the frontend, e.g. "/roadmap" or "/tasks". */
        private String linkPath;
        /** Label for the deep-link CTA button. */
        private String linkLabel;
    }

    /**
     * Backward-compatible deserializer: accepts either the new
     * {@link Recommendation} object or a legacy plain string emitted by
     * older snapshots stored in {@code summary_snapshot}.
     */
    public static class RecommendationDeserializer extends JsonDeserializer<Recommendation> {
        @Override
        public Recommendation deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
            JsonToken token = parser.currentToken();
            if (token == JsonToken.VALUE_STRING) {
                String legacyText = parser.getValueAsString();
                return Recommendation.builder()
                        .id("legacy")
                        .tier("IMPROVE")
                        .category("GROWTH")
                        .title(legacyText)
                        .build();
            }
            JsonNode node = parser.readValueAsTree();
            if (node == null || node.isNull()) {
                return null;
            }
            Recommendation.RecommendationBuilder builder = Recommendation.builder()
                    .id(text(node, "id"))
                    .tier(text(node, "tier"))
                    .category(text(node, "category"))
                    .title(text(node, "title"))
                    .analysis(text(node, "analysis"))
                    .action(text(node, "action"))
                    .metricLabel(text(node, "metricLabel"))
                    .metricUnit(text(node, "metricUnit"))
                    .linkPath(text(node, "linkPath"))
                    .linkLabel(text(node, "linkLabel"));
            JsonNode value = node.get("metricValue");
            if (value != null && value.isNumber()) {
                builder.metricValue(value.numberValue());
            }
            JsonNode target = node.get("metricTarget");
            if (target != null && target.isNumber()) {
                builder.metricTarget(target.numberValue());
            }
            return builder.build();
        }

        private String text(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null || value.isNull() ? null : value.asText();
        }
    }

    /** Convenience helper used by service code to coerce builder lists. */
    @SuppressWarnings("unused")
    private static List<Recommendation> ensureRecommendations(List<Recommendation> input) {
        return input == null ? new ArrayList<>() : input;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudyStats {
        private Integer studyMinutesToday;
        private Integer studyMinutesWeek;
        private Integer studyMinutesMonth;
        private Integer totalStudyHours;
        private Integer currentStreak;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoadmapStats {
        private Integer totalRoadmaps;
        private Integer completedRoadmaps;
        private Integer inProgressRoadmaps;
        private Integer totalMissions;
        private Integer completedMissions;
        private Integer pendingMissions;
        private Integer roadmapProgress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskStats {
        private Integer totalTasks;
        private Integer completedTasks;
        private Integer pendingTasks;
        private Integer overdueTasks;
        private Integer taskProgress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseStats {
        private Integer activeCourses;
        private Integer completedCourses;
        private Integer averageActiveCourseProgress;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoadmapBreakdownItem {
        private Long roadmapId;
        private String title;
        private String goal;
        private String status;
        private Integer totalMissions;
        private Integer completedMissions;
        private Integer pendingMissions;
        private Integer progressPercent;
        private String nextMissionTitle;
        private LocalDateTime lastCompletedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseBreakdownItem {
        private Long courseId;
        private String courseTitle;
        private String status;
        private Integer progressPercent;
        private LocalDateTime completedAt;
        private LocalDateTime enrolledAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShortTermJobStats {
        private Integer totalJobsApplied;
        private Integer completedJobs;
        private Integer inProgressJobs;
        private Integer pendingApplications;
        private Integer rejectedApplications;
        private BigDecimal totalEarnings;
        private Double averageRating;
        private Integer totalMilestonesDelivered;
        private Integer onTimeDeliveryRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobBreakdownItem {
        private Long jobId;
        private String jobTitle;
        private String recruiterName;
        private String status;
        private BigDecimal budget;
        private BigDecimal earnedAmount;
        private Integer milestonesTotal;
        private Integer milestonesCompleted;
        private LocalDateTime appliedAt;
        private LocalDateTime completedAt;
        private Double rating;
        private String primarySkill;
        private List<String> skillsDemonstrated;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimelinePoint {
        private String bucketLabel;
        private LocalDate bucketStart;
        private Integer studyMinutes;
        private Integer missionsCompleted;
        private Integer tasksCompleted;
        private Integer jobsCompleted;
        private BigDecimal earnings;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportSections {
        private String currentSkills;
        private String learningGoals;
        private String progressSummary;
        private String strengths;
        private String areasToImprove;
        private String recommendations;
        private String skillGaps;
        private String nextSteps;
        private String motivation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StudentMetrics {
        private Integer totalRoadmaps;
        private Integer completedRoadmaps;
        private Integer inProgressRoadmaps;
        private Integer averageProgress;
        private Integer totalStudyMinutesToday;
        private Integer totalStudyMinutesWeek;
        private Integer totalStudyMinutesMonth;
        private Integer totalStudyHours;
        private Integer streakDays;
        private Integer currentStreak;
        private Integer totalChatSessions;
        private Integer totalTasks;
        private Integer completedTasks;
        private Integer totalTasksCompleted;
        private Integer totalTasksPending;
        private Integer totalEnrolledCourses;
        private Integer completedCourses;
        private List<SkillInfo> topSkills;
        private List<RoadmapProgress> roadmapDetails;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillInfo {
        private String skillName;
        private String level;
        private Integer progressPercent;
        private String source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoadmapProgress {
        private Long roadmapId;
        private String title;
        private String goal;
        private Integer totalQuests;
        private Integer completedQuests;
        private Integer progressPercent;
        private Double totalEstimatedHours;
        private Instant createdAt;
        private LocalDateTime lastActivityAt;
    }
}
