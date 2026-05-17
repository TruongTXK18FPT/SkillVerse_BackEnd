package com.exe.skillverse_backend.question_bank_service.dto.response;

import com.exe.skillverse_backend.question_bank_service.entity.enums.QuestionBankSubmissionSource;
import com.exe.skillverse_backend.question_bank_service.entity.enums.QuestionBankSubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionBankSubmissionResponse {

    private Long id;
    private Long mentorId;
    private String mentorName;
    private String mentorEmail;
    private String mentorAvatarUrl;
    private String mentorPortfolioSlug;
    private Long domainId;
    private Long jobPositionId;
    private Long skillId;
    private String domain;
    private String domainName;
    private String jobPositionName;
    private String skillName;
    private String title;
    private String description;
    private String difficultyDistribution;
    private QuestionBankSubmissionStatus status;
    private QuestionBankSubmissionSource source;
    private Integer questionCount;
    private Integer savedQuestionCount;
    private Integer duplicateQuestionCount;
    private Long resolvedQuestionBankId;
    private String resolvedQuestionBankTitle;
    private String reviewNote;
    private Long reviewedById;
    private String reviewedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime reviewedAt;
    private List<QuestionItemResponse> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionItemResponse {
        private Long id;
        private Integer displayOrder;
        private String questionText;
        private List<String> options;
        private String correctAnswer;
        private String explanation;
        private String difficulty;
        private String skillArea;
        private String category;
    }
}
