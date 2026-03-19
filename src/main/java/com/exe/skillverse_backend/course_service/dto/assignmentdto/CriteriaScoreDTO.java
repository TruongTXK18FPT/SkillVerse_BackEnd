package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CriteriaScoreDTO {
    private Long id;
    private Long criteriaId;
    private String criteriaName;
    private BigDecimal score;
    private BigDecimal maxPoints;
    private BigDecimal passingPoints; // Min score to pass this criterion
    private Boolean passed;          // Whether this criterion is passed
    private String feedback;
}
