package com.exe.skillverse_backend.course_service.dto.quizdto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizOptionUpdateDTO {
    private String optionText;
    private boolean correct;
    private String feedback;
}
