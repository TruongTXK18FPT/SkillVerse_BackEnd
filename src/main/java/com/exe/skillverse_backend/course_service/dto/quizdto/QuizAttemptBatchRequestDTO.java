package com.exe.skillverse_backend.course_service.dto.quizdto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttemptBatchRequestDTO {
    @NotEmpty
    private List<Long> quizIds;
    @NotNull
    private Long userId;
}
