package com.exe.skillverse_backend.course_service.dto.quizdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptSubmittedAnswerReviewDTO {
    private List<Long> selectedOptionIds;
    private List<String> selectedOptionTexts;
    private String textAnswer;
}
