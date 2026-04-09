package com.exe.skillverse_backend.student_learning_report_service.dto.response;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO Response cho báo cáo học tập cá nhân của học viên.
 * Cung cấp thông tin chi tiết về kỹ năng, tiến độ và đề xuất cá nhân.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentLearningReportResponse {
    
    private Long id;
    private String reportName;
    private LocalDateTime generatedAt;
    private Long studentId;
    private String studentName;
    private String reportContent;
    private ReportSections sections;
    private StudentMetrics metrics;
    private String reportType;

    // --- Derived/Computed fields ---
    /** Tiến độ tổng thể (0-100), computed từ metrics.averageProgress */
    private Integer overallProgress;
    /** Xu hướng học tập: improving / stable / declining, computed từ so sánh với report trước */
    private String learningTrend;
    /** Đề xuất tập trung, extracted từ AI report content (recommendations/skillGaps) */
    private String recommendedFocus;

    /**
     * Các phần báo cáo được parse từ AI response.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReportSections {
        private String currentSkills;           // Kỹ năng hiện có
        private String learningGoals;           // Mục tiêu học tập
        private String progressSummary;         // Tổng kết tiến độ
        private String strengths;               // Điểm mạnh
        private String areasToImprove;          // Lĩnh vực cần cải thiện
        private String recommendations;         // Khuyến nghị cá nhân
        private String skillGaps;               // Khoảng trống kỹ năng
        private String nextSteps;               // Các bước tiếp theo
        private String motivation;              // Động lực & khích lệ
    }

    /**
     * Các chỉ số đo lường của học viên.
     */
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
        private Integer totalStudyHours;  // Tổng giờ học (chuyển đổi từ minutes)
        private Integer streakDays;
        private Integer currentStreak;    // Alias cho streakDays (frontend expectation)
        private Integer totalChatSessions;
        private Integer totalTasks;
        private Integer completedTasks;
        private Integer totalTasksCompleted;  // Alias cho completedTasks (frontend expectation)
        private Integer totalEnrolledCourses;
        private Integer completedCourses;
        private Integer totalTasksPending;       // = totalTasks - completedTasks
        private List<SkillInfo> topSkills;
        private List<RoadmapProgress> roadmapDetails;
    }

    /**
     * Thông tin kỹ năng của học viên.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillInfo {
        private String skillName;
        private String level;           // Beginner, Intermediate, Advanced, Expert
        private Integer progressPercent;
        private String source;          // Từ roadmap nào
    }

    /**
     * Chi tiết tiến độ từng roadmap.
     */
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
        private Double totalEstimatedHours;   // từ RoadmapSession.totalEstimatedHours
        private Instant createdAt;
        private LocalDateTime lastActivityAt;
    }
}
