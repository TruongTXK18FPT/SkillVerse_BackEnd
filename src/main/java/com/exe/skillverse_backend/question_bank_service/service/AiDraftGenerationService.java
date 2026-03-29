package com.exe.skillverse_backend.question_bank_service.service;

import com.exe.skillverse_backend.question_bank_service.dto.request.AiGenerateDraftRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.AiDraftResponse;

public interface AiDraftGenerationService {

    AiDraftResponse generateDraftQuestions(Long bankId, AiGenerateDraftRequest request);
}
