package com.exe.skillverse_backend.question_bank_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SkillResolveResponse {

    private String skillName;
    private String domain;
    private String industry;
    private String jobRole;
    private int confidence;
    private String reasoning;

    /** True if a question bank already exists for this scope + skill */
    private boolean questionBankExists;
    private Long existingQuestionBankId;
    private String existingQuestionBankTitle;

    /** Populated if the user asked to auto-create via /resolve-and-create */
    private Long createdQuestionBankId;
    private String createdQuestionBankTitle;

    /** Alternative matches if confidence < 100 */
    private List<AlternativeMatch> alternatives;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AlternativeMatch {
        private String domain;
        private String industry;
        private String jobRole;
        private int confidence;
    }
}
