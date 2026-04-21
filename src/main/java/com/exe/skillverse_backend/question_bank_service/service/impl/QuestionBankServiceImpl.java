package com.exe.skillverse_backend.question_bank_service.service.impl;

import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.UpdateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankResponse;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankSummaryResponse;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBank;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBankQuestion;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankQuestionRepository;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankRepository;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankService;
import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.QuestionInfo;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class QuestionBankServiceImpl implements QuestionBankService {

    private final QuestionBankRepository questionBankRepository;
    private final QuestionBankQuestionRepository questionBankQuestionRepository;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_DIFFICULTY_DISTRIBUTION =
            "{\"BEGINNER\":0.20,\"INTERMEDIATE\":0.35,\"ADVANCED\":0.30,\"EXPERT\":0.15}";

    private static final String[] REQUIRED_DIFFICULTY_LEVELS = {"BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"};

    @Override
    public QuestionBankResponse createBank(CreateQuestionBankRequest request) {
        String domain = requireValue(request.getDomain(), "Domain is required");
        String industry = requireValue(request.getIndustry(), "Industry is required");
        String jobRole = requireValue(request.getJobRole(), "Job role is required");
        String title = requireValue(request.getTitle(), "Title is required");

        if (questionBankRepository.existsByDomainAndIndustryAndJobRoleAndIsActiveTrue(domain, industry, jobRole)) {
            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    String.format("Question bank đã tồn tại cho %s / %s / %s", domain, industry, jobRole)
            );
        }

        QuestionBank bank = QuestionBank.builder()
                .domain(domain)
                .industry(industry)
                .jobRole(jobRole)
                .title(title)
                .description(normalizeOptional(request.getDescription()))
                .difficultyDistribution(request.getDifficultyDistribution() != null
                        ? request.getDifficultyDistribution() : DEFAULT_DIFFICULTY_DISTRIBUTION)
                .isActive(true)
                .build();

        bank = questionBankRepository.save(bank);
        log.info("Created question bank: id={}, domain={}, title={}", bank.getId(), bank.getDomain(), bank.getTitle());
        return toResponse(bank);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuestionBankSummaryResponse> listBanks(String domain, String industry, String jobRole, Pageable pageable) {
        Page<QuestionBank> banks = questionBankRepository.findByFilters(
                normalizeOptional(domain),
                normalizeOptional(industry),
                normalizeOptional(jobRole),
                pageable
        );
        return banks.map(this::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionBankResponse getBankById(Long id) {
        QuestionBank bank = findByIdOrThrow(id);
        return toResponse(bank);
    }

    @Override
    public QuestionBankResponse updateBank(Long id, UpdateQuestionBankRequest request) {
        QuestionBank bank = findByIdOrThrow(id);

        String nextIndustry = request.getIndustry() != null ? requireValue(request.getIndustry(), "Industry is required") : bank.getIndustry();
        String nextJobRole = request.getJobRole() != null ? requireValue(request.getJobRole(), "Job role is required") : bank.getJobRole();

        if (questionBankRepository.existsByDomainAndIndustryAndJobRoleAndIsActiveTrueAndIdNot(
                bank.getDomain(), nextIndustry, nextJobRole, bank.getId())) {
            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    String.format("Đã tồn tại Question Bank khác cho %s / %s / %s", bank.getDomain(), nextIndustry, nextJobRole)
            );
        }

        if (request.getTitle() != null) bank.setTitle(requireValue(request.getTitle(), "Title is required"));
        if (request.getDescription() != null) bank.setDescription(normalizeOptional(request.getDescription()));
        bank.setIndustry(nextIndustry);
        bank.setJobRole(nextJobRole);
        if (request.getDifficultyDistribution() != null) {
            bank.setDifficultyDistribution(request.getDifficultyDistribution());
        }
        if (request.getIsActive() != null) bank.setIsActive(request.getIsActive());

        bank = questionBankRepository.save(bank);
        log.info("Updated question bank: id={}", bank.getId());
        return toResponse(bank);
    }

    @Override
    public void deleteBank(Long id) {
        QuestionBank bank = findByIdOrThrow(id);
        bank.setIsActive(false);
        questionBankRepository.save(bank);
        log.info("Soft-deleted question bank: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuestionBankResponse> findActiveBank(String domain, String industry, String jobRole) {
        return questionBankRepository.findTopByDomainAndIndustryAndJobRoleAndIsActiveTrueOrderByUpdatedAtDescIdDesc(
                        normalizeOptional(domain),
                        normalizeOptional(industry),
                        normalizeOptional(jobRole))
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuestionBankResponse> findActiveBank(String domain, String jobRole) {
        return questionBankRepository
                .findTopByDomainAndJobRoleAndIsActiveTrueOrderByUpdatedAtDescIdDesc(
                        normalizeOptional(domain),
                        normalizeOptional(jobRole))
                .map(this::toResponse);
    }

    @Override
    public boolean isBankReadyForAllLevels(Long bankId) {
        if (bankId == null) return false;

        Map<String, Long> difficultyBreakdown = new LinkedHashMap<>();
        List<Object[]> counts = questionBankQuestionRepository.countByDifficulty(bankId);
        for (Object[] row : counts) {
            difficultyBreakdown.put((String) row[0], (Long) row[1]);
        }

        StringBuilder missingLevels = new StringBuilder();
        for (String level : REQUIRED_DIFFICULTY_LEVELS) {
            long count = difficultyBreakdown.getOrDefault(level, 0L);
            if (count < MIN_READY_QUESTION_COUNT_PER_LEVEL) {
                if (missingLevels.length() > 0) missingLevels.append(", ");
                missingLevels.append(level).append("(=").append(count).append("/").append(MIN_READY_QUESTION_COUNT_PER_LEVEL).append(")");
            }
        }

        if (missingLevels.length() > 0) {
            log.info("Question bank {} not ready: insufficient questions in [{}]", bankId, missingLevels);
            return false;
        }

        log.info("Question bank {} is ready for all 4 difficulty levels (all >= {} questions). Breakdown: {}",
                bankId, MIN_READY_QUESTION_COUNT_PER_LEVEL, difficultyBreakdown);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionInfo> selectRandomQuestions(Long bankId, int targetCount, String difficultyDistributionJson) {
        Map<String, Double> distribution = parseDistribution(difficultyDistributionJson);
        List<QuestionInfo> result = new ArrayList<>();

        // Phase 1: Select by difficulty distribution
        for (Map.Entry<String, Double> entry : distribution.entrySet()) {
            int needed = (int) Math.round(targetCount * entry.getValue());
            if (needed > 0) {
                List<QuestionBankQuestion> byDifficulty =
                        questionBankQuestionRepository.findRandomActiveByBankAndDifficulty(
                                bankId, entry.getKey(), needed);
                for (QuestionBankQuestion q : byDifficulty) {
                    result.add(toQuestionInfo(q));
                }
            }
        }

        // Phase 2: Fill remaining if difficulty buckets are undersupplied
        int stillNeeded = targetCount - result.size();
        if (stillNeeded > 0) {
            Set<Long> selectedIds = result.stream()
                    .map(QuestionInfo::questionId)
                    .collect(Collectors.toSet());
            List<QuestionBankQuestion> anyLevel =
                    questionBankQuestionRepository.findRandomActiveByBankExcluding(
                            bankId, new ArrayList<>(selectedIds), stillNeeded);
            for (QuestionBankQuestion q : anyLevel) {
                result.add(toQuestionInfo(q));
            }
        }

        // Phase 3: Shuffle to avoid predictable difficulty ordering
        Collections.shuffle(result);
        log.debug("Selected {} questions from bank {} (target: {})", result.size(), bankId, targetCount);
        return result;
    }

    @Override
    public void incrementUsedCount(List<QuestionInfo> questions) {
        List<Long> ids = questions.stream()
                .map(QuestionInfo::questionId)
                .collect(Collectors.toList());
        if (!ids.isEmpty()) {
            questionBankQuestionRepository.incrementUsedCount(ids);
        }
    }

    @Override
    public List<QuestionInfo> selectRandomQuestionsByLevel(Long bankId, int targetCount, String userLevel) {
        // Mapping: userLevel → [BEGINNER%, INTERMEDIATE%, ADVANCED%, EXPERT%]
        Map<String, Double[]> levelDistribution = Map.of(
                "BEGINNER",     new Double[]{0.80, 0.20, 0.00, 0.00},
                "ELEMENTARY",   new Double[]{0.60, 0.30, 0.10, 0.00},
                "INTERMEDIATE", new Double[]{0.00, 0.80, 0.20, 0.00},
                "ADVANCED",     new Double[]{0.00, 0.00, 0.80, 0.20}
        );
        Double[] dist = levelDistribution.getOrDefault(
                (userLevel != null ? userLevel : "").toUpperCase(),
                new Double[]{0.20, 0.35, 0.30, 0.15});

        String[] difficulties = {"BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"};
        List<QuestionInfo> result = new ArrayList<>();
        Set<Long> selectedIds = new LinkedHashSet<>();

        for (int i = 0; i < difficulties.length; i++) {
            int needed = (int) Math.round(targetCount * dist[i]);
            if (needed <= 0) continue;
            List<QuestionBankQuestion> qs =
                    questionBankQuestionRepository.findRandomActiveByBankAndDifficultyExact(
                            bankId, difficulties[i], needed);
            for (QuestionBankQuestion q : qs) {
                result.add(toQuestionInfo(q));
                selectedIds.add(q.getId());
            }
        }

        // Fill remaining if undersupplied
        int stillNeeded = targetCount - result.size();
        if (stillNeeded > 0) {
            List<QuestionBankQuestion> fill =
                    questionBankQuestionRepository.findRandomActiveByBankExcluding(
                            bankId, new ArrayList<>(selectedIds), stillNeeded);
            for (QuestionBankQuestion q : fill) {
                result.add(toQuestionInfo(q));
            }
        }

        Collections.shuffle(result);
        log.debug("Selected {} level-matched questions from bank {} (target: {}, level: {})",
                result.size(), bankId, targetCount, userLevel);
        return result;
    }

    @Override
    public List<Object[]> countBySkillAreaAndDifficulty(Long bankId) {
        if (bankId == null) return List.of();
        return questionBankQuestionRepository.countBySkillAreaAndDifficulty(bankId);
    }

    @Override
    public List<QuestionInfo> selectRandomQuestionsBySkillAreaAndDifficulty(
            Long bankId, String skillArea, String difficulty, int limit) {
        if (bankId == null || skillArea == null || difficulty == null || limit <= 0) {
            return List.of();
        }
        List<QuestionBankQuestion> questions =
                questionBankQuestionRepository.findRandomActiveByBankAndSkillAreaAndDifficulty(
                        bankId, skillArea, difficulty.toUpperCase(), limit);
        return questions.stream().map(this::toQuestionInfo).collect(Collectors.toList());
    }

    // ==================== Private Helpers ====================

    private QuestionBank findByIdOrThrow(Long id) {
        return questionBankRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Question bank not found: " + id));
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private String requireValue(String value, String message) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, message);
        }
        return normalized;
    }

    private QuestionBankResponse toResponse(QuestionBank bank) {
        Map<String, Long> difficultyBreakdown = new LinkedHashMap<>();
        List<Object[]> counts = questionBankQuestionRepository.countByDifficulty(bank.getId());
        for (Object[] row : counts) {
            difficultyBreakdown.put((String) row[0], (Long) row[1]);
        }

        return QuestionBankResponse.builder()
                .id(bank.getId())
                .domain(bank.getDomain())
                .industry(bank.getIndustry())
                .jobRole(bank.getJobRole())
                .title(bank.getTitle())
                .description(bank.getDescription())
                .difficultyDistribution(bank.getDifficultyDistribution())
                .isActive(bank.getIsActive())
                .createdAt(bank.getCreatedAt())
                .updatedAt(bank.getUpdatedAt())
                .activeQuestionCount(bank.getActiveQuestionCount())
                .difficultyBreakdown(difficultyBreakdown)
                .build();
    }

    private QuestionBankSummaryResponse toSummaryResponse(QuestionBank bank) {
        return QuestionBankSummaryResponse.builder()
                .id(bank.getId())
                .domain(bank.getDomain())
                .industry(bank.getIndustry())
                .jobRole(bank.getJobRole())
                .title(bank.getTitle())
                .activeQuestionCount(bank.getActiveQuestionCount())
                .isActive(bank.getIsActive())
                .createdAt(bank.getCreatedAt())
                .build();
    }

    private Map<String, Double> parseDistribution(String json) {
        if (json == null || json.isBlank()) {
            json = DEFAULT_DIFFICULTY_DISTRIBUTION;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse difficulty distribution: {}, using default", json);
            return objectMapper.convertValue(
                    Map.of("BEGINNER", 0.20, "INTERMEDIATE", 0.35, "ADVANCED", 0.30, "EXPERT", 0.15),
                    new TypeReference<Map<String, Double>>() {});
        }
    }

    private QuestionInfo toQuestionInfo(QuestionBankQuestion q) {
        List<String> options = null;
        if (q.getOptions() != null) {
            try {
                options = objectMapper.readValue(q.getOptions(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse options for question {}: {}", q.getId(), e.getMessage());
            }
        }
        return new QuestionInfo(
                q.getId(),
                q.getQuestionText(),
                options,
                q.getCorrectAnswer(),
                q.getExplanation(),
                q.getDifficulty(),
                q.getSkillArea()
        );
    }
}
