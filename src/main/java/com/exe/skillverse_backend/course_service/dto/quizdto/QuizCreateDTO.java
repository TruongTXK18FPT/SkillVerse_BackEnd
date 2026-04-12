package com.exe.skillverse_backend.course_service.dto.quizdto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.exe.skillverse_backend.course_service.entity.enums.QuizGradingMethod;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuizCreateDTO {
    private String title;
    private String description;
    private Integer orderIndex;
    private Integer passScore;
    private Integer maxAttempts;
    private Integer timeLimitMinutes;
    private Integer roundingIncrement;
    private QuizGradingMethod gradingMethod;
    private Integer cooldownHours;
    private List<QuizQuestionCreateDTO> questions; // Quiz questions to create with the quiz
}
