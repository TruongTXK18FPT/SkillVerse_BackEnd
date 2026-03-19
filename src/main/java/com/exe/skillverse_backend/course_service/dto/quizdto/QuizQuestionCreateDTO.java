package com.exe.skillverse_backend.course_service.dto.quizdto;

import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizQuestionCreateDTO {
    //@NotBlank questionText, @NotNull QuestionType questionType, @Min(1) int score, Integer orderIndex, List<QuizOptionCreateDto> options
    private Long id;
    private String clientId;
    @NotBlank
    private String questionText;
    @NotNull
    private QuestionType questionType;
    @Min(1)
    private int score;
    private Integer orderIndex;
    private List<QuizOptionCreateDTO> options;
}
