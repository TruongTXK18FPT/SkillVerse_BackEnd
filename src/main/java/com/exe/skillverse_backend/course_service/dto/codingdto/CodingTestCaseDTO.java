package com.exe.skillverse_backend.course_service.dto.codingdto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodingTestCaseDTO {
    private Long id;
    private String kind;
    private BigDecimal scoreWeight;
    private Integer orderIndex;
}
