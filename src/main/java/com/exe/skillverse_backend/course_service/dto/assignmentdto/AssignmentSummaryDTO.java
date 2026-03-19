package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentSummaryDTO {
    private Long id;
    private String title;
    private String description;
    private SubmissionType submissionType;
    private BigDecimal maxScore;
    private Instant dueAt;
    private Long moduleId;
    private Integer orderIndex; // For ordering in the UI
}
