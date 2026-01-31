package com.exe.skillverse_backend.course_service.dto.assignmentdto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentCriteriaDTO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal maxPoints;
    private Integer orderIndex;
    private boolean isRequired;
}
