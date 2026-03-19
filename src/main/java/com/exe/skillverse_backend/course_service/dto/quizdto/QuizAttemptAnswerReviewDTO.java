package com.exe.skillverse_backend.course_service.dto.quizdto;

import com.exe.skillverse_backend.course_service.entity.enums.QuestionType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptAnswerReviewDTO {
    private Long questionId;
    private Integer questionOrderIndex;
    private String questionText;
    private QuestionType questionType;
    private QuizAttemptSubmittedAnswerReviewDTO submittedAnswer;
    private List<QuizAttemptAnswerOptionReviewDTO> optionsSnapshot;
    private String submittedAnswerText;
    private String correctAnswerText;
    private Boolean answered;
    private Boolean correct;
    private Integer scoreEarned;
    private Integer maxScore;
}
