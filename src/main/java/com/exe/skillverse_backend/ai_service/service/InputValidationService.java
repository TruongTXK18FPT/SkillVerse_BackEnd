package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion;
import com.exe.skillverse_backend.ai_service.dto.response.ValidationResult;

import java.util.List;

public interface InputValidationService {

    List<ValidationResult> validateWithWarnings(GenerateRoadmapRequest request);

    void validateTextOrThrow(String input);

    void validateLearningGoalOrThrow(String goal);

    List<ClarificationQuestion> generateClarificationQuestions(GenerateRoadmapRequest request);
}
