package com.exe.skillverse_backend.question_bank_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiDraftResponse {

    private List<QuestionDraft> drafts;
    private int totalGenerated;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionDraft {
        private int draftId;
        private String questionText;
        private List<String> options;
        private String correctAnswer;
        private String explanation;
        private String difficulty;
        private String skillArea;
        private String category;
        private boolean edited;
    }
}
