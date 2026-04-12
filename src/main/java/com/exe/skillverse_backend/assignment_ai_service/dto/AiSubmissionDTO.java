package com.exe.skillverse_backend.assignment_ai_service.dto;

import com.exe.skillverse_backend.course_service.dto.assignmentdto.CriteriaScoreDTO;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSubmissionDTO {
    private Long submissionId;
    private Long assignmentId;
    private String assignmentTitle;
    private Long studentId;
    private String studentName;
    private Long mentorId;
    private String mentorName;
    private BigDecimal aiScore;
    private BigDecimal actualScore;
    private Double aiConfidence;
    private Boolean mentorConfirmed;
    private Boolean isPassed;
    private Boolean disputeFlag;
    private Instant aiGradedAt;
    private Instant gradedAt;
    private Instant submittedAt;
    private String courseName;
    private String moduleName;
    private String submissionType;
    private BigDecimal assignmentMaxScore;
    private Integer aiGradeAttemptCount;
    private String aiFeedback;
    private String mentorFeedback;
    private String disputeReason;
    private BigDecimal scoreDelta;
    private Integer passedCriteriaCount;
    private Integer failedCriteriaCount;
    private List<CriteriaScoreDTO> criteriaScores;
}
