package com.exe.skillverse_backend.student_learning_report_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
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
        private List<String> recommendations;
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
