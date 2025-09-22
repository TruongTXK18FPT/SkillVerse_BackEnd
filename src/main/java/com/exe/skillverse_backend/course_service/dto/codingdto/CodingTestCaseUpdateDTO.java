package com.exe.skillverse_backend.course_service.dto.codingdto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodingTestCaseUpdateDTO {
    // This class is intentionally left empty.
    //String kind, String input, String expectedOutput, BigDecimal scoreWeight, Integer orderIndex
    private String kind; // PUBLIC/HIDDEN
    private String input;
    private String expectedOutput;
    private BigDecimal scoreWeight;
    private Integer orderIndex;
}
