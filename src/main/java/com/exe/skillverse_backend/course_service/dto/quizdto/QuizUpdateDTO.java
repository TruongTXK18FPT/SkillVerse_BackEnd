package com.exe.skillverse_backend.course_service.dto.quizdto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.exe.skillverse_backend.course_service.entity.enums.QuizGradingMethod;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizUpdateDTO {
    //String title, String description, Integer passScore
    private String title;
    private String description;
    private Integer passScore;
    private Integer maxAttempts;
    private Integer timeLimitMinutes;
    private Integer roundingIncrement;
    private QuizGradingMethod gradingMethod;
    private Boolean isAssessment;
    private Integer cooldownHours;
}
