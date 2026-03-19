package com.exe.skillverse_backend.course_service.dto.quizdto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptSubmittedAnswerReviewDTO {
    private List<Long> selectedOptionIds;
    private List<String> selectedOptionTexts;
    private String textAnswer;
}
