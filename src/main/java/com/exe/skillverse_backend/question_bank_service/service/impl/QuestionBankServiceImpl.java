package com.exe.skillverse_backend.question_bank_service.service.impl;

import com.exe.skillverse_backend.ai_service.service.AssessmentPromptService.QuestionInfo;
import com.exe.skillverse_backend.career_taxonomy_service.entity.Domain;
import com.exe.skillverse_backend.career_taxonomy_service.entity.JobPosition;
import com.exe.skillverse_backend.career_taxonomy_service.enums.TaxonomyStatus;
import com.exe.skillverse_backend.career_taxonomy_service.repository.DomainRepository;
import com.exe.skillverse_backend.career_taxonomy_service.repository.JobPositionRepository;
import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.request.UpdateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankResponse;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankSummaryResponse;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBank;
import com.exe.skillverse_backend.question_bank_service.entity.QuestionBankQuestion;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankQuestionRepository;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankRepository;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankService;
import com.exe.skillverse_backend.shared.entity.Skill;
import com.exe.skillverse_backend.shared.enums.SkillStatus;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.exe.skillverse_backend.shared.repository.SkillRepository;
import com.exe.skillverse_backend.shared.util.SkillNameUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class QuestionBankServiceImpl implements QuestionBankService {

    private static final String DEFAULT_DIFFICULTY_DISTRIBUTION =
            "{\"BEGINNER\":0.20,\"INTERMEDIATE\":0.35,\"ADVANCED\":0.30,\"EXPERT\":0.15}";

    private static final String[] REQUIRED_DIFFICULTY_LEVELS = {"BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"};

    private final QuestionBankRepository questionBankRepository;
    private final QuestionBankQuestionRepository questionBankQuestionRepository;
    private final DomainRepository domainRepository;
    private final JobPositionRepository jobPositionRepository;
    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;

    private record TaxonomyScope(
            Long domainId,
            Long jobPositionId,
            Long skillId,
            String domainCode,
            String domainName,
            String jobPositionName,
            String skillName
    ) {
    }

    @Override
    public QuestionBankResponse createBank(CreateQuestionBankRequest request) {
        TaxonomyScope scope = resolveScopeForCreate(request);
        String title = requireValue(request.getTitle(), "Title is required");

        if (questionBankRepository.existsActiveByTaxonomyScope(scope.domainId(), scope.jobPositionId(), scope.skillId())) {
            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    String.format(
                            "Question bank already exists for domainId=%s / jobPositionId=%s / skillId=%s",
                            scope.domainId(),
                            scope.jobPositionId(),
                            scope.skillId() != null ? scope.skillId() : "GENERAL"
                    )
            );
        }

        QuestionBank bank = QuestionBank.builder()
                .domainId(scope.domainId())
                .jobPositionId(scope.jobPositionId())
                .skillId(scope.skillId())
                .domain(scope.domainCode())
                .skillName(scope.skillName())
                .title(title)
                .description(normalizeOptional(request.getDescription()))
                .difficultyDistribution(normalizeOptional(request.getDifficultyDistribution()) != null
                        ? normalizeOptional(request.getDifficultyDistribution())
                        : DEFAULT_DIFFICULTY_DISTRIBUTION)
                .isActive(true)
                .build();

        bank = questionBankRepository.save(bank);
        log.info("Created question bank: id={}, domainId={}, jobPositionId={}, skillId={}",
                bank.getId(), bank.getDomainId(), bank.getJobPositionId(), bank.getSkillId());
        return toResponse(bank);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuestionBankSummaryResponse> listBanks(
            Long domainId,
            Long jobPositionId,
            Long skillId,
            Pageable pageable
    ) {
        Page<QuestionBank> banks = questionBankRepository.findByTaxonomyFilters(
                domainId,
                jobPositionId,
                skillId,
                pageable
        );
        return banks.map(this::toSummaryResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionBankResponse getBankById(Long id) {
        return toResponse(findByIdOrThrow(id));
    }

    @Override
    public QuestionBankResponse updateBank(Long id, UpdateQuestionBankRequest request) {
        QuestionBank bank = findByIdOrThrow(id);

        TaxonomyScope nextScope = resolveScopeForUpdate(bank, request);

        if (questionBankRepository.existsActiveByTaxonomyScopeAndIdNot(
                nextScope.domainId(),
                nextScope.jobPositionId(),
                nextScope.skillId(),
                bank.getId()
        )) {
            throw new ApiException(
                    ErrorCode.BAD_REQUEST,
                    String.format(
                            "Another question bank already exists for domainId=%s / jobPositionId=%s / skillId=%s",
                            nextScope.domainId(),
                            nextScope.jobPositionId(),
                            nextScope.skillId() != null ? nextScope.skillId() : "GENERAL"
                    )
            );
        }

        if (request.getTitle() != null) {
            bank.setTitle(requireValue(request.getTitle(), "Title is required"));
        }
        if (request.getDescription() != null) {
            bank.setDescription(normalizeOptional(request.getDescription()));
        }
        bank.setDomainId(nextScope.domainId());
        bank.setJobPositionId(nextScope.jobPositionId());
        bank.setSkillId(nextScope.skillId());
        bank.setDomain(nextScope.domainCode());
        bank.setSkillName(nextScope.skillName());
        if (request.getDifficultyDistribution() != null) {
            bank.setDifficultyDistribution(request.getDifficultyDistribution());
        }
        if (request.getIsActive() != null) {
            bank.setIsActive(request.getIsActive());
        }

        bank = questionBankRepository.save(bank);
        log.info("Updated question bank: id={}, skill={}", bank.getId(), bank.getSkillName());
        return toResponse(bank);
    }

    @Override
    public void deleteBank(Long id) {
        QuestionBank existing = questionBankRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "QUESTION_BANK_NOT_FOUND"));
        existing.setIsActive(false);
        questionBankRepository.save(existing);
        log.info("Soft-deleted question bank: id={}", id);
    }

    @Override
    @Transactional
    public void syncJobPositionBanks() {
        List<JobPosition> activeJobs = 
            jobPositionRepository.findAllActiveWithActiveDomain(TaxonomyStatus.ACTIVE);
        
        int createdCount = 0;
        for (var jp : activeJobs) {
            boolean exists = questionBankRepository.existsActiveByTaxonomyScope(jp.getDomainId(), jp.getId(), null);
            if (!exists) {
                CreateQuestionBankRequest request = 
                    CreateQuestionBankRequest.builder()
                        .domainId(jp.getDomainId())
                        .jobPositionId(jp.getId())
                        .domain(jp.getDomain().getCode())
                        .title("Ngân hàng câu hỏi - " + jp.getName())
                        .description("Ngân hàng câu hỏi được đồng bộ tự động cho vị trí " + jp.getName())
                        .build();
                createBank(request);
                createdCount++;
                log.info("Synced question bank for JobPosition {}", jp.getId());
            }
        }
        log.info("Finished syncing Job Position question banks. Created {} new banks.", createdCount);
    }

    // Legacy string-based lookup removed — taxonomy ID-based methods are canonical now.

    @Override
    @Transactional(readOnly = true)
    public Optional<QuestionBankResponse> findActiveBank(Long domainId, Long jobPositionId, Long skillId) {
        if (domainId == null || jobPositionId == null) {
            return Optional.empty();
        }

        if (skillId != null) {
            Optional<QuestionBank> exactSkillBank = questionBankRepository.findByExactTaxonomyScope(
                            domainId,
                            jobPositionId,
                            skillId,
                            PageRequest.of(0, 1))
                    .stream()
                    .findFirst();
            if (exactSkillBank.isPresent()) {
                return exactSkillBank.map(this::toResponse);
            }
        }

        return questionBankRepository.findPreferredByTaxonomyScope(
                        domainId,
                        jobPositionId,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuestionBankResponse> findActiveBank(Long domainId, Long jobPositionId) {
        if (domainId == null || jobPositionId == null) {
            return Optional.empty();
        }
        return questionBankRepository.findByExactTaxonomyScope(
                        domainId,
                        jobPositionId,
                        null,
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(this::toResponse);
    }


    @Override
    public boolean isBankReadyForAllLevels(Long bankId) {
        if (bankId == null) {
            return false;
        }

        Map<String, Long> difficultyBreakdown = new LinkedHashMap<>();
        List<Object[]> counts = questionBankQuestionRepository.countByDifficulty(bankId);
        for (Object[] row : counts) {
            difficultyBreakdown.put((String) row[0], (Long) row[1]);
        }

        StringBuilder missingLevels = new StringBuilder();
        for (String level : REQUIRED_DIFFICULTY_LEVELS) {
            long count = difficultyBreakdown.getOrDefault(level, 0L);
            if (count < MIN_READY_QUESTION_COUNT_PER_LEVEL) {
                if (missingLevels.length() > 0) {
                    missingLevels.append(", ");
                }
                missingLevels.append(level)
                        .append("(=")
                        .append(count)
                        .append("/")
                        .append(MIN_READY_QUESTION_COUNT_PER_LEVEL)
                        .append(")");
            }
        }

        if (missingLevels.length() > 0) {
            log.info("Question bank {} not ready: {}", bankId, missingLevels);
            return false;
        }

        log.info("Question bank {} is ready for all levels", bankId);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionInfo> selectRandomQuestions(Long bankId, int targetCount, String difficultyDistributionJson) {
        Map<String, Double> distribution = parseDistribution(difficultyDistributionJson);
        List<QuestionInfo> result = new ArrayList<>();

        for (Map.Entry<String, Double> entry : distribution.entrySet()) {
            int needed = (int) Math.round(targetCount * entry.getValue());
            if (needed <= 0) {
                continue;
            }
            List<QuestionBankQuestion> byDifficulty = questionBankQuestionRepository.findRandomActiveByBankAndDifficulty(
                    bankId,
                    entry.getKey(),
                    needed
            );
            byDifficulty.forEach(question -> result.add(toQuestionInfo(question)));
        }

        int stillNeeded = targetCount - result.size();
        if (stillNeeded > 0) {
            Set<Long> selectedIds = result.stream()
                    .map(QuestionInfo::questionId)
                    .collect(Collectors.toSet());
            List<QuestionBankQuestion> fill = questionBankQuestionRepository.findRandomActiveByBankExcluding(
                    bankId,
                    new ArrayList<>(selectedIds),
                    stillNeeded
            );
            fill.forEach(question -> result.add(toQuestionInfo(question)));
        }

        Collections.shuffle(result);
        return result;
    }

    @Override
    public void incrementUsedCount(List<QuestionInfo> questions) {
        List<Long> ids = questions.stream()
                .map(QuestionInfo::questionId)
                .toList();
        if (!ids.isEmpty()) {
            questionBankQuestionRepository.incrementUsedCount(ids);
        }
    }

    @Override
    public List<QuestionInfo> selectRandomQuestionsByLevel(Long bankId, int targetCount, String userLevel) {
        Map<String, Double[]> levelDistribution = Map.of(
                "BEGINNER", new Double[]{0.80, 0.20, 0.00, 0.00},
                "ELEMENTARY", new Double[]{0.60, 0.30, 0.10, 0.00},
                "INTERMEDIATE", new Double[]{0.00, 0.80, 0.20, 0.00},
                "ADVANCED", new Double[]{0.00, 0.00, 0.80, 0.20},
                "EXPERT", new Double[]{0.00, 0.00, 0.20, 0.80}
        );
        Double[] distribution = levelDistribution.getOrDefault(
                userLevel != null ? userLevel.toUpperCase() : "",
                new Double[]{0.20, 0.35, 0.30, 0.15}
        );

        String[] difficulties = {"BEGINNER", "INTERMEDIATE", "ADVANCED", "EXPERT"};
        List<QuestionInfo> result = new ArrayList<>();
        Set<Long> selectedIds = new LinkedHashSet<>();

        for (int index = 0; index < difficulties.length; index++) {
            int needed = (int) Math.round(targetCount * distribution[index]);
            if (needed <= 0) {
                continue;
            }
            List<QuestionBankQuestion> questions = questionBankQuestionRepository.findRandomActiveByBankAndDifficultyExact(
                    bankId,
                    difficulties[index],
                    needed
            );
            for (QuestionBankQuestion question : questions) {
                result.add(toQuestionInfo(question));
                selectedIds.add(question.getId());
            }
        }

        int stillNeeded = targetCount - result.size();
        if (stillNeeded > 0) {
            List<QuestionBankQuestion> fill = questionBankQuestionRepository.findRandomActiveByBankExcluding(
                    bankId,
                    new ArrayList<>(selectedIds),
                    stillNeeded
            );
            fill.forEach(question -> result.add(toQuestionInfo(question)));
        }

        Collections.shuffle(result);
        return result;
    }

    @Override
    public List<Object[]> countBySkillAreaAndDifficulty(Long bankId) {
        if (bankId == null) {
            return List.of();
        }
        return questionBankQuestionRepository.countBySkillAreaAndDifficulty(bankId);
    }

    @Override
    public List<QuestionInfo> selectRandomQuestionsBySkillAreaAndDifficulty(
            Long bankId,
            String skillArea,
            String difficulty,
            int limit
    ) {
        if (bankId == null || skillArea == null || difficulty == null || limit <= 0) {
            return List.of();
        }
        return questionBankQuestionRepository.findRandomActiveByBankAndSkillAreaAndDifficulty(
                        bankId,
                        skillArea,
                        difficulty.toUpperCase(),
                        limit)
                .stream()
                .map(this::toQuestionInfo)
                .toList();
    }

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

    private TaxonomyScope resolveScopeForCreate(CreateQuestionBankRequest request) {
        return resolveScope(
                request.getDomainId(),
                request.getJobPositionId(),
                request.getSkillId(),
                request.getDomain(),
                null,
                null,
                request.getSkillName(),
                true
        );
    }

    private TaxonomyScope resolveScopeForUpdate(QuestionBank bank, UpdateQuestionBankRequest request) {
        boolean scopeRequested = request.getDomainId() != null
                || request.getJobPositionId() != null
                || request.getSkillId() != null
                || normalizeOptional(request.getSkillName()) != null;
        if (!scopeRequested) {
            return resolveScope(
                    bank.getDomainId(),
                    bank.getJobPositionId(),
                    bank.getSkillId(),
                    bank.getDomain(),
                    null,
                    null,
                    bank.getSkillName(),
                    true
            );
        }

        return resolveScope(
                request.getDomainId() != null ? request.getDomainId() : bank.getDomainId(),
                request.getJobPositionId() != null ? request.getJobPositionId() : bank.getJobPositionId(),
                request.getSkillId(),
                bank.getDomain(),
                null,
                null,
                request.getSkillName(),
                true
        );
    }

    private TaxonomyScope resolveScope(
            Long domainId,
            Long jobPositionId,
            Long skillId,
            String legacyDomain,
            String legacyIndustry,
            String legacyJobRole,
            String legacySkillName,
            boolean required
    ) {
        Domain domain = resolveDomain(domainId, legacyDomain)
                .orElseThrow(() -> required
                        ? new ApiException(ErrorCode.BAD_REQUEST, "Domain khong hop le hoac chua ton tai trong taxonomy.")
                        : new ApiException(ErrorCode.BAD_REQUEST, "Domain khong hop le."));

        if (domain.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Domain khong con ACTIVE.");
        }

        JobPosition jobPosition = resolveJobPosition(jobPositionId, domain.getId(), legacyJobRole, legacyIndustry)
                .orElseThrow(() -> required
                        ? new ApiException(ErrorCode.BAD_REQUEST, "Job position khong hop le hoac khong thuoc domain da chon.")
                        : new ApiException(ErrorCode.BAD_REQUEST, "Job position khong hop le."));

        if (jobPosition.getStatus() != TaxonomyStatus.ACTIVE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Job position khong con ACTIVE.");
        }
        if (!domain.getId().equals(jobPosition.getDomainId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Job position khong thuoc domain da chon.");
        }

        Skill skill = resolveSkill(skillId, legacySkillName).orElse(null);
        if (skill != null && skill.getStatus() != SkillStatus.ACTIVE) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Skill khong con ACTIVE.");
        }

        String skillSnapshot = null;
        Long resolvedSkillId = null;
        if (skill != null) {
            resolvedSkillId = skill.getId();
            skillSnapshot = normalizeOptional(skill.getCanonicalKey()) != null
                    ? skill.getCanonicalKey()
                    : SkillNameUtils.normalize(skill.getName());
        } else if (skillId != null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Skill khong hop le hoac chua ton tai.");
        }

        return new TaxonomyScope(
                domain.getId(),
                jobPosition.getId(),
                resolvedSkillId,
                normalizeOptional(domain.getCode()),
                normalizeOptional(domain.getName()),
                normalizeOptional(jobPosition.getName()),
                skillSnapshot
        );
    }

    private Optional<TaxonomyScope> resolveLegacyScopeIfPossible(
            String domain,
            String industry,
            String jobRole,
            String skillName
    ) {
        try {
            return Optional.of(resolveScope(null, null, null, domain, industry, jobRole, skillName, false));
        } catch (ApiException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Domain> resolveDomain(Long domainId, String legacyDomain) {
        if (domainId != null) {
            return domainRepository.findById(domainId);
        }
        String normalizedDomain = normalizeOptional(legacyDomain);
        if (normalizedDomain == null) {
            return Optional.empty();
        }
        return domainRepository.findByCodeIgnoreCase(normalizedDomain);
    }

    private Optional<JobPosition> resolveJobPosition(
            Long jobPositionId,
            Long domainId,
            String legacyJobRole,
            String legacyIndustry
    ) {
        if (jobPositionId != null) {
            return jobPositionRepository.findById(jobPositionId);
        }

        String requestedName = normalizeOptional(legacyJobRole);
        if (requestedName == null) {
            requestedName = normalizeOptional(legacyIndustry);
        }
        if (domainId == null || requestedName == null) {
            return Optional.empty();
        }

        String normalizedRequestedName = requestedName.trim();
        return jobPositionRepository.findByDomainId(domainId).stream()
                .filter(jobPosition -> equalsIgnoreCase(jobPosition.getName(), normalizedRequestedName)
                        || equalsIgnoreCase(jobPosition.getCode(), normalizedRequestedName))
                .findFirst();
    }

    private Optional<Skill> resolveSkill(Long skillId, String legacySkillName) {
        if (skillId != null) {
            return skillRepository.findById(skillId);
        }

        String normalizedSkillName = normalizeOptional(legacySkillName);
        if (normalizedSkillName == null) {
            return Optional.empty();
        }

        String canonicalKey = SkillNameUtils.normalize(normalizedSkillName);
        return skillRepository.findByCanonicalKey(canonicalKey)
                .or(() -> skillRepository.findByNameIgnoreCase(normalizedSkillName));
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String resolveDomainName(QuestionBank bank) {
        if (bank.getDomainId() == null) {
            return bank.getDomain();
        }
        return domainRepository.findById(bank.getDomainId())
                .map(Domain::getName)
                .orElse(bank.getDomain());
    }

    private String resolveJobPositionName(QuestionBank bank) {
        if (bank.getJobPositionId() == null) {
            return null;
        }
        return jobPositionRepository.findById(bank.getJobPositionId())
                .map(JobPosition::getName)
                .orElse(null);
    }

    private QuestionBankResponse toResponse(QuestionBank bank) {
        Map<String, Long> difficultyBreakdown = new LinkedHashMap<>();
        List<Object[]> counts = questionBankQuestionRepository.countByDifficulty(bank.getId());
        for (Object[] row : counts) {
            difficultyBreakdown.put((String) row[0], (Long) row[1]);
        }

        Map<String, Long> skillBreakdown = new LinkedHashMap<>();
        List<Object[]> skillCounts = questionBankQuestionRepository.countBySkillArea(bank.getId());
        for (Object[] row : skillCounts) {
            if (row[0] != null) {
                skillBreakdown.put((String) row[0], (Long) row[1]);
            }
        }

        return QuestionBankResponse.builder()
                .id(bank.getId())
                .domainId(bank.getDomainId())
                .jobPositionId(bank.getJobPositionId())
                .skillId(bank.getSkillId())
                .domain(bank.getDomain())
                .domainName(resolveDomainName(bank))
                .jobPositionName(resolveJobPositionName(bank))
                .skillName(bank.getSkillName())
                .title(bank.getTitle())
                .description(bank.getDescription())
                .difficultyDistribution(bank.getDifficultyDistribution())
                .isActive(bank.getIsActive())
                .createdAt(bank.getCreatedAt())
                .updatedAt(bank.getUpdatedAt())
                .activeQuestionCount(bank.getActiveQuestionCount())
                .difficultyBreakdown(difficultyBreakdown)
                .skillBreakdown(skillBreakdown)
                .build();
    }

    private QuestionBankSummaryResponse toSummaryResponse(QuestionBank bank) {
        return QuestionBankSummaryResponse.builder()
                .id(bank.getId())
                .domainId(bank.getDomainId())
                .jobPositionId(bank.getJobPositionId())
                .skillId(bank.getSkillId())
                .domain(bank.getDomain())
                .domainName(resolveDomainName(bank))
                .jobPositionName(resolveJobPositionName(bank))
                .skillName(bank.getSkillName())
                .title(bank.getTitle())
                .activeQuestionCount(bank.getActiveQuestionCount())
                .isActive(bank.getIsActive())
                .createdAt(bank.getCreatedAt())
                .build();
    }

    private Map<String, Double> parseDistribution(String json) {
        String normalizedJson = (json == null || json.isBlank())
                ? DEFAULT_DIFFICULTY_DISTRIBUTION
                : json;
        try {
            return objectMapper.readValue(normalizedJson, new TypeReference<Map<String, Double>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse difficulty distribution {}, using default", normalizedJson);
            return objectMapper.convertValue(
                    Map.of("BEGINNER", 0.20, "INTERMEDIATE", 0.35, "ADVANCED", 0.30, "EXPERT", 0.15),
                    new TypeReference<Map<String, Double>>() {});
        }
    }

    private QuestionInfo toQuestionInfo(QuestionBankQuestion question) {
        List<String> options = null;
        if (question.getOptions() != null) {
            try {
                options = objectMapper.readValue(question.getOptions(), new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn("Failed to parse options for question {}: {}", question.getId(), e.getMessage());
            }
        }

        return new QuestionInfo(
                question.getId(),
                question.getQuestionText(),
                options,
                question.getCorrectAnswer(),
                question.getExplanation(),
                question.getDifficulty(),
                question.getSkillArea()
        );
    }
}
