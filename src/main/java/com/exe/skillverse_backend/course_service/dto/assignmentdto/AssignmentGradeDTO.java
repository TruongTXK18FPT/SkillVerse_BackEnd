package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentGradeDTO {
    private BigDecimal score;
    private String feedback;
    private List<CriteriaScoreDTO> criteriaScores;
}
