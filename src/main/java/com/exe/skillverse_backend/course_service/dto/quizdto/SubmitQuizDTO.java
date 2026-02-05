package com.exe.skillverse_backend.course_service.dto.quizdto;

import lombok.*;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitQuizDTO {

    @NotNull(message = "Quiz ID is required")
    private Long quizId;

    @NotNull(message = "Answers are required")
    private List<Answer> answers;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Answer {
        @NotNull(message = "Question ID is required")
        private Long questionId;

        // Backward compatible (single-select)
        private Long selectedOptionId;

        // Preferred multi-select payload
        private List<Long> selectedOptionIds;

        // For short answer questions
        private String textAnswer;
    }
}
