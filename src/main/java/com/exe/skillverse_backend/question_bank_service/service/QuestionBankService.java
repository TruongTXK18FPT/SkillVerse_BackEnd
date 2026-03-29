package com.exe.skillverse_backend.question_bank_service.service;

import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.UpdateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankResponse;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankSummaryResponse;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.QuestionInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface QuestionBankService {

    QuestionBankResponse createBank(CreateQuestionBankRequest request);

    Page<QuestionBankSummaryResponse> listBanks(String domain, String industry, String jobRole, Pageable pageable);

    QuestionBankResponse getBankById(Long id);

    QuestionBankResponse updateBank(Long id, UpdateQuestionBankRequest request);

    void deleteBank(Long id);

    Optional<QuestionBankResponse> findActiveBank(String domain, String industry, String jobRole);

    List<QuestionInfo> selectRandomQuestions(Long bankId, int targetCount, String difficultyDistributionJson);

    void incrementUsedCount(List<QuestionInfo> questions);
}
