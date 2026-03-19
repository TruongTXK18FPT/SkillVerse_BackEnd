package com.exe.skillverse_backend.course_service.dto.quizdto;

import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import java.util.List;
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
    private Integer correctOptionCount;
}
