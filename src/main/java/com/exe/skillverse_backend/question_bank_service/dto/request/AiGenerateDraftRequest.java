package com.exe.skillverse_backend.question_bank_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGenerateDraftRequest {

    @Builder.Default
    private Integer questionCount = 25;

    private Map<String, Double> difficultyDistribution;

    private List<String> focusSkillAreas;
}
