package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.request.UpdateProgressRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion;
import com.exe.skillverse_backend.ai_service.dto.response.ProgressResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.response.ValidationResult;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.premium_service.exception.UsageLimitExceededException;
import com.exe.skillverse_backend.premium_service.dto.response.UserSubscriptionResponse;
import com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo;
import com.exe.skillverse_backend.premium_service.entity.PremiumPlan;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import com.exe.skillverse_backend.ai_service.dto.gemini.GeminiDTO;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for AI-powered roadmap generation using Spring AI with Gemini
 * Using Spring AI OpenAI client with Gemini's OpenAI-compatible API
 */
@Service
@Slf4j
public class AiRoadmapServiceImpl implements AiRoadmapService {

    @Value("${spring.ai.openai.api-key}")
    private String geminiApiKey;

    @Value("${spring.ai.openai.chat.options.model}")
    private String geminiModel;

    // Use Gemini native endpoint instead of OpenAI-compatible one
    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final RoadmapSessionRepository roadmapSessionRepository;
    private final UserRoadmapProgressRepository progressRepository;
    private final ObjectMapper objectMapper;
    private final InputValidationService inputValidationService;
    private final UsageLimitService usageLimitService;
    private final ExpertPromptService expertPromptService;
    private final TaxonomyService taxonomyService;
    private final PremiumService premiumService;
    private final ChatModel mistralChatModel;

    public AiRoadmapServiceImpl(
            RoadmapSessionRepository roadmapSessionRepository,
            UserRoadmapProgressRepository progressRepository,
            ObjectMapper objectMapper,
            InputValidationService inputValidationService,
            UsageLimitService usageLimitService,
            ExpertPromptService expertPromptService,
            TaxonomyService taxonomyService,
            PremiumService premiumService,
            @Qualifier("mistralAiChatModel") ChatModel mistralChatModel) {
        this.roadmapSessionRepository = roadmapSessionRepository;
        this.progressRepository = progressRepository;
        this.objectMapper = objectMapper;
        this.inputValidationService = inputValidationService;
        this.usageLimitService = usageLimitService;
        this.expertPromptService = expertPromptService;
        this.taxonomyService = taxonomyService;
        this.premiumService = premiumService;
        this.mistralChatModel = mistralChatModel;
    }

    /**
     * Pre-validate roadmap generation request without actually generating
     * 
     * @param request User request to validate
     * @return List of validation results (INFO/WARNING/ERROR severity)
     */
    public List<ValidationResult> preValidateRequest(GenerateRoadmapRequest request) {
        log.info("🔍 Pre-validating request: goal='{}', duration='{}', experience='{}', style='{}'",
                request.getGoal(), request.getDuration(), request.getExperience(), request.getStyle());

        List<ValidationResult> results = new ArrayList<>();

        // 🚨 STAGE 1: AI Goal Validation (lightweight ~100 tokens)
        ValidationResult aiValidation = validateGoalWithAI(request.getGoal());
        results.add(aiValidation);

        // If goal is invalid, short-circuit to save tokens
        if (aiValidation.isError()) {
            log.error("❌ Goal rejected by AI: {}", aiValidation.getMessage());
            return results; // Don't proceed to expensive inputValidationService
        }

        // 🔧 STAGE 2: Input Validation (format, test scores, etc.)
        results.addAll(inputValidationService.validateWithWarnings(request));

        long errorCount = results.stream().filter(ValidationResult::isError).count();
        long warningCount = results.stream().filter(ValidationResult::isWarning).count();
        long infoCount = results.stream().filter(ValidationResult::isInfo).count();

        log.info("✅ Validation complete: {} errors, {} warnings, {} info",
                errorCount, warningCount, infoCount);

        return results;
    }

