package com.exe.skillverse_backend.parent_service.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LearningReportResponse {
    private Long id;
    private LocalDateTime generatedAt;
    private Long studentId;
    private String studentName;
    private String reportContent;
    private ReportSections sections;

    @Data
    @Builder
    public static class ReportSections {
        private String learningGoals;
        private String achievements;
        private String learningBehavior;
        private String strengths;
        private String risksAndGaps;
        private String recommendations;
    }
}
