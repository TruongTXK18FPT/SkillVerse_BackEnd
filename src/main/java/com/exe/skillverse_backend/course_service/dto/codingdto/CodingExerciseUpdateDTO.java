package com.exe.skillverse_backend.course_service.dto.codingdto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CodingExerciseUpdateDTO {
    //String title, String prompt, String language, String starterCode, BigDecimal maxScore
    private String title;
    private String prompt;
    private String language;
    private String startedCode;
    private BigDecimal maxScore;
}
