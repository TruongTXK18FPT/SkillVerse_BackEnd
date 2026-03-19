package com.exe.skillverse_backend.course_service.dto.quizdto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuizCreateDTO {
    private String title;
    private String description;
    private Integer passScore;
    private Integer maxAttempts;
    private Integer timeLimitMinutes;
    private Integer roundingIncrement;
    private com.exe.skillverse_backend.course_service.entity.enums.QuizGradingMethod gradingMethod;
    private Boolean isAssessment;
    private Integer cooldownHours;
    private List<QuizQuestionCreateDTO> questions; // Quiz questions to create with the quiz
}