    /**
     * Generate a personalized learning roadmap using Gemini AI (Schema V2)
     */
    @Transactional
    public RoadmapResponse generateRoadmap(GenerateRoadmapRequest request, User user) {
        String logGoal = request.getTarget() != null && !request.getTarget().isBlank() ? request.getTarget()
                : request.getGoal();
        log.info("🚀 Generating roadmap V2 for user {} with goal/target: {}", user.getId(), logGoal);

        try {
            // Step 0: CHECK STORAGE LIMIT (Quantity Limit)
            // Roadmap limit is defined as "Max quantity user can own", NOT "Max creations
            // per day".
            // So we check DB count against Plan Limit.
            checkRoadmapStorageLimit(user);

            if (request.getAiAgentMode() != null
                    && "deep-research-pro-preview-12-2025".equalsIgnoreCase(request.getAiAgentMode())) {
                boolean hasPremium = premiumService.hasActivePremiumSubscription(user.getId());
                if (!hasPremium) {
                    throw new ApiException(ErrorCode.FORBIDDEN,
                            "Chỉ tài khoản Premium mới có thể chọn chế độ AI Deep Research");
                }
            }

            // Step 1: AI Goal Validation (CRITICAL - blocks invalid/malicious goals)
            ValidationResult aiValidation = validateGoalWithAI(request.getGoal());

            if (aiValidation.isError()) {
                log.error("❌ BLOCKED: Invalid goal from user {} - '{}'", user.getId(), request.getGoal());
                throw new ApiException(
                        ErrorCode.BAD_REQUEST,
                        "Mục tiêu không hợp lệ: " + aiValidation.getMessage());
            }

            if (aiValidation.isWarning()) {
                log.warn("⚠️ WARNING: Vague goal from user {} - '{}' | {}",
                        user.getId(), request.getGoal(), aiValidation.getMessage());
                // Continue but log warning for monitoring
            }

            // Step 2: Format validation (throws on ERROR severity)
            inputValidationService.validateLearningGoalOrThrow(request.getGoal());
            inputValidationService.validateTextOrThrow(request.getDuration());
            inputValidationService.validateTextOrThrow(request.getExperience());
            inputValidationService.validateTextOrThrow(request.getStyle());

            // Step 3: Call Gemini API with comprehensive prompt
            String roadmapJson;
            try {
                roadmapJson = callGeminiAPI(request);
            } catch (Exception e) {
                log.warn("⚠️ Gemini API failed, attempting fallback to Mistral AI: {}", e.getMessage());
                String prompt = buildPrompt(request);
                String finalPrompt = prompt + "\n\nCRITICAL: Trả lời bằng TIẾNG VIỆT. Nếu phát hiện mục tiêu/đầu vào vô lý, hãy từ chối lịch sự. Chỉ trả về JSON hợp lệ.";
                roadmapJson = callMistralAPI(finalPrompt);
                roadmapJson = extractJsonFromResponse(roadmapJson);
            }
            String storedJson = sanitizeJson(roadmapJson);

            // Step 4: Parse and validate JSON (Schema V2)
            ParsedRoadmap parsed = validateAndParseRoadmapV2(roadmapJson);

            // Inject mode-specific metadata from request for clarity
            try {
                if (request.getRoadmapMode() != null) {
                    parsed.metadata().setRoadmapMode(request.getRoadmapMode().name());
                }
                if (request.getRoadmapMode() == GenerateRoadmapRequest.RoadmapMode.SKILL_BASED) {
                    RoadmapResponse.SkillModeMeta sm = RoadmapResponse.SkillModeMeta.builder()
                            .skillName(request.getSkillName())
                            .skillCategory(request.getSkillCategory())
                            .desiredDepth(request.getDesiredDepth())
                            .learnerType(request.getLearnerType())
                            .currentSkillLevel(request.getCurrentSkillLevel())
                            .learningGoal(request.getLearningGoal())
                            .dailyLearningTime(request.getDailyLearningTime())
                            .assessmentPreference(request.getAssessmentPreference())
                            .difficultyTolerance(request.getDifficultyTolerance())
                            .toolPreference(request.getToolPreference())
                            .build();
                    parsed.metadata().setSkillMode(sm);
                    parsed.metadata().setCareerMode(null);
                } else if (request.getRoadmapMode() == GenerateRoadmapRequest.RoadmapMode.CAREER_BASED) {
                    RoadmapResponse.CareerModeMeta cm = RoadmapResponse.CareerModeMeta.builder()
                            .targetRole(request.getTargetRole())
                            .careerTrack(request.getCareerTrack())
                            .targetSeniority(request.getTargetSeniority())
                            .workMode(request.getWorkMode())
                            .targetMarket(request.getTargetMarket())
                            .companyType(request.getCompanyType())
                            .timelineToWork(request.getTimelineToWork())
                            .incomeExpectation(request.getIncomeExpectation())
                            .workExperience(request.getWorkExperience())
                            .transferableSkills(request.getTransferableSkills())
                            .confidenceLevel(request.getConfidenceLevel())
                            .build();
                    parsed.metadata().setCareerMode(cm);
                    parsed.metadata().setSkillMode(null);
                }
            } catch (Exception ignored) {
            }

            // Step 5: Time budget validator vs total_estimated_hours
            List<String> warnings = new ArrayList<>();
            try {
                if (parsed.statistics() != null && parsed.statistics().getTotalEstimatedHours() != null) {
                    int minutesPerDay = parseDailyTimeMinutes(request.getDailyTime());
                    int plannedDays = parseDesiredDurationDays(request.getDesiredDuration());
                    double timeBudgetHours = (minutesPerDay * plannedDays) / 60.0;
                    double totalHoursGen = parsed.statistics().getTotalEstimatedHours();
                    double diff = Math.abs(totalHoursGen - timeBudgetHours);
                    double rel = timeBudgetHours > 0 ? diff / timeBudgetHours : 0.0;
                    if (rel > 0.10) {
                        String note = "Cảnh báo: Tổng thời gian lộ trình (" + String.format("%.1f", totalHoursGen)
                                + "h) lệch hơn 10% so với ngân sách thời gian ("
                                + String.format("%.1f", timeBudgetHours)
                                + "h).";
                        String existing = parsed.metadata().getValidationNotes();
                        parsed.metadata().setValidationNotes(
                                existing == null || existing.isBlank() ? note : existing + " " + note);
                        warnings.add(note);
                        String priority = request.getPriority();
                        if (priority != null && priority.equalsIgnoreCase("Nhanh đi làm")) {
                            warnings.add("Đề xuất: Giảm số node hoặc hạ độ khó để phù hợp ưu tiên nhanh đi làm");
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            // Step 6: Extract statistics for database
            Integer totalNodes = parsed.statistics() != null ? parsed.statistics().getTotalNodes()
                    : parsed.nodes().size();
            Double totalHours = parsed.statistics() != null ? parsed.statistics().getTotalEstimatedHours()
                    : calculateTotalHours(parsed.nodes());

            // Step 7: Save to database with V2 schema
            RoadmapSession session = RoadmapSession.builder()
                    .user(user)
                    .schemaVersion(2)
                    // Metadata
                    .title(parsed.metadata().getTitle())
                    .originalGoal(parsed.metadata().getOriginalGoal())
                    .validatedGoal(parsed.metadata().getValidatedGoal())
                    .duration(parsed.metadata().getDuration())
                    .experienceLevel(parsed.metadata().getExperienceLevel())
                    .learningStyle(parsed.metadata().getLearningStyle())
                    .roadmapMode(truncate(parsed.metadata().getRoadmapMode() != null ? parsed.metadata().getRoadmapMode()
                            : (request.getRoadmapMode() != null ? request.getRoadmapMode().name() : null), 20))
                    .roadmapType(truncate(parsed.metadata().getRoadmapType() != null ? parsed.metadata().getRoadmapType()
                            : request.getRoadmapType(), 20))
                    .target(parsed.metadata().getTarget() != null ? parsed.metadata().getTarget() : request.getTarget())
                    .finalObjective(
                            truncate(parsed.metadata().getFinalObjective() != null ? parsed.metadata().getFinalObjective()
                                    : request.getFinalObjective(), 100))
                    // Statistics (for premium quota)
                    .totalNodes(totalNodes)
                    .totalEstimatedHours(totalHours)
                    .difficultyLevel(truncate(parsed.metadata().getDifficultyLevel(), 20))
                    // Premium tracking
                    .isPremiumGenerated(false) // TODO: Check user premium status
                    // Full JSON
                    .roadmapJson(storedJson)
                    .build();

            session = roadmapSessionRepository.save(session);

            log.info("✅ Roadmap V2 session {} created: {} nodes, {}h, difficulty: {}",
                    session.getId(), totalNodes, String.format("%.1f", totalHours),
                    parsed.metadata().getDifficultyLevel());

            // Step 8: Return response (new format)
            return RoadmapResponse.builder()
                    .sessionId(session.getId())
                    .metadata(parsed.metadata())
                    .roadmap(parsed.nodes())
                    .statistics(parsed.statistics())
                    .learningTips(parsed.learningTips())
                    .warnings(warnings)
                    .overview(parsed.overview())
                    .structure(parsed.structure())
                    .thinkingProgression(parsed.thinkingProgression())
                    .projectsEvidence(parsed.projectsEvidence())
                    .nextSteps(parsed.nextSteps())
                    .skillDependencies(parsed.skillDependencies())
                    .createdAt(session.getCreatedAt())
                    .build();

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Failed to generate roadmap V2", e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to generate roadmap: " + e.getMessage());
        }
    }

    /**
     * Helper to truncate strings to database column limits
     */
    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }

    public Map<String, Long> getModeCountsGlobal() {
        Map<String, Long> map = new HashMap<>();
        try {
            List<Object[]> rows = roadmapSessionRepository.countGroupedByMode();
            for (Object[] row : rows) {
                String mode = (String) row[0];
                Long count = (Long) row[1];
                if (mode != null)
                    map.put(mode, count);
            }
        } catch (Exception e) {
            log.warn("Failed to load global mode counts: {}", e.getMessage());
        }
        map.putIfAbsent("SKILL_BASED", 0L);
        map.putIfAbsent("CAREER_BASED", 0L);
        return map;
    }

    public Map<String, Long> getModeCountsForUser(Long userId) {
        Map<String, Long> map = new HashMap<>();
        try {
            List<Object[]> rows = roadmapSessionRepository.countGroupedByModeForUser(userId);
            for (Object[] row : rows) {
                String mode = (String) row[0];
                Long count = (Long) row[1];
                if (mode != null)
                    map.put(mode, count);
            }
        } catch (Exception e) {
            log.warn("Failed to load user mode counts: {}", e.getMessage());
        }
        map.putIfAbsent("SKILL_BASED", 0L);
        map.putIfAbsent("CAREER_BASED", 0L);
        return map;
    }

    public Map<String, Long> getModeCountsGlobalRange(Instant from, Instant to) {
        Map<String, Long> map = new HashMap<>();
        try {
            List<Object[]> rows = roadmapSessionRepository.countGroupedByModeInRange(from, to);
            for (Object[] row : rows) {
                String mode = (String) row[0];
                Long count = (Long) row[1];
                if (mode != null)
                    map.put(mode, count);
            }
        } catch (Exception e) {
            log.warn("Failed to load global mode counts (range): {}", e.getMessage());
        }
        map.putIfAbsent("SKILL_BASED", 0L);
        map.putIfAbsent("CAREER_BASED", 0L);
        return map;
    }

    public Map<String, Long> getModeCountsForUserRange(Long userId, Instant from, Instant to) {
        Map<String, Long> map = new HashMap<>();
        try {
            List<Object[]> rows = roadmapSessionRepository.countGroupedByModeInRangeForUser(userId, from, to);
            for (Object[] row : rows) {
                String mode = (String) row[0];
                Long count = (Long) row[1];
                if (mode != null)
                    map.put(mode, count);
            }
        } catch (Exception e) {
            log.warn("Failed to load user mode counts (range): {}", e.getMessage());
        }
        map.putIfAbsent("SKILL_BASED", 0L);
        map.putIfAbsent("CAREER_BASED", 0L);
        return map;
    }

    private Map<String, Map<String, Long>> aggregateBucketRows(List<Object[]> rows) {
        Map<String, Map<String, Long>> buckets = new LinkedHashMap<>();
        for (Object[] row : rows) {
            Timestamp ts = (Timestamp) row[0];
            String mode = (String) row[1];
            Number cntNum = (Number) row[2];
            Long cnt = cntNum == null ? 0L : cntNum.longValue();
            String key = ts.toInstant().toString();
            Map<String, Long> m = buckets.computeIfAbsent(key, k -> new HashMap<>());
            m.put(mode, cnt);
        }
        // Ensure both modes present
        for (Map.Entry<String, Map<String, Long>> e : buckets.entrySet()) {
            e.getValue().putIfAbsent("SKILL_BASED", 0L);
            e.getValue().putIfAbsent("CAREER_BASED", 0L);
        }
        return buckets;
    }

    public Map<String, Map<String, Long>> getModeCountsDaily(Instant from, Instant to) {
        List<Object[]> rows = roadmapSessionRepository.countModeDaily(from, to);
        return aggregateBucketRows(rows);
    }

    public Map<String, Map<String, Long>> getModeCountsDailyForUser(Long userId, Instant from,
            Instant to) {
        List<Object[]> rows = roadmapSessionRepository.countModeDailyForUser(userId, from, to);
        return aggregateBucketRows(rows);
    }

    public Map<String, Map<String, Long>> getModeCountsWeekly(Instant from, Instant to) {
        List<Object[]> rows = roadmapSessionRepository.countModeWeekly(from, to);
        return aggregateBucketRows(rows);
    }

    public Map<String, Map<String, Long>> getModeCountsWeeklyForUser(Long userId, Instant from,
            Instant to) {
        List<Object[]> rows = roadmapSessionRepository.countModeWeeklyForUser(userId, from, to);
        return aggregateBucketRows(rows);
    }

    public Map<String, Map<String, Long>> getModeCountsMonthly(Instant from, Instant to) {
        List<Object[]> rows = roadmapSessionRepository.countModeMonthly(from, to);
        return aggregateBucketRows(rows);
    }

    public Map<String, Map<String, Long>> getModeCountsMonthlyForUser(Long userId, Instant from,
            Instant to) {
        List<Object[]> rows = roadmapSessionRepository.countModeMonthlyForUser(userId, from, to);
        return aggregateBucketRows(rows);
    }

    /**
     * Calculate total hours from nodes (fallback if statistics missing)
     */
    private Double calculateTotalHours(List<RoadmapResponse.RoadmapNode> nodes) {
        int totalMinutes = nodes.stream()
                .mapToInt(RoadmapResponse.RoadmapNode::getEstimatedTimeMinutes)
                .sum();
        return totalMinutes / 60.0;
    }

    /**
     * Call Mistral AI via Spring AI ChatModel
     */
    private String callMistralAPI(String prompt) {
        log.info("📡 Calling Mistral AI as fallback");
        try {
            return ChatClient.builder(mistralChatModel)
                .build()
                .prompt()
                .user(prompt)
                .call()
                .content();
        } catch (Exception e) {
            log.error("❌ Failed to call Mistral AI: {}", e.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Mistral AI generation failed: " + e.getMessage());
        }
    }

    /**
     * Call Gemini API directly using RestClient with extended timeout
     */
    private String callGeminiAPI(GenerateRoadmapRequest request) {
        String prompt = buildPrompt(request);
        
        // Append critical instruction for JSON format
        String finalPrompt = prompt + "\n\nCRITICAL: Trả lời bằng TIẾNG VIỆT. Nếu phát hiện mục tiêu/đầu vào vô lý (ví dụ: IELTS 10.0, nội dung thô tục), hãy từ chối lịch sự bằng tiếng Việt và gợi ý cách nhập lại hợp lệ. Chỉ trả về JSON hợp lệ như yêu cầu.";

        String rawResponse = callGeminiDirectly(finalPrompt, geminiModel);
        return extractJsonFromResponse(rawResponse);
    }

    /**
     * Call Gemini API directly via HTTP REST and return raw text response
     */
    private String callGeminiDirectly(String prompt, String modelName) {
        log.info("📡 Calling Gemini API directly (model: {})", modelName);

        try {
            // 1. Configure RestClient with 1-hour timeout
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(60 * 1000); // 60s connect
            requestFactory.setReadTimeout(3600 * 1000);  // 1 hour read

            RestClient restClient = RestClient.builder()
                    .requestFactory(requestFactory)
                    .baseUrl(GEMINI_API_BASE_URL)
                    .build();

            // 2. Build Request Payload (Google Gemini Format)
            GeminiDTO.Part part = GeminiDTO.Part.builder()
                    .text(prompt)
                    .build();

            GeminiDTO.Content content = GeminiDTO.Content.builder()
                    .role("user")
                    .parts(List.of(part))
                    .build();

            GeminiDTO.GenerationConfig genConfig = GeminiDTO.GenerationConfig.builder()
                    .temperature(0.7)
                    .maxOutputTokens(30000)
                    // .responseMimeType("application/json") // Don't force JSON here to support validation prompt
                    .build();

            GeminiDTO.Request geminiRequest = GeminiDTO.Request.builder()
                    .contents(List.of(content))
                    .generationConfig(genConfig)
                    .build();

            // 3. Execute Request
            String url = GEMINI_API_BASE_URL + modelName + ":generateContent?key=" + geminiApiKey;
            
            GeminiDTO.Response response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(geminiRequest)
                    .retrieve()
                    .body(GeminiDTO.Response.class);

            // 4. Process Response
            if (response != null && response.getCandidates() != null && !response.getCandidates().isEmpty()) {
                GeminiDTO.Candidate candidate = response.getCandidates().get(0);
                if (candidate.getContent() != null && candidate.getContent().getParts() != null 
                        && !candidate.getContent().getParts().isEmpty()) {
                    
                    String rawText = candidate.getContent().getParts().get(0).getText();
                    log.debug("Raw Gemini response length: {}", rawText.length());
                    
                    return rawText;
                }
            }

            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Empty response from Gemini API");

        } catch (Exception e) {
            log.error("❌ Failed to call Gemini API directly: {}", e.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "AI generation failed: " + e.getMessage());
        }
    }

    /**
     * Extract JSON from AI response, handling markdown code blocks
     */
    private String extractJsonFromResponse(String response) {
        String text = response.trim();

        // Extract JSON from markdown code blocks if present
        if (text.contains("```json")) {
            int startIndex = text.indexOf("```json") + 7;
            int endIndex = text.indexOf("```", startIndex);
            if (endIndex > startIndex) {
                text = text.substring(startIndex, endIndex);
            }
        } else if (text.contains("```")) {
            int startIndex = text.indexOf("```") + 3;
            int endIndex = text.indexOf("```", startIndex);
            if (endIndex > startIndex) {
                text = text.substring(startIndex, endIndex);
            }
        }

        String cleanedText = text.trim();
        log.info("Extracted JSON length: {} chars", cleanedText.length());
        log.debug("Extracted JSON preview: {}", cleanedText.substring(0, Math.min(300, cleanedText.length())));

        return cleanedText;
    }

    /**
     * Build comprehensive prompt for Gemini using System Prompt V2
     * Includes: Pattern Detection, Validation Framework, Adaptation Logic
     */
    private String buildPrompt(GenerateRoadmapRequest request) {
        // Build the comprehensive system prompt
        String systemPrompt = buildSystemPromptV2(request);

        // Add user input context
        String userContext = String.format("""

                === USER INPUT ===
                Roadmap Mode: %s
                Roadmap Type: %s
                Target: %s
                Industry: %s
                Final Objective: %s
                Core Goal: %s
                Duration: %s
                Desired Duration: %s
                Current Level: %s
                Experience Level: %s
                Learning Style: %s
                Background: %s
                Daily Time: %s
                Target Environment: %s
                Location: %s
                Priority: %s
                Tool Preferences: %s
                Difficulty Concern: %s
                Income Goal: %s

                === SKILL MODE INPUT ===
                Skill Name: %s
                Skill Category: %s
                Desired Depth: %s
                Learner Type: %s
                Current Skill Level: %s
                Learning Goal: %s
                Daily Learning Time: %s
                Assessment Preference: %s
                Difficulty Tolerance: %s
                Tool Preference: %s

                === CAREER MODE INPUT ===
                Target Role: %s
                Career Track: %s
                Target Seniority: %s
                Work Mode: %s
                Target Market: %s
                Company Type: %s
                Timeline To Work: %s
                Income Expectation: %s
                Work Experience: %s
                Transferable Skills: %s
                Confidence Level: %s

                === YOUR TASK ===
                Analyze inputs using Pattern Detection Engine.
                Validate using Validation Framework (scores, deprecated tech, time feasibility).
                Generate roadmap adapted to level, style, context, preferences.
                CRITICAL: Trong phần description của mỗi node, hãy sử dụng Markdown phong phú để làm nổi bật thông tin quan trọng:
                - Dùng **in đậm** cho từ khóa quan trọng.
                - Dùng *in nghiêng* cho lưu ý.
                - Dùng danh sách (- item) để liệt kê.
                - Dùng `code block` cho các thuật ngữ kỹ thuật hoặc lệnh.
                Return ONLY valid JSON following the exact format specified above.

                CRITICAL: Response must be pure JSON starting with { and ending with }.
                NO markdown, NO explanations, ONLY JSON.
                %s""",
                request.getRoadmapMode() != null ? request.getRoadmapMode().name() : "",
                nullSafe(request.getRoadmapType()),
                nullSafe(request.getTarget()),
                nullSafe(request.getIndustry()),
                nullSafe(request.getFinalObjective()),
                nullSafe(request.getGoal()),
                nullSafe(request.getDuration()),
                nullSafe(request.getDesiredDuration()),
                nullSafe(request.getCurrentLevel()),
                nullSafe(request.getExperience()),
                nullSafe(request.getLearningStyle() != null ? request.getLearningStyle() : request.getStyle()),
                nullSafe(request.getBackground()),
                nullSafe(request.getDailyTime()),
                nullSafe(request.getTargetEnvironment()),
                nullSafe(request.getLocation()),
                nullSafe(request.getPriority()),
                request.getToolPreferences() != null ? String.join(", ", request.getToolPreferences()) : "",
                nullSafe(request.getDifficultyConcern()),
                String.valueOf(request.getIncomeGoal() != null ? request.getIncomeGoal() : false),
                nullSafe(request.getSkillName()),
                nullSafe(request.getSkillCategory()),
                nullSafe(request.getDesiredDepth()),
                nullSafe(request.getLearnerType()),
                nullSafe(request.getCurrentSkillLevel()),
                nullSafe(request.getLearningGoal()),
                nullSafe(request.getDailyLearningTime()),
                nullSafe(request.getAssessmentPreference()),
                nullSafe(request.getDifficultyTolerance()),
                request.getToolPreference() != null ? String.join(", ", request.getToolPreference()) : "",
                nullSafe(request.getTargetRole()),
                nullSafe(request.getCareerTrack()),
                nullSafe(request.getTargetSeniority()),
                nullSafe(request.getWorkMode()),
                nullSafe(request.getTargetMarket()),
                nullSafe(request.getCompanyType()),
                nullSafe(request.getTimelineToWork()),
                String.valueOf(request.getIncomeExpectation() != null ? request.getIncomeExpectation() : false),
                nullSafe(request.getWorkExperience()),
                String.valueOf(request.getTransferableSkills() != null ? request.getTransferableSkills() : false),
                nullSafe(request.getConfidenceLevel()),
                buildConstraintsBlock(request));

        String finalPrompt = systemPrompt + userContext;
        if (request.getAiAgentMode() != null
                && "deep-research-pro-preview-12-2025".equalsIgnoreCase(request.getAiAgentMode())) {
            finalPrompt = finalPrompt
                    + "\nMODE: Deep Research Pro Preview 12/2025 — Yêu cầu tư duy nghiên cứu sâu, kiểm chứng nguồn, ưu tiên số liệu thực tế 2025, trình bày có cấu trúc và trả về JSON theo yêu cầu.";
        }
        return finalPrompt;
    }

    private String nullSafe(String v) {
        return v == null ? "" : v;
    }

    private String buildGlobalRuleBlock() {
        return """
                RULE SYSTEM:
                - Value-first, Deliverable-first
                - Trade-off awareness
                - Dependency-first ordering
                - Thinking-before-Tool
                - Level-gated progression
                - Minimal sufficiency
                - Pain-driven design with explicit pitfalls
                - Real-work simulation with constraints, deadline, KPI
                - Decision-making requirement
                - Evidence generation (artifact)
                - Rubric-based evaluation (≥3 criteria), upgrade threshold ≥70%%
                - Mode isolation: Skill-based vs Career-based output contracts
                - Skill→Career bridge only when thresholds met
                - Context-aware (VN/Global; Startup/Corporate), tool localization
                - No hallucination; Ask-before-Assume; Explain reasoning
                - Valid roadmap must include: Overview, Structure, Thinking Progression, Projects & Evidence, Next-step
                """;
    }

    private String buildDomainContextBlock(GenerateRoadmapRequest request) {
        String target = request.getTarget() != null ? request.getTarget() : request.getGoal();
        String roleCategory = taxonomyService.detectRoleCategory(target);
        String domain = taxonomyService.detectDomain(target, request.getIndustry(), roleCategory);
        String market = nullSafe(request.getTargetMarket());
        String company = nullSafe(request.getCompanyType());
        String workMode = nullSafe(request.getWorkMode());
        return "DOMAIN CONTEXT: " + nullSafe(domain)
                + " | ROLE CATEGORY: " + nullSafe(roleCategory)
                + " | MARKET: " + market
                + " | COMPANY: " + company
                + " | WORK MODE: " + workMode
                + "\nAPPLY: Role library, project kits, rubric; enforce mode-specific output contract; prefer localized tools; no hallucination.";
    }

    /**
     * Build System Prompt V2 - Comprehensive AI Roadmap Architect Instructions
     */
    private String buildSystemPromptV2(GenerateRoadmapRequest request) {
        String expertPersona = selectExpertPersonaForRequest(request);
        String ruleBlock = buildGlobalRuleBlock();
        String domainBlock = buildDomainContextBlock(request);
        String expertPackBlock = buildExpertPackContextBlock(request);
        String base = expertPersona + "\n" + ruleBlock + "\n" + domainBlock + "\n" + expertPackBlock + "\n"
                + """
                        # AI ROADMAP ARCHITECT - SYSTEM PROMPT V2

                        ## VAI TRÒ & SỨ MỆNH
                        Bạn là AI Roadmap Architect - Chuyên gia thiết kế lộ trình học tập:
                        - Phát hiện 99%% ý định học tập từ văn bản tự nhiên
                        - Xác thực thông tin với độ chính xác cao
                        - Tạo lộ trình cấu trúc cây (tree) tối ưu
                        - Trả về JSON chuẩn, KHÔNG chat hay hỏi han

                        ## PATTERN DETECTION - BẮT Ý ĐỊNH HỌC TẬP

                        ### Ý định trực tiếp:
                        - "học [X]" → Học Python, học tiếng Anh
                        - "muốn học [X]" → Muốn học design
                        - "lộ trình [X]" → Lộ trình học AI
                        - "tự học [X]" → Tự học machine learning

                        ### Ý định gián tiếp (QUAN TRỌNG):
                        - "muốn [động từ]" → "muốn thiết kế" = Học thiết kế
                        - "muốn [công cụ]" → "muốn Canva" = Học Canva
                        - "làm sao để [X]" → "làm sao để code game" = Học game programming
                        - "trở thành [nghề]" → "trở thành Backend Developer" = Lộ trình Backend
                        - Chỉ tên công cụ → "Canva" = Học Canva
                        - "thi [kỳ thi]" → "thi IELTS" = Lộ trình IELTS

                        ### VALIDATION RULES:
                        - IELTS: max 9.0 (nếu > 9.0 → điều chỉnh, ghi validation_notes)
                        - TOEIC: max 990
                        - TOEFL iBT: max 120
                        - Công nghệ lỗi thời (Flash, AngularJS 1.x...) → Gợi ý thay thế

                        ## OUTPUT FORMAT SPECIFICATION

                        CRITICAL: Trả về ĐÚNG format JSON sau (không thêm/bớt field):

                        ```json
                        {
                          "roadmap_metadata": {
                            "title": "Tên lộ trình",
                            "original_goal": "Mục tiêu gốc",
                            "validated_goal": "Mục tiêu đã làm rõ",
                            "duration": "Thời lượng",
                            "experience_level": "Mức độ kinh nghiệm",
                            "learning_style": "Phong cách học",
                            "detected_intention": "Ý định học",
                            "validation_notes": "Ghi chú xác thực hoặc null",
                            "estimated_completion": "Thời gian thực tế",
                            "difficulty_level": "beginner | intermediate | advanced | expert",
                            "prerequisites": ["Danh sách tiền đề"],
                            "career_relevance": "Liên quan nghề nghiệp",
                            "roadmap_type": "skill | career",
                            "target": "Tên kỹ năng hoặc nghề",
                            "final_objective": "Đi làm | Freelance | Học cho trường | Build sản phẩm",
                            "current_level": "zero | basic | intermediate",
                            "desired_duration": "1 tháng | 3 tháng | 6 tháng",
                            "background": "Ngữ cảnh",
                            "daily_time": "Thời gian mỗi ngày",
                            "target_environment": "Startup | corporate | freelance",
                            "location": "Việt Nam | quốc tế",
                            "priority": "Nhanh đi làm | Học sâu",
                            "tool_preferences": ["React", "Vue", "No-code"],
                            "difficulty_concern": "Mối lo",
                            "income_goal": true,
                            "roadmap_mode": "SKILL_BASED | CAREER_BASED",
                            "skill_mode": {
                              "skill_name": "ReactJS | SQL | Figma",
                              "skill_category": "Technical | Creative | Business",
                              "desired_depth": "BASIC | SOLID | ADVANCED",
                              "learner_type": "Student | Working | Explorer",
                              "current_skill_level": "ZERO | BASIC | INTERMEDIATE",
                              "learning_goal": "UNDERSTAND | APPLY | MASTER",
                              "daily_learning_time": "30_MIN | 1_HOUR | 2_HOURS",
                              "assessment_preference": "QUIZ | PROJECT | MIXED",
                              "difficulty_tolerance": "EASY | MEDIUM | HARD",
                              "tool_preference": ["React", "Vue", "No-code"]
                            },
                            "career_mode": {
                              "target_role": "Frontend Developer | Digital Marketer | UI Designer",
                              "career_track": "IT | Marketing | Design",
                              "target_seniority": "INTERN | JUNIOR | FREELANCER",
                              "work_mode": "FULL_TIME | FREELANCE | REMOTE",
                              "target_market": "VIETNAM | GLOBAL",
                              "company_type": "STARTUP | SME | CORPORATE",
                              "timeline_to_work": "3M | 6M | 12M",
                              "income_expectation": true,
                              "work_experience": "NONE | RELATED | UNRELATED",
                              "transferable_skills": true,
                              "confidence_level": "LOW | MEDIUM | HIGH"
                            }
                          },
                          "overview": {
                            "purpose": "Nghề/skill dùng để làm gì",
                            "audience": "Phù hợp với ai",
                            "post_roadmap_state": "Sau roadmap đạt trạng thái gì"
                          },
                          "structure": [
                            {
                              "phase_id": "phase-1",
                              "title": "Tên giai đoạn",
                              "timeframe": "Tuần/Tháng",
                              "goal": "Mục tiêu",
                              "skill_focus": ["Kỹ năng trọng tâm"],
                              "mindset_goal": "Mục tiêu tư duy",
                              "expected_output": "Output mong đợi"
                            }
                          ],
                          "thinking_progression": [
                            "Phase 1: ...",
                            "Phase 2: ..."
                          ],
                          "projects_evidence": [
                            {
                              "phase_id": "phase-1",
                              "project": "Tên dự án",
                              "objective": "Mục tiêu dự án",
                              "skills_proven": ["Kỹ năng chứng minh"],
                              "kpi": ["KPI đánh giá"]
                            }
                          ],
                          "next_steps": {
                            "jobs": ["Job/role có thể apply"],
                            "next_skills": ["Skill nên học tiếp"],
                            "mentors_micro_jobs": ["Mentor/micro-job/cơ hội thực tế"]
                          },
                          "skill_dependencies": [
                            { "from": "skill-a", "to": "skill-b" }
                          ],
                          "roadmap": [
                            {
                              "id": "quest-...",
                              "title": "Tiêu đề",
                              "description": "Mô tả chi tiết với Markdown đầy đủ (bôi đậm, nghiêng, list, code block)",
                              "estimated_time_minutes": 180,
                              "type": "MAIN",
                              "difficulty": "easy | medium | hard",
                              "learning_objectives": ["..."],
                              "key_concepts": ["..."],
                              "practical_exercises": ["..."],
                              "suggested_resources": ["..."],
                              "success_criteria": ["..."],
                              "prerequisites": ["..."],
                              "children": ["..."],
                              "estimated_completion_rate": "90%%"
                            }
                          ],
                          "roadmap_statistics": {
                            "total_nodes": 12,
                            "main_nodes": 8,
                            "side_nodes": 4,
                            "total_estimated_hours": 48.5,
                            "difficulty_distribution": { "easy": 4, "medium": 6, "hard": 2 }
                          },
                          "learning_tips": ["Tip 1", "Tip 2"]
                        }
                        ```

                        ## QUY TẮC ROADMAP CONSTRUCTION

                        ### Node Structure:
                        - 10-15 nodes (bắt buộc)
                        - 2-3 root nodes (không có prerequisites)
                        - Main path ≥ 6 nodes

                        ### Node Types by Experience:
                        - Mới bắt đầu: 75%% MAIN, 25%% SIDE (difficulty: 60%% easy, 30%% medium, 10%% hard)
                        - Biết một ít: 65%% MAIN, 35%% SIDE (difficulty: 30%% easy, 50%% medium, 20%% hard)
                        - Trung cấp: 55%% MAIN, 45%% SIDE (difficulty: 20%% medium, 60%% hard, 20%% expert)
                        - Nâng cao: 45%% MAIN, 55%% SIDE (difficulty: 10%% hard, 70%% expert, 20%% research)

                        ### Time Allocation:
                        - 2 tuần = 1680 phút | 1 tháng = 3600 phút | 3 tháng = 10800 phút | 6 tháng = 21600 phút | 1 năm = 43200 phút
                        - Tổng thời gian nodes ≈ 80-100%% total (20%% buffer)

                        ### Graph Integrity:
                        - Mọi node (trừ ROOT) PHẢI có prerequisites
                        - Mọi ID trong prerequisites/children PHẢI tồn tại
                        - KHÔNG circular dependencies
                        - KHÔNG orphan nodes

                        ## ADAPTATION BY LEARNING STYLE

                        ### "Theo dự án - Học bằng cách làm":
                        - Mỗi chuỗi MAIN = 1 complete project
                        - Mỗi node = 1 feature/component
                        - Description format: "Xây dựng [feature X] cho project..."

                        ### "Lý thuyết - Nắm vững khái niệm":
                        - Concept-driven approach
                        - Theory → Practice cycle
                        - Description format: "Hiểu về [concept X]. Sau node này bạn sẽ..."

                        ### "Video - Học qua hình ảnh":
                        - Video-first approach
                        - Description format: "Xem video [X] từ [platform]. Sau đó thực hành..."

                        ### "Thực hành - Tương tác nhiều":
                        - Exercise-heavy
                        - Description format: "Hoàn thành [N] bài tập về [topic]..."

                        ### "Cân bằng - Lý thuyết + Thực hành":
                        - 50%% theory, 50%% practice
                        - Alternating pattern
                        - Description format: "Phần lý thuyết:... Phần thực hành:..."

                        ## CONTENT QUALITY STANDARDS

                        ### Title Quality:
                        ✅ GOOD: "Làm quen với HTML5 và cấu trúc web", "Xây dựng API RESTful với Spring Boot"
                        ❌ BAD: "Bước 1", "Học JavaScript", "Module 3"
                        - Bắt đầu bằng động từ hành động
                        - Chứa công nghệ/kỹ năng cụ thể
                        - 40-80 ký tự, Tiếng Việt có dấu

                        ### Learning Objectives:
                        ✅ GOOD: "Tạo được form đăng ký có validation", "Xây dựng được 3 component React"
                        ❌ BAD: "Hiểu về React", "Giỏi JavaScript"
                        - Format: "[Động từ] được [Kết quả cụ thể]"

                        ### Suggested Resources:
                        ✅ GOOD: "MDN Web Docs - HTML Basics", "FreeCodeCamp - Responsive Web Design"
                        ❌ BAD: "Khóa học ABC", "Video hướng dẫn"
                        - Tài nguyên CÓ THẬT, PHỔ BIẾN, CHẤT LƯỢNG

                        ## CRITICAL REQUIREMENTS

                        1. NEVER ASK QUESTIONS - Just generate roadmap from input
                        2. ALWAYS VALIDATE - Scores, deprecated tech, time feasibility
                        3. HIGH-QUALITY CONTENT - Clear titles, specific objectives, real resources
                        4. PERFECT JSON - Valid format, no markdown wrapper, UTF-8, Tiếng Việt có dấu
                        5. RETURN ONLY JSON - No text before or after the JSON object

                        ## SELF-VALIDATION CHECKLIST

                        Trước khi trả về, kiểm tra:
                        □ Detect đúng learning intention?
                        □ Validate goal? (scores, tech, time)
                        □ Số nodes: 10-15?
                        □ Main path ≥ 6 nodes?
                        □ Mọi ID tồn tại?
                        □ Không orphan nodes?
                        □ Tổng thời gian ≈ duration?
                        □ Tiếng Việt có dấu?
                        □ JSON valid, no markdown wrapper?


                        ## ADAPTATION BY PRIORITY/TIME
                        - Nếu priority = "Nhanh đi làm": 10-12 nodes, MAIN ≥ 75%%, SIDE ≤ 25%%, difficulty ưu tiên easy/medium
                        - Nếu priority = "Học sâu": 12-18 nodes, MAIN ≈ 60%%, SIDE ≈ 40%%, difficulty cân bằng medium/hard
                        - Dựa vào daily_time và desired_duration để tính ngân sách thời gian tổng và phân bổ thời gian cho từng node
                        - Tổng thời gian nodes ≈ time_budget_minutes × 0.9 (10%% buffer)

                        """;
        return base;
    }

    private String buildExpertPackContextBlock(GenerateRoadmapRequest request) {
        String target = request.getTarget() != null ? request.getTarget() : request.getGoal();
        String roleCategory = taxonomyService.detectRoleCategory(target);
        String domainName = taxonomyService.detectDomain(target, request.getIndustry(), roleCategory);
        String domainId = taxonomyService.mapToDomainPackId(domainName);
        String roleId = taxonomyService.normalizeToRoleId(roleCategory);
        boolean roleKnown = taxonomyService.isRoleKnown(domainId, roleId);
        Set<String> roles = domainId != null ? taxonomyService.getKnownRolesForDomain(domainId)
                : Collections.emptySet();
        Set<String> tools = domainId != null ? taxonomyService.getAllowedTools(domainId) : Collections.emptySet();
        Set<String> skills = taxonomyService.getAllowedSkills(domainId, roleKnown ? roleId : null);
        String rolesList = String.join(", ", roles);
        String toolsList = String.join(", ", tools);
        String skillsList = String.join(", ", skills);
        return String.format("""
                ## EXPERT PACK CONTEXT
                DomainId: %s
                RoleId: %s
                Known Roles: %s
                Allowed Tools: %s
                Allowed Skills: %s

                ENFORCE:
                - Chỉ sử dụng kỹ năng trong danh sách Allowed Skills
                - Ưu tiên công cụ trong Allowed Tools cho thị trường mục tiêu
                - Nếu phát hiện kỹ năng/công cụ/role không tồn tại, đặt 'unknown_term' trong validation_notes
                """, nullSafe(domainId), nullSafe(roleId), rolesList, toolsList, skillsList);
    }

    public List<ClarificationQuestion> generateClarificationQuestions(
            GenerateRoadmapRequest request) {
        return inputValidationService.generateClarificationQuestions(request);
    }

    private String selectExpertPersonaForRequest(GenerateRoadmapRequest request) {
        String target = request.getTarget() != null ? request.getTarget() : request.getGoal();
        if (target == null)
            return expertPromptService.getGenericExpertPrompt("General Mentor");
        String roleCategory = taxonomyService.detectRoleCategory(target);
        String domain = taxonomyService.detectDomain(target, request.getIndustry(), roleCategory);
        String persona = expertPromptService.getSystemPrompt(domain, request.getIndustry(), roleCategory);
        return persona == null ? expertPromptService.getGenericExpertPrompt(target) : persona;
    }

    private int parseDailyTimeMinutes(String dailyTime) {
        if (dailyTime == null)
            return 60;
        String s = dailyTime.toLowerCase();
        if (s.contains("30"))
            return 30;
        if (s.contains("2"))
            return 120;
        if (s.contains("1"))
            return 60;
        if (s.contains("phút"))
            return 30;
        if (s.contains("giờ"))
            return 60;
        return 60;
    }

    private int parseDesiredDurationDays(String desiredDuration) {
        if (desiredDuration == null)
            return 30;
        String s = desiredDuration.toLowerCase();
        if (s.contains("tuần")) {
            String num = s.replaceAll("[^0-9]", "");
            int n = num.isEmpty() ? 2 : Integer.parseInt(num);
            return n * 7;
        }
        if (s.contains("tháng")) {
            String num = s.replaceAll("[^0-9]", "");
            int n = num.isEmpty() ? 1 : Integer.parseInt(num);
            return n * 30;
        }
        if (s.contains("năm")) {
            String num = s.replaceAll("[^0-9]", "");
            int n = num.isEmpty() ? 1 : Integer.parseInt(num);
            return n * 365;
        }
        return 30;
    }

    private List<String> computeWarnings(RoadmapResponse.RoadmapMetadata metadata,
            RoadmapResponse.RoadmapStatistics statistics) {
        List<String> warnings = new ArrayList<>();
        if (metadata == null || statistics == null || statistics.getTotalEstimatedHours() == null)
            return warnings;
        int minutesPerDay = parseDailyTimeMinutes(metadata.getDailyTime());
        int plannedDays = parseDesiredDurationDays(metadata.getDesiredDuration());
        double timeBudgetHours = (minutesPerDay * plannedDays) / 60.0;
        double totalHoursGen = statistics.getTotalEstimatedHours();
        double diff = Math.abs(totalHoursGen - timeBudgetHours);
        double rel = timeBudgetHours > 0 ? diff / timeBudgetHours : 0.0;
        if (rel > 0.10) {
            String note = "Cảnh báo: Tổng thời gian lộ trình (" + String.format("%.1f", totalHoursGen)
                    + "h) lệch hơn 10% so với ngân sách thời gian (" + String.format("%.1f", timeBudgetHours)
                    + "h).";
            warnings.add(note);
            if (metadata.getPriority() != null && metadata.getPriority().equalsIgnoreCase("Nhanh đi làm")) {
                warnings.add("Đề xuất: Giảm số node hoặc hạ độ khó để phù hợp ưu tiên nhanh đi làm");
            }
        }
        return warnings;
    }

    private String buildConstraintsBlock(GenerateRoadmapRequest request) {
        int minutesPerDay = parseDailyTimeMinutes(request.getDailyTime());
        int plannedDays = parseDesiredDurationDays(request.getDesiredDuration());
        int timeBudgetMinutes = minutesPerDay * plannedDays;
        String ratio = "65/35";
        String priority = nullSafe(request.getPriority());
        if (priority.equalsIgnoreCase("Nhanh đi làm"))
            ratio = "75/25";
        if (priority.equalsIgnoreCase("Học sâu"))
            ratio = "60/40";
        return String.format(
                "\nCONSTRAINTS:\navailable_minutes_per_day=%d\nplanned_days=%d\ntime_budget_minutes=%d\nmain_side_ratio=%s\n",
                minutesPerDay, plannedDays, timeBudgetMinutes, ratio);
    }

    /**
     * Validate and parse enhanced roadmap JSON (Schema V2)
     * Parses: metadata, roadmap nodes, statistics, learning tips
     */
    private ParsedRoadmap validateAndParseRoadmapV2(String roadmapJson) {
        try {
            String sanitized = sanitizeJson(roadmapJson);
            try {
                objectMapper.getFactory()
                        .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature());
                objectMapper.getFactory()
                        .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature());
                objectMapper.getFactory().enable(
                        com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature());
                objectMapper.getFactory()
                        .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature());
                objectMapper.getFactory().enable(
                        com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature());
            } catch (Throwable t) {
                try {
                    objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
                    objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_TRAILING_COMMA, true);
                    objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES,
                            true);
                    objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
                    objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS,
                            true);
                } catch (Throwable ignored) {
                }
            }

            JsonNode root = objectMapper.readTree(sanitized);

            // Parse metadata
            JsonNode metadataNode = root.path("roadmap_metadata");
            if (metadataNode.isMissingNode()) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Invalid roadmap structure: missing 'roadmap_metadata'");
            }
            RoadmapResponse.RoadmapMetadata metadata = parseMetadata(metadataNode);

            // Parse roadmap nodes
            JsonNode roadmapArray = root.path("roadmap");
            if (!roadmapArray.isArray() || roadmapArray.isEmpty()) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Invalid roadmap structure: missing or empty 'roadmap' array");
            }
            List<RoadmapResponse.RoadmapNode> nodes = parseNodes(roadmapArray);

            // Parse statistics
            JsonNode statsNode = root.path("roadmap_statistics");
            RoadmapResponse.RoadmapStatistics statistics = statsNode.isMissingNode() ? null
                    : parseStatistics(statsNode);

            // Parse learning tips
            List<String> learningTips = new ArrayList<>();
            JsonNode tipsNode = root.path("learning_tips");
            if (tipsNode.isArray()) {
                for (JsonNode tip : tipsNode) {
                    learningTips.add(tip.asText());
                }
            }

            RoadmapResponse.Overview overview = null;
            JsonNode overviewNode = root.path("overview");
            if (overviewNode.isObject()) {
                overview = RoadmapResponse.Overview.builder()
                        .purpose(overviewNode.path("purpose").asText(null))
                        .audience(overviewNode.path("audience").asText(null))
                        .postRoadmapState(overviewNode.path("post_roadmap_state").asText(null))
                        .build();
            }

            List<RoadmapResponse.StructurePhase> structure = new ArrayList<>();
            JsonNode structureNode = root.path("structure");
            if (structureNode.isArray()) {
                for (JsonNode p : structureNode) {
                    List<String> skillFocus = parseStringArray(p.path("skill_focus"));
                    RoadmapResponse.StructurePhase phase = RoadmapResponse.StructurePhase.builder()
                            .phaseId(p.path("phase_id").asText(null))
                            .title(p.path("title").asText(null))
                            .timeframe(p.path("timeframe").asText(null))
                            .goal(p.path("goal").asText(null))
                            .skillFocus(skillFocus)
                            .mindsetGoal(p.path("mindset_goal").asText(null))
                            .expectedOutput(p.path("expected_output").asText(null))
                            .build();
                    structure.add(phase);
                }
            }

            List<String> thinkingProgression = new ArrayList<>();
            JsonNode thinkingNode = root.path("thinking_progression");
            if (thinkingNode.isArray()) {
                for (JsonNode t : thinkingNode) {
                    thinkingProgression.add(t.asText());
                }
            }

            List<RoadmapResponse.ProjectEvidence> projectsEvidence = new ArrayList<>();
            JsonNode projectsNode = root.path("projects_evidence");
            if (projectsNode.isArray()) {
                for (JsonNode pr : projectsNode) {
                    RoadmapResponse.ProjectEvidence pe = RoadmapResponse.ProjectEvidence.builder()
                            .phaseId(pr.path("phase_id").asText(null))
                            .project(pr.path("project").asText(null))
                            .objective(pr.path("objective").asText(null))
                            .skillsProven(parseStringArray(pr.path("skills_proven")))
                            .kpi(parseStringArray(pr.path("kpi")))
                            .build();
                    projectsEvidence.add(pe);
                }
            }

            RoadmapResponse.NextSteps nextSteps = null;
            JsonNode nextStepsNode = root.path("next_steps");
            if (nextStepsNode.isObject()) {
                nextSteps = RoadmapResponse.NextSteps.builder()
                        .jobs(parseStringArray(nextStepsNode.path("jobs")))
                        .nextSkills(parseStringArray(nextStepsNode.path("next_skills")))
                        .mentorsMicroJobs(parseStringArray(nextStepsNode.path("mentors_micro_jobs")))
                        .build();
            }

            List<RoadmapResponse.SkillDependency> skillDependencies = new ArrayList<>();
            JsonNode depsNode = root.path("skill_dependencies");
            if (depsNode.isArray()) {
                for (JsonNode d : depsNode) {
                    RoadmapResponse.SkillDependency dep = RoadmapResponse.SkillDependency.builder()
                            .from(d.path("from").asText(null))
                            .to(d.path("to").asText(null))
                            .build();
                    skillDependencies.add(dep);
                }
            }

            log.info("✅ Validated roadmap V2: {} nodes, difficulty: {}",
                    nodes.size(), metadata.getDifficultyLevel());

            return new ParsedRoadmap(metadata, nodes, statistics, learningTips,
                    overview, structure, thinkingProgression, projectsEvidence, nextSteps, skillDependencies);

        } catch (JsonProcessingException e) {
            log.error("❌ Failed to parse roadmap JSON V2", e);
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "AI generation failed: invalid JSON format. Please retry.");
        }
    }

    private String sanitizeJson(String text) {
        if (text == null)
            return "";
        String s = text.replace("\uFEFF", "").trim();
        int objStart = s.indexOf('{');
        int arrStart = s.indexOf('[');
        int start = -1;
        int end = -1;
        if (objStart >= 0) {
            start = objStart;
            end = s.lastIndexOf('}');
        } else if (arrStart >= 0) {
            start = arrStart;
            end = s.lastIndexOf(']');
        }
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        s = s.replaceAll("(?s)/\\*.*?\\*/", "");
        s = s.replaceAll("(?m)^\\s*//.*$", "");
        s = s.replaceAll(",\\s*([}\\]])", "$1");
        // If the JSON uses single quotes globally, convert them to double quotes
        if (!s.contains("\"") && s.contains("'")) {
            s = s.replace('\'', '"');
        }
        return s.trim();
    }

    /**
     * Parse roadmap metadata
     */
    private RoadmapResponse.RoadmapMetadata parseMetadata(JsonNode node) {
        RoadmapResponse.RoadmapMetadata meta = RoadmapResponse.RoadmapMetadata.builder()
                .title(node.path("title").asText())
                .originalGoal(node.path("original_goal").asText())
                .validatedGoal(node.path("validated_goal").asText())
                .duration(node.path("duration").asText())
                .experienceLevel(node.path("experience_level").asText())
                .learningStyle(node.path("learning_style").asText())
                .detectedIntention(node.path("detected_intention").asText(""))
                .validationNotes(node.path("validation_notes").isNull() ? null : node.path("validation_notes").asText())
                .estimatedCompletion(node.path("estimated_completion").asText(null))
                .difficultyLevel(node.path("difficulty_level").asText("medium"))
                .prerequisites(parseStringArray(node.path("prerequisites")))
                .careerRelevance(node.path("career_relevance").asText(null))
                .roadmapType(node.path("roadmap_type").asText(null))
                .target(node.path("target").asText(null))
                .finalObjective(node.path("final_objective").asText(null))
                .currentLevel(node.path("current_level").asText(null))
                .desiredDuration(node.path("desired_duration").asText(null))
                .background(node.path("background").asText(null))
                .dailyTime(node.path("daily_time").asText(null))
                .targetEnvironment(node.path("target_environment").asText(null))
                .location(node.path("location").asText(null))
                .priority(node.path("priority").asText(null))
                .toolPreferences(parseStringArray(node.path("tool_preferences")))
                .difficultyConcern(node.path("difficulty_concern").asText(null))
                .incomeGoal(node.path("income_goal").isMissingNode() ? null : node.path("income_goal").asBoolean())
                .build();
        // Optional: mode-specific metadata if AI provides
        meta.setRoadmapMode(node.path("roadmap_mode").asText(null));
        JsonNode skillMode = node.path("skill_mode");
        if (skillMode.isObject()) {
            RoadmapResponse.SkillModeMeta sm = RoadmapResponse.SkillModeMeta.builder()
                    .skillName(skillMode.path("skill_name").asText(null))
                    .skillCategory(skillMode.path("skill_category").asText(null))
                    .desiredDepth(skillMode.path("desired_depth").asText(null))
                    .learnerType(skillMode.path("learner_type").asText(null))
                    .currentSkillLevel(skillMode.path("current_skill_level").asText(null))
                    .learningGoal(skillMode.path("learning_goal").asText(null))
                    .dailyLearningTime(skillMode.path("daily_learning_time").asText(null))
                    .assessmentPreference(skillMode.path("assessment_preference").asText(null))
                    .difficultyTolerance(skillMode.path("difficulty_tolerance").asText(null))
                    .toolPreference(parseStringArray(skillMode.path("tool_preference")))
                    .build();
            meta.setSkillMode(sm);
        }
        JsonNode careerMode = node.path("career_mode");
        if (careerMode.isObject()) {
            RoadmapResponse.CareerModeMeta cm = RoadmapResponse.CareerModeMeta.builder()
                    .targetRole(careerMode.path("target_role").asText(null))
                    .careerTrack(careerMode.path("career_track").asText(null))
                    .targetSeniority(careerMode.path("target_seniority").asText(null))
                    .workMode(careerMode.path("work_mode").asText(null))
                    .targetMarket(careerMode.path("target_market").asText(null))
                    .companyType(careerMode.path("company_type").asText(null))
                    .timelineToWork(careerMode.path("timeline_to_work").asText(null))
                    .incomeExpectation(careerMode.path("income_expectation").isMissingNode() ? null
                            : careerMode.path("income_expectation").asBoolean())
                    .workExperience(careerMode.path("work_experience").asText(null))
                    .transferableSkills(careerMode.path("transferable_skills").isMissingNode() ? null
                            : careerMode.path("transferable_skills").asBoolean())
                    .confidenceLevel(careerMode.path("confidence_level").asText(null))
                    .build();
            meta.setCareerMode(cm);
        }
        return meta;
    }

    /**
     * Parse roadmap nodes with enhanced fields
     */
    private List<RoadmapResponse.RoadmapNode> parseNodes(JsonNode nodesArray) {
        List<RoadmapResponse.RoadmapNode> nodes = new ArrayList<>();

        for (JsonNode nodeJson : nodesArray) {
            // Validate required fields
            if (!nodeJson.has("id") || !nodeJson.has("title") || !nodeJson.has("type")) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Invalid node: missing required fields (id, title, type)");
            }

            // Parse type enum
            String typeStr = nodeJson.path("type").asText();
            RoadmapResponse.RoadmapNode.NodeType type;
            try {
                type = RoadmapResponse.RoadmapNode.NodeType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Invalid node type: " + typeStr + ". Must be MAIN or SIDE");
            }

            RoadmapResponse.RoadmapNode node = RoadmapResponse.RoadmapNode.builder()
                    .id(nodeJson.path("id").asText())
                    .title(nodeJson.path("title").asText())
                    .description(nodeJson.path("description").asText(""))
                    .estimatedTimeMinutes(nodeJson.path("estimated_time_minutes").asInt(0))
                    .type(type)
                    .difficulty(nodeJson.path("difficulty").asText("medium"))
                    .learningObjectives(parseStringArray(nodeJson.path("learning_objectives")))
                    .keyConcepts(parseStringArray(nodeJson.path("key_concepts")))
                    .practicalExercises(parseStringArray(nodeJson.path("practical_exercises")))
                    .suggestedResources(parseStringArray(nodeJson.path("suggested_resources")))
                    .successCriteria(parseStringArray(nodeJson.path("success_criteria")))
                    .prerequisites(parseStringArray(nodeJson.path("prerequisites")))
                    .children(parseStringArray(nodeJson.path("children")))
                    .estimatedCompletionRate(nodeJson.path("estimated_completion_rate").asText(null))
                    .build();

            nodes.add(node);
        }

        return nodes;
    }

    /**
     * Parse roadmap statistics
     */
    private RoadmapResponse.RoadmapStatistics parseStatistics(JsonNode node) {
        Map<String, Integer> difficultyDistribution = new HashMap<>();
        JsonNode distNode = node.path("difficulty_distribution");
        if (distNode.isObject()) {
            // Use fieldNames() instead of deprecated fields()
            Iterator<String> fieldNames = distNode.fieldNames();
            fieldNames.forEachRemaining(
                    fieldName -> difficultyDistribution.put(fieldName, distNode.get(fieldName).asInt()));
        }

        return RoadmapResponse.RoadmapStatistics.builder()
                .totalNodes(node.path("total_nodes").asInt(0))
                .mainNodes(node.path("main_nodes").asInt(0))
                .sideNodes(node.path("side_nodes").asInt(0))
                .totalEstimatedHours(node.path("total_estimated_hours").asDouble(0.0))
                .difficultyDistribution(difficultyDistribution)
                .build();
    }

    /**
     * Helper: Parse JSON array to List<String>
     */
    private List<String> parseStringArray(JsonNode arrayNode) {
        List<String> result = new ArrayList<>();
        if (arrayNode.isArray()) {
            for (JsonNode item : arrayNode) {
                result.add(item.asText());
            }
        }
        return result;
    }

    /**
     * Helper class to hold parsed roadmap data
     */
    private record ParsedRoadmap(
            RoadmapResponse.RoadmapMetadata metadata,
            List<RoadmapResponse.RoadmapNode> nodes,
            RoadmapResponse.RoadmapStatistics statistics,
            List<String> learningTips,
            RoadmapResponse.Overview overview,
            List<RoadmapResponse.StructurePhase> structure,
            List<String> thinkingProgression,
            List<RoadmapResponse.ProjectEvidence> projectsEvidence,
            RoadmapResponse.NextSteps nextSteps,
            List<RoadmapResponse.SkillDependency> skillDependencies) {
    }

    /**
     * Generate a readable title from goal and duration
     */
    /**
     * Check if user has reached the storage limit for roadmaps
     * This checks actual DB count against the Plan's limit
     */
    private void checkRoadmapStorageLimit(User user) {
        // 1. Get limit info for the current user's plan
        FeatureLimitInfo limitInfo = usageLimitService.getUserUsage(user.getId(), FeatureType.AI_ROADMAP_GENERATION);

        // 2. If unlimited, do nothing
        if (Boolean.TRUE.equals(limitInfo.getIsUnlimited())) {
            return;
        }

        // 3. If limit exists, check DB count
        Integer limitValue = limitInfo.getLimit();
        if (limitValue != null && limitValue > 0) {
            long currentCount = roadmapSessionRepository.countByUserId(user.getId());

            if (currentCount >= limitValue) {
                throw new UsageLimitExceededException(
                        "Bạn đã đạt giới hạn số lượng roadmap (" + limitValue
                                + "). Vui lòng xóa bớt roadmap cũ hoặc nâng cấp gói Premium.",
                        FeatureType.AI_ROADMAP_GENERATION);
            }
        }
    }

    /**
     * Get all roadmap sessions (Admin)
     */
    @Transactional(readOnly = true)
    public List<RoadmapSessionSummary> getAllRoadmaps() {
        List<RoadmapSession> sessions = roadmapSessionRepository.findAllByOrderByCreatedAtDesc();
        List<RoadmapSessionSummary> summaries = new ArrayList<>();

        for (RoadmapSession session : sessions) {
            // Parse roadmap JSON to count total quests
            int totalQuests = 0;
            try {
                // Support both V1 and V2 schema
                JsonNode root = objectMapper.readTree(session.getRoadmapJson());
                JsonNode roadmapArray = root.path("roadmap");
                totalQuests = roadmapArray.size();

                // Fallback: if roadmap is empty, try using totalNodes from DB (V2 only)
                if (totalQuests == 0 && session.getTotalNodes() != null) {
                    totalQuests = session.getTotalNodes();
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse roadmap JSON for session {}", session.getId());
                // Fallback to DB field
                if (session.getTotalNodes() != null) {
                    totalQuests = session.getTotalNodes();
                }
            }

            // Count completed quests
            Long completedCount = progressRepository.countCompletedBySessionId(session.getId());
            int completed = completedCount != null ? completedCount.intValue() : 0;

            // Calculate progress percentage
            int progressPercentage = totalQuests > 0 ? (completed * 100) / totalQuests : 0;

            // Build summary with V2 fields (fallback to V1 for old data)
            @SuppressWarnings("deprecation") // Intentional V1 fallback for backward compatibility
            RoadmapSessionSummary summary = RoadmapSessionSummary.builder()
                    .sessionId(session.getId())
                    .title(session.getTitle())
                    .roadmapMode(session.getRoadmapMode())
                    // Use V2 fields with fallback to deprecated V1 fields
                    .originalGoal(session.getOriginalGoal() != null ? session.getOriginalGoal() : session.getGoal())
                    .validatedGoal(session.getValidatedGoal())
                    .duration(session.getDuration())
                    .experienceLevel(session.getExperienceLevel() != null ? session.getExperienceLevel()
                            : session.getExperience())
                    .learningStyle(session.getLearningStyle() != null ? session.getLearningStyle() : session.getStyle())
                    .totalQuests(totalQuests)
                    .completedQuests(completed)
                    .progressPercentage(progressPercentage)
                    .difficultyLevel(session.getDifficultyLevel())
                    .schemaVersion(session.getSchemaVersion())
                    .createdAt(session.getCreatedAt())
                    .build();

            summaries.add(summary);
        }

        return summaries;
    }

    /**
     * Get all roadmap sessions for a user
     */
    @Transactional(readOnly = true)
    public List<RoadmapSessionSummary> getUserRoadmaps(Long userId) {
        List<RoadmapSession> sessions = roadmapSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<RoadmapSessionSummary> summaries = new ArrayList<>();

        for (RoadmapSession session : sessions) {
            // Parse roadmap JSON to count total quests
            int totalQuests = 0;
            try {
                // Support both V1 and V2 schema
                JsonNode root = objectMapper.readTree(session.getRoadmapJson());
                JsonNode roadmapArray = root.path("roadmap");
                totalQuests = roadmapArray.size();

                // Fallback: if roadmap is empty, try using totalNodes from DB (V2 only)
                if (totalQuests == 0 && session.getTotalNodes() != null) {
                    totalQuests = session.getTotalNodes();
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse roadmap JSON for session {}", session.getId());
                // Fallback to DB field
                if (session.getTotalNodes() != null) {
                    totalQuests = session.getTotalNodes();
                }
            }

            // Count completed quests
            Long completedCount = progressRepository.countCompletedBySessionId(session.getId());
            int completed = completedCount != null ? completedCount.intValue() : 0;

            // Calculate progress percentage
            int progressPercentage = totalQuests > 0 ? (completed * 100) / totalQuests : 0;

            // Build summary with V2 fields (fallback to V1 for old data)
            @SuppressWarnings("deprecation") // Intentional V1 fallback for backward compatibility
            RoadmapSessionSummary summary = RoadmapSessionSummary.builder()
                    .sessionId(session.getId())
                    .title(session.getTitle())
                    .roadmapMode(session.getRoadmapMode())
                    // Use V2 fields with fallback to deprecated V1 fields
                    .originalGoal(session.getOriginalGoal() != null ? session.getOriginalGoal() : session.getGoal())
                    .validatedGoal(session.getValidatedGoal())
                    .duration(session.getDuration())
                    .experienceLevel(session.getExperienceLevel() != null ? session.getExperienceLevel()
                            : session.getExperience())
                    .learningStyle(session.getLearningStyle() != null ? session.getLearningStyle() : session.getStyle())
                    .totalQuests(totalQuests)
                    .completedQuests(completed)
                    .progressPercentage(progressPercentage)
                    .difficultyLevel(session.getDifficultyLevel())
                    .schemaVersion(session.getSchemaVersion())
                    .createdAt(session.getCreatedAt())
                    .build();

            summaries.add(summary);
        }

        return summaries;
    }

    /**
     * Get a specific roadmap session with full details (supports V1 and V2 schemas)
     */
    @Transactional(readOnly = true)
    public RoadmapResponse getRoadmapById(Long sessionId, Long userId) {
        RoadmapSession session = roadmapSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found"));

        // Detect schema version and parse accordingly
        Integer schemaVersion = session.getSchemaVersion() != null ? session.getSchemaVersion() : 1;

        if (schemaVersion >= 2) {
            // V2: Parse full structure
            ParsedRoadmap parsed = validateAndParseRoadmapV2(session.getRoadmapJson());

            // Load progress data
            Map<String, RoadmapResponse.QuestProgress> progressMap = loadProgressData(sessionId);

            return RoadmapResponse.builder()
                    .sessionId(session.getId())
                    .metadata(parsed.metadata())
                    .roadmap(parsed.nodes())
                    .statistics(parsed.statistics())
                    .learningTips(parsed.learningTips())
                    .warnings(computeWarnings(parsed.metadata(), parsed.statistics()))
                    .overview(parsed.overview())
                    .structure(parsed.structure())
                    .thinkingProgression(parsed.thinkingProgression())
                    .projectsEvidence(parsed.projectsEvidence())
                    .nextSteps(parsed.nextSteps())
                    .skillDependencies(parsed.skillDependencies())
                    .createdAt(session.getCreatedAt())
                    .progress(progressMap)
                    .build();
        } else {
            // V1 Legacy: Convert to V2 format (best-effort)
            log.warn("🔄 Converting legacy V1 roadmap {} to V2 format", sessionId);

            List<RoadmapResponse.RoadmapNode> nodes = parseNodesFromV1Json(session.getRoadmapJson());

            // Build minimal V2 metadata from V1 data (suppress deprecation for V1 fallback)
            @SuppressWarnings("deprecation")
            RoadmapResponse.RoadmapMetadata metadata = RoadmapResponse.RoadmapMetadata.builder()
                    .title(session.getTitle())
                    .originalGoal(session.getGoal() != null ? session.getGoal() : "Unknown")
                    .validatedGoal(null)
                    .duration(session.getDuration())
                    .experienceLevel(session.getExperience() != null ? session.getExperience() : "beginner")
                    .learningStyle(session.getStyle() != null ? session.getStyle() : "visual")
                    .difficultyLevel("intermediate") // default
                    .build();

            // Build minimal statistics
            RoadmapResponse.RoadmapStatistics statistics = RoadmapResponse.RoadmapStatistics.builder()
                    .totalNodes(nodes.size())
                    .mainNodes(nodes.size())
                    .sideNodes(0)
                    .totalEstimatedHours(calculateTotalHours(nodes))
                    .build();

            // Load progress data for V1 roadmaps too
            Map<String, RoadmapResponse.QuestProgress> progressMap = loadProgressData(sessionId);

            return RoadmapResponse.builder()
                    .sessionId(session.getId())
                    .metadata(metadata)
                    .roadmap(nodes)
                    .statistics(statistics)
                    .learningTips(List.of()) // Empty list for V1 data
                    .warnings(List.of())
                    .overview(null)
                    .structure(List.of())
                    .thinkingProgression(List.of())
                    .projectsEvidence(List.of())
                    .nextSteps(null)
                    .skillDependencies(List.of())
                    .createdAt(session.getCreatedAt())
                    .progress(progressMap)
                    .build();
        }
    }

    /**
     * Load progress data for a roadmap session
     */
    private Map<String, RoadmapResponse.QuestProgress> loadProgressData(Long sessionId) {
        List<UserRoadmapProgress> progressList = progressRepository.findBySessionId(sessionId);

        return progressList.stream()
                .collect(Collectors.toMap(
                        UserRoadmapProgress::getQuestId,
                        progress -> RoadmapResponse.QuestProgress.builder()
                                .questId(progress.getQuestId())
                                .status(progress.getStatus().toString())
                                .progress(
                                        progress.getStatus() == UserRoadmapProgress.ProgressStatus.COMPLETED ? 100 : 0)
                                .completedAt(progress.getCompletedAt())
                                .build()));
    }

    /**
     * Parse V1 roadmap JSON (backward compatibility)
     */
    private List<RoadmapResponse.RoadmapNode> parseNodesFromV1Json(String roadmapJson) {
        try {
            JsonNode root = objectMapper.readTree(roadmapJson);
            JsonNode roadmapArray = root.path("roadmap");

            if (!roadmapArray.isArray()) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "V1 roadmap field must be an array");
            }

            // Parse V1 nodes (simpler structure)
            return parseNodes(roadmapArray);

        } catch (JsonProcessingException e) {
            log.error("Failed to parse V1 roadmap JSON", e);
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Invalid V1 JSON format: " + e.getMessage());
        }
    }

    /**
     * Update quest/milestone progress for a roadmap session
     */
    @Transactional
    public ProgressResponse updateProgress(Long sessionId, Long userId, UpdateProgressRequest request) {
        log.info("Updating progress for session {} - quest: {}, completed: {}",
                sessionId, request.getQuestId(), request.getCompleted());

        // Verify session belongs to user
        RoadmapSession session = roadmapSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found"));

        // Find or create progress record
        UserRoadmapProgress progress = progressRepository
                .findBySessionIdAndQuestId(sessionId, request.getQuestId())
                .orElse(UserRoadmapProgress.builder()
                        .roadmapSession(session)
                        .questId(request.getQuestId())
                        .status(UserRoadmapProgress.ProgressStatus.NOT_STARTED)
                        .build());

        // Update completion status
        if (request.getCompleted()) {
            progress.setStatus(UserRoadmapProgress.ProgressStatus.COMPLETED);
            progress.setProgress(100);
            progress.setCompletedAt(Instant.now());
        } else {
            progress.setStatus(UserRoadmapProgress.ProgressStatus.NOT_STARTED);
            progress.setProgress(0);
            progress.setCompletedAt(null);
        }

        progressRepository.save(progress);

        // Calculate progress statistics (support V1 and V2)
        int totalQuests = 0;
        try {
            Integer schemaVersion = session.getSchemaVersion() != null ? session.getSchemaVersion() : 1;

            if (schemaVersion >= 2) {
                // V2: Parse or use cached totalNodes from DB
                if (session.getTotalNodes() != null) {
                    totalQuests = session.getTotalNodes();
                } else {
                    ParsedRoadmap parsed = validateAndParseRoadmapV2(session.getRoadmapJson());
                    totalQuests = parsed.nodes().size();
                }
            } else {
                // V1: Parse nodes
                List<RoadmapResponse.RoadmapNode> nodes = parseNodesFromV1Json(session.getRoadmapJson());
                totalQuests = nodes.size();
            }
        } catch (Exception e) {
            log.warn("Failed to determine totalQuests for session {}, using progress entries count", session.getId());
            List<UserRoadmapProgress> allProgress = progressRepository.findBySessionId(sessionId);
            totalQuests = allProgress.size(); // Fallback: count all progress entries
        }

        List<UserRoadmapProgress> allProgress = progressRepository.findBySessionId(sessionId);
        int completedQuests = (int) allProgress.stream()
                .filter(p -> p.getStatus() == UserRoadmapProgress.ProgressStatus.COMPLETED)
                .count();

        double completionPercentage = totalQuests > 0
                ? (completedQuests * 100.0 / totalQuests)
                : 0.0;

        log.info("Progress updated - {}/{} quests completed ({}%)",
                completedQuests, totalQuests, String.format("%.1f", completionPercentage));

        return ProgressResponse.builder()
                .sessionId(sessionId)
                .questId(request.getQuestId())
                .completed(request.getCompleted())
                .stats(ProgressResponse.ProgressStats.builder()
                        .totalQuests(totalQuests)
                        .completedQuests(completedQuests)
                        .completionPercentage(completionPercentage)
                        .build())
                .build();
    }

    /**
     * Validate learning goal with AI (Stage 1 - Lightweight Validation)
     * Prevents wasting tokens on invalid/inappropriate goals
     * 
     * @param goal User's learning goal
     * @return ValidationResult with severity INFO/WARNING/ERROR
     */
    private ValidationResult validateGoalWithAI(String goal) {
        log.info("🤖 AI Goal Validation Stage 1: Checking goal='{}'", goal);

        String validationPrompt = buildGoalValidationPrompt(goal);

        try {
            // Use Gemini RestClient for validation
            String aiResponse = callGeminiDirectly(validationPrompt, geminiModel);
            log.debug("AI Validation Response: {}", aiResponse);

            // Parse AI response
            return parseAIValidationResponse(aiResponse, goal);

        } catch (Exception e) {
            log.warn("⚠️ AI validation (Gemini) failed, attempting fallback to Mistral: {}", e.getMessage());
            try {
                 String aiResponse = callMistralAPI(validationPrompt);
                 return parseAIValidationResponse(aiResponse, goal);
            } catch (Exception ex) {
                log.warn("⚠️ AI validation (Mistral) failed, falling back to basic validation: {}", ex.getMessage());
            }

            // Fallback: Basic validation if AI fails
            if (goal == null || goal.trim().isEmpty()) {
                return ValidationResult.error("goal", "Mục tiêu học tập không được để trống",
                        "Vui lòng nhập mục tiêu học tập của bạn");
            }

            if (goal.trim().length() < 5) {
                return ValidationResult.error("goal",
                        "Mục tiêu quá ngắn. Vui lòng mô tả rõ hơn bạn muốn học gì.",
                        "Ví dụ: 'Học Python', 'Trở thành UX Designer'");
            }

            // Allow request to proceed if AI validation fails
            return ValidationResult.info("goal",
                    "Không thể xác thực bằng AI, tiếp tục với validation cơ bản", null);
        }
    }

    /**
     * Build prompt for AI goal validation (Stage 1)
     */
    private String buildGoalValidationPrompt(String goal) {
        return String.format(
                """
                        # NHIỆM VỤ: XÁC THỰC MỤC TIÊU HỌC TẬP

                        Bạn là AI validator chuyên kiểm tra tính hợp lệ của mục tiêu học tập.

                        ## MỤC TIÊU CẦN KIỂM TRA:
                        "%s"

                        ## TIÊU CHÍ ĐÁNH GIÁ:

                        ### ✅ HỢP LỆ NÕU:
                        1. Liên quan đến học tập, giáo dục, phát triển kỹ năng
                        2. Có thể tạo lộ trình học tập (học ngôn ngữ lập trình, công nghệ, kỹ năng mềm, nghề nghiệp)
                        3. Mục đích tích cực, xây dựng
                        4. Rõ ràng hoặc có thể hiểu được ý định

                        ### ❌ KHÔNG HỢP LỆ NỐI:
                        1. Vi phạm đạo đức: bạo lực, lừa đảo, hack bất hợp pháp
                        2. Không liên quan học tập: "học làm súc vật", "học cách ngủ cả ngày", "học cách lười biếng"
                        3. Nội dung không phù hợp: 18+, độc hại, phân biệt đối xử
                        4. Spam/vô nghĩa: ký tự ngẫu nhiên, câu văn không có nghĩa
                        5. Mục đích phá hoại hệ thống

                        ## FORMAT TRẢ VỀ (BẮT BUỘC):

                        Trả về ĐÚNG 1 trong 3 format sau:

                        ```
                        VALID|Mục tiêu hợp lệ
                        ```

                        ```
                        WARNING|[Lý do cảnh báo]|Gợi ý: [Cách cải thiện]
                        ```

                        ```
                        ERROR|[Lý do từ chối cụ thể - Tiếng Việt]
                        ```

                        ## VÍ DỤ:

                        Input: "học Python"
                        Output: VALID|Mục tiêu hợp lệ

                        Input: "học lm suc vat"
                        Output: ERROR|Mục tiêu không liên quan đến học tập hoặc phát triển kỹ năng. Vui lòng nhập mục tiêu học tập hợp lệ (ví dụ: học lập trình, học ngoại ngữ, học thiết kế).

                        Input: "hoc hack facebook"
                        Output: ERROR|Mục tiêu vi phạm đạo đức và pháp luật. Hệ thống không hỗ trợ tạo lộ trình cho hoạt động bất hợp pháp.

                        Input: "asdfghjkl"
                        Output: ERROR|Mục tiêu không rõ ràng hoặc không có nghĩa. Vui lòng mô tả cụ thể bạn muốn học gì.

                        Input: "muon hoc ve AI nhung khong biet bat dau tu dau"
                        Output: WARNING|Mục tiêu chưa rõ ràng về lĩnh vực cụ thể của AI|Gợi ý: Hãy chọn lĩnh vực cụ thể như Machine Learning, Computer Vision, hoặc NLP.

                        QUAN TRỌNG:
                        - Chỉ trả về MỘT dòng theo format trên
                        - KHÔNG giải thích thêm
                        - Sử dụng Tiếng Việt có dấu
                        """,
                goal);
    }

    /**
     * Parse AI validation response
     */
    private ValidationResult parseAIValidationResponse(String aiResponse, String goal) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return ValidationResult.error("goal",
                    "Không thể xác thực mục tiêu. Vui lòng thử lại.", null);
        }

        String[] parts = aiResponse.trim().split("\\|");

        if (parts.length == 0) {
            return ValidationResult.error("goal",
                    "Phản hồi AI không hợp lệ. Vui lòng thử lại.", null);
        }

        String status = parts[0].trim().toUpperCase();

        switch (status) {
            case "VALID":
                log.info("✅ AI Validation: Goal VALID - '{}'", goal);
                return ValidationResult.info("goal", "Mục tiêu hợp lệ", null);

            case "WARNING":
                String warningMessage = parts.length > 1 ? parts[1].trim() : "Mục tiêu cần làm rõ hơn";
                String suggestion = parts.length > 2 ? parts[2].trim() : "";
                String fullWarning = suggestion.isEmpty() ? warningMessage : warningMessage + ". " + suggestion;

                log.warn("⚠️ AI Validation: Goal WARNING - '{}' | {}", goal, fullWarning);
                return ValidationResult.warning("goal", warningMessage, suggestion.isEmpty() ? null : suggestion);

            case "ERROR":
                String errorMessage = parts.length > 1 ? parts[1].trim()
                        : "Mục tiêu không hợp lệ. Vui lòng nhập mục tiêu học tập phù hợp.";

                log.error("❌ AI Validation: Goal REJECTED - '{}' | {}", goal, errorMessage);
                return ValidationResult.error("goal", errorMessage,
                        "Vui lòng nhập mục tiêu học tập hợp lệ (ví dụ: học lập trình, học ngoại ngữ)");

            default:
                log.warn("⚠️ AI Validation: Unknown status '{}', treating as error", status);
                return ValidationResult.error("goal",
                        "Không thể xác định tính hợp lệ của mục tiêu. Vui lòng kiểm tra lại.", null);
        }
    }

}
