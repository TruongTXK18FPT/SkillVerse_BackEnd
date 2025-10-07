package com.exe.skillverse_backend.course_service.dto.quizdto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizOptionCreateDTO {
    //@NotBlank optionText, boolean correct, String feedback
    @NotBlank
    private String optionText;
    private boolean correct;
    private String feedback;
}
