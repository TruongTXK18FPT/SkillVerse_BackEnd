package com.exe.skillverse_backend.student_learning_report_service.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningReportTimelineResponse {

    private String range;
    private Long snapshotId;
    private LocalDateTime generatedAt;
    private List<StudentLearningReportResponse.TimelinePoint> timeline;
}
