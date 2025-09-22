package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentSubmissionDetailDTO {
    //Long id, Long userId, String userName, Long fileMediaId, String linkUrl, String submissionText, Instant submittedAt, BigDecimal score, String feedback, Long gradedBy
    private Long id;
    private Long userId;
    private String userName;
    private Long fileMediaId;
    private String linkUrl;
    private String submissionText;
    private Instant submittedAt;
    private BigDecimal score;
    private String feedback;
    private Long gradedBy;
}
