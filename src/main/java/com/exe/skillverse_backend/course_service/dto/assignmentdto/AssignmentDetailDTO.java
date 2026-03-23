package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssignmentDetailDTO {
    //Long id, String title, SubmissionType submissionType, BigDecimal maxScore, Instant dueAt, String description
    private Long id;
    private String title;
    private SubmissionType submissionType;
    private Integer orderIndex;
    private BigDecimal maxScore;
    private BigDecimal passingScore;
    private Instant dueAt;
    private String description;
    private Boolean isRequired;
    private String learningOutcome;
    private String gradingCriteria;
    private List<AssignmentCriteriaDTO> criteria;
}
