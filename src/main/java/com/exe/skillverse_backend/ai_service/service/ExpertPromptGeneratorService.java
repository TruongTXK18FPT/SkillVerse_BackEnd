package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.request.GeneratePromptRequest;
import com.exe.skillverse_backend.ai_service.dto.response.PromptGenerationResponse;

public interface ExpertPromptGeneratorService {
    PromptGenerationResponse generateExpertPrompts(GeneratePromptRequest request);
}
