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
public class AssignmentCriteriaDTO {
    private Long id;
    private String clientId;
    private String name;
    private String description;
    private BigDecimal maxPoints;
    private BigDecimal passingPoints; // Minimum score to pass this criterion (Coursera pattern)
    private Integer orderIndex;
    private boolean isRequired;
}
