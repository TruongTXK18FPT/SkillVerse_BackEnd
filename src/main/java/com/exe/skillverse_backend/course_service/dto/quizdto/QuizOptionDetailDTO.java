package com.exe.skillverse_backend.course_service.dto.quizdto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizOptionDetailDTO {
    //Long id, String optionText, boolean isCorrect, String feedback
    private Long id;
    private String optionText;
    private boolean isCorrect;
    private String feedback;
}
