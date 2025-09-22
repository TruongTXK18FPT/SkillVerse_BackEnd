package com.exe.skillverse_backend.course_service.dto.codingdto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodingTestCaseCreateDTO {
    //@NotBlank kind, @NotBlank input, @NotBlank expectedOutput, @NotNull BigDecimal scoreWeight, Integer orderIndex
    @NotBlank
    private String kind; // PUBLIC/HIDDEN
    @NotBlank
    private String input;
    @NotBlank
    private String expectedOutput;
    @NotNull
    private BigDecimal scoreWeight;
    private Integer orderIndex;
}
