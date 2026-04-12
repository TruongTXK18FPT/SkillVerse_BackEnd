package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    
    /**
     * Criteria-based pass/fail (Coursera pattern).
     * True if ALL required criteria meet their passingPoints.
     * Null if not yet graded.
     */
    private Boolean isPassed;
    
    /**
     * The passing score for the assignment (derived from criteria or assignment-level).
     * Sent to FE so it doesn't need to compute.
     */
    private BigDecimal passingScore;

    // AI Grading fields
    private Boolean isAiGraded;
    private Instant aiGradedAt;
    private BigDecimal aiScore;
    private String aiFeedback;
    private Double aiConfidence;
    private Boolean mentorConfirmed;
    private Integer aiGradeAttemptCount;
    private Boolean disputeFlag;
    private Instant disputeAt;
    private String disputeReason;

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
