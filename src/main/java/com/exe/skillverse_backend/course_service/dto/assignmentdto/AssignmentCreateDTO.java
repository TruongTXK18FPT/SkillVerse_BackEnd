package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentCreateDTO {
    //@NotBlank title, @NotNull SubmissionType submissionType, BigDecimal maxScore, Instant dueAt, String description
    @NotBlank
    private String title;
    @NotNull
    private SubmissionType submissionType;
    private Integer orderIndex;
    @NotNull
    private BigDecimal maxScore;
    private BigDecimal passingScore;
    private Instant dueAt;
    private String description;
    private Boolean isRequired;
    private String learningOutcome;
    private String gradingCriteria;
    private List<AssignmentCriteriaDTO> criteria;
}
