package com.exe.skillverse_backend.question_bank_service.service.impl;

import com.exe.skillverse_backend.ai_service.entity.ExpertPromptConfig;
import com.exe.skillverse_backend.ai_service.repository.ExpertPromptConfigRepository;
import com.exe.skillverse_backend.question_bank_service.dto.request.CreateQuestionBankRequest;
import com.exe.skillverse_backend.question_bank_service.dto.response.QuestionBankResponse;
import com.exe.skillverse_backend.question_bank_service.dto.response.SkillResolveResponse;
import com.exe.skillverse_backend.question_bank_service.repository.QuestionBankRepository;
import com.exe.skillverse_backend.question_bank_service.service.QuestionBankService;
import com.exe.skillverse_backend.question_bank_service.service.SkillResolveService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI-powered service that resolves a skill name to the matching
 * domain / industry / job role using the same Mistral AI model
 * used for quiz generation.
 *
 * The prompt is intentionally compact to keep latency low (~1-3s).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class SkillResolveServiceImpl implements SkillResolveService {

    private final ChatModel generateTestChatModel;
    private final ExpertPromptConfigRepository expertPromptConfigRepository;
    private final QuestionBankRepository questionBankRepository;
    private final QuestionBankService questionBankService;
    private final ObjectMapper objectMapper;

    @Override
    public SkillResolveResponse resolveSkill(String skillName) {
        return doResolve(skillName, false);
    }

    @Override
    public SkillResolveResponse resolveAndCreateQuestionBank(String skillName) {
        return doResolve(skillName, true);
    }

    // ================================================================
    // Core logic
    // ================================================================

    private SkillResolveResponse doResolve(String skillName, boolean autoCreate) {
        String normalizedSkill = normalizeSkillName(skillName);
        log.info("Resolving skill '{}' (normalized: '{}'), autoCreate={}", skillName, normalizedSkill, autoCreate);

        // Step 1: Build available roles catalog for the AI prompt
        List<ExpertPromptConfig> allConfigs = expertPromptConfigRepository
                .findByIsActiveTrueOrderByDomainAscIndustryAscJobRoleAsc();

        if (allConfigs.isEmpty()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "No expert prompt configs found in database");
        }

        String rolesCatalog = buildRolesCatalog(allConfigs);

        // Step 2: Call AI
        String prompt = buildResolvePrompt(skillName, rolesCatalog);
        String aiResponse;
        try {
            aiResponse = ChatClient.create(generateTestChatModel)
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI skill resolution failed for '{}': {}", skillName, e.getMessage(), e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "AI analysis failed: " + e.getMessage());
        }

        // Step 3: Parse AI response
        AiResolveResult parsed = parseAiResponse(aiResponse, allConfigs);

        // Step 4: Check existing question bank
        String domainForDb = parsed.domain;
        Optional<com.exe.skillverse_backend.question_bank_service.entity.QuestionBank> existingBank =
                questionBankRepository.findByExactScope(
                        domainForDb,
                        parsed.industry,
                        parsed.jobRole,
                        normalizedSkill,
                        PageRequest.of(0, 1)
                ).stream().findFirst();

        // Build response
        SkillResolveResponse.SkillResolveResponseBuilder responseBuilder = SkillResolveResponse.builder()
                .skillName(normalizedSkill)
                .domain(parsed.domain)
                .industry(parsed.industry)
                .jobRole(parsed.jobRole)
                .confidence(parsed.confidence)
                .reasoning(parsed.reasoning)
                .alternatives(parsed.alternatives);

        if (existingBank.isPresent()) {
            responseBuilder
                    .questionBankExists(true)
                    .existingQuestionBankId(existingBank.get().getId())
                    .existingQuestionBankTitle(existingBank.get().getTitle());
        } else {
            responseBuilder.questionBankExists(false);
        }

        // Step 5: Auto-create question bank if requested and doesn't exist
        if (autoCreate && existingBank.isEmpty() && parsed.confidence >= 50) {
            try {
                QuestionBankResponse createdBank = questionBankService.createBank(
                        CreateQuestionBankRequest.builder()
                                .domain(parsed.domain)
                                .industry(parsed.industry)
                                .jobRole(parsed.jobRole)
                                .skillName(normalizedSkill)
                                .title("Bộ câu hỏi đầu vào " + parsed.jobRole + " - " + formatSkillLabel(normalizedSkill))
                                .description("Bộ câu hỏi đánh giá đầu vào cho kỹ năng "
                                        + formatSkillLabel(normalizedSkill) + " thuộc vị trí "
                                        + parsed.jobRole + " trong ngành " + parsed.industry + ".")
                                .build()
                );
                responseBuilder
                        .createdQuestionBankId(createdBank.getId())
                        .createdQuestionBankTitle(createdBank.getTitle())
                        .questionBankExists(true)
                        .existingQuestionBankId(createdBank.getId())
                        .existingQuestionBankTitle(createdBank.getTitle());

                log.info("Auto-created question bank {} for skill '{}'", createdBank.getId(), normalizedSkill);
            } catch (Exception e) {
                log.warn("Auto-create question bank failed for skill '{}': {}", normalizedSkill, e.getMessage());
                // Don't fail the whole request, just report without the created bank
            }
        }

        return responseBuilder.build();
    }

    // ================================================================
    // AI Prompt & Parsing
    // ================================================================

    private String buildRolesCatalog(List<ExpertPromptConfig> configs) {
        StringBuilder sb = new StringBuilder();
        String currentDomain = "";
        String currentIndustry = "";

        for (ExpertPromptConfig config : configs) {
            if (!config.getDomain().equals(currentDomain)) {
                currentDomain = config.getDomain();
                sb.append("\n[DOMAIN: ").append(currentDomain).append("]\n");
                currentIndustry = "";
            }
            if (!config.getIndustry().equals(currentIndustry)) {
                currentIndustry = config.getIndustry();
                sb.append("  [INDUSTRY: ").append(currentIndustry).append("]\n");
            }
            sb.append("    - ").append(config.getJobRole());
            if (config.getKeywords() != null && !config.getKeywords().isBlank()) {
                sb.append(" (keywords: ").append(config.getKeywords()).append(")");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private String buildResolvePrompt(String skillName, String rolesCatalog) {
        return """
            Bạn là hệ thống phân loại kỹ năng (skill classification).
            
            Cho skill: "%s"
            
            Dưới đây là danh sách TẤT CẢ các domain/industry/jobRole có trong hệ thống:
            %s
            
            Nhiệm vụ: Xác định skill "%s" phù hợp nhất với domain/industry/jobRole NÀO trong danh sách trên.
            
            Quy tắc:
            1. Chỉ được chọn từ danh sách đã cho, KHÔNG được tạo giá trị mới.
            2. Ưu tiên match theo keywords trước, rồi theo tên jobRole.
            3. Nếu skill có thể thuộc nhiều role, chọn role phù hợp nhất và liệt kê alternatives.
            4. Confidence: 90-100 = chắc chắn, 70-89 = khá chắc, 50-69 = có thể, <50 = không chắc.
            
            Ví dụ:
            - "React" → Information Technology / Software Development / Frontend Developer (confidence: 95)
            - "Java Spring Boot" → Information Technology / Software Development / Backend Developer (confidence: 95)
            - "Figma" → Information Technology / Software Development / UI/UX Designer (confidence: 90)
            
            CHỈ trả lời bằng JSON hợp lệ (không markdown):
            {
              "domain": "...",
              "industry": "...",
              "jobRole": "...",
              "confidence": 95,
              "reasoning": "Giải thích ngắn tại sao chọn role này",
              "alternatives": [
                {"domain": "...", "industry": "...", "jobRole": "...", "confidence": 70}
              ]
            }
            """.formatted(skillName, rolesCatalog, skillName);
    }

    @SuppressWarnings("unchecked")
    private AiResolveResult parseAiResponse(String aiResponse, List<ExpertPromptConfig> allConfigs) {
        String jsonStr = extractJsonFromResponse(aiResponse);

        try {
            Map<String, Object> parsed = objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});

            String rawDomain = (String) parsed.get("domain");
            String rawIndustry = (String) parsed.get("industry");
            String rawJobRole = (String) parsed.get("jobRole");
            int rawConfidence = parsed.get("confidence") instanceof Number
                    ? ((Number) parsed.get("confidence")).intValue()
                    : 50;
            String reasoning = (String) parsed.get("reasoning");

            // Validate that the AI returned values that actually exist
            boolean isValid = allConfigs.stream().anyMatch(c ->
                    c.getDomain().equalsIgnoreCase(rawDomain)
                    && c.getIndustry().equalsIgnoreCase(rawIndustry)
                    && c.getJobRole().equalsIgnoreCase(rawJobRole)
            );

            // Use corrected values if AI returned a non-existent combo
            String domain = rawDomain;
            String industry = rawIndustry;
            String jobRole = rawJobRole;
            int confidence = rawConfidence;

            if (!isValid) {
                log.warn("AI returned non-existent combo: {}/{}/{}, attempting correction", rawDomain, rawIndustry, rawJobRole);
                ExpertPromptConfig closest = findClosestConfig(rawDomain, rawIndustry, rawJobRole, allConfigs);
                if (closest != null) {
                    domain = closest.getDomain();
                    industry = closest.getIndustry();
                    jobRole = closest.getJobRole();
                    confidence = Math.max(rawConfidence - 20, 30);
                }
            }

            // Parse alternatives
            List<SkillResolveResponse.AlternativeMatch> alternatives = new ArrayList<>();
            Object altsObj = parsed.get("alternatives");
            if (altsObj instanceof List) {
                for (Object alt : (List<?>) altsObj) {
                    if (alt instanceof Map) {
                        Map<String, Object> altMap = (Map<String, Object>) alt;
                        String altDomain = (String) altMap.get("domain");
                        String altIndustry = (String) altMap.get("industry");
                        String altJobRole = (String) altMap.get("jobRole");
                        int altConfidence = altMap.get("confidence") instanceof Number
                                ? ((Number) altMap.get("confidence")).intValue()
                                : 40;

                        // Only include if the combo exists in our system
                        boolean altValid = allConfigs.stream().anyMatch(c ->
                                c.getDomain().equalsIgnoreCase(altDomain)
                                && c.getIndustry().equalsIgnoreCase(altIndustry)
                                && c.getJobRole().equalsIgnoreCase(altJobRole)
                        );

                        if (altValid) {
                            alternatives.add(SkillResolveResponse.AlternativeMatch.builder()
                                    .domain(altDomain)
                                    .industry(altIndustry)
                                    .jobRole(altJobRole)
                                    .confidence(altConfidence)
                                    .build());
                        }
                    }
                }
            }

            return new AiResolveResult(domain, industry, jobRole, confidence, reasoning, alternatives);
        } catch (Exception e) {
            log.error("Failed to parse AI skill resolve response: {}", e.getMessage());
            log.debug("Raw AI response: {}", aiResponse);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to parse AI response: " + e.getMessage());
        }
    }

    private ExpertPromptConfig findClosestConfig(String domain, String industry, String jobRole,
                                                  List<ExpertPromptConfig> configs) {
        // Try exact jobRole match
        for (ExpertPromptConfig c : configs) {
            if (c.getJobRole().equalsIgnoreCase(jobRole)) {
                return c;
            }
        }
        // Try domain + industry match
        for (ExpertPromptConfig c : configs) {
            if (c.getDomain().equalsIgnoreCase(domain) && c.getIndustry().equalsIgnoreCase(industry)) {
                return c;
            }
        }
        // Just return first config
        return configs.isEmpty() ? null : configs.get(0);
    }

    private String extractJsonFromResponse(String response) {
        String jsonStr = response.trim();
        if (jsonStr.startsWith("```json")) {
            jsonStr = jsonStr.substring(7);
        } else if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.substring(3);
        }
        if (jsonStr.endsWith("```")) {
            jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
        }
        return jsonStr.trim();
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String normalizeSkillName(String skillName) {
        if (skillName == null || skillName.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Skill name is required");
        }
        return skillName.trim()
                .replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "")
                .replaceAll("_+", "_")
                .toUpperCase(Locale.ROOT);
    }

    private String formatSkillLabel(String normalizedSkill) {
        if (normalizedSkill == null) return "";
        String[] parts = normalizedSkill.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(part.substring(0, 1).toUpperCase()).append(part.substring(1));
        }
        return sb.toString();
    }

    private record AiResolveResult(
            String domain,
            String industry,
            String jobRole,
            int confidence,
            String reasoning,
            List<SkillResolveResponse.AlternativeMatch> alternatives
    ) {}
}
