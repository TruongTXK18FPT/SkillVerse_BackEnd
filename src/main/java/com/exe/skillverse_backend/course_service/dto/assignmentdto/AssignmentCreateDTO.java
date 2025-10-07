package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import java.math.BigDecimal;
import java.time.Instant;

import com.exe.skillverse_backend.course_service.entity.enums.SubmissionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotNull
    private BigDecimal maxScore;
    private Instant dueAt;
    private String description;
}
