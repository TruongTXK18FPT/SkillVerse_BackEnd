package com.exe.skillverse_backend.course_service.dto.quizdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptAnswerOptionReviewDTO {
    private Long optionId;
    private Integer orderIndex;
    private String optionText;
    private Boolean correct;
    private Boolean selected;
    private String feedback;
}
