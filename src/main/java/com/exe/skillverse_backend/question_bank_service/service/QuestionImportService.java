package com.exe.skillverse_backend.question_bank_service.service;

import com.exe.skillverse_backend.question_bank_service.dto.request.BulkImportRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.ImportResultResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface QuestionImportService {

    BulkImportRequest previewImport(MultipartFile file, Long bankId);

    ImportResultResponse confirmImport(Long bankId, List<CreateQuestionRequest> questions, String source);
}
