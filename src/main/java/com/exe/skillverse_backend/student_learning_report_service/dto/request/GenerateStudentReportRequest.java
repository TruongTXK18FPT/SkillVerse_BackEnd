package com.exe.skillverse_backend.student_learning_report_service.dto.request;

import com.exe.skillverse_backend.student_learning_report_service.entity.StudentLearningReport;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO để generate báo cáo học tập cá nhân cho học viên.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateStudentReportRequest {

    /**
     * Loại báo cáo muốn tạo.
     * - COMPREHENSIVE: Báo cáo toàn diện (mặc định)
     * - WEEKLY_SUMMARY: Tóm tắt tuần
     * - MONTHLY_SUMMARY: Tóm tắt tháng
     * - SKILL_ASSESSMENT: Đánh giá kỹ năng
     * - GOAL_TRACKING: Theo dõi mục tiêu
     */
    @Builder.Default
    private StudentLearningReport.ReportType reportType = StudentLearningReport.ReportType.COMPREHENSIVE;

    /**
     * Các kỹ năng cụ thể muốn đánh giá (optional).
     * Nếu cung cấp, báo cáo sẽ tập trung vào các kỹ năng này.
     */
    private String[] focusSkills;

    /**
     * Bao gồm chi tiết roadmap không (mặc định: true).
     */
    @Builder.Default
    private Boolean includeRoadmapDetails = true;

    /**
     * Bao gồm lịch sử chat không (mặc định: true).
     */
    @Builder.Default
    private Boolean includeChatHistory = true;

    /**
     * Bao gồm chi tiết kỹ năng không (mặc định: true).
     */
    @Builder.Default
    private Boolean includeDetailedSkills = true;

    /**
     * Ghi chú cá nhân của học viên (optional).
     * Có thể là những điều học viên muốn AI tập trung phân tích.
     */
    private String personalNotes;
}
