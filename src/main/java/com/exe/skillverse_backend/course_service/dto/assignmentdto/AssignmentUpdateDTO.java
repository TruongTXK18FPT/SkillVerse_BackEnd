package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import java.math.BigDecimal;
import java.time.Instant;

import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentUpdateDTO {
    private String title;
    private SubmissionType submissionType;
    private BigDecimal maxScore;
    private Instant dueAt;
    private String description;

}
