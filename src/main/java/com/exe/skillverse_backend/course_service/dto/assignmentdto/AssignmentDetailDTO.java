package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import java.math.BigDecimal;
import java.time.Instant;

import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;

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
    private BigDecimal maxScore;
    private Instant dueAt;
    private String description;
}
