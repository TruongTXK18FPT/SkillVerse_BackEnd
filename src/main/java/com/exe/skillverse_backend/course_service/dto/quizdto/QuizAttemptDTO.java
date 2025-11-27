package com.exe.skillverse_backend.course_service.dto.quizdto;

import lombok.*;
import java.time.Instant;

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
