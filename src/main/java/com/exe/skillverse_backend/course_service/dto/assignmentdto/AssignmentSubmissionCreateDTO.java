package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentSubmissionCreateDTO {
    //Long fileMediaId, String submissionText, String linkUrl
    private Long fileMediaId;
    private String submissionText;
    private String linkUrl;
}
