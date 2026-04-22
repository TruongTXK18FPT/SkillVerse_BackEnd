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

    int MIN_READY_QUESTION_COUNT_PER_LEVEL = 50;

    QuestionBankResponse createBank(CreateQuestionBankRequest request);

    Page<QuestionBankSummaryResponse> listBanks(String domain, String industry, String jobRole, String skillName, Pageable pageable);

    QuestionBankResponse getBankById(Long id);

    QuestionBankResponse updateBank(Long id, UpdateQuestionBankRequest request);

    void deleteBank(Long id);

    Optional<QuestionBankResponse> findActiveBank(String domain, String industry, String jobRole, String skillName);

    Optional<QuestionBankResponse> findActiveBank(String domain, String industry, String jobRole);

    /**
     * Find active question bank by domain + job role (no industry filter), with optional skill scope.
     * Used by JourneyService for bank-first test generation.
     */
    Optional<QuestionBankResponse> findActiveBankByJobRole(String domain, String jobRole, String skillName);

    /**
     * Find active question bank by domain + job role (no industry filter).
     * Used by JourneyService for bank-first test generation.
     */
    Optional<QuestionBankResponse> findActiveBank(String domain, String jobRole);

    /**
     * Check whether a question bank has sufficient questions in ALL four difficulty levels
     * to meet the minimum pool size requirement for each level.
     * @param bankId the question bank ID
     * @return true if every difficulty level has >= MIN_READY_QUESTION_COUNT_PER_LEVEL active questions
     */
    boolean isBankReadyForAllLevels(Long bankId);

    List<QuestionInfo> selectRandomQuestions(Long bankId, int targetCount, String difficultyDistributionJson);

    List<QuestionInfo> selectRandomQuestionsByLevel(Long bankId, int targetCount, String userLevel);

    void incrementUsedCount(List<QuestionInfo> questions);

    /**
     * Count active questions grouped by (skillArea, difficulty) for a question bank.
     * Used to determine per-skill-area readiness for hybrid test generation.
     * @return List of [skillArea, difficulty, count] rows
     */
    List<Object[]> countBySkillAreaAndDifficulty(Long bankId);

    /**
     * Find questions from question bank filtered by specific skillArea.
     * @param bankId question bank ID
     * @param skillArea the skill area to filter by
     * @param difficulty target difficulty (BEGINNER/INTERMEDIATE/ADVANCED/EXPERT)
     * @param limit max number of questions to return
     * @return list of QuestionInfo matching criteria
     */
    List<QuestionInfo> selectRandomQuestionsBySkillAreaAndDifficulty(
            Long bankId, String skillArea, String difficulty, int limit);
}
