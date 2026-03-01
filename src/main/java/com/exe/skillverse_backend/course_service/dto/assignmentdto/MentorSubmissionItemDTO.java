package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO combining a newest submission with its course/module/assignment context.
 * Used by mentor submission dashboards that need both pending and graded items.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentorSubmissionItemDTO {
    private AssignmentSubmissionDetailDTO submission;
    private String courseName;
    private Long courseId;
    private String moduleName;
    private Long moduleId;
    private String assignmentName;
    private Instant assignmentDueAt;
}
