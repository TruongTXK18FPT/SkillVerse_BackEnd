package com.exe.skillverse_backend.question_bank_service.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuestionRequest {

    private String questionText;

    @Size(min = 4, max = 4, message = "Must have exactly 4 options")
    private List<String> options;

    @Pattern(regexp = "^[A-Da-d]$", message = "Correct answer must be A, B, C, or D")
    private String correctAnswer;

    private String explanation;
    private String difficulty;
    private String skillArea;
    private String category;
    private Boolean isActive;
}
