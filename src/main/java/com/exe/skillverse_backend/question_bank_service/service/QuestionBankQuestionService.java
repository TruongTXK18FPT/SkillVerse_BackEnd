package com.exe.skillverse_backend.question_bank_service.service;

import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.UpdateQuestionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionResponse;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.QuestionInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface QuestionBankQuestionService {

    QuestionResponse addQuestion(Long bankId, CreateQuestionRequest request);

    Page<QuestionResponse> listQuestions(Long bankId, String difficulty, String skillArea, String category, Pageable pageable);

    QuestionResponse getQuestion(Long bankId, Long questionId);

    QuestionResponse updateQuestion(Long bankId, Long questionId, UpdateQuestionRequest request);

    void deleteQuestion(Long bankId, Long questionId);

    int bulkAddQuestions(Long bankId, List<CreateQuestionRequest> questions, String source);

    void saveQuestionsFromTest(Long bankId, List<QuestionInfo> questions);
}
