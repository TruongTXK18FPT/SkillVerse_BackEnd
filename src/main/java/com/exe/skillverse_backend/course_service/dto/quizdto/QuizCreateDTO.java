package com.exe.skillverse_backend.course_service.dto.quizdto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuizCreateDTO {
    private String title;
    private String description;
    private Integer passScore;
    private List<QuizQuestionCreateDTO> questions; // Quiz questions to create with the quiz
}
