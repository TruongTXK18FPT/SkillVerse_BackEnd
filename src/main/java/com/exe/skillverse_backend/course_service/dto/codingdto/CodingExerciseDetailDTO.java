package com.exe.skillverse_backend.course_service.dto.codingdto;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodingExerciseDetailDTO {
    //Long id, String title, String prompt, String language, BigDecimal maxScore, List<CodingTestCaseDto> testCases
    private Long id;
    private String title;
    private String prompt;
    private String language;
    private String startedCode;
    private BigDecimal maxScore;
    private List<CodingTestCaseDTO> testCases;
}
