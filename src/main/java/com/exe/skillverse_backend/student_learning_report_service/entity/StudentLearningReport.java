package com.exe.skillverse_backend.student_learning_report_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity lưu trữ báo cáo học tập cá nhân của học viên.
 * Đây là báo cáo tự động phân tích dựa trên dữ liệu học tập,
 * giúp học viên hiểu rõ tiến độ, điểm mạnh và điểm cần cải thiện.
 */
@Entity
@Table(name = "student_learning_reports", indexes = {
    @Index(columnList = "student_id, generated_at DESC"),
    @Index(columnList = "report_type")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentLearningReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "student_name", nullable = false)
    private String studentName;

    @Lob
    @Column(name = "report_content", columnDefinition = "TEXT")
    private String reportContent;

    // === Parsed Sections ===
    
    @Lob
    @Column(name = "current_skills_section", columnDefinition = "TEXT")
    private String currentSkillsSection;

    @Lob
    @Column(name = "learning_goals_section", columnDefinition = "TEXT")
    private String learningGoalsSection;

    @Lob
    @Column(name = "progress_section", columnDefinition = "TEXT")
    private String progressSection;

    @Lob
    @Column(name = "strengths_section", columnDefinition = "TEXT")
    private String strengthsSection;

    @Lob
    @Column(name = "areas_to_improve_section", columnDefinition = "TEXT")
    private String areasToImproveSection;

    @Lob
    @Column(name = "recommendations_section", columnDefinition = "TEXT")
    private String recommendationsSection;

    @Lob
    @Column(name = "skill_gaps_section", columnDefinition = "TEXT")
    private String skillGapsSection;

    @Lob
    @Column(name = "next_steps_section", columnDefinition = "TEXT")
    private String nextStepsSection;

    @Lob
    @Column(name = "motivation_section", columnDefinition = "TEXT")
    private String motivationSection;

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private LocalDateTime generatedAt;

    @Column(name = "is_ai_generated")
    @Builder.Default
    private Boolean isAiGenerated = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    @Builder.Default
    private ReportType reportType = ReportType.COMPREHENSIVE;

    /** Snapshot tiến độ trung bình tại thời điểm tạo báo cáo */
    @Column(name = "average_progress_snapshot")
    private Integer averageProgressSnapshot;

    /** Xu hướng học tập tại thời điểm tạo */
    @Column(name = "learning_trend", length = 20)
    private String learningTrend;

    /** Đề xuất tập trung (extracted từ AI content) */
    @Lob
    @Column(name = "recommended_focus", columnDefinition = "TEXT")
    private String recommendedFocus;

    /** Tổng giờ học tại thời điểm tạo */
    @Column(name = "total_study_hours_snapshot")
    private Integer totalStudyHoursSnapshot;

    /** Streak ngày tại thời điểm tạo */
    @Column(name = "streak_days_snapshot")
    private Integer streakDaysSnapshot;

    /** Số tasks hoàn thành tại thời điểm tạo */
    @Column(name = "tasks_completed_snapshot")
    private Integer tasksCompletedSnapshot;

    /**
     * Loại báo cáo học tập cá nhân.
     */
    public enum ReportType {
        COMPREHENSIVE,      // Báo cáo toàn diện
        WEEKLY_SUMMARY,     // Tóm tắt tuần
        MONTHLY_SUMMARY,    // Tóm tắt tháng
        SKILL_ASSESSMENT,   // Đánh giá kỹ năng
        GOAL_TRACKING       // Theo dõi mục tiêu
    }
}
