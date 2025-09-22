package com.exe.skillverse_backend.course_service.dto.quizdto;

import java.util.List;

import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestionDetailDTO {
    private Long id;
    private String questionText;
    private QuestionType questionType;
    private Integer score;
    private Integer orderIndex;
    private List<QuizOptionDetailDTO> options;
}
