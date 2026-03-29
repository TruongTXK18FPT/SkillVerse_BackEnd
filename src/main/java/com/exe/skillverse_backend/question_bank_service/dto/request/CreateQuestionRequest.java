package com.exe.skillverse_backend.question_bank_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class CreateQuestionRequest {

    @NotBlank(message = "Question text is required")
    private String questionText;

    @NotNull(message = "Options are required")
    @Size(min = 4, max = 4, message = "Must have exactly 4 options")
    private List<String> options;

    @NotBlank(message = "Correct answer is required")
    @Pattern(regexp = "^[A-Da-d]$", message = "Correct answer must be A, B, C, or D")
    private String correctAnswer;

    private String explanation;

    @NotBlank(message = "Difficulty is required")
    private String difficulty;

    private String skillArea;
    private String category;
}
