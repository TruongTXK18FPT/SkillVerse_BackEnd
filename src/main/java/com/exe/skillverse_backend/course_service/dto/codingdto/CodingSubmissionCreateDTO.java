package com.exe.skillverse_backend.course_service.dto.codingdto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodingSubmissionCreateDTO {
    @NotBlank
    private String code;
}
