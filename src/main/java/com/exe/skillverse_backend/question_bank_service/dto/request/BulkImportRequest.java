package com.exe.skillverse_backend.question_bank_service.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportRequest {

    @NotEmpty(message = "Questions list is required")
    private List<QuestionImportItem> questions;

    private String source;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionImportItem {
        private String questionText;
        private List<String> options;
        private String correctAnswer;
        private String explanation;
        private String difficulty;
        private String skillArea;
        private String category;
        private Integer lineNumber;
        private List<String> errors;
        private boolean valid;
    }
}
