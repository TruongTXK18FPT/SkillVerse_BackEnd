package com.exe.skillverse_backend.question_bank_service.service;

import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.QuestionInfo;
import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.UpdateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankResponse;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBank;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBankQuestion;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankQuestionRepository;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankRepository;
import com.exe.skillverse_backend.question_bank_service.service.impl.QuestionBankServiceImpl;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionBankServiceImplTest {

    @Mock
    private QuestionBankRepository questionBankRepository;

    @Mock
    private QuestionBankQuestionRepository questionBankQuestionRepository;

    private QuestionBankServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new QuestionBankServiceImpl(questionBankRepository, questionBankQuestionRepository, new ObjectMapper());
        lenient().when(questionBankRepository.save(any(QuestionBank.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("createBank should reject duplicate active banks for the same domain tuple")
    void createBank_ShouldRejectDuplicateActiveBanks() {
        CreateQuestionBankRequest request = CreateQuestionBankRequest.builder()
                .domain("IT")
                .industry("Software")
                .jobRole("Backend Developer")
                .title("Backend Screening")
                .build();
        when(questionBankRepository.existsByDomainAndIndustryAndJobRoleAndIsActiveTrue("IT", "Software", "Backend Developer"))
                .thenReturn(true);

        assertThrows(ApiException.class, () -> service.createBank(request));
    }

    @Test
    @DisplayName("createBank should use the default difficulty distribution when the request omits it")
    void createBank_ShouldUseDefaultDifficultyDistribution() {
        CreateQuestionBankRequest request = CreateQuestionBankRequest.builder()
                .domain("IT")
                .industry("Software")
                .jobRole("Backend Developer")
                .title("Backend Screening")
                .build();
        QuestionBank bank = QuestionBank.builder()
                .id(1L)
                .domain("IT")
                .industry("Software")
                .jobRole("Backend Developer")
                .title("Backend Screening")
                .difficultyDistribution("{\"BEGINNER\":0.20,\"INTERMEDIATE\":0.35,\"ADVANCED\":0.30,\"EXPERT\":0.15}")
                .isActive(true)
                .build();

        when(questionBankRepository.existsByDomainAndIndustryAndJobRoleAndIsActiveTrue("IT", "Software", "Backend Developer"))
                .thenReturn(false);
        when(questionBankQuestionRepository.countByDifficulty(1L)).thenReturn(List.of());
        when(questionBankRepository.save(any(QuestionBank.class))).thenReturn(bank);

        QuestionBankResponse response = service.createBank(request);

        assertFalse(response.getDifficultyDistribution().isBlank());
        assertEquals("Backend Screening", response.getTitle());
    }

    @Test
    @DisplayName("updateBank should reject collisions with another active bank")
    void updateBank_ShouldRejectCollisionsWithAnotherActiveBank() {
        QuestionBank existing = QuestionBank.builder()
                .id(5L)
                .domain("IT")
                .industry("Software")
                .jobRole("Backend Developer")
                .title("Existing")
                .isActive(true)
                .build();
        UpdateQuestionBankRequest request = UpdateQuestionBankRequest.builder()
                .industry("Software")
                .jobRole("QA Engineer")
                .build();

        when(questionBankRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(questionBankRepository.existsByDomainAndIndustryAndJobRoleAndIsActiveTrueAndIdNot(
                "IT", "Software", "QA Engineer", 5L))
                .thenReturn(true);

        assertThrows(ApiException.class, () -> service.updateBank(5L, request));
        verify(questionBankRepository, never()).save(existing);
    }

    @Test
    @DisplayName("selectRandomQuestions should backfill undersupplied difficulty buckets")
    void selectRandomQuestions_ShouldBackfillUndersuppliedDifficultyBuckets() {
        QuestionBankQuestion beginner = question(1L, "BEGINNER", "[\"A\",\"B\"]");
        QuestionBankQuestion advanced = question(2L, "ADVANCED", "[\"C\",\"D\"]");
        QuestionBankQuestion filler = question(3L, "INTERMEDIATE", null);

        when(questionBankQuestionRepository.findRandomActiveByBankAndDifficulty(10L, "BEGINNER", 2))
                .thenReturn(List.of(beginner));
        when(questionBankQuestionRepository.findRandomActiveByBankAndDifficulty(10L, "ADVANCED", 2))
                .thenReturn(List.of(advanced));
        when(questionBankQuestionRepository.findRandomActiveByBankExcluding(eq(10L), anyList(), anyInt()))
                .thenReturn(List.of(filler));

        List<QuestionInfo> questions = service.selectRandomQuestions(
                10L,
                3,
                "{\"BEGINNER\":0.50,\"ADVANCED\":0.50}");

        assertEquals(3, questions.size());
        assertEquals(2L, questions.stream().filter(q -> q.questionId().equals(2L)).findFirst().orElseThrow().questionId());
    }

    @Test
    @DisplayName("isBankReadyForAllLevels should accept banks with at least 50 questions per difficulty")
    void isBankReadyForAllLevels_ShouldAcceptFiftyQuestionsPerDifficulty() {
        when(questionBankQuestionRepository.countByDifficulty(10L)).thenReturn(List.of(
                new Object[]{"BEGINNER", 50L},
                new Object[]{"INTERMEDIATE", 50L},
                new Object[]{"ADVANCED", 50L},
                new Object[]{"EXPERT", 50L}
        ));

        assertTrue(service.isBankReadyForAllLevels(10L));
    }

    @Test
    @DisplayName("isBankReadyForAllLevels should reject banks with any difficulty below 50 questions")
    void isBankReadyForAllLevels_ShouldRejectDifficultyBelowFifty() {
        when(questionBankQuestionRepository.countByDifficulty(11L)).thenReturn(List.of(
                new Object[]{"BEGINNER", 50L},
                new Object[]{"INTERMEDIATE", 50L},
                new Object[]{"ADVANCED", 50L},
                new Object[]{"EXPERT", 49L}
        ));

        assertFalse(service.isBankReadyForAllLevels(11L));
    }

    @Test
    @DisplayName("deleteBank should soft delete the bank")
    void deleteBank_ShouldSoftDeleteTheBank() {
        QuestionBank bank = QuestionBank.builder().id(7L).isActive(true).build();
        when(questionBankRepository.findById(7L)).thenReturn(Optional.of(bank));

        service.deleteBank(7L);

        assertFalse(bank.getIsActive());
        verify(questionBankRepository).save(bank);
    }

    private QuestionBankQuestion question(Long id, String difficulty, String options) {
        return QuestionBankQuestion.builder()
                .id(id)
                .questionText("Question " + id)
                .correctAnswer("A")
                .difficulty(difficulty)
                .skillArea("Backend")
                .options(options)
                .build();
    }
}
