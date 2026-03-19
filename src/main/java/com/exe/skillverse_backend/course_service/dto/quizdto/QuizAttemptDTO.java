package com.exe.skillverse_backend.course_service.dto.quizdto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptDTO {
    private Long id;
    private Long quizId;
    private String quizTitle;
    private Long userId;
    private Integer score;
    private Boolean passed;
    private Integer correctAnswers;
    private Integer totalQuestions;
    private Instant submittedAt;
    private Instant createdAt;
}
