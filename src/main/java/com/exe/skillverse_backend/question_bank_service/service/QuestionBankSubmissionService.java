package com.exe.skillverse_backend.question_bank_service.service;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankSubmissionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.ReviewQuestionBankSubmissionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankSubmissionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface QuestionBankSubmissionService {

    QuestionBankSubmissionResponse createSubmission(User mentor, CreateQuestionBankSubmissionRequest request);

    List<QuestionBankSubmissionResponse> getMySubmissions(User mentor);

    QuestionBankSubmissionResponse getMySubmissionDetail(User mentor, Long submissionId);

    Page<QuestionBankSubmissionResponse> getAdminSubmissions(List<String> statuses, Pageable pageable);

    QuestionBankSubmissionResponse getAdminSubmissionDetail(Long submissionId);

    QuestionBankSubmissionResponse reviewSubmission(Long submissionId, User admin, ReviewQuestionBankSubmissionRequest request);

    long countPendingSubmissions();
}
