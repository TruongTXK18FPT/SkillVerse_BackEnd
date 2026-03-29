package com.exe.skillverse_backend.question_bank_service.service.impl;

import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.UpdateQuestionRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionResponse;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBank;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBankQuestion;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankQuestionRepository;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankRepository;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankQuestionService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class QuestionBankQuestionServiceImpl implements QuestionBankQuestionService {

    private final QuestionBankRepository questionBankRepository;
    private final QuestionBankQuestionRepository questionBankQuestionRepository;
    private final ObjectMapper objectMapper;

    @Override
    public QuestionResponse addQuestion(Long bankId, CreateQuestionRequest request) {
        QuestionBank bank = questionBankRepository.findById(bankId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question bank not found: " + bankId));

        QuestionBankQuestion question = QuestionBankQuestion.builder()
                .questionBank(bank)
                .questionText(request.getQuestionText())
                .options(toOptionsJson(request.getOptions()))
                .correctAnswer(request.getCorrectAnswer().toUpperCase())
                .explanation(request.getExplanation())
                .difficulty(request.getDifficulty())
                .skillArea(request.getSkillArea())
                .category(request.getCategory())
                .source("MANUAL")
                .isActive(true)
                .usedCount(0)
                .build();

        question = questionBankQuestionRepository.save(question);
        log.info("Added question id={} to bank {}", question.getId(), bankId);
        return toResponse(question);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuestionResponse> listQuestions(Long bankId, String difficulty, String skillArea, String category, Pageable pageable) {
        // verify bank exists
        if (!questionBankRepository.existsById(bankId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Question bank not found: " + bankId);
        }
        Page<QuestionBankQuestion> questions =
                questionBankQuestionRepository.findByFilters(bankId, difficulty, skillArea, category, pageable);
        return questions.map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionResponse getQuestion(Long bankId, Long questionId) {
        QuestionBankQuestion question = findByIdOrThrow(bankId, questionId);
        return toResponse(question);
    }

    @Override
    public QuestionResponse updateQuestion(Long bankId, Long questionId, UpdateQuestionRequest request) {
        QuestionBankQuestion question = findByIdOrThrow(bankId, questionId);

        if (request.getQuestionText() != null) question.setQuestionText(request.getQuestionText());
        if (request.getOptions() != null) question.setOptions(toOptionsJson(request.getOptions()));
        if (request.getCorrectAnswer() != null) question.setCorrectAnswer(request.getCorrectAnswer().toUpperCase());
        if (request.getExplanation() != null) question.setExplanation(request.getExplanation());
        if (request.getDifficulty() != null) question.setDifficulty(request.getDifficulty());
        if (request.getSkillArea() != null) question.setSkillArea(request.getSkillArea());
        if (request.getCategory() != null) question.setCategory(request.getCategory());
        if (request.getIsActive() != null) question.setIsActive(request.getIsActive());

        question = questionBankQuestionRepository.save(question);
        log.info("Updated question id={} in bank {}", questionId, bankId);
        return toResponse(question);
    }

    @Override
    public void deleteQuestion(Long bankId, Long questionId) {
        QuestionBankQuestion question = findByIdOrThrow(bankId, questionId);
        question.setIsActive(false);
        questionBankQuestionRepository.save(question);
        log.info("Soft-deleted question id={} from bank {}", questionId, bankId);
    }

    @Override
    public int bulkAddQuestions(Long bankId, List<CreateQuestionRequest> questions, String source) {
        QuestionBank bank = questionBankRepository.findById(bankId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question bank not found: " + bankId));

        List<QuestionBankQuestion> entities = questions.stream()
                .map(req -> QuestionBankQuestion.builder()
                        .questionBank(bank)
                        .questionText(req.getQuestionText())
                        .options(toOptionsJson(req.getOptions()))
                        .correctAnswer(req.getCorrectAnswer().toUpperCase())
                        .explanation(req.getExplanation())
                        .difficulty(req.getDifficulty())
                        .skillArea(req.getSkillArea())
                        .category(req.getCategory())
                        .source(source != null ? source : "MANUAL")
                        .isActive(true)
                        .usedCount(0)
                        .build())
                .collect(Collectors.toList());

        questionBankQuestionRepository.saveAll(entities);
        log.info("Bulk added {} questions to bank {}", entities.size(), bankId);
        return entities.size();
    }

    // ==================== Private Helpers ====================

    private QuestionBankQuestion findByIdOrThrow(Long bankId, Long questionId) {
        QuestionBankQuestion question = questionBankQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question not found: " + questionId));
        if (!question.getQuestionBank().getId().equals(bankId)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Question does not belong to this bank");
        }
        return question;
    }

    private String toOptionsJson(List<String> options) {
        if (options == null) return null;
        try {
            return objectMapper.writeValueAsString(options);
        } catch (Exception e) {
            return null;
        }
    }

    private QuestionResponse toResponse(QuestionBankQuestion q) {
        List<String> options = null;
        if (q.getOptions() != null) {
            try {
                options = objectMapper.readValue(q.getOptions(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse options for question {}: {}", q.getId(), e.getMessage());
            }
        }
        return QuestionResponse.builder()
                .id(q.getId())
                .bankId(q.getQuestionBank().getId())
                .questionText(q.getQuestionText())
                .options(options)
                .correctAnswer(q.getCorrectAnswer())
                .explanation(q.getExplanation())
                .difficulty(q.getDifficulty())
                .skillArea(q.getSkillArea())
                .category(q.getCategory())
                .source(q.getSource())
                .usedCount(q.getUsedCount())
                .isActive(q.getIsActive())
                .createdAt(q.getCreatedAt())
                .updatedAt(q.getUpdatedAt())
                .build();
    }
}
