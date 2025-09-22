package com.exe.skillverse_backend.course_service.dto.quizdto;

import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestionUpdateDTO {
    //String questionText, QuestionType questionType, Integer score, Integer orderIndex
    private String questionText;
    private QuestionType questionType;
    private Integer score;
    private Integer orderIndex;
}
