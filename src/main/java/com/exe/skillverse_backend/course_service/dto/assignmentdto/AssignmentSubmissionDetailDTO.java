package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentSubmissionDetailDTO {
    private Long id;
    private Long assignmentId;
    private String assignmentTitle;
    private Long userId;
    private String userName;
    private Long fileMediaId;
    private String fileMediaUrl;
    private String linkUrl;
    private String submissionText;
    private Instant submittedAt;
    private BigDecimal score;
    private BigDecimal maxScore;
    private String feedback;
    private Long gradedBy;
    private String gradedByName;
    private Instant gradedAt;
    
    // Criteria scores for detailed grading
    private List<CriteriaScoreDTO> criteriaScores;
    
    // Version tracking (Coursera pattern)
    private Integer attemptNumber;
    private Boolean isNewest;
    private Boolean isPrevious;
    private Boolean isLate;
    
    // Derived field for frontend status display
    public String getStatus() {
        if (score != null) {
            return "GRADED";
        } else if (Boolean.TRUE.equals(isLate)) {
            return "LATE_PENDING";
        } else {
            return "PENDING";
        }
    }
}
