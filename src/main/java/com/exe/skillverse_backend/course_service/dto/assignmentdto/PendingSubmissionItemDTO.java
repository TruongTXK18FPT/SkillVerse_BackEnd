package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO combining a submission with its course/module/assignment context.
 * Used by the mentor grading dashboard's batch-load endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingSubmissionItemDTO {
    private AssignmentSubmissionDetailDTO submission;
    private String courseName;
    private Long courseId;
    private String moduleName;
    private Long moduleId;
    private String assignmentName;
}
