package com.exe.skillverse_backend.course_service.dto.codingdto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CodingExerciseCreateDTO {
    @NotBlank
    private String title;
    @NotBlank
    //prompt
    private String prompt;
    private String language;
    private String startedCode;
    private BigDecimal maxScore;
}
