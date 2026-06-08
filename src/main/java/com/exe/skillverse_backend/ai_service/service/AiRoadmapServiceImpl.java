package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_knowledge_service.util.AiKnowledgeSlugUtils;
import com.exe.skillverse_backend.ai_service.dto.gemini.GeminiDTO;
import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_usage_service.dto.AiTokenUsageRecordCommand;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiFlowType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiProviderType;
import com.exe.skillverse_backend.ai_usage_service.entity.enums.AiUsageStatus;
import com.exe.skillverse_backend.ai_usage_service.service.AiTokenUsageRecorder;
import com.exe.skillverse_backend.ai_usage_service.util.TokenCounterUtil;
import com.exe.skillverse_backend.ai_service.dto.request.UpdateProgressRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ClarificationQuestion;
import com.exe.skillverse_backend.ai_service.dto.response.CompleteNodeResponse;
import com.exe.skillverse_backend.ai_service.dto.response.ProgressResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.response.ValidationResult;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession.RoadmapStatus;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.ai_rag_service.service.AiRagGateway;
import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import com.exe.skillverse_backend.ai_service.service.dto.CourseCatalogEntry;
import com.exe.skillverse_backend.course_service.entity.Course;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import com.exe.skillverse_backend.course_service.repository.CourseRepository;
import com.exe.skillverse_backend.journey_service.repository.JourneyRepository;
import com.exe.skillverse_backend.journey_service.entity.Journey;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission.SubmissionStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.repository.RoadmapNodeSubmissionRepository;
import com.exe.skillverse_backend.study_service.entity.Task;
import com.exe.skillverse_backend.study_service.repository.TaskRepository;
import com.exe.skillverse_backend.study_service.service.TaskBoardService;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.premium_service.dto.response.FeatureLimitInfo;
import com.exe.skillverse_backend.premium_service.entity.FeatureType;
import com.exe.skillverse_backend.premium_service.exception.UsageLimitExceededException;
import com.exe.skillverse_backend.premium_service.service.PremiumService;
import com.exe.skillverse_backend.premium_service.service.UsageLimitService;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.exe.skillverse_backend.ai_service.service.impl.MultiLevelCourseMatcher;

/**
 * Service for AI-powered roadmap generation using Spring AI with Gemini
 * Using Spring AI OpenAI client with Gemini's OpenAI-compatible API
 *
 * @deprecated [LEGACY] This class contains legacy monolithic dynamic generation from scratch.
 * For the new standardized template-guided sequential AI enrichment loop,
 * please use {@link com.exe.skillverse_backend.roadmap_package_service.service.impl.RoadmapNodeAiEnrichmentServiceImpl}
 * and {@link com.exe.skillverse_backend.roadmap_package_service.service.impl.RoadmapTemplateServiceImpl} instead.
 * Pending cleanup or migration.
 */
@Service
@Slf4j
public class AiRoadmapServiceImpl implements AiRoadmapService {

    @Value("${spring.ai.openai.api-key}")
    private String geminiApiKey;

    @Value("${spring.ai.openai.chat.options.model}")
    private String geminiModel;

    @Value("${app.ai.roadmap.matching.prefill-enabled:false}")
    private boolean prefillCourseMatchingEnabled;

    // Use Gemini native endpoint instead of OpenAI-compatible one
    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final int MAX_CONCURRENT_ACTIVE_ROADMAPS = 5;
    private static final int AI_TRANSIENT_MAX_RETRIES = 2;
    private static final long AI_RETRY_BASE_WAIT_MS = 1_000L;
    private static final Pattern ROADMAP_NODE_LINK_PATTERN = Pattern.compile(
            "\\[ROADMAP_NODE_LINK\\](?:\\s+journey=(\\d+))?\\s+roadmap=(\\d+)\\s+node=([^\\s]+)",
            Pattern.CASE_INSENSITIVE);
        private static final Pattern HOURS_PATTERN = Pattern
            .compile("(\\d+(?:[.,]\\d+)?)\\s*(h|hr|hrs|hour|hours|gio|giờ)", Pattern.CASE_INSENSITIVE);
        private static final Pattern MINUTES_PATTERN = Pattern
            .compile("(\\d+(?:[.,]\\d+)?)\\s*(m|min|mins|minute|minutes|phut|phút)", Pattern.CASE_INSENSITIVE);
        private static final Pattern COMPACT_HM_PATTERN = Pattern.compile("(\\d{1,3})\\s*h\\s*(\\d{1,2})",
            Pattern.CASE_INSENSITIVE);

    private final RoadmapSessionRepository roadmapSessionRepository;
    private final UserRoadmapProgressRepository progressRepository;
    private final ObjectMapper objectMapper;
    private final InputValidationService inputValidationService;
    private final UsageLimitService usageLimitService;
    private final ExpertPromptService expertPromptService;
    private final TaxonomyService taxonomyService;
    private final PremiumService premiumService;
    private final ChatModel mistralChatModel;
    private final CourseRepository courseRepository;
    private final JourneyRepository journeyRepository;
    private final TaskRepository taskRepository;
    private final RoadmapCompletionSyncService roadmapCompletionSyncService;
    private final TaskBoardService taskBoardService;
    private final AiCourseCatalogService aiCourseCatalogService;
    private final MultiLevelCourseMatcher multiLevelCourseMatcher;
    private final LocalAiGateway localAiGateway;
    private final AiRagGateway aiRagGateway;
    private final AiTokenUsageRecorder tokenUsageRecorder;
    private final RoadmapNodeSubmissionRepository nodeSubmissionRepository;

    public AiRoadmapServiceImpl(
            RoadmapSessionRepository roadmapSessionRepository,
            UserRoadmapProgressRepository progressRepository,
            ObjectMapper objectMapper,
            InputValidationService inputValidationService,
            UsageLimitService usageLimitService,
            ExpertPromptService expertPromptService,
            TaxonomyService taxonomyService,
            PremiumService premiumService,
            @Qualifier("mistralAiChatModel") ChatModel mistralChatModel,
            CourseRepository courseRepository,
            JourneyRepository journeyRepository,
            TaskRepository taskRepository,
            RoadmapCompletionSyncService roadmapCompletionSyncService,
            TaskBoardService taskBoardService,
            AiCourseCatalogService aiCourseCatalogService,
            MultiLevelCourseMatcher multiLevelCourseMatcher,
            @Autowired(required = false) LocalAiGateway localAiGateway,
            @Autowired(required = false) AiRagGateway aiRagGateway,
            @Autowired(required = false) AiTokenUsageRecorder tokenUsageRecorder,
            RoadmapNodeSubmissionRepository nodeSubmissionRepository) {
        this.roadmapSessionRepository = roadmapSessionRepository;
        this.progressRepository = progressRepository;
        this.objectMapper = objectMapper;
        this.inputValidationService = inputValidationService;
        this.usageLimitService = usageLimitService;
        this.expertPromptService = expertPromptService;
        this.taxonomyService = taxonomyService;
        this.premiumService = premiumService;
        this.mistralChatModel = mistralChatModel;
        this.courseRepository = courseRepository;
        this.journeyRepository = journeyRepository;
        this.taskRepository = taskRepository;
        this.roadmapCompletionSyncService = roadmapCompletionSyncService;
        this.taskBoardService = taskBoardService;
        this.aiCourseCatalogService = aiCourseCatalogService;
        this.multiLevelCourseMatcher = multiLevelCourseMatcher;
        this.localAiGateway = localAiGateway;
        this.aiRagGateway = aiRagGateway;
        this.tokenUsageRecorder = tokenUsageRecorder;
        this.nodeSubmissionRepository = nodeSubmissionRepository;
    }

    /**
     * Pre-validate roadmap generation request without actually generating
     * 
     * @param request User request to validate
     * @return List of validation results (INFO/WARNING/ERROR severity)
     */
    public List<ValidationResult> preValidateRequest(GenerateRoadmapRequest request, Long userId) {
        log.info("🔍 Pre-validating request: goal='{}', duration='{}', experience='{}', style='{}'",
                request.getGoal(), request.getDuration(), request.getExperience(), request.getStyle());

        List<ValidationResult> results = new ArrayList<>();

        // 🚨 STAGE 1: AI Goal Validation (lightweight ~100 tokens)
        ValidationResult aiValidation = validateGoalWithAI(request.getGoal(), userId);
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
        String existingTraceId = MDC.get("traceId");
        boolean traceOwnedByMethod = existingTraceId == null || existingTraceId.isBlank();
        String traceId = traceOwnedByMethod ? UUID.randomUUID().toString().substring(0, 12) : existingTraceId;
        if (traceOwnedByMethod) {
            MDC.put("traceId", traceId);
        }
        long requestStartedAt = System.nanoTime();
        RoadmapGenerationTelemetry telemetry = new RoadmapGenerationTelemetry();
        String summaryOutcome = "failed";
        String summaryErrorCode = "n/a";
        String generationPrompt = null; // Declared here for access in catch block
        long generationStartTime = 0;

        log.info("🚀 [trace={}] Generating roadmap V2 for user {} with goal/target: {} (roadmapMode={}, aiAgentMode={})",
                traceId,
                user.getId(),
                logGoal,
                request.getRoadmapMode(),
                request.getAiAgentMode());
        ensureCanStartAnotherActiveRoadmap(user.getId(),
                "Bạn đang học tối đa 5 lộ trình cùng lúc. Hãy hoàn thành, tạm dừng hoặc xóa một lộ trình trước khi tạo mới.");
        log.debug("🧪 [trace={}] Roadmap request payload: mode={}, aiAgentMode={}, goal='{}', target='{}', duration='{}', desiredDuration='{}', experience='{}', currentSkillLevel='{}', learningStyle='{}', roadmapType='{}', skillName='{}', skillCategory='{}', desiredDepth='{}', learnerType='{}', dailyLearningTime='{}', assessmentPreference='{}', difficultyTolerance='{}', priority='{}'",
            traceId,
            request.getRoadmapMode(),
            request.getAiAgentMode(),
            previewHead(logGoal, 120),
            previewHead(request.getTarget(), 120),
            previewHead(request.getDuration(), 80),
            previewHead(request.getDesiredDuration(), 80),
            previewHead(request.getExperience(), 80),
            previewHead(request.getCurrentSkillLevel(), 80),
            previewHead(request.getStyle(), 80),
            previewHead(request.getRoadmapType(), 80),
            previewHead(request.getSkillName(), 120),
            previewHead(request.getSkillCategory(), 120),
            previewHead(request.getDesiredDepth(), 80),
            previewHead(request.getLearnerType(), 80),
            previewHead(request.getDailyLearningTime(), 80),
            previewHead(request.getAssessmentPreference(), 80),
            previewHead(request.getDifficultyTolerance(), 80),
            previewHead(request.getPriority(), 80));

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
            ValidationResult aiValidation = validateGoalWithAI(request.getGoal(), user.getId());

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

            // Step 3: AI Generation — 4-tier cascade: Local → Mistral → Gemini → Mistral compact
            String roadmapJson = null;
            generationPrompt = buildPrompt(request)
                    + "\n\nCRITICAL: Trả lời bằng TIẾNG VIỆT. Chỉ trả về JSON hợp lệ như yêu cầu.";
            generationStartTime = System.currentTimeMillis();

            // Step 3A-pre: Local AI first (fast path)
            roadmapJson = callLocalAiWithRetry(generationPrompt, telemetry, traceId);
            if (roadmapJson != null) {
                telemetry.markModelPath("local");
            }

            // Step 3A: Mistral primary (2 attempts, 30s fixed backoff) — only if local didn't succeed
            if (roadmapJson == null) {
            try {
                log.info("🧭 [trace={}] Primary model path: Mistral", traceId);
                telemetry.markModelPath("mistral");
                roadmapJson = callMistralWithRetry(request, telemetry, traceId);
            } catch (Exception mistralEx) {
                String mistralFailureType = classifyAiFailure(mistralEx);
                int mistralStatus = extractHttpStatus(mistralEx);
                telemetry.markFallback("mistral-call-failed", mistralFailureType, mistralStatus);
                log.warn(
                        "⚠️ [trace={}] Mistral failed (type={}, status={}): {}. "
                        + "Falling back to Gemini (2 attempts, exponential backoff).",
                        traceId,
                        mistralFailureType,
                        mistralStatus,
                        safeMessage(mistralEx));

                // Step 3B: Gemini fallback (2 attempts, exponential backoff via callGeminiWithRetry)
                telemetry.markModelPath("gemini");
                try {
                    roadmapJson = callGeminiWithRetry(request, telemetry);
                } catch (Exception geminiEx) {
                    String geminiFailureType = classifyAiFailure(geminiEx);
                    int geminiStatus = extractHttpStatus(geminiEx);
                    telemetry.markFallback("gemini-call-failed", geminiFailureType, geminiStatus);
                    log.warn(
                            "⚠️ [trace={}] Gemini fallback failed (type={}, status={}): {}. "
                            + "Attempting Mistral compact (short prompt).",
                            traceId,
                            geminiFailureType,
                            geminiStatus,
                            safeMessage(geminiEx));

                    // Step 3C: Mistral compact as final tier
                    telemetry.markModelPath("mistral-compact");
                    roadmapJson = callMistralRoadmapFallback(request, telemetry);
                }
            }
            } // end if (roadmapJson == null) — cloud fallback chain

            long generationLatencyMs = System.currentTimeMillis() - generationStartTime;

            // Step 4: Parse and validate JSON (Schema V2)
            // Retry up to 2 times if parse fails due to JSON truncation (unclosed brackets)
            ParsedRoadmap parsed = null;
            for (int parseRetry = 0; parseRetry < 3; parseRetry++) {
                try {
                    parsed = validateAndParseRoadmapV2(roadmapJson, telemetry);
                    break;
                } catch (ApiException parseEx) {
                    boolean isTruncation = parseEx.getMessage() != null
                            && (parseEx.getMessage().contains("Unexpected end-of-input")
                                || parseEx.getMessage().contains("Unexpected character")
                                || parseEx.getMessage().contains("not complete"));
                    String currentPath = telemetry.getModelPath();
                    boolean isLocalPath = "local".equals(currentPath);
                    if ((isTruncation || isLocalPath) && parseRetry < 2) {
                        String reason = isLocalPath ? "local-schema-fail" : "truncation";
                        log.warn("⚠️ [trace={}] Parse attempt {}/3 failed ({}). Falling back to cloud...",
                                traceId, parseRetry + 2, reason);
                        telemetry.markFallback("parse-fail-retry-" + (parseRetry + 1), reason, 0);
                        if ("local".equals(currentPath)) {
                            roadmapJson = callLocalAiWithRetry(generationPrompt, telemetry, traceId);
                            if (roadmapJson == null) {
                                log.warn("⚠️ [trace={}] Local AI parse retry exhausted, falling back to Mistral...", traceId);
                                currentPath = "mistral";
                                telemetry.markModelPath("mistral");
                                roadmapJson = callMistralWithRetry(request, telemetry, traceId);
                            }
                        } else if ("mistral".equals(currentPath)) {
                            roadmapJson = callMistralWithRetry(request, telemetry, traceId);
                        } else if ("gemini".equals(currentPath)) {
                            roadmapJson = callGeminiWithRetry(request, telemetry);
                        } else if ("mistral-compact".equals(currentPath)) {
                            roadmapJson = callMistralRoadmapFallback(request, telemetry);
                        } else {
                            roadmapJson = callMistralWithRetry(request, telemetry, traceId);
                        }
                        continue;
                    }
                    throw parseEx;
                }
            }

            // Step 4.5: Validate AI importance scores and backfill missing ones
            RoadmapImportanceScorer.validateAndBackfill(parsed.nodes(), request);

            // Step 4.6: Normalize orderIndex and apply importance-aware stable ordering
            RoadmapNodeOrderNormalizer.normalize(parsed.nodes());

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

            // Keep timeline fields consistent by mode before validation/warning and persistence.
            alignTimelineMetadataWithRequest(parsed.metadata(), request);

            // Step 5: Time budget check (internal log only — not exposed to FE)
            if (parsed.graphWarnings() != null && !parsed.graphWarnings().isEmpty()) {
                log.debug("[RoadmapGen] Graph warnings (internal): {}", parsed.graphWarnings());
            }
            try {
                if (parsed.statistics() != null && parsed.statistics().getTotalEstimatedHours() != null) {
                    int minutesPerDay = parseDailyTimeMinutes(request.getDailyTime());
                    int plannedDays = parseDesiredDurationDays(request.getDesiredDuration());
                    double timeBudgetHours = (minutesPerDay * plannedDays) / 60.0;
                    double totalHoursGen = parsed.statistics().getTotalEstimatedHours();
                    double diff = Math.abs(totalHoursGen - timeBudgetHours);
                    double rel = timeBudgetHours > 0 ? diff / timeBudgetHours : 0.0;
                    if (rel > 0.10) {
                        log.debug("[RoadmapGen] Time budget deviation {}% ({}h vs {}h budget)",
                                String.format("%.0f", rel * 100), String.format("%.1f", totalHoursGen),
                                String.format("%.1f", timeBudgetHours));
                    }
                }
            } catch (Exception ignored) {
            }
            List<String> warnings = new ArrayList<>();

            // Step 6: Extract statistics for database
            Integer totalNodes = parsed.statistics() != null ? parsed.statistics().getTotalNodes()
                    : parsed.nodes().size();
            Double totalHours = parsed.statistics() != null ? parsed.statistics().getTotalEstimatedHours()
                    : calculateTotalHours(parsed.nodes());

            // Step 6.5: Optional prefill matcher (disabled by default for strict intent-first)
            if (prefillCourseMatchingEnabled) {
                matchNodesToRealCourses(parsed.nodes());
            } else {
                log.info("🎯 [trace={}] Prefill node-course matcher disabled (strict intent-first mode)", traceId);
            }

            // Step 6.7: Phase 2 — Multi-level course-module matching (Pre-Selection + Post-Matching)
            // Replaces course-only matching with module-level distribution via MultiLevelCourseMatcher.
            // Preserves existing behavior if no courses match (Study Planner fallback unchanged).
            multiLevelCourseMatcher.matchNodesToCoursesAndModules(
                    parsed.nodes(),
                    request.getTarget() != null ? request.getTarget() : request.getGoal(),
                    request.getRoadmapMode() != null ? request.getRoadmapMode().name() : "CAREER_BASED",
                    request.getSkillName(),
                    request.getTargetRole(),
                    user.getId()
            );

            // Step 6.6: Validate suggestedCourseIds against real DB (Anti-Hallucination)
            validateAndStripFakeCourseIds(parsed.nodes());
            telemetry.captureNodeCoverage(parsed.nodes());

            // IMPORTANT: serialize after all node/metadata enrichments to avoid stale roadmap_json.
            String storedJson = serializeParsedRoadmap(parsed);

            ensureCanStartAnotherActiveRoadmap(user.getId(),
                    "Bạn đang học tối đa 5 lộ trình cùng lúc. Hãy hoàn thành, tạm dừng hoặc xóa một lộ trình trước khi tạo mới.");

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
                    .roadmapMode(
                            truncate(parsed.metadata().getRoadmapMode() != null ? parsed.metadata().getRoadmapMode()
                                    : (request.getRoadmapMode() != null ? request.getRoadmapMode().name() : null), 20))
                    .roadmapType(
                            truncate(parsed.metadata().getRoadmapType() != null ? parsed.metadata().getRoadmapType()
                                    : request.getRoadmapType(), 20))
                    .target(parsed.metadata().getTarget() != null ? parsed.metadata().getTarget() : request.getTarget())
                    .finalObjective(
                            truncate(parsed.metadata().getFinalObjective() != null
                                    ? parsed.metadata().getFinalObjective()
                                    : request.getFinalObjective(), 100))
                    // Statistics (for premium quota)
                    .totalNodes(totalNodes)
                    .totalEstimatedHours(totalHours)
                    .difficultyLevel(truncate(parsed.metadata().getDifficultyLevel(), 20))
                    // Premium tracking
                    .isPremiumGenerated(false) // TODO: Check user premium status
                    // Status: new roadmap is always ACTIVE
                    .status(RoadmapStatus.ACTIVE)
                    // Full JSON
                    .roadmapJson(storedJson)
                    .build();

            session = roadmapSessionRepository.save(session);

            // Record token usage for successful generation AFTER all validations and save
            String modelPath = telemetry.getModelPath();
            AiProviderType providerType;
            String modelName;
            if ("local".equals(modelPath)) {
                providerType = AiProviderType.LOCAL_AI;
                modelName = "local-ai";
            } else if ("mistral".equals(modelPath)) {
                providerType = AiProviderType.MISTRAL;
                modelName = "mistral-large-latest";
            } else if ("gemini".equals(modelPath)) {
                providerType = AiProviderType.GEMINI;
                modelName = geminiModel;
            } else if ("mistral-compact".equals(modelPath)) {
                providerType = AiProviderType.MISTRAL;
                modelName = "mistral-large-latest";
            } else {
                // Default fallback
                providerType = AiProviderType.MISTRAL;
                modelName = "mistral-large-latest";
            }
            recordRoadmapSuccess(providerType, modelName, user.getId(), session.getId(),
                    generationPrompt, roadmapJson, generationLatencyMs);

            log.info("✅ Roadmap V2 session {} created: {} nodes, {}h, difficulty: {}",
                    session.getId(), totalNodes, String.format("%.1f", totalHours),
                    parsed.metadata().getDifficultyLevel());

            // Step 8: Return response (new format)
                RoadmapResponse response = RoadmapResponse.builder()
                    .sessionId(session.getId())
                    .roadmapStatus(RoadmapStatus.ACTIVE.name())
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

                    log.debug("🧪 [trace={}] Roadmap response payload: roadmapNodes={}, projectsEvidence={}, nextSteps={}, warnings={}, structure={}, thinkingProgression={}, learningTips={}, nodeCoverage(total={}, withCourses={}, withModules={})",
                        traceId,
                        response.getRoadmap() != null ? response.getRoadmap().size() : 0,
                        response.getProjectsEvidence() != null ? response.getProjectsEvidence().size() : 0,
                        response.getNextSteps() != null,
                        response.getWarnings() != null ? response.getWarnings().size() : 0,
                        response.getStructure() != null ? response.getStructure().size() : 0,
                        response.getThinkingProgression() != null ? response.getThinkingProgression().size() : 0,
                        response.getLearningTips() != null ? response.getLearningTips().size() : 0,
                        telemetry.totalNodes,
                        telemetry.nodesWithCourses,
                        telemetry.nodesWithModules);

            long elapsedMs = (System.nanoTime() - requestStartedAt) / 1_000_000;
            log.info("✅ [trace={}] Roadmap generation completed in {}ms (sessionId={}, roadmapStatus={})",
                    traceId,
                    elapsedMs,
                    session.getId(),
                    response.getRoadmapStatus());
            summaryOutcome = "success";
            summaryErrorCode = "none";
            return response;

        } catch (ApiException e) {
            long elapsedMs = (System.nanoTime() - requestStartedAt) / 1_000_000;
            String errorCode = e.getErrorCode() != null ? e.getErrorCode().code : "UNKNOWN";
            int status = e.getErrorCode() != null ? e.getErrorCode().status.value() : 500;
            log.warn("⚠️ [trace={}] Roadmap generation failed after {}ms with ApiException (code={}, status={}): {}",
                    traceId,
                    elapsedMs,
                    errorCode,
                    status,
                    e.getMessage());
            summaryOutcome = "api-error";
            summaryErrorCode = errorCode;

            // Only record failure if AI generation was actually attempted
            // (not for business/precondition errors before AI call)
            boolean aiAttempted = generationPrompt != null && generationStartTime > 0
                    && !"unknown".equals(telemetry.getModelPath());
            if (aiAttempted) {
                long failureLatencyMs = System.currentTimeMillis() - generationStartTime;
                String modelPath = telemetry.getModelPath();
                AiProviderType failedProvider;
                String failedModel;
                if ("local".equals(modelPath)) {
                    failedProvider = AiProviderType.LOCAL_AI;
                    failedModel = "local-ai";
                } else if ("gemini".equals(modelPath)) {
                    failedProvider = AiProviderType.GEMINI;
                    failedModel = geminiModel;
                } else if ("mistral-compact".equals(modelPath)) {
                    failedProvider = AiProviderType.MISTRAL;
                    failedModel = "mistral-large-latest";
                } else {
                    // Default to Mistral if mistral or null
                    failedProvider = AiProviderType.MISTRAL;
                    failedModel = "mistral-large-latest";
                }

                recordRoadmapFailure(failedProvider, failedModel, user.getId(), null,
                        generationPrompt, e.getMessage(), failureLatencyMs);
            }

            throw e;
        } catch (Exception e) {
            long elapsedMs = (System.nanoTime() - requestStartedAt) / 1_000_000;
            log.error("❌ [trace={}] Failed to generate roadmap V2 after {}ms", traceId, elapsedMs, e);
            summaryOutcome = "unexpected-error";
            summaryErrorCode = e.getClass().getSimpleName();

            // Only record failure if AI generation was actually attempted
            // Use null for relatedEntityId since no session was saved on failure
            boolean aiAttempted = generationPrompt != null && generationStartTime > 0
                    && !"unknown".equals(telemetry.getModelPath());
            if (aiAttempted) {
                long failureLatencyMs = System.currentTimeMillis() - generationStartTime;
                String modelPath = telemetry.getModelPath();
                AiProviderType failedProvider;
                String failedModel;
                if ("local".equals(modelPath)) {
                    failedProvider = AiProviderType.LOCAL_AI;
                    failedModel = "local-ai";
                } else if ("gemini".equals(modelPath)) {
                    failedProvider = AiProviderType.GEMINI;
                    failedModel = geminiModel;
                } else if ("mistral-compact".equals(modelPath)) {
                    failedProvider = AiProviderType.MISTRAL;
                    failedModel = "mistral-large-latest";
                } else {
                    // Default to Mistral if mistral or null
                    failedProvider = AiProviderType.MISTRAL;
                    failedModel = "mistral-large-latest";
                }

                recordRoadmapFailure(failedProvider, failedModel, user.getId(), null,
                        generationPrompt, e.getMessage(), failureLatencyMs);
            }

            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to generate roadmap: " + e.getMessage());
        } finally {
            long elapsedMs = (System.nanoTime() - requestStartedAt) / 1_000_000;
            logRoadmapTelemetrySummary(traceId, telemetry, summaryOutcome, summaryErrorCode, elapsedMs);
            if (traceOwnedByMethod) {
                MDC.remove("traceId");
            }
        }
    }

    /**
     * Helper to truncate strings to database column limits
     */
    private String truncate(String value, int maxLength) {
        if (value == null)
            return null;
        if (value.length() <= maxLength)
            return value;
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
                .map(RoadmapResponse.RoadmapNode::getEstimatedTimeMinutes)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        return totalMinutes / 60.0;
    }

    private String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null || traceId.isBlank() ? "n/a" : traceId;
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "n/a";
        }
        return throwable.getMessage();
    }

    private String previewHead(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String previewTail(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return "..." + normalized.substring(normalized.length() - maxLength);
    }

    private boolean closesLikeJson(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return trimmed.endsWith("}") || trimmed.endsWith("]");
    }

    /**
     * Detect if a JSON string is likely truncated by checking bracket balance.
     * If it ends with } but has more [ than ] → truncated.
     * If it ends with ] but has more { than } → truncated.
     */
    private boolean isJsonLikelyTruncated(String json) {
        if (json == null || json.isBlank()) {
            return true;
        }
        int openBraces = 0, closeBraces = 0, openBrackets = 0, closeBrackets = 0;
        for (char c : json.toCharArray()) {
            if (c == '{') openBraces++;
            else if (c == '}') closeBraces++;
            else if (c == '[') openBrackets++;
            else if (c == ']') closeBrackets++;
        }
        boolean endsWithBrace = json.trim().endsWith("}");
        boolean endsWithBracket = json.trim().endsWith("]");
        return (endsWithBrace && openBrackets > closeBrackets)
            || (endsWithBracket && openBraces > closeBraces);
    }

    private void logJsonCheckpoint(String stage, String payload) {
        if (!log.isDebugEnabled()) {
            return;
        }

        String traceId = currentTraceId();
        if (payload == null) {
            log.debug("🔎 [trace={}] {}: payload is null", traceId, stage);
            return;
        }

        String trimmed = payload.trim();
        log.debug(
                "🔎 [trace={}] {}: chars={}, closesLikeJson={}, head='{}', tail='{}'",
                traceId,
                stage,
                trimmed.length(),
                closesLikeJson(trimmed),
                previewHead(trimmed, 120),
                previewTail(trimmed, 120));
    }

    private int extractHttpStatus(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HttpStatusCodeException hsce) {
                return hsce.getStatusCode().value();
            }
            String msg = current.getMessage();
            if (msg != null) {
                Matcher m = Pattern.compile("\\b([45]\\d{2})\\b").matcher(msg);
                if (m.find()) {
                    try {
                        return Integer.parseInt(m.group(1));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            current = current.getCause();
        }
        return -1;
    }

    private String classifyAiFailure(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        if (throwable instanceof ResourceAccessException) {
            String msg = safeMessage(throwable).toLowerCase(Locale.ROOT);
            if (msg.contains("read timed out")) {
                return "read-timeout";
            }
            if (msg.contains("connect timed out") || msg.contains("connection timed out")) {
                return "connect-timeout";
            }
            return "resource-access";
        }

        int httpStatus = extractHttpStatus(throwable);
        if (httpStatus > 0) {
            if (httpStatus >= 500) {
                return "http-" + httpStatus + "-server";
            }
            if (httpStatus >= 400) {
                return "http-" + httpStatus + "-client";
            }
        }

        String msg = safeMessage(throwable).toLowerCase(Locale.ROOT);
        if (msg.contains("timed out")) {
            return "timeout";
        }
        if (msg.contains("connection refused")) {
            return "connection-refused";
        }
        return throwable.getClass().getSimpleName();
    }

    private boolean isRetryableAiFailure(Throwable throwable) {
        if (throwable == null) {
            return false;
        }

        int httpStatus = extractHttpStatus(throwable);
        if (httpStatus == 408 || httpStatus == 409 || httpStatus == 425 || httpStatus == 429 || httpStatus >= 500) {
            return true;
        }

        String failureType = classifyAiFailure(throwable).toLowerCase(Locale.ROOT);
        return failureType.contains("timeout")
                || failureType.contains("resource-access")
                || failureType.contains("connection-refused");
    }

    private long computeRetryBackoffMs(int attemptIndex) {
        return (long) Math.pow(2, attemptIndex) * AI_RETRY_BASE_WAIT_MS;
    }

    private void sleepRetryBackoff(long waitMs) {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Call Mistral AI via Spring AI ChatModel (with retry for transient errors).
     * This is the fallback when Gemini API fails.
     *
     * Note: Timeout is controlled by Spring Boot auto-config (spring.ai.mistralai connection/read timeouts).
     * Retry is handled in-process for transient failures.
     */
    private String callMistralAPI(String prompt) {
        return callMistralAPI(prompt, null, "mistral-generic");
    }

    private String callMistralAPI(String prompt, RoadmapGenerationTelemetry telemetry, String channel) {
        String traceId = currentTraceId();
        int maxRetries = AI_TRANSIENT_MAX_RETRIES;
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (telemetry != null) {
                telemetry.recordModelAttempt(channel);
            }
            long attemptStartedAt = System.nanoTime();
            try {
                // DEBUG: Log BEFORE call to measure prompt size
                int promptCharsEstimate = prompt != null ? prompt.length() : 0;
                int promptTokensEstimate = promptCharsEstimate / 4; // rough estimate
                log.info("📤 [trace={}] Mistral sending request: prompt≈{} chars (≈{} tokens), maxRetries={}/{}",
                    traceId, promptCharsEstimate, promptTokensEstimate, attempt + 1, maxRetries + 1);

                String content = ChatClient.builder(mistralChatModel)
                        .build()
                        .prompt()
                        .user(prompt)
                        .call()
                        .content();

                if (content == null || content.isBlank()) {
                    throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                            "Mistral AI returned empty response. Please retry.");
                }

                long elapsedMs = (System.nanoTime() - attemptStartedAt) / 1_000_000;
                boolean closesLikeJson = closesLikeJson(content);
                log.info("✅ [trace={}] Mistral AI responded attempt {}/{} | elapsed={}ms | response_chars={} | prompt_est_tokens={} | closesLikeJson={}",
                    traceId,
                    attempt + 1,
                    maxRetries + 1,
                    elapsedMs,
                    content.length(),
                    promptTokensEstimate,
                    closesLikeJson);

                if (!closesLikeJson) {
                    log.warn("⚠️ [trace={}] Mistral payload does not end with JSON closer. tail='{}'",
                            traceId,
                            previewTail(content, 200));
                }
                return content;
            } catch (Exception e) {
                lastException = e;
                long elapsedMs = (System.nanoTime() - attemptStartedAt) / 1_000_000;
                String failureType = classifyAiFailure(e);
                int httpStatus = extractHttpStatus(e);
                boolean retryable = isRetryableAiFailure(e);

                if (attempt < maxRetries && retryable) {
                    long waitMs = computeRetryBackoffMs(attempt);
                    log.warn(
                        "⚠️ [trace={}] Mistral attempt {}/{} failed (type={}, status={}, elapsedMs={}): {} — retrying in {}ms",
                        traceId,
                        attempt + 1,
                        maxRetries + 1,
                        failureType,
                        httpStatus,
                        elapsedMs,
                        safeMessage(e),
                        waitMs);
                    sleepRetryBackoff(waitMs);
                    continue;
                }

                log.error("❌ [trace={}] Failed to call Mistral AI on attempt {}/{} (type={}, status={}, elapsedMs={}): {}",
                    traceId,
                    attempt + 1,
                    maxRetries + 1,
                    failureType,
                    httpStatus,
                    elapsedMs,
                    safeMessage(e));
                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "Mistral AI generation failed (type=" + failureType + ", status=" + httpStatus + "): "
                        + safeMessage(e));
            }
        }

        // Should not reach here, but safeguard
        throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                "Mistral AI retry exhausted. Last error: "
                + (lastException != null ? lastException.getMessage() : "unknown"));
    }

    /**
     * Call Gemini API directly using RestClient with extended timeout
     */
    private String callGeminiAPI(GenerateRoadmapRequest request) {
        return callGeminiAPI(request, null);
    }

    private String callGeminiAPI(GenerateRoadmapRequest request, RoadmapGenerationTelemetry telemetry) {
        String prompt = buildPrompt(request);

        // Append critical instruction for JSON format
        String finalPrompt = prompt
                + "\n\nCRITICAL: Trả lời bằng TIẾNG VIỆT. Nếu phát hiện mục tiêu/đầu vào vô lý (ví dụ: IELTS 10.0, nội dung thô tục), hãy từ chối lịch sự bằng tiếng Việt và gợi ý cách nhập lại hợp lệ. Chỉ trả về JSON hợp lệ như yêu cầu.";

        if (telemetry != null) {
            telemetry.recordPromptLength(finalPrompt.length());
        }

        String rawResponse = callGeminiDirectly(finalPrompt, geminiModel, telemetry);
        String extracted = extractJsonFromResponse(rawResponse);
        if (telemetry != null) {
            telemetry.recordPayloadLength(rawResponse, extracted);
        }
        return extracted;
    }

    /**
     * Call Gemini as fallback tier with retry.
     * Wrapper around callGeminiDirectly() which already has 2 retry + exponential backoff (1s → 2s).
     * Uses the same primary prompt as callGeminiAPI().
     * Throws ApiException on all failures (no further fallback).
     */
    private String callGeminiWithRetry(GenerateRoadmapRequest request, RoadmapGenerationTelemetry telemetry) {
        String traceId = currentTraceId();
        log.info("🧭 [trace={}] Gemini fallback tier — attempting with primary prompt", traceId);

        String prompt = buildPrompt(request)
                + "\n\nCRITICAL: Trả lời bằng TIẾNG VIỆT. Nếu phát hiện mục tiêu/đầu vào vô lý (ví dụ: IELTS 10.0, nội dung thô tục), hãy từ chối lịch sự bằng tiếng Việt và gợi ý cách nhập lại hợp lệ. Chỉ trả về JSON hợp lệ như yêu cầu.";

        if (telemetry != null) {
            telemetry.recordPromptLength(prompt.length());
        }

        // callGeminiDirectly already handles 2 retries with exponential backoff internally
        String rawResponse = callGeminiDirectly(prompt, geminiModel, telemetry);
        String extracted = extractJsonFromResponse(rawResponse);

        if (telemetry != null) {
            telemetry.recordPayloadLength(rawResponse, extracted);
        }
        log.info("✅ [trace={}] Gemini fallback responded ({} chars raw, {} chars extracted)",
                traceId, rawResponse.length(), extracted.length());
        return extracted;
    }

    private String callMistralRoadmapWithPrimaryPrompt(GenerateRoadmapRequest request) {
        return callMistralRoadmapWithPrimaryPrompt(request, null);
    }

    private String callMistralRoadmapWithPrimaryPrompt(GenerateRoadmapRequest request, RoadmapGenerationTelemetry telemetry) {
        String prompt = buildPrompt(request)
                + "\n\nCRITICAL: Trả lời bằng TIẾNG VIỆT. Nếu phát hiện mục tiêu/đầu vào vô lý (ví dụ: IELTS 10.0, nội dung thô tục), hãy từ chối lịch sự bằng tiếng Việt và gợi ý cách nhập lại hợp lệ. Chỉ trả về JSON hợp lệ như yêu cầu.";
        if (telemetry != null) {
            telemetry.recordPromptLength(prompt.length());
        }
        String response = callMistralAPI(prompt, telemetry, "mistral-primary");
        String extracted = extractJsonFromResponse(response);
        if (telemetry != null) {
            telemetry.recordPayloadLength(response, extracted);
        }
        logJsonCheckpoint("mistral-primary/raw", response);
        logJsonCheckpoint("mistral-primary/extracted", extracted);
        return extracted;
    }

    /**
     * Call Local AI with retry.
     * Attempts up to 2 times with backoff.
     */
    private String callLocalAiWithRetry(String prompt, RoadmapGenerationTelemetry telemetry, String traceId) {
        if (localAiGateway == null || !localAiGateway.isAvailable()) {
            return null;
        }
        int maxAttempts = 2;
        int backoffMs = 5_000;
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            log.info("🔄 [trace={}] Local AI attempt {}/{}", traceId, attempt, maxAttempts);
            try {
                if (telemetry != null) telemetry.recordModelAttempt("local");
                String rawResponse = localAiGateway.call("", prompt);
                String json = extractJsonFromResponse(rawResponse);
                
                if (isJsonLikelyTruncated(json)) {
                    log.warn("⚠️ [trace={}] Local AI response appears truncated ({} chars). Retrying...", traceId, json.length());
                    if (attempt < maxAttempts) sleepRetryBackoff(backoffMs);
                    continue;
                }
                
                log.info("✅ [trace={}] Local AI succeeded (attempt {}/{}), {} chars", traceId, attempt, maxAttempts, json.length());
                if (telemetry != null) telemetry.recordPayloadLength(rawResponse, json);
                return json;
                
            } catch (LocalAiGateway.LocalAiQueueFullException qfe) {
                log.warn("⚠️ [trace={}] Local AI queue full: {}", traceId, qfe.getMessage());
                if (attempt < maxAttempts) sleepRetryBackoff(backoffMs);
            } catch (Exception e) {
                log.warn("⚠️ [trace={}] Local AI attempt {}/{} failed (type={}, status={}): {}", 
                        traceId, attempt, maxAttempts, classifyAiFailure(e), extractHttpStatus(e), safeMessage(e));
                if (attempt < maxAttempts) sleepRetryBackoff(backoffMs);
            }
        }
        
        log.warn("⚠️ [trace={}] Local AI prompt failed after {} attempts. Falling back.", traceId, maxAttempts);
        return null;
    }

    /**
     * Call Mistral as primary tier with retry.
     * Attempts up to 2 times with 30s backoff (respects 2 RPM free-tier limit).
     * Retries on: API failure OR truncated JSON detection.
     * Throws to caller if all retries fail (caller handles fallback to Gemini).
     */
    private String callMistralWithRetry(
            GenerateRoadmapRequest request,
            RoadmapGenerationTelemetry telemetry,
            String traceId) {

        int maxAttempts = 2;
        int backoffMs = 30_000; // 30 seconds — respects 2 RPM free-tier limit

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            log.info("🔄 [trace={}] Mistral attempt {}/{}", traceId, attempt, maxAttempts);
            try {
                String json = callMistralRoadmapWithPrimaryPrompt(request, telemetry);

                // Check if response is likely truncated (unbalanced brackets)
                if (isJsonLikelyTruncated(json)) {
                    log.warn("⚠️ [trace={}] Mistral response appears truncated "
                            + "(unbalanced brackets, {} chars). Retrying...",
                            traceId, json.length());
                    if (attempt < maxAttempts) {
                        sleepRetryBackoff(backoffMs);
                    }
                    continue;
                }

                log.info("✅ [trace={}] Mistral succeeded (attempt {}/{}), {} chars",
                        traceId, attempt, maxAttempts, json.length());
                return json;

            } catch (Exception e) {
                log.warn("⚠️ [trace={}] Mistral attempt {}/{} failed "
                        + "(type={}, status={}): {}",
                        traceId, attempt, maxAttempts,
                        classifyAiFailure(e), extractHttpStatus(e), safeMessage(e));
                if (attempt < maxAttempts) {
                    sleepRetryBackoff(backoffMs);
                }
            }
        }

        // All attempts exhausted — throw to trigger compact fallback in caller
        throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                "Mistral prompt failed after " + maxAttempts + " attempts. "
                        + "Falling back to compact prompt.");
    }

    /**
     * Call Gemini API directly via HTTP REST and return raw text response
     */
    private String callGeminiDirectly(String prompt, String modelName) {
        return callGeminiDirectly(prompt, modelName, null);
    }

    private String callGeminiDirectly(String prompt, String modelName, RoadmapGenerationTelemetry telemetry) {
        String traceId = currentTraceId();
        int maxRetries = AI_TRANSIENT_MAX_RETRIES;
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (telemetry != null) {
                telemetry.recordModelAttempt("gemini");
            }
            long startedAt = System.nanoTime();

            try {
                // 1. Configure RestClient with 1-hour timeout
                SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                int connectTimeoutMs = 60 * 1000;
                int readTimeoutMs = 3600 * 1000;
                requestFactory.setConnectTimeout(connectTimeoutMs); // 60s connect
                requestFactory.setReadTimeout(readTimeoutMs); // 1 hour read

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
                        // .responseMimeType("application/json") // Don't force JSON here to support
                        // validation prompt
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
                        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
                        String finishReason = candidate.getFinishReason();
                        boolean closesLikeJson = closesLikeJson(rawText);

                        // 📊 DEBUG: Gemini token usage via REST response (not in DTO, parse from raw JSON)
                        int promptTokens = 0, completionTokens = 0, totalTokens = 0;
                        String rawBody = null;
                        try {
                            // Re-call to get raw response for token usage (Gemini puts usageMetadata at response root)
                            // Actually, we already have response - let's check if there's usage data
                            // The GeminiDTO doesn't include usageMetadata, but we can infer from rawText length
                            // For now, log what we have from the candidate
                        } catch (Exception tokenEx) {
                            // ignore
                        }

                        log.info("✅ [trace={}] Gemini responded attempt {}/{} | elapsed={}ms | chars={} | finishReason={} | closesLikeJson={}",
                            traceId,
                            attempt + 1,
                            maxRetries + 1,
                            elapsedMs,
                            rawText.length(),
                            finishReason != null ? finishReason : "null",
                            closesLikeJson);

                        // ⚠️ DETECT: Max tokens truncation for Gemini
                        if ("MAX_TOKENS".equals(finishReason) || "MAX_OUTPUT_TOKENS".equals(finishReason)) {
                            if (!closesLikeJson) {
                                log.warn("⚠️ [trace={}] 🚨 GEMINI TRUNCATION DETECTED! finishReason={} but JSON unclosed. "
                                        + "LIKELY CAUSE: maxOutputTokens(30000) too low for this prompt ({}+ chars). "
                                        + "Consider increasing maxOutputTokens or reducing prompt/output schema.",
                                    traceId, finishReason);
                            }
                        }

                        if (!closesLikeJson) {
                            log.warn("⚠️ [trace={}] Gemini payload does not end with JSON closer. tail='{}'",
                                    traceId, previewTail(rawText, 200));
                        }

                        return rawText;
                    }
                }
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Empty response from Gemini API");
            } catch (Exception e) {
                lastException = e;
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
                String failureType = classifyAiFailure(e);
                int status = extractHttpStatus(e);
                boolean retryable = isRetryableAiFailure(e);

                if (attempt < maxRetries && retryable) {
                    long waitMs = computeRetryBackoffMs(attempt);
                    log.warn(
                        "⚠️ [trace={}] Gemini attempt {}/{} failed (type={}, status={}, elapsedMs={}): {} — retrying in {}ms",
                        traceId,
                        attempt + 1,
                        maxRetries + 1,
                        failureType,
                        status,
                        elapsedMs,
                        safeMessage(e),
                        waitMs);
                    sleepRetryBackoff(waitMs);
                    continue;
                }

                if (e instanceof HttpStatusCodeException hsce) {
                    String bodyPreview = hsce.getResponseBodyAsString();
                    if (bodyPreview != null && bodyPreview.length() > 300) {
                        bodyPreview = bodyPreview.substring(0, 300) + "...";
                    }
                    log.error("❌ [trace={}] Gemini HTTP error on attempt {}/{} (status={}, model={}, elapsedMs={}): bodyPreview={}",
                            traceId,
                            attempt + 1,
                            maxRetries + 1,
                            status,
                            modelName,
                            elapsedMs,
                            bodyPreview);
                } else {
                    log.error("❌ [trace={}] Failed to call Gemini API on attempt {}/{} (type={}, status={}, elapsedMs={}): {}",
                            traceId,
                            attempt + 1,
                            maxRetries + 1,
                            failureType,
                            status,
                            elapsedMs,
                            safeMessage(e));
                }

                throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "AI generation failed (Gemini type=" + failureType + ", status=" + status + "): "
                        + safeMessage(e));
            }
        }

        throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                "AI generation failed (Gemini retry exhausted): "
                        + (lastException != null ? safeMessage(lastException) : "unknown"));
    }

    /**
     * Extract JSON from AI response, handling markdown code blocks
     */
    private String extractJsonFromResponse(String response) {
        String text = response.trim();
        boolean hadJsonCodeFence = text.contains("```json");
        boolean hadGenericCodeFence = !hadJsonCodeFence && text.contains("```");

        // Extract JSON from markdown code blocks if present
        if (hadJsonCodeFence) {
            int startIndex = text.indexOf("```json") + 7;
            int endIndex = text.indexOf("```", startIndex);
            if (endIndex > startIndex) {
                text = text.substring(startIndex, endIndex);
            }
        } else if (hadGenericCodeFence) {
            int startIndex = text.indexOf("```") + 3;
            int endIndex = text.indexOf("```", startIndex);
            if (endIndex > startIndex) {
                text = text.substring(startIndex, endIndex);
            }
        }

        String cleanedText = text.trim();
        if (log.isDebugEnabled()) {
            log.debug(
                    "🔎 [trace={}] extractJsonFromResponse: rawChars={}, cleanedChars={}, hadJsonFence={}, hadFence={}, closesLikeJson={}",
                    currentTraceId(),
                    response != null ? response.length() : 0,
                    cleanedText.length(),
                    hadJsonCodeFence,
                    hadGenericCodeFence,
                    closesLikeJson(cleanedText));
        }
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
        String userContext = String.format(
                """

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
                        OUTPUT: Chỉ trả về MỘT JSON object hợp lệ. KHÔNG giải thích, KHÔNG chat, KHÔNG code fences (```). CHỉ dùng **inline** Markdown trong description.

                        CRITICAL BRACKET RULES:
                        - Mỗi [ phải có ] đóng đúng vị trí.
                        - Mỗi { phải có } đóng đúng vị trí.
                        - Tất cả arrays (children, prerequisites, learning_objectives, tips, dependencies, next_steps, structure) phải kết thúc bằng ].
                        - Nếu phải dừng giữa chừng: đóng node hiện tại (}), đóng array hiện tại (]), rồi dừng. KHÔNG bỏ dở mid-field.
                        - TRƯỚC KHI dừng output, luôn verify: tất cả brackets đã balanced chưa.
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
        String promptPrefill = buildRoadmapPromptPrefill(request);
        if (!promptPrefill.isBlank()) {
            finalPrompt = promptPrefill + "\n\n" + finalPrompt;
        }
        if (request.getAiAgentMode() != null
                && "deep-research-pro-preview-12-2025".equalsIgnoreCase(request.getAiAgentMode())) {
            finalPrompt = finalPrompt
                    + "\nMODE: Deep Research Pro Preview 12/2025 — Yêu cầu tư duy nghiên cứu sâu, kiểm chứng nguồn, ưu tiên số liệu thực tế 2026, trình bày có cấu trúc và trả về JSON theo yêu cầu.";
        }

        return finalPrompt;
    }

    private String buildRoadmapPromptPrefill(GenerateRoadmapRequest request) {
        List<String> sections = new ArrayList<>();

        String ragSection = buildRoadmapRagSection(request);
        if (!ragSection.isBlank()) {
            sections.add(ragSection);
        }

        String courseShortlistSection = buildRoadmapCourseShortlistSection(request);
        if (!courseShortlistSection.isBlank()) {
            sections.add(courseShortlistSection);
        }

        sections.add("""
                ## QUY TẮC TRÍCH DẪN NGUỒN
                - Khi dùng tài liệu tham khảo để gợi ý học liệu, ưu tiên đặt trích dẫn trong `suggested_resources`.
                - KHÔNG chèn trích dẫn nguồn vào `description` trừ khi thật sự bất khả kháng.
                - Nếu một node dựa trên tài liệu SkillVerse, hãy thể hiện nguồn ngắn gọn trong `suggested_resources`, ví dụ: \"SkillVerse Knowledge: <tên tài liệu/chủ đề>\".
                - Ưu tiên gợi ý khóa học có sẵn trong hệ thống SkillVerse trước nguồn bên ngoài.
                """);

        return String.join("\n\n", sections).trim();
    }

    private String buildRoadmapRagSection(GenerateRoadmapRequest request) {
        if (aiRagGateway == null) {
            return "";
        }

        String ragQuery = ((request.getSkillName() != null ? request.getSkillName() + " " : "")
                + (request.getGoal() != null ? request.getGoal() : "")).trim();
        if (ragQuery.isBlank()) {
            return "";
        }

        String skillSlug = resolveRoadmapSkillSlug(request);

        // Tier 1: if skillSlug + canonical industry/level available, try scoped query first
        if (skillSlug != null) {
            String canonicalIndustry = resolveCanonicalIndustry(request.getIndustry());
            String canonicalLevel = resolveCanonicalLevel(request.getCurrentLevel());
            boolean hasIndustry = canonicalIndustry != null;
            boolean hasLevel = canonicalLevel != null;

            if (hasIndustry || hasLevel) {
                Map<String, String> scopedFilters = new LinkedHashMap<>();
                scopedFilters.put("doc_type", "skill");
                scopedFilters.put("domain", AiKnowledgeSlugUtils.toRoadmapDomain(skillSlug));
                if (hasIndustry) scopedFilters.put("industry", canonicalIndustry);
                if (hasLevel) scopedFilters.put("level", canonicalLevel);

                if (aiRagGateway != null) {
                    String ragContext = aiRagGateway.fetchRagContext(ragQuery, scopedFilters, 5);
                    if (!ragContext.isBlank()) {
                        return "## Tài liệu Skill tham khảo từ SkillVerse\n"
                                + ragContext
                                + "\n\nHãy tạo roadmap bám sát tài liệu trên nếu phù hợp với mục tiêu người học.";
                    }
                }
            }

            // Tier 2: skill-domain only (no industry/level)
            Map<String, String> domainFilters = new LinkedHashMap<>();
            domainFilters.put("doc_type", "skill");
            domainFilters.put("domain", AiKnowledgeSlugUtils.toRoadmapDomain(skillSlug));

            if (aiRagGateway != null) {
                String ragContext = aiRagGateway.fetchRagContext(ragQuery, domainFilters, 5);
                if (!ragContext.isBlank()) {
                    return "## Tài liệu Skill tham khảo từ SkillVerse\n"
                            + ragContext
                            + "\n\nHãy tạo roadmap bám sát tài liệu trên nếu phù hợp với mục tiêu người học.";
                }
            }

            return "";
        }

        // Tier 3: skillSlug unresolvable — broad doc_type=skill only
        if (aiRagGateway != null) {
            String ragContext = aiRagGateway.fetchRagContext(ragQuery, Map.of("doc_type", "skill"), 5);
            if (!ragContext.isBlank()) {
                return "## Tài liệu Skill tham khảo từ SkillVerse\n"
                        + ragContext
                        + "\n\nHãy tạo roadmap bám sát tài liệu trên nếu phù hợp với mục tiêu người học.";
            }
        }

        return "";
    }

    private String buildRoadmapCourseShortlistSection(GenerateRoadmapRequest request) {
        String topic = ((request.getSkillName() != null ? request.getSkillName() + " " : "")
                + (request.getTarget() != null ? request.getTarget() + " " : "")
                + (request.getGoal() != null ? request.getGoal() : "")).trim();
        if (topic.isBlank()) {
            return "";
        }

        int limit = request.getRoadmapMode() == GenerateRoadmapRequest.RoadmapMode.SKILL_BASED ? 5 : 15;
        List<CourseCatalogEntry> candidates = aiCourseCatalogService.preSelectCourses(topic, limit);
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }

        String shortlist = candidates.stream()
                .limit(limit)
                .map(course -> String.format("- Course ID %d: %s | category=%s | level=%s | modules=%d",
                        course.getId(),
                        nullSafe(course.getTitle()),
                        nullSafe(course.getCategory()),
                        nullSafe(course.getLevel()),
                        course.getModuleCount()))
                .collect(Collectors.joining("\n"));

        return "## Khóa học SkillVerse được pre-select\n"
                + shortlist
                + "\n\nCRITICAL RULE: TUYỆT ĐỐI KHÔNG ĐƯỢC làm sai lệch mục tiêu gốc của người học dựa vào danh sách khóa học này (Ví dụ: Nếu user muốn học 'Java', KHÔNG ĐƯỢC biến lộ trình thành 'Spring Boot' chỉ vì có khóa học Spring Boot). CHỈ dùng danh sách này để điền vào `suggested_resources`. Mục tiêu của roadmap phải bám sát 100% yêu cầu gốc.";
    }

    private String resolveRoadmapSkillSlug(GenerateRoadmapRequest request) {
        if (request.getSkillName() == null || request.getSkillName().isBlank()) {
            return null;
        }

        String skillSlug = AiKnowledgeSlugUtils.toRoadmapSkillSlug(request.getSkillName());
        if (skillSlug == null || skillSlug.isBlank()) {
            return null;
        }

        return skillSlug.matches("[a-z0-9]+(?:-[a-z0-9]+)*") ? skillSlug : null;
    }

    private static final Set<String> CANONICAL_INDUSTRIES = Set.of(
            "IT", "Business", "Finance", "Marketing", "Design",
            "Education", "Healthcare", "Logistics", "Legal",
            "Public Administration", "Agriculture", "Service Hospitality", "Arts Entertainment");

    private static final Set<String> CANONICAL_LEVELS = Set.of("beginner", "intermediate", "advanced");

    /**
     * Returns the canonical industry value if the input matches any canonical industry
     * (case-insensitive). Returns null if no match — prevents non-canonical values from
     * being sent as RAG exact-match filters.
     */
    private String resolveCanonicalIndustry(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        for (String canonical : CANONICAL_INDUSTRIES) {
            if (canonical.equalsIgnoreCase(trimmed)) return canonical;
        }
        return null;
    }

    /**
     * Returns the canonical level value if the input matches any canonical level
     * (case-insensitive). Returns null if no match.
     */
    private String resolveCanonicalLevel(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        return CANONICAL_LEVELS.contains(lower) ? lower : null;
    }

        private String callMistralRoadmapFallback(GenerateRoadmapRequest request) {
            return callMistralRoadmapFallback(request, null);
        }

        private String callMistralRoadmapFallback(GenerateRoadmapRequest request, RoadmapGenerationTelemetry telemetry) {
            String compactPrompt = buildCompactFallbackPrompt(request)
                    + "\n\nCRITICAL: Trả lời bằng TIẾNG VIỆT. Chỉ trả về MỘT JSON object hợp lệ, bắt đầu bằng { và kết thúc bằng }.";
            if (telemetry != null) {
                telemetry.recordPromptLength(compactPrompt.length());
            }
            String fallbackResponse = callMistralAPI(compactPrompt, telemetry, "mistral-compact");
            String extracted = extractJsonFromResponse(fallbackResponse);
            if (telemetry != null) {
                telemetry.recordPayloadLength(fallbackResponse, extracted);
            }
            logJsonCheckpoint("mistral-compact/raw", fallbackResponse);
            logJsonCheckpoint("mistral-compact/extracted", extracted);
            return extracted;
        }

        private boolean isTruncatedJsonParseFailure(ApiException ex) {
                if (ex == null || ex.getErrorCode() != ErrorCode.BAD_REQUEST) {
                        return false;
                }
                String message = safeMessage(ex).toLowerCase(Locale.ROOT);
            boolean matched = message.contains("unexpected end-of-input")
                                || message.contains("unexpected end of input")
                                || message.contains("expected close marker")
                                || message.contains("end-of-input");
            if (matched) {
                log.warn("⚠️ [trace={}] Parse failure classified as truncated/incomplete JSON: {}",
                        currentTraceId(),
                        previewHead(message, 220));
            }
            return matched;
        }

        private String buildCompactFallbackPrompt(GenerateRoadmapRequest request) {
                String mode = request.getRoadmapMode() != null
                                ? request.getRoadmapMode().name()
                                : "CAREER_BASED";
                String constraintsInline = buildConstraintsBlock(request).replace("\n", "; ").trim();

                return String.format(
                                """
                                                SYSTEM:
                                                Bạn là AI Roadmap Architect.
                                                Mục tiêu: trả về JSON NGẮN GỌN và HỢP LỆ để backend parse được ngay.
                                                Không giải thích, không ký tự ngoài JSON. Chỉ dùng **inline** Markdown trong description (được: **bold**, *italic*, `code`).

                                                YÊU CẦU:
                                                1) Số node roadmap: chính xác 11-14 node, gồm đúng 8 MAIN node và tối thiểu 3 SIDE node khi có nội dung bổ trợ phù hợp.
                                                2) Mỗi node bắt buộc có: id, title, type, estimated_time_minutes, parent_id, children.
                                                3) type chỉ nhận MAIN hoặc SIDE.
                                                4) estimated_time_minutes phải là số nguyên > 0.
                                                5) children là array id con (có thể [] nếu node lá).
                                                6) Language: tiếng Việt có dấu.
                                                7) Viết mô tả rõ ràng (2-4 câu ngắn), nêu bối cảnh + việc cần làm + kết quả mong đợi; mỗi list tối đa 3 items.
                                                8) Không tạo field ngoài schema dưới đây.
                                                10) description: cho phép **inline** Markdown (được: **bold**, *italic*, `code`). CẤM: ```, >, #, -, newlines trong chuỗi. Tối đa 240 ký tự.
                                                11) `next_steps.jobs`: BẮT BUỘC 2-3 vị trí công việc thực tế sau khi hoàn thành lộ trình.
                                                12) `next_steps.next_skills`: BẮT BUỘC 2-3 kỹ năng nên học tiếp theo sau lộ trình này.
                                                9) CRITICAL BRACKET RULE: Trước khi dừng output, verify tất cả [ có ] đóng và { có } đóng. Nếu phải dừng giữa chừng: đóng node (}), đóng array (]), rồi dừng. KHÔNG bỏ dở mid-field.

                                                BRANCHING POLICY:
                                                - MAIN nodes form one required spine from first to last.
                                                - SIDE nodes are optional support attached to one MAIN parent.
                                                - Never use SIDE as prerequisite for MAIN.
                                                - Always create exactly 8 MAIN nodes.
                                                - Create at least 3 SIDE nodes when they can support, deepen, or practice a MAIN node.

                                                SCHEMA JSON:
                                                {
                                                    "roadmap_metadata": {
                                                        "title": "",
                                                        "original_goal": "",
                                                        "validated_goal": "",
                                                        "duration": "",
                                                        "desired_duration": "",
                                                        "experience_level": "",
                                                        "learning_style": "",
                                                        "difficulty_level": "easy",
                                                        "roadmap_type": "",
                                                        "target": "",
                                                        "roadmap_mode": "",
                                                        "daily_time": "",
                                                        "current_level": "zero"
                                                    },
                                                    "overview": {
                                                        "purpose": "",
                                                        "audience": "",
                                                        "post_roadmap_state": ""
                                                    },
                                                    "structure": [],
                                                    "thinking_progression": ["Bắt đầu từ khái niệm cơ bản", "Xây dựng nền tảng lý thuyết", "Thực hành qua bài tập", "Tổng hợp qua dự án thực tế"],
                                                    "projects_evidence": [],
                                                    "next_steps": {"jobs": ["Vị trí công việc 1", "Vị trí công việc 2"], "next_skills": ["Kỹ năng tiếp theo 1", "Kỹ năng tiếp theo 2"]},
                                                    "learning_tips": [],
                                                    "skill_dependencies": [{"from": "", "to": ""}],
                                                    "roadmap": [
                                                        {
                                                            "id": "quest-1",
                                                            "title": "",
                                                            "description": "",
                                                            "estimated_time_minutes": 60,
                                                            "type": "MAIN",
                                                            "is_core": true,
                                                            "parent_id": null,
                                                            "difficulty": "easy",
                                                            "learning_objectives": [],
                                                            "key_concepts": [],
                                                            "practical_exercises": [],
                                                            "suggested_resources": [],
                                                            "success_criteria": [],
                                                            "skills": [{"skill_id": null, "skill_name": "Skill A", "canonical_key": null, "requirement_type": "REQUIRED"}, {"skill_id": null, "skill_name": "Skill B", "canonical_key": null, "requirement_type": "IMPORTANT"}],
                                                            "prerequisites": [],
                                                            "children": [],
                                                            "order_index": 1,
                                                            "importance_score": 0.85,
                                                            "confidence_score": 0.90,
                                                            "reason": "Node nền tảng bắt buộc, mở khóa toàn bộ các bước tiếp theo trong lộ trình.",
                                                            "evidence": ["Là node MAIN đầu tiên trong lộ trình học chính", "Skill gap: người học chưa có nền tảng theo đánh giá đầu vào"]
                                                        }
                                                    ],
                                                    "roadmap_statistics": {
                                                        "total_nodes": 11,
                                                        "main_nodes": 8,
                                                        "side_nodes": 3,
                                                        "total_estimated_hours": 40.0,
                                                        "difficulty_distribution": {"easy": 4, "medium": 4, "hard": 2}
                                                    }
                                                }

                                                INPUT:
                                                roadmap_mode=%s
                                                roadmap_type=%s
                                                goal=%s
                                                target=%s
                                                duration=%s
                                                desired_duration=%s
                                                daily_time=%s
                                                experience=%s
                                                learning_style=%s
                                                priority=%s
                                                skill_name=%s
                                                target_role=%s
                                                timeline_to_work=%s
                                                constraints=%s
                                                """,
                                mode,
                                nullSafe(request.getRoadmapType()),
                                nullSafe(request.getGoal()),
                                nullSafe(request.getTarget()),
                                nullSafe(request.getDuration()),
                                nullSafe(request.getDesiredDuration()),
                                nullSafe(request.getDailyTime()),
                                nullSafe(request.getExperience()),
                                nullSafe(request.getLearningStyle() != null ? request.getLearningStyle() : request.getStyle()),
                                nullSafe(request.getPriority()),
                                nullSafe(request.getSkillName()),
                                nullSafe(request.getTargetRole()),
                                nullSafe(request.getTimelineToWork()),
                                constraintsInline);
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
                - Valid roadmap must include: Overview, Skill dependencies, Roadmap nodes, Statistics, Learning tips
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
        int currentYear = Year.now().getValue();
        String yearContext = "CURRENT_YEAR: " + currentYear + "\n";
        String adaptiveGraphGuidance = buildAdaptiveGraphGuidance(request);
        String branchingPolicyGuidance = buildBranchingPolicyGuidance(request);
        String base = expertPersona + "\n" + ruleBlock + "\n" + domainBlock + "\n" + expertPackBlock + "\n"
                + yearContext
                + """
                        # AI ROADMAP ARCHITECT - SYSTEM PROMPT V2

                        ## VAI TRÒ & SỨ MỆNH
                        Bạn là AI Roadmap Architect - Chuyên gia thiết kế lộ trình học tập trong năm (xem CURRENT_YEAR):
                        - Phát hiện 99%% ý định học tập từ văn bản tự nhiên
                        - Xác thực thông tin với độ chính xác cao
                        - Tạo lộ trình dạng graph/tree có đường chính rõ và các nhánh mở rộng thực sự
                        - parent_id/children biểu diễn cấu trúc cây học tập; prerequisites chỉ biểu diễn unlock/dependency
                        - Sử dụng công nghệ, framework, công cụ MỚI NHẤT của năm (xem CURRENT_YEAR)
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

                        CRITICAL: Trả về JSON hợp lệ. KHÔNG giải thích, KHÔNG chat, CHỈ JSON. Chỉ dùng **inline** Markdown trong description (được: **bold**, *italic*, `code`; CẤM: ```, >, #, -, multiline).

                        **Cấu trúc bắt buộc — TẤT CẢ fields phải được fill đầy đủ:**
                        - `roadmap_metadata`: title, original_goal, difficulty_level, roadmap_mode, desired_duration, daily_time, **current_level** (BẮT BUỘC — zero/basic/intermediate/advanced)
                        - `overview`: purpose, audience, post_roadmap_state
                        - `structure`: array {phase_id, title, goal, skill_focus (array), timeframe, expected_output} — tối đa 4 phases
                        - `thinking_progression`: array string (2-4 steps) — **BẮT BUỘC phải có nội dung thực tế**, tư duy/tiến trình học từ cơ bản → nâng cao, mỗi bước tối đa 100 ký tự
                        - `projects_evidence`: array {phase_id, project, objective, skills_proven, kpi} — **BẮT BUỘC 1-3 dự án**, mỗi project tối đa 120 ký tự
                        - `next_steps`: jobs (2-3 string), next_skills (2-3 string) — **BẮT BUỘC phải có nội dung thực tế**
                        - `learning_tips`: array string (2-3 tips)
                        - `skill_dependencies`: array {from, to} — tối thiểu 1 entry
                        - `difficulty_level` phải là: easy | medium | hard (KHÔNG phải beginner/intermediate/advanced)
                        - `current_level` phải là: zero | basic | intermediate | advanced
                        - `roadmap`: array nodes, mỗi node BẮT BUỘC có đủ các fields sau:
                          1. `id` (string)
                          2. `title` (string, 40-80 chars, bắt đầu bằng động từ)
                          3. `description` (**inline** Markdown, tối đa 500 ký tự, được dùng: **bold**, *italic*, `code`; CẤM: ```, >, #, -, multiline). BẮT BUỘC viết chi tiết 3-5 câu. Nêu rõ: **những gì sẽ học được** + hành động thực hành chính + kết quả đầu ra cụ thể. KHÔNG giải thích tại sao quan trọng (đã có importance_score/reason).
                          4. `estimated_time_minutes` (int, > 0)
                          5. `type` (MAIN hoặc SIDE)
                          6. `parent_id` (string id HOẶC null cho root node)
                          7. `children` (array string id, LUÔN LÀ array — dùng [] nếu node lá)
                          8. `difficulty` (easy | medium | hard)
                          9. `prerequisites` (array string id, LUÔN LÀ array — dùng [] nếu không có)
                          10. `learning_objectives` (array string 2-4 items — BẮT BUỘC, diễn giải chi tiết kỹ năng đạt được, mỗi item tối đa 120 ký tự)
                          11. `key_concepts` (array string 2-5 items — BẮT BUỘC cho MAIN node, tối thiểu 3 items; dùng [] chỉ với SIDE node)
                          12. `practical_exercises` (array string 1-3 items — BẮT BUỘC cho mọi node, tối thiểu 2 items mô tả bài tập/task cụ thể để thực hành kiến thức của node này)
                          13. `success_criteria` (array string 1-3 items — BẮT BUỘC cho MAIN node, tối thiểu 2 items mô tả tiêu chí hoàn thành cụ thể có thể đo lường được)
                          14. `suggested_resources` (array string 1-3 items — NÊN có, dùng [] nếu không có gợi ý)
                          15. `order_index` (int, 1-based global position of this node in the roadmap)
                          16. `importance_score` (float 0.0–1.0 — MAIN nodes >= 0.7, SIDE nodes 0.3–0.6; how critical relative to goal)
                          17. `confidence_score` (float 0.0–1.0 — AI confidence this node belongs in the roadmap)
                          18. `reason` (string, 1 câu: tại sao node này quan trọng với người học này cụ thể)
                          19. `evidence` (array string 1-3 items — tín hiệu cụ thể từ đầu vào: skill gap, điểm test, nhu cầu thị trường)
                        - `skills` inside each roadmap node is REQUIRED: array object 2-5 items for normal nodes, max 7 for large nodes: {skill_id, skill_name, canonical_key, requirement_type}
                        - Module-centric rule: a roadmap node is a practical learning module, not a single skill. Do not create a checklist where `1 skill = 1 node`; first group related skills into realistic learning modules.
                        - `roadmap_statistics`: total_nodes, main_nodes, side_nodes, total_estimated_hours
                        - `learning_tips`: array string 2-3 tips

                        **NGUYÊN TẮC QUAN TRỌNG:**
                        - description: cho phép **inline** Markdown trong JSON. BẮT BUỘC viết dài và chi tiết (tối thiểu 3 câu, tối đa 500 ký tự), tập trung vào nội dung học + hành động + output cụ thể. KHÔNG lặp lại reason/importance_score.
                        - key_concepts: BẮT BUỘC cho MAIN node (tối thiểu 3 items). Mỗi item là 1 khái niệm/kỹ thuật cốt lõi, diễn đạt chi tiết (tối đa 60 ký tự).
                        - practical_exercises: BẮT BUỘC cho MỌI node (tối thiểu 2 items). Mỗi item PHẢI mô tả 1 bài tập/task cụ thể và có độ khó thực tế (VD: 'Xây dựng API quản lý user có phân quyền và validate data'), tối đa 120 ký tự.
                        - success_criteria: BẮT BUỘC cho MAIN node (tối thiểu 2 items). Mỗi item là 1 tiêu chí có thể đo lường được, tối đa 120 ký tự.
                        - suggested_resources: KHÔNG BẮT BUỘC. Mỗi array tối đa 3 items, ưu tiên lấy từ danh sách khóa học pre-select.
                        - thinking_progression: 2-4 bước tư duy, mỗi bước tối đa 100 ký tự.
                        - projects_evidence: 1-3 dự án, mỗi project tối đa 120 ký tự.
                        - Tất cả arrays (children, prerequisites, learning_objectives, tips, key_concepts, practical_exercises, success_criteria, suggested_resources) phải là valid JSON array với ] đóng. KHÔNG bao giờ để unclosed bracket.
                        - Tất cả nodes phải có children (array), không được bỏ trống — dùng [] cho node lá.
                        - Tất cả nodes (trừ root) phải có prerequisites (array), không được null — dùng [] nếu không có.
                        - Nếu gần hết output: đóng array hiện tại bằng ], đóng object cuối bằng }, rồi DỪNG. KHÔNG bỏ dở giữa field.
                        - Luôn verify: mỗi [ phải có ] đóng, mỗi { phải có } đóng TRƯỚC KHI kết thúc output.
                        - importance_score và confidence_score: KHÔNG được gán cao nếu không có evidence. Căn cứ PHẢI từ ít nhất một trong: mục tiêu cụ thể của người học, skill gap từ đánh giá, vai trò prerequisite trong graph, context từ RAG/course, hoặc tính thực tiễn của dự án. confidence_score phải thấp hơn nếu evidence yếu hoặc chỉ có 1 tín hiệu.

                        ## QUY TẮC ROADMAP CONSTRUCTION

                        ### Node Structure (BẮT BUỘC TUÂN THỦ TÚY ĐỐI):
                        - Chính xác 11-14 nodes tổng cộng: ĐÚNG 8 MAIN nodes và tối thiểu 3 SIDE nodes.
                        - LƯU Ý CHO AI: Tuyệt đối không dừng lại ở 4 hay 5 node. Dù chủ đề ngắn đến đâu, BẠN PHẢI CHIA NHỎ CHỦ ĐỀ ra thành ĐÚNG 8 bước MAIN nối tiếp nhau.
                        - Exactly 1 root MAIN node (no prerequisites).
                        - Main path EXACTLY 8 MAIN nodes for every roadmap. NẾU BẠN CHỈ TẠO 4-5 MAIN NODES LÀ BẠN SẼ BỊ LỖI NGHIÊM TRỌNG.
                        - MỖI node bắt buộc có estimated_time_minutes là số nguyên > 0 (không dùng 0).

                        ### Node Types by Experience:
                        - Mới bắt đầu: 75%% MAIN, 25%% SIDE (difficulty: 60%% easy, 30%% medium, 10%% hard)
                        - Biết một ít: 65%% MAIN, 35%% SIDE (difficulty: 30%% easy, 50%% medium, 20%% hard)
                        - Trung cấp: 55%% MAIN, 45%% SIDE (difficulty: 20%% medium, 60%% hard, 20%% expert)
                        - Nâng cao: 45%% MAIN, 55%% SIDE (difficulty: 10%% hard, 70%% expert, 20%% research)

                        ### Time Allocation:
                        - 2 tuần = 1680 phút | 1 tháng = 3600 phút | 3 tháng = 10800 phút | 6 tháng = 21600 phút | 1 năm = 43200 phút
                        - Tổng thời gian nodes ≈ 80-100%% total (20%% buffer)
                        - Nếu thiếu dữ liệu, vẫn phải ước lượng tối thiểu 30 phút cho node nhỏ và tăng theo độ khó/khối lượng bài tập

                        ### Time Benchmarks (phút, beginner基准):
                        - Setup: 30-60 | Lý thuyết: 30-90 | Cú pháp: 45-90 | Thực hành: 60-120 | Project: 120-240
                        - Scale: difficulty easy×1.0, medium×1.5, hard×2.0 | experience zero/basic×1.0, intermediate×0.8, advanced×0.6

                        ### Phase/Structure Time Distribution:
                        - Phase 1 (Nền tảng): ~25%% tổng thời gian
                        - Phase 2 (Cốt lõi): ~40%% tổng thời gian
                        - Phase 3 (Thực hành): ~25%% tổng thời gian
                        - Phase 4 (Dự án/Kiểm tra): ~10%% tổng thời gian

                        ### Graph Integrity (ENFORCED — AI PHẢI tuân theo):
                        - parent_id là CHA trong cây, KHÔNG được suy ra thay cho prerequisites
                        - MỌI node trừ ROOT đầu tiên PHẢI có parent_id hợp lệ
                        - MỌI node (trừ node cuối cùng) PHẢI có children (không bắt buộc phải là array rỗng — nếu node có child tiếp theo, phải liệt kê trong children)
                        - children PHẢI khớp với parent_id: nếu A là parent_id của B thì children của A phải chứa B
                        - Một node chỉ có tối đa 1 parent_id
                        - Mọi node (trừ ROOT) PHẢI có prerequisites
                        - Mọi ID trong prerequisites/children PHẢI tồn tại
                        - KHÔNG circular dependencies
                        - KHÔNG orphan nodes
                        - KHÔNG biến toàn bộ roadmap thành một chuỗi tuyến tính; phải có nhánh SIDE/Main mở rộng thật khi phù hợp
                        - Nếu có structure/phase, roadmap nodes phải phân bổ hợp lý theo các phase đó
                        - CRITICAL: children và parent_id phải được SET cho TẤT CẢ nodes. Array rỗng [] chỉ dùng cho node KHÔNG CÓ child nào. KHÔNG ĐƯỢC bỏ trống hoặc null.

                        ## CONTENT QUALITY STANDARDS
                        - Title: bắt đầu bằng động từ hành động, chứa công nghệ cụ thể, 40-80 ký tự, Tiếng Việt có dấu. Ví dụ: "Xây dựng API RESTful với Spring Boot"
                        - Learning Objectives: format "[Động từ] được [Kết quả cụ thể]", ví dụ: "Tạo được form đăng ký có validation"
                        - Resources: phải là tài liệu CÓ THẬT và PHỔ BIẾN (VD: MDN, FreeCodeCamp, Offical Docs)

                        ## ADAPTATION BY PRIORITY
                        - "Nhanh đi làm": 11-14 nodes, đúng 8 MAIN và >=3 SIDE bổ trợ, easy/medium difficulty
                        - "Học sâu": 11-14 nodes, đúng 8 MAIN và >=3 SIDE bổ trợ, medium/hard difficulty

                        ## ADAPTATION BY LEARNING STYLE
                        - "Theo dự án": mỗi node = 1 feature/project, format "Xây dựng [feature X] cho project..."
                        - "Lý thuyết": concept-driven, format "Hiểu về [concept X]..."
                        - "Video": video-first, format "Xem video [X] từ [platform]..."
                        - "Thực hành": exercise-heavy, format "Hoàn thành [N] bài tập về [topic]..."
                        - "Cân bằng": 50%% theory + 50%% practice, alternating pattern

                        """
                + "\n"
                + adaptiveGraphGuidance
                + "\n"
                + branchingPolicyGuidance
                + "\n";
        return base;
    }

    private String buildBranchingPolicyGuidance(GenerateRoadmapRequest request) {
        String desiredDepth = nullSafe(request.getDesiredDepth()).trim().toUpperCase(Locale.ROOT);
        if (desiredDepth.isBlank()) {
            desiredDepth = "SOLID";
        }
        return String.format("""
                ## ROADMAP BRANCHING POLICY (ENFORCED)
                - Always create a clear MAIN spine: MAIN nodes are required steps from first to last.
                - Create exactly 8 MAIN nodes.
                - Create at least 3 SIDE nodes when they can support, deepen, or practice a MAIN node.
                - Every MAIN node after the first must depend on the previous MAIN node.
                - SIDE nodes are optional support only. Attach each SIDE node to exactly one MAIN parent.
                - Never put a SIDE node in the prerequisites of a MAIN node.
                - Create SIDE nodes only for extra practice, alternative tools, deeper theory, mini-project extensions, interview/job-ready bonuses, or foundation review.
                - Do not branch just to make the roadmap look complex.
                - BASIC depth should still include 3 concise SIDE nodes only when they directly support MAIN nodes.
                - SOLID depth should use 3-4 meaningful SIDE nodes when the goal has optional support topics.
                - ADVANCED depth may use 4-6 SIDE nodes for specialization, advanced practice, and job-ready evidence.
                - Current desiredDepth: %s.
                """, desiredDepth);
    }

    private String buildAdaptiveGraphGuidance(GenerateRoadmapRequest request) {
        String desiredDepth = nullSafe(request.getDesiredDepth()).trim().toUpperCase(Locale.ROOT);
        if (desiredDepth.isBlank()) {
            desiredDepth = "SOLID";
        }

        if ("BASIC".equals(desiredDepth)) {
            return """
                    ## ADAPTIVE BRANCHING (DESIRED_DEPTH=BASIC)
                    - Node count: exactly 8 MAIN and at least 3 concise SIDE nodes when supportive.
                    - Ưu tiên đường chính tuần tự, nhánh phụ tối thiểu và dễ theo dõi.
                    - Graph depth mục tiêu: tối đa 3 tầng.
                    - Fan-out trung bình mỗi node: 1-2 nhánh.
                    - Side nodes chỉ dùng để củng cố nền tảng, tránh mở rộng lan man.
                    """;
        }

        if ("ADVANCED".equals(desiredDepth)) {
            return """
                    ## ADAPTIVE BRANCHING (DESIRED_DEPTH=ADVANCED)
                    - Node count: exactly 8 MAIN and 4-6 SIDE nodes when supportive.
                    - Tăng số nhánh phụ có mục tiêu rõ ràng để mở rộng chuyên sâu.
                    - Graph depth mục tiêu: >= 4 tầng khi hợp lý.
                    - Fan-out trung bình mỗi node: 2-3 nhánh ở các node chính.
                    - Bổ sung nhánh dự án/thử thách nâng cao để phản ánh năng lực thực chiến.
                    """;
        }

        return """
                ## ADAPTIVE BRANCHING (DESIRED_DEPTH=SOLID)
                - Node count: exactly 8 MAIN and 3-4 SIDE nodes when supportive.
                - Cân bằng giữa đường chính và nhánh phụ để vừa chắc nền, vừa có mở rộng.
                - Graph depth mục tiêu: khoảng 4 tầng.
                - Fan-out trung bình mỗi node: 1-2 nhánh, ưu tiên nhánh có tác dụng rõ ràng.
                - Nhánh phụ nên hỗ trợ trực tiếp cho mục tiêu nghề nghiệp/kỹ năng chính.
                """;
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
        // Extract all numbers from the string
        String numMatch = s.replaceAll("[^0-9]", "");
        if (!numMatch.isEmpty()) {
            try {
                int num = Integer.parseInt(numMatch);
                if (s.contains("phút") || s.contains("min")) {
                    return Math.min(num, 480);
                }
                if (s.contains("giờ") || s.contains("hour")) {
                    return Math.min(num * 60, 480);
                }
                if (s.contains("ngày") || s.contains("day")) {
                    return Math.min(num * 480, 1440);
                }
                // If only a number is present (e.g., "1" or "2"), treat as hours
                if (numMatch.length() <= 2 && !s.contains("phút") && !s.contains("min")) {
                    // Plain number like "1" or "2" = hours
                    if (num <= 8) return Math.min(num * 60, 480);
                }
                // If number is large (e.g., "30" without unit), assume minutes
                return Math.min(num, 480);
            } catch (NumberFormatException e) {
                // Fall through to string-based detection
            }
        }
        // Fallback string checks
        if (s.contains("30"))
            return 30;
        if (s.contains("2"))
            return 120;
        if (s.contains("1"))
            return 60;
        return 60;
    }

    private int parseDesiredDurationDays(String desiredDuration) {
        if (desiredDuration == null)
            return 30;
        String s = desiredDuration.trim().toLowerCase(Locale.ROOT);
        String digits = s.replaceAll("[^0-9]", "");
        int n = digits.isEmpty() ? 1 : Integer.parseInt(digits);

        if (s.matches("^\\d+\\s*w$") || s.contains("tuần") || s.contains("week")) {
            return n * 7;
        }
        if (s.matches("^\\d+\\s*y$") || s.contains("năm") || s.contains("year")) {
            return n * 365;
        }
        if (s.matches("^\\d+\\s*m$") || s.contains("tháng") || s.contains("month")) {
            return n * 30;
        }

        // Default to months for numeric-only hints.
        return n * 30;
    }

    private void alignTimelineMetadataWithRequest(
            RoadmapResponse.RoadmapMetadata metadata,
            GenerateRoadmapRequest request) {
        if (metadata == null || request == null) {
            return;
        }

        String requestDesiredDuration = sanitizeDurationLabel(
                request.getDesiredDuration(),
                metadata.getDesiredDuration());
        metadata.setDesiredDuration(requestDesiredDuration);

        String normalizedDuration = sanitizeDurationLabel(
                metadata.getDuration(),
                requestDesiredDuration);
        metadata.setDuration(normalizedDuration);

        if (metadata.getDailyTime() == null || metadata.getDailyTime().isBlank()) {
            metadata.setDailyTime(request.getDailyTime());
        }
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
                "\nCONSTRAINTS:\navailable_minutes_per_day=%d\nplanned_days=%d\ntime_budget_minutes=%d\nmain_side_ratio=%s\nnode_count_rule=exactly_8_MAIN_and_at_least_3_SIDE_when_supportive\n",
                minutesPerDay, plannedDays, timeBudgetMinutes, ratio);
    }

    /**
     * Validate and parse enhanced roadmap JSON (Schema V2)
     * Parses: metadata, roadmap nodes, statistics, learning tips
     */
    private ParsedRoadmap validateAndParseRoadmapV2(String roadmapJson) {
        return validateAndParseRoadmapV2(roadmapJson, null);
    }

    private ParsedRoadmap validateAndParseRoadmapV2(String roadmapJson, RoadmapGenerationTelemetry telemetry) {
        String sanitized = null;
        try {
            if (telemetry != null) {
                telemetry.recordParseAttempt();
            }

            // 📊 DEBUG: Sanitization monitoring
            int rawLen = roadmapJson != null ? roadmapJson.length() : 0;
            sanitized = sanitizeJson(roadmapJson);
            int sanitizedLen = sanitized != null ? sanitized.length() : 0;
            int removed = rawLen - sanitizedLen;
            double removedPct = rawLen > 0 ? (removed * 100.0) / rawLen : 0.0;

            if (telemetry != null) {
                telemetry.recordSanitizedLength(sanitizedLen);
            }

            log.info("📝 [trace={}] Sanitization: raw={} chars → sanitized={} chars (removed {} chars, {:.1f}%)",
                currentTraceId(), rawLen, sanitizedLen, removed, removedPct);

            if (removedPct > 30) {
                log.warn("⚠️ [trace={}] 🚨 MASSIVE SANITIZATION ({:.1f}% removed)! "
                        + "Possible cause: Markdown text embedded in JSON, or model generated explanatory text after JSON. "
                        + "Original tail (300 chars): '{}'",
                    currentTraceId(), removedPct, previewTail(roadmapJson, 300));
            }

            logJsonCheckpoint("parse-v2/raw", roadmapJson);
            logJsonCheckpoint("parse-v2/sanitized", sanitized);
            try {
                objectMapper.getFactory()
                        .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature());
                objectMapper.getFactory()
                        .enable(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature());
                objectMapper.getFactory().enable(
                        JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature());
                objectMapper.getFactory()
                        .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature());
                objectMapper.getFactory().enable(
                        JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS.mappedFeature());
            } catch (Throwable t) {
                try {
                    objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
                    objectMapper.configure(JsonParser.Feature.ALLOW_TRAILING_COMMA, true);
                    objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES,
                            true);
                    objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
                    objectMapper.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS,
                            true);
                } catch (Throwable ignored) {
                }
            }

            JsonNode root = objectMapper.readTree(sanitized);
                log.debug("🧪 [trace={}] Raw roadmap JSON sections present: metadata={}, roadmap={}, overview={}, structure={}, thinking_progression={}, projects_evidence={}, next_steps={}, skill_dependencies={}",
                    currentTraceId(),
                    firstPresentNode(root, "roadmap_metadata", "roadmapMetadata") != null,
                    root.path("roadmap").isArray(),
                    firstPresentNode(root, "overview") != null,
                    firstPresentNode(root, "structure") != null && firstPresentNode(root, "structure").isArray(),
                    firstPresentNode(root, "thinking_progression", "thinkingProgression") != null && firstPresentNode(root, "thinking_progression", "thinkingProgression").isArray(),
                    firstPresentNode(root, "projects_evidence", "projectsEvidence") != null && firstPresentNode(root, "projects_evidence", "projectsEvidence").isArray(),
                    firstPresentNode(root, "next_steps", "nextSteps") != null && firstPresentNode(root, "next_steps", "nextSteps").isObject(),
                    firstPresentNode(root, "skill_dependencies", "skillDependencies") != null && firstPresentNode(root, "skill_dependencies", "skillDependencies").isArray());

            // Parse metadata
            JsonNode metadataNode = firstPresentNode(root, "roadmap_metadata", "roadmapMetadata");
            if (metadataNode == null || metadataNode.isMissingNode()) {
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
            List<String> graphWarnings = new ArrayList<>();
            RoadmapGraphCanonicalizer.Result canonicalGraph = RoadmapGraphCanonicalizer.canonicalize(nodes);
            nodes = canonicalGraph.nodes();
            if (canonicalGraph.warnings() != null) {
                graphWarnings.addAll(canonicalGraph.warnings());
            }

            // BUG-8 FIX: Post-canonicalization fallback — if NO edges exist at all,
            // infer a linear chain from sequential order so the roadmap is never a "floating" graph
            long edgesCount = nodes.stream()
                    .filter(n -> n.getChildren() != null && !n.getChildren().isEmpty())
                    .count();
            if (edgesCount == 0 && nodes.size() > 1) {
                log.warn("[RoadmapGen] BUG-8: No parent-child edges after canonicalization for {} nodes — "
                        + "inferring linear chain fallback", nodes.size());
                for (int i = 1; i < nodes.size(); i++) {
                    nodes.get(i).setParentId(nodes.get(i - 1).getId());
                    nodes.get(i - 1).setChildren(List.of(nodes.get(i).getId()));
                }
            }

            RoadmapBranchingNormalizer.Result branchingGraph = RoadmapBranchingNormalizer.normalize(nodes);
            nodes = branchingGraph.nodes();
            if (branchingGraph.warnings() != null) {
                graphWarnings.addAll(branchingGraph.warnings());
            }

            // INFO log: summary of canonicalized graph
            log.info("[RoadmapGen] Canonicalized {} nodes: {} roots, {} branches, {} child-derived-parents, {} dep-only",
                    nodes.size(),
                    canonicalGraph.rootCount(),
                    canonicalGraph.branchCount(),
                    canonicalGraph.childDerivedParents(),
                    canonicalGraph.dependencyOnlyNodes());
            if (canonicalGraph.warnings() != null && !canonicalGraph.warnings().isEmpty()) {
                log.warn("[RoadmapGen] Canonicalization warnings: {}", canonicalGraph.warnings());
            }
            if (edgesCount == 0 && nodes.size() > 1) {
                log.warn("[RoadmapGen] ALL {} nodes are roots — BUG-8 linear chain applied", nodes.size());
            }

            log.info("[RoadmapGen] Branching normalized {} nodes into {} MAIN spine nodes and {} optional SIDE nodes",
                    nodes.size(),
                    branchingGraph.mainNodes(),
                    branchingGraph.sideNodes());
            if (!graphWarnings.isEmpty()) {
                log.warn("[RoadmapGen] Graph normalization warnings: {}", graphWarnings);
            }

            // Parse statistics
            JsonNode statsNode = root.path("roadmap_statistics");
                RoadmapResponse.RoadmapStatistics statistics = normalizeRoadmapStatistics(
                    nodes,
                    statsNode.isMissingNode() ? null : parseStatistics(statsNode));

            // Parse learning tips
            List<String> learningTips = new ArrayList<>();
            JsonNode tipsNode = firstPresentNode(root, "learning_tips", "learningTips");
            if (tipsNode != null && tipsNode.isArray()) {
                for (JsonNode tip : tipsNode) {
                    learningTips.add(tip.asText());
                }
            }

            // Parse overview
            RoadmapResponse.Overview overview = null;
            JsonNode overviewNode = firstPresentNode(root, "overview");
            if (overviewNode != null && overviewNode.isObject()) {
                overview = RoadmapResponse.Overview.builder()
                        .purpose(overviewNode.path("purpose").asText(null))
                        .audience(overviewNode.path("audience").asText(null))
                        .postRoadmapState(overviewNode.path("post_roadmap_state").asText(null))
                        .build();
            }

            // Parse structure (phases)
            List<RoadmapResponse.StructurePhase> structure = new ArrayList<>();
            JsonNode structureNode = firstPresentNode(root, "structure");
            if (structureNode != null && structureNode.isArray()) {
                for (JsonNode phase : structureNode) {
                    structure.add(RoadmapResponse.StructurePhase.builder()
                            .phaseId(phase.path("phase_id").asText(null))
                            .title(phase.path("title").asText(null))
                            .timeframe(phase.path("timeframe").asText(null))
                            .goal(phase.path("goal").asText(null))
                            .skillFocus(parseStringArray(phase.path("skill_focus")))
                            .mindsetGoal(phase.path("mindset_goal").asText(null))
                            .expectedOutput(phase.path("expected_output").asText(null))
                            .build());
                }
            }

            // Parse thinking progression
            List<String> thinkingProgression = parseStringArray(firstPresentNode(root, "thinking_progression", "thinkingProgression"));

            // Parse projects evidence
            List<RoadmapResponse.ProjectEvidence> projectsEvidence = new ArrayList<>();
            JsonNode projectsNode = firstPresentNode(root, "projects_evidence", "projectsEvidence");
            if (projectsNode != null && projectsNode.isArray()) {
                for (JsonNode proj : projectsNode) {
                    projectsEvidence.add(RoadmapResponse.ProjectEvidence.builder()
                            .phaseId(proj.path("phase_id").asText(null))
                            .project(proj.path("project").asText(null))
                            .objective(proj.path("objective").asText(null))
                            .skillsProven(parseStringArray(proj.path("skills_proven")))
                            .kpi(parseStringArray(proj.path("kpi")))
                            .build());
                }
            }

            // Parse next steps
            RoadmapResponse.NextSteps nextSteps = null;
            JsonNode nextStepsNode = firstPresentNode(root, "next_steps", "nextSteps");
            if (nextStepsNode != null && nextStepsNode.isObject()) {
                nextSteps = RoadmapResponse.NextSteps.builder()
                        .jobs(parseStringArray(nextStepsNode.path("jobs")))
                        .nextSkills(parseStringArray(nextStepsNode.path("next_skills")))
                        .mentorsMicroJobs(parseStringArray(nextStepsNode.path("mentors_micro_jobs")))
                        .build();
            }

            List<RoadmapResponse.SkillDependency> skillDependencies = new ArrayList<>();

            JsonNode depsNode = firstPresentNode(root, "skill_dependencies", "skillDependencies");
            if (depsNode != null && depsNode.isArray()) {
                for (JsonNode d : depsNode) {
                    RoadmapResponse.SkillDependency dep = RoadmapResponse.SkillDependency.builder()
                            .from(d.path("from").asText(null))
                            .to(d.path("to").asText(null))
                            .build();
                    skillDependencies.add(dep);
                }
            }

                log.debug("🧪 [trace={}] Parsed roadmap sections: nodes={}, learningTips={}, overview={}, structure={}, thinkingProgression={}, projectsEvidence={}, nextSteps={}, nextStepsJobs={}, nextStepsSkills={}, skillDependencies={}",
                    currentTraceId(),
                    nodes.size(),
                    learningTips.size(),
                    overview != null,
                    structure.size(),
                    thinkingProgression.size(),
                    projectsEvidence.size(),
                    nextSteps != null,
                    nextSteps != null && nextSteps.getJobs() != null ? nextSteps.getJobs().size() : 0,
                    nextSteps != null && nextSteps.getNextSkills() != null ? nextSteps.getNextSkills().size() : 0,
                    skillDependencies.size());

            return new ParsedRoadmap(metadata, nodes, statistics, learningTips,
                    overview, structure, thinkingProgression, projectsEvidence, nextSteps, skillDependencies,
                    graphWarnings);

        } catch (JsonProcessingException e) {
                String errorMessage = safeMessage(e);
                String lowerMessage = errorMessage.toLowerCase(Locale.ROOT);
                boolean truncatedHint = lowerMessage.contains("unexpected end-of-input")
                    || lowerMessage.contains("unexpected end of input")
                    || lowerMessage.contains("expected close marker")
                    || lowerMessage.contains("end-of-input");
                if (telemetry != null) {
                    telemetry.recordParseFailure(previewHead(errorMessage, 160));
                }

                log.error(
                    "❌ Failed to parse roadmap JSON V2 (trace={}, truncatedHint={}, rawChars={}, sanitizedChars={}): {}",
                    currentTraceId(),
                    truncatedHint,
                    roadmapJson != null ? roadmapJson.length() : 0,
                    sanitized != null ? sanitized.length() : 0,
                    e.getMessage());
                String parseErrorContext = previewJsonErrorContext(sanitized, e, 180);
                if (!parseErrorContext.isBlank()) {
                    log.error("Parse error context (up to 360 chars):\n{}", parseErrorContext);
                }
                log.error("📄 Raw AI JSON head (up to 500 chars):\n{}", previewHead(roadmapJson, 500));
                log.error("📄 Raw AI JSON tail (up to 500 chars):\n{}", previewTail(roadmapJson, 500));
                if (sanitized != null && !sanitized.isBlank()) {
                log.error("📄 Sanitized AI JSON tail (up to 500 chars):\n{}", previewTail(sanitized, 500));
                }
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "AI response was incomplete or invalid JSON. Please retry. (Error: " + e.getMessage() + ")");
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
        s = repairCommonAiJsonIssues(s);
        return s.trim();
    }

    private String repairCommonAiJsonIssues(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }

        StringBuilder repaired = new StringBuilder(json.length() + 32);
        Deque<Character> containerStack = new ArrayDeque<>();
        boolean insideString = false;
        boolean escaping = false;
        boolean stringIsObjectKey = false;
        boolean expectingObjectKey = false;

        for (int i = 0; i < json.length(); i++) {
            char current = json.charAt(i);

            if (insideString) {
                if (escaping) {
                    repaired.append(current);
                    escaping = false;
                    continue;
                }
                if (current == '\\') {
                    repaired.append(current);
                    escaping = true;
                    continue;
                }
                if (current == '\r') {
                    if (i + 1 < json.length() && json.charAt(i + 1) == '\n') {
                        i++;
                    }
                    repaired.append("\\n");
                    continue;
                }
                if (current == '\n') {
                    repaired.append("\\n");
                    continue;
                }
                if (current == '\t') {
                    repaired.append("\\t");
                    continue;
                }
                if (current == '"') {
                    char nextSignificant = nextSignificantChar(json, i + 1);
                    boolean closesObjectKey = stringIsObjectKey && nextSignificant == ':';
                    boolean closesValue = !stringIsObjectKey
                            && (nextSignificant == ',' || nextSignificant == '}'
                                    || nextSignificant == ']' || nextSignificant == '\0');
                    if (closesObjectKey || closesValue) {
                        repaired.append(current);
                        insideString = false;
                        continue;
                    }
                    repaired.append("\\\"");
                    continue;
                }

                repaired.append(current);
                continue;
            }

            repaired.append(current);
            switch (current) {
                case '{':
                    containerStack.push('{');
                    expectingObjectKey = true;
                    break;
                case '[':
                    containerStack.push('[');
                    break;
                case '}':
                case ']':
                    if (!containerStack.isEmpty()) {
                        containerStack.pop();
                    }
                    break;
                case ',':
                    expectingObjectKey = !containerStack.isEmpty() && containerStack.peek() == '{';
                    break;
                case ':':
                    expectingObjectKey = false;
                    break;
                case '"':
                    insideString = true;
                    escaping = false;
                    stringIsObjectKey = !containerStack.isEmpty()
                            && containerStack.peek() == '{'
                            && expectingObjectKey;
                    break;
                default:
                    break;
            }
        }

        // Safety net: if JSON is truncated, close remaining unclosed brackets
        if (!containerStack.isEmpty()) {
            StringBuilder close = new StringBuilder();
            Deque<Character> reverseStack = new ArrayDeque<>();
            while (!containerStack.isEmpty()) {
                reverseStack.push(containerStack.pop());
            }
            while (!reverseStack.isEmpty()) {
                char need = reverseStack.pop();
                close.append(need == '[' ? ']' : '}');
            }
            repaired.append(close);
        }

        return repaired.toString();
    }

    private char nextSignificantChar(String text, int startIndex) {
        if (text == null || startIndex < 0) {
            return '\0';
        }
        for (int i = startIndex; i < text.length(); i++) {
            char current = text.charAt(i);
            if (!Character.isWhitespace(current)) {
                return current;
            }
        }
        return '\0';
    }

    private String previewJsonErrorContext(String json, JsonProcessingException exception, int radius) {
        if (json == null || json.isBlank() || exception == null || exception.getLocation() == null) {
            return "";
        }

        long charOffset = exception.getLocation().getCharOffset();
        if (charOffset < 0) {
            return "";
        }

        int anchor = (int) Math.max(0, Math.min(json.length(), charOffset));
        int start = Math.max(0, anchor - radius);
        int end = Math.min(json.length(), anchor + radius);
        return json.substring(start, end)
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private JsonNode firstPresentNode(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            JsonNode candidate = node.path(key);
            if (!candidate.isMissingNode() && !candidate.isNull()) {
                return candidate;
            }
        }
        return null;
    }

    private String readText(JsonNode node, String... keys) {
        JsonNode target = firstPresentNode(node, keys);
        if (target == null) {
            return null;
        }
        String value = target.asText(null);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * Normalize experience level to UI display value.
     * Maps AI model values (zero/basic/...) to FE-friendly display values (Zero/Beginner/...).
     * Also handles lowercase/uppercase variations.
     */
    private String normalizeExperienceLevel(String level) {
        if (level == null || level.isBlank()) return "beginner";
        String lower = level.toLowerCase(Locale.ROOT).trim();
        switch (lower) {
            case "zero":  return "Zero";
            case "basic": return "Beginner";
            case "beginner": return "Beginner";
            case "intermediate": return "Intermediate";
            case "advanced": return "Advanced";
            default: return "Beginner"; // treat unknown as beginner
        }
    }

    private Boolean readBoolean(JsonNode node, Boolean fallback, String... keys) {
        JsonNode target = firstPresentNode(node, keys);
        if (target == null) {
            return fallback;
        }
        return target.asBoolean();
    }

    private Integer readInteger(JsonNode node, String... keys) {
        JsonNode target = firstPresentNode(node, keys);
        if (target == null || target.isNull()) {
            return null;
        }
        if (target.isInt() || target.isLong()) {
            return target.asInt();
        }
        if (target.isNumber()) {
            return (int) Math.round(target.asDouble());
        }
        if (target.isTextual()) {
            try {
                return Integer.parseInt(target.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long readLong(JsonNode node, String... keys) {
        JsonNode target = firstPresentNode(node, keys);
        if (target == null || target.isNull()) {
            return null;
        }
        if (target.isIntegralNumber()) {
            return target.asLong();
        }
        if (target.isNumber()) {
            return Math.round(target.asDouble());
        }
        if (target.isTextual()) {
            try {
                return Long.parseLong(target.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    // Scoring and ordering delegated to package-private utility classes.
    // See RoadmapImportanceScorer and RoadmapNodeOrderNormalizer.

    private Double readDouble(JsonNode node, String... keys) {
        JsonNode target = firstPresentNode(node, keys);
        if (target == null || target.isNull()) {
            return null;
        }
        if (target.isNumber()) {
            double v = target.asDouble();
            if (!Double.isFinite(v)) return null;
            return Math.max(0.0, Math.min(1.0, v));
        }
        if (target.isTextual()) {
            try {
                double v = Double.parseDouble(target.asText().trim());
                if (!Double.isFinite(v)) return null;
                return Math.max(0.0, Math.min(1.0, v));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String timelineTokenToVietnameseDuration(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (upper.matches("\\d+M")) {
            return upper.substring(0, upper.length() - 1) + " tháng";
        }
        if (upper.matches("\\d+W")) {
            return upper.substring(0, upper.length() - 1) + " tuần";
        }
        if (upper.matches("\\d+Y")) {
            return upper.substring(0, upper.length() - 1) + " năm";
        }
        return value;
    }

    private String sanitizeDurationLabel(String rawDuration, String fallbackDuration) {
        String candidate = rawDuration;
        if (candidate == null || candidate.isBlank()) {
            candidate = fallbackDuration;
        }
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        String normalized = timelineTokenToVietnameseDuration(candidate).trim();
        normalized = normalized.replaceAll("(?i)\\s*\\(\\s*dựa\\s*trên[^)]*\\)", "").trim();
        normalized = normalized.replaceAll("(?i)\\s*\\(\\s*based\\s*on[^)]*\\)", "").trim();
        normalized = normalized.replaceAll("\\s+", " ");

        return normalized.isBlank() ? fallbackDuration : normalized;
    }

    /**
     * Parse roadmap metadata
     */
    private RoadmapResponse.RoadmapMetadata parseMetadata(JsonNode node) {
        String desiredDuration = sanitizeDurationLabel(
            readText(node, "desired_duration", "desiredDuration"),
            null);
        String duration = sanitizeDurationLabel(
            readText(node, "duration"),
            desiredDuration);

        RoadmapResponse.RoadmapMetadata meta = RoadmapResponse.RoadmapMetadata.builder()
            .title(defaultText(readText(node, "title"), "Roadmap học tập"))
            .originalGoal(defaultText(readText(node, "original_goal", "originalGoal"), ""))
            .validatedGoal(readText(node, "validated_goal", "validatedGoal"))
            .duration(defaultText(duration, defaultText(desiredDuration, "1 tháng")))
            .experienceLevel(normalizeExperienceLevel(defaultText(readText(node, "experience_level", "experienceLevel"), "zero")))
            .learningStyle(defaultText(readText(node, "learning_style", "learningStyle"), "project-based"))
            .detectedIntention(defaultText(readText(node, "detected_intention", "detectedIntention"), ""))
            .validationNotes(readText(node, "validation_notes", "validationNotes"))
            .estimatedCompletion(readText(node, "estimated_completion", "estimatedCompletion"))
            .difficultyLevel(defaultText(
                    readText(node, "difficulty_level", "difficultyLevel"), "medium").toLowerCase(Locale.ROOT))
            .prerequisites(parseStringArray(node.path("prerequisites")))
            .careerRelevance(readText(node, "career_relevance", "careerRelevance"))
            .roadmapType(readText(node, "roadmap_type", "roadmapType"))
            .target(readText(node, "target"))
            .finalObjective(readText(node, "final_objective", "finalObjective"))
            .currentLevel(readText(node, "current_level", "currentLevel"))
            .desiredDuration(desiredDuration)
            .background(readText(node, "background"))
            .dailyTime(readText(node, "daily_time", "dailyTime"))
            .targetEnvironment(readText(node, "target_environment", "targetEnvironment"))
            .location(readText(node, "location"))
            .priority(readText(node, "priority"))
            .toolPreferences(parseStringArray(node.path("tool_preferences"), node.path("toolPreferences")))
            .difficultyConcern(readText(node, "difficulty_concern", "difficultyConcern"))
            .incomeGoal(readBoolean(node, null, "income_goal", "incomeGoal"))
                .build();
        // Optional: mode-specific metadata if AI provides
        meta.setRoadmapMode(readText(node, "roadmap_mode", "roadmapMode"));
        JsonNode skillMode = firstPresentNode(node, "skill_mode", "skillMode");
        if (skillMode != null && skillMode.isObject()) {
            RoadmapResponse.SkillModeMeta sm = RoadmapResponse.SkillModeMeta.builder()
                .skillName(readText(skillMode, "skill_name", "skillName"))
                .skillCategory(readText(skillMode, "skill_category", "skillCategory"))
                .desiredDepth(readText(skillMode, "desired_depth", "desiredDepth"))
                .learnerType(readText(skillMode, "learner_type", "learnerType"))
                .currentSkillLevel(defaultText(readText(skillMode, "current_skill_level", "currentSkillLevel"),
                        readText(node, "experience_level", "experienceLevel")))
                .learningGoal(readText(skillMode, "learning_goal", "learningGoal"))
                .dailyLearningTime(readText(skillMode, "daily_learning_time", "dailyLearningTime"))
                .assessmentPreference(readText(skillMode, "assessment_preference", "assessmentPreference"))
                .difficultyTolerance(readText(skillMode, "difficulty_tolerance", "difficultyTolerance"))
                .toolPreference(parseStringArray(skillMode.path("tool_preference"), skillMode.path("toolPreference")))
                    .build();
            meta.setSkillMode(sm);
        }
        JsonNode careerMode = firstPresentNode(node, "career_mode", "careerMode");
        if (careerMode != null && careerMode.isObject()) {
            RoadmapResponse.CareerModeMeta cm = RoadmapResponse.CareerModeMeta.builder()
                .targetRole(readText(careerMode, "target_role", "targetRole"))
                .careerTrack(readText(careerMode, "career_track", "careerTrack"))
                .targetSeniority(readText(careerMode, "target_seniority", "targetSeniority"))
                .workMode(readText(careerMode, "work_mode", "workMode"))
                .targetMarket(readText(careerMode, "target_market", "targetMarket"))
                .companyType(readText(careerMode, "company_type", "companyType"))
                .timelineToWork(readText(careerMode, "timeline_to_work", "timelineToWork"))
                .incomeExpectation(readBoolean(careerMode, null, "income_expectation", "incomeExpectation"))
                .workExperience(readText(careerMode, "work_experience", "workExperience"))
                .transferableSkills(readBoolean(careerMode, null, "transferable_skills", "transferableSkills"))
                .confidenceLevel(readText(careerMode, "confidence_level", "confidenceLevel"))
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
            String typeStr = defaultText(readText(nodeJson, "type"), "MAIN");
            RoadmapResponse.RoadmapNode.NodeType type;
            try {
                type = RoadmapResponse.RoadmapNode.NodeType.valueOf(typeStr);
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Invalid node type: " + typeStr + ". Must be MAIN or SIDE");
            }

                Integer estimatedTimeMinutes = resolveEstimatedTimeMinutes(nodeJson, type);

            RoadmapResponse.RoadmapNode node = RoadmapResponse.RoadmapNode.builder()
                    .id(nodeJson.path("id").asText())
                    .title(nodeJson.path("title").asText())
                    .description(defaultText(readText(nodeJson, "description"), ""))
                    .estimatedTimeMinutes(estimatedTimeMinutes)
                    .type(type)
                    .difficulty(defaultText(readText(nodeJson, "difficulty"), "medium"))
                    .phaseId(readText(nodeJson, "phase_id", "phaseId"))
                    .orderIndex(readInteger(nodeJson, "order_index", "orderIndex"))
                    .mainPathIndex(readInteger(nodeJson, "main_path_index", "mainPathIndex"))
                    // Tree node fields (with smart fallback)
                    .isCore(readBoolean(nodeJson, type == RoadmapResponse.RoadmapNode.NodeType.MAIN, "is_core", "isCore"))
                    .parentId(readText(nodeJson, "parent_id", "parentId"))
                    .suggestedCourseIds(parseStringArray(nodeJson.path("suggested_course_ids"), nodeJson.path("suggestedCourseIds")))
                    .suggestedModuleIds(parseStringArray(nodeJson.path("suggested_module_ids"), nodeJson.path("suggestedModuleIds")))
                    // Learning content
                    .learningObjectives(parseStringArray(nodeJson.path("learning_objectives"), nodeJson.path("learningObjectives")))
                    .keyConcepts(parseStringArray(nodeJson.path("key_concepts"), nodeJson.path("keyConcepts")))
                    .practicalExercises(parseStringArray(nodeJson.path("practical_exercises"), nodeJson.path("practicalExercises")))
                    .suggestedResources(parseStringArray(nodeJson.path("suggested_resources"), nodeJson.path("suggestedResources")))
                    .successCriteria(parseStringArray(nodeJson.path("success_criteria"), nodeJson.path("successCriteria")))
                    .skills(parseNodeSkillRequirements(nodeJson))
                    .prerequisites(parseStringArray(nodeJson.path("prerequisites")))
                    .children(parseStringArray(nodeJson.path("children")))
                    .estimatedCompletionRate(readText(nodeJson, "estimated_completion_rate", "estimatedCompletionRate"))
                    .importanceScore(readDouble(nodeJson, "importance_score", "importanceScore"))
                    .confidenceScore(readDouble(nodeJson, "confidence_score", "confidenceScore"))
                    .reason(readText(nodeJson, "reason"))
                    .evidence(parseStringArray(nodeJson.path("evidence")))
                    .importanceValidationStatus(readText(nodeJson, "importance_validation_status", "importanceValidationStatus"))
                    .build();

            nodes.add(node);
        }

        return nodes;
    }

    private List<RoadmapResponse.NodeSkillRequirement> parseNodeSkillRequirements(JsonNode nodeJson) {
        JsonNode requirements = firstExistingNode(nodeJson, "skills", "skill_requirements", "skillRequirements", "nodeSkills");
        if (requirements == null || !requirements.isArray()) {
            Long legacySkillId = readLong(nodeJson, "skill_id", "skillId");
            String legacySkillName = readText(nodeJson, "skill_name", "skillName");
            if (legacySkillId == null && legacySkillName == null) {
                return List.of();
            }
            return List.of(RoadmapResponse.NodeSkillRequirement.builder()
                    .skillId(legacySkillId)
                    .skillName(legacySkillName)
                    .canonicalKey(readText(nodeJson, "canonical_key", "canonicalKey"))
                    .requirementType(RequirementType.REQUIRED)
                    .build());
        }

        List<RoadmapResponse.NodeSkillRequirement> parsed = new ArrayList<>();
        for (JsonNode item : requirements) {
            if (item == null || item.isNull()) {
                continue;
            }
            Long skillId = readLong(item, "skill_id", "skillId");
            String skillName = readText(item, "skill_name", "skillName", "name");
            String canonicalKey = readText(item, "canonical_key", "canonicalKey");
            if (skillId == null && skillName == null && canonicalKey == null) {
                continue;
            }
            parsed.add(RoadmapResponse.NodeSkillRequirement.builder()
                    .skillId(skillId)
                    .skillName(skillName)
                    .canonicalKey(canonicalKey)
                    .requirementType(RequirementType.fromValue(readText(item, "requirement_type", "requirementType", "importance")))
                    .build());
        }
        return parsed;
    }

    private JsonNode firstExistingNode(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private Integer resolveEstimatedTimeMinutes(
            JsonNode nodeJson,
            RoadmapResponse.RoadmapNode.NodeType type) {
        String[] numericKeys = {
                "estimated_time_minutes",
                "estimatedTimeMinutes",
                "estimated_minutes",
                "estimated_duration_minutes",
                "estimated_duration_min",
                "duration_minutes",
                "time_minutes"
        };

        Integer parsedMinutes = null;
        for (String key : numericKeys) {
            JsonNode valueNode = nodeJson.path(key);
            parsedMinutes = parseMinutesValue(valueNode);
            if (parsedMinutes != null) {
                break;
            }
        }

        if (parsedMinutes == null) {
            String[] textualKeys = {
                    "estimated_time",
                    "estimated_duration",
                    "duration",
                    "time_estimate",
                    "timeEstimate",
                    "estimated_completion_time"
            };
            for (String key : textualKeys) {
                JsonNode valueNode = nodeJson.path(key);
                parsedMinutes = parseMinutesValue(valueNode);
                if (parsedMinutes != null) {
                    break;
                }
            }
        }

        // Step 1: AI gave a value — apply task-type sanity cap
        if (parsedMinutes != null && parsedMinutes > 0) {
            int capped = capByTaskType(parsedMinutes, nodeJson);
            return Math.min(capped, 480);
        }

        // Step 2: No value from AI — use realistic fallback
        return computeFallbackEstimatedMinutes(nodeJson, type);
    }

    /**
     * Apply a task-type sanity cap on top of whatever time the AI generated.
     * This is the last line of defense — if AI says "setup" = 6 hours, we cap it.
     *
     * Strategy: Use ONLY generic, cross-domain patterns. Cap only the
     * most obviously overestimated categories. Everything else gets a
     * generous proportional cap based on content depth.
     *
     * Cross-domain means we do NOT hard-code "Java", "Python", "Design", etc.
     * — those would be domain-specific and break non-technical roadmaps.
     */
    private int capByTaskType(int minutes, JsonNode nodeJson) {
        String title = nodeJson.path("title").asText("").toLowerCase();
        String description = nodeJson.path("description").asText("").toLowerCase();

        // Hard-cap: setup/environment — universal pattern, max 90 min
        // Matches any language/skill: "Setup", "Cài đặt", "Install JDK", "Configure IDE", etc.
        if (title.matches("(?i).*(setup|cài.?đặt|install|môi.?trường|environment|config|configure|workspace|import|setting.?up|initiali[sz]e).*")) {
            return Math.min(minutes, 90);
        }

        // Compute "expected" minutes via fallback formula (domain-agnostic)
        // This gives us a baseline for what a reasonable time looks like
        int objectiveCount = countArrayItems(nodeJson.path("learning_objectives"));
        int conceptCount = countArrayItems(nodeJson.path("key_concepts"));
        int exerciseCount = countArrayItems(nodeJson.path("practical_exercises"));
        int criteriaCount = countArrayItems(nodeJson.path("success_criteria"));
        int expectedBase = 60 + (objectiveCount * 15) + (conceptCount * 8)
                + (exerciseCount * 22) + (criteriaCount * 6);
        int expected = Math.max(30, Math.min(240, expectedBase));

        // If AI is within 2x expected, keep AI's value (trust AI for non-setup tasks)
        if (minutes <= expected * 2) {
            return Math.min(minutes, 360); // Generous cap for normal nodes
        }

        // AI grossly overestimated (> 2x expected) — use expected as cap
        return Math.min(minutes, expected * 2);
    }

    private Integer parseMinutesValue(JsonNode valueNode) {
        if (valueNode == null || valueNode.isMissingNode() || valueNode.isNull()) {
            return null;
        }

        if (valueNode.isInt() || valueNode.isLong()) {
            return valueNode.asInt();
        }

        if (valueNode.isNumber()) {
            return (int) Math.round(valueNode.asDouble());
        }

        if (valueNode.isTextual()) {
            return parseMinutesFromText(valueNode.asText());
        }

        return null;
    }

    private Integer parseMinutesFromText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);

        Matcher compactMatcher = COMPACT_HM_PATTERN.matcher(normalized);
        if (compactMatcher.find()) {
            int hours = Integer.parseInt(compactMatcher.group(1));
            int minutes = Integer.parseInt(compactMatcher.group(2));
            return (hours * 60) + minutes;
        }

        int totalMinutes = 0;
        boolean matched = false;

        Matcher hoursMatcher = HOURS_PATTERN.matcher(normalized);
        while (hoursMatcher.find()) {
            String group = hoursMatcher.group(1).replace(',', '.');
            double hours = Double.parseDouble(group);
            totalMinutes += (int) Math.round(hours * 60);
            matched = true;
        }

        Matcher minutesMatcher = MINUTES_PATTERN.matcher(normalized);
        while (minutesMatcher.find()) {
            String group = minutesMatcher.group(1).replace(',', '.');
            double minutes = Double.parseDouble(group);
            totalMinutes += (int) Math.round(minutes);
            matched = true;
        }

        if (matched) {
            return totalMinutes;
        }

        String digitsOnly = normalized.replaceAll("[^0-9]", "");
        if (!digitsOnly.isEmpty()) {
            return Integer.parseInt(digitsOnly);
        }

        return null;
    }

    private Integer computeFallbackEstimatedMinutes(
            JsonNode nodeJson,
            RoadmapResponse.RoadmapNode.NodeType type) {
        int objectiveCount = countArrayItems(nodeJson.path("learning_objectives"));
        int conceptCount = countArrayItems(nodeJson.path("key_concepts"));
        int exerciseCount = countArrayItems(nodeJson.path("practical_exercises"));
        int criteriaCount = countArrayItems(nodeJson.path("success_criteria"));

        int baseMinutes = type == RoadmapResponse.RoadmapNode.NodeType.MAIN ? 90 : 60;
        String difficulty = nodeJson.path("difficulty").asText("").trim().toLowerCase(Locale.ROOT);
        if (difficulty.contains("easy") || difficulty.contains("beginner")) {
            baseMinutes -= 15;
        } else if (difficulty.contains("hard") || difficulty.contains("advanced") || difficulty.contains("expert")) {
            baseMinutes += 25;
        }

        int estimated = baseMinutes
                + (objectiveCount * 15)
                + (conceptCount * 8)
                + (exerciseCount * 22)
                + (criteriaCount * 6);

        return Math.max(30, Math.min(480, estimated));
    }

    private int countArrayItems(JsonNode arrayNode) {
        if (arrayNode == null || !arrayNode.isArray()) {
            return 0;
        }

        int count = 0;
        for (JsonNode value : arrayNode) {
            if (!value.asText("").isBlank()) {
                count++;
            }
        }
        return count;
    }

    private String serializeParsedRoadmap(ParsedRoadmap parsed) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("roadmap_metadata", parsed.metadata());
            root.put("overview", parsed.overview());
            root.put("structure", parsed.structure());
            root.put("thinking_progression", parsed.thinkingProgression());
            root.put("projects_evidence", parsed.projectsEvidence());
            root.put("next_steps", parsed.nextSteps());
            root.put("skill_dependencies", parsed.skillDependencies());
            root.put("roadmap", parsed.nodes());
            root.put("roadmap_statistics", parsed.statistics());
            root.put("learning_tips", parsed.learningTips());
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to serialize canonical roadmap JSON");
        }
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

            private RoadmapResponse.RoadmapStatistics normalizeRoadmapStatistics(
                List<RoadmapResponse.RoadmapNode> nodes,
                RoadmapResponse.RoadmapStatistics parsedStatistics) {
            int totalNodes = nodes.size();
            int mainNodes = (int) nodes.stream()
                .filter(node -> node.getType() == RoadmapResponse.RoadmapNode.NodeType.MAIN
                    || Boolean.TRUE.equals(node.getIsCore()))
                .count();
            int sideNodes = Math.max(0, totalNodes - mainNodes);
            double computedHours = calculateTotalHours(nodes);

            Map<String, Integer> difficultyDistribution = parsedStatistics != null
                ? new HashMap<>(parsedStatistics.getDifficultyDistribution() != null
                    ? parsedStatistics.getDifficultyDistribution()
                    : Collections.emptyMap())
                : new HashMap<>();
            if (difficultyDistribution.isEmpty()) {
                for (RoadmapResponse.RoadmapNode node : nodes) {
                String difficulty = node.getDifficulty();
                if (difficulty == null || difficulty.isBlank()) {
                    continue;
                }
                String key = difficulty.trim().toLowerCase(Locale.ROOT);
                difficultyDistribution.put(key, difficultyDistribution.getOrDefault(key, 0) + 1);
                }
            }

            if (parsedStatistics == null) {
                return RoadmapResponse.RoadmapStatistics.builder()
                    .totalNodes(totalNodes)
                    .mainNodes(mainNodes)
                    .sideNodes(sideNodes)
                    .totalEstimatedHours(computedHours)
                    .difficultyDistribution(difficultyDistribution)
                    .build();
            }

            Integer normalizedTotalNodes = parsedStatistics.getTotalNodes() != null && parsedStatistics.getTotalNodes() > 0
                ? parsedStatistics.getTotalNodes()
                : totalNodes;
            Integer normalizedMainNodes = parsedStatistics.getMainNodes() != null && parsedStatistics.getMainNodes() >= 0
                ? parsedStatistics.getMainNodes()
                : mainNodes;
            Integer normalizedSideNodes = parsedStatistics.getSideNodes() != null && parsedStatistics.getSideNodes() >= 0
                ? parsedStatistics.getSideNodes()
                : sideNodes;

            if (normalizedMainNodes + normalizedSideNodes != normalizedTotalNodes
                || !normalizedTotalNodes.equals(totalNodes)
                || !normalizedMainNodes.equals(mainNodes)
                || !normalizedSideNodes.equals(sideNodes)) {
                normalizedMainNodes = mainNodes;
                normalizedSideNodes = sideNodes;
                normalizedTotalNodes = totalNodes;
            }

            Double parsedHours = parsedStatistics.getTotalEstimatedHours();
            Double normalizedHours = (parsedHours == null || parsedHours <= 0.0)
                ? computedHours
                : parsedHours;

            double diffRatio = computedHours > 0
                ? Math.abs(normalizedHours - computedHours) / computedHours
                : 0.0;
            if (diffRatio > 0.25) {
                normalizedHours = computedHours;
            }

            return RoadmapResponse.RoadmapStatistics.builder()
                .totalNodes(normalizedTotalNodes)
                .mainNodes(normalizedMainNodes)
                .sideNodes(normalizedSideNodes)
                .totalEstimatedHours(normalizedHours)
                .difficultyDistribution(difficultyDistribution)
                .build();
            }

    /**
     * Helper: Parse JSON array to List<String>
     */
    private List<String> parseStringArray(JsonNode... arrayNodes) {
        List<String> result = new ArrayList<>();
        if (arrayNodes == null) {
            return result;
        }
        for (JsonNode arrayNode : arrayNodes) {
            if (arrayNode == null || !arrayNode.isArray()) {
                continue;
            }
            for (JsonNode item : arrayNode) {
                String value = item.asText(null);
                if (value != null && !value.isBlank()) {
                    result.add(value);
                }
            }
            if (!result.isEmpty()) {
                return result;
            }
        }
        return result;
    }

    private void logRoadmapTelemetrySummary(
            String traceId,
            RoadmapGenerationTelemetry telemetry,
            String outcome,
            String errorCode,
            long elapsedMs) {
        if (telemetry == null) {
            return;
        }

        log.info(
                "📊 [trace={}] Roadmap summary: outcome={}, errorCode={}, elapsedMs={}, modelPath={}, fallback={}, fallbackReason={}, failureType={}, status={}, attempts(gemini={}, mistralPrimary={}, mistralCompact={}), promptChars={}, rawChars={}, extractedChars={}, sanitizedChars={}, parseAttempts={}, parseFailures={}, parseSignature='{}', nodeCoverage(total={}, withCourses={}, withModules={})",
                traceId,
                outcome,
                errorCode,
                elapsedMs,
                telemetry.modelPath,
                telemetry.fallbackTriggered,
                telemetry.fallbackReason,
                telemetry.fallbackFailureType,
                telemetry.fallbackHttpStatus,
                telemetry.geminiAttempts,
                telemetry.mistralPrimaryAttempts,
                telemetry.mistralCompactAttempts,
                telemetry.promptChars,
                telemetry.rawChars,
                telemetry.extractedChars,
                telemetry.sanitizedChars,
                telemetry.parseAttempts,
                telemetry.parseFailures,
                telemetry.parseFailureSignature,
                telemetry.totalNodes,
                telemetry.nodesWithCourses,
                telemetry.nodesWithModules);
    }

    private static final class RoadmapGenerationTelemetry {
        private String modelPath = "unknown";
        private boolean fallbackTriggered = false;
        private String fallbackReason = "none";
        private String fallbackFailureType = "none";
        private int fallbackHttpStatus = -1;

        private int promptChars = 0;
        private int rawChars = 0;
        private int extractedChars = 0;
        private int sanitizedChars = 0;

        private int parseAttempts = 0;
        private int parseFailures = 0;
        private String parseFailureSignature = "none";

        private int geminiAttempts = 0;
        private int mistralPrimaryAttempts = 0;
        private int mistralCompactAttempts = 0;

        private int totalNodes = 0;
        private int nodesWithCourses = 0;
        private int nodesWithModules = 0;

        private void markModelPath(String modelPath) {
            if (modelPath != null && !modelPath.isBlank()) {
                this.modelPath = modelPath;
            }
        }

        private String getModelPath() {
            return this.modelPath;
        }

        private void markFallback(String reason, String failureType, int httpStatus) {
            this.fallbackTriggered = true;
            if (reason != null && !reason.isBlank()) {
                if ("none".equals(this.fallbackReason)) {
                    this.fallbackReason = reason;
                } else if (!this.fallbackReason.contains(reason)) {
                    this.fallbackReason = this.fallbackReason + "|" + reason;
                }
            }
            if (failureType != null && !failureType.isBlank()) {
                this.fallbackFailureType = failureType;
            }
            this.fallbackHttpStatus = httpStatus;
        }

        private void recordPromptLength(int length) {
            this.promptChars = Math.max(0, length);
        }

        private void recordPayloadLength(String raw, String extracted) {
            this.rawChars = raw != null ? raw.length() : 0;
            this.extractedChars = extracted != null ? extracted.length() : 0;
        }

        private void recordSanitizedLength(int length) {
            this.sanitizedChars = Math.max(0, length);
        }

        private void recordParseAttempt() {
            this.parseAttempts++;
        }

        private void recordParseFailure(String signature) {
            this.parseFailures++;
            if (signature != null && !signature.isBlank()) {
                this.parseFailureSignature = signature;
            }
        }

        private void recordModelAttempt(String channel) {
            if (channel == null) {
                return;
            }
            switch (channel) {
                case "gemini" -> this.geminiAttempts++;
                case "mistral-primary" -> this.mistralPrimaryAttempts++;
                case "mistral-compact" -> this.mistralCompactAttempts++;
                default -> {
                    // no-op for channels not tracked in summary
                }
            }
        }

        private void captureNodeCoverage(List<RoadmapResponse.RoadmapNode> nodes) {
            if (nodes == null) {
                this.totalNodes = 0;
                this.nodesWithCourses = 0;
                this.nodesWithModules = 0;
                return;
            }

            this.totalNodes = nodes.size();
            this.nodesWithCourses = (int) nodes.stream()
                    .filter(node -> node.getSuggestedCourseIds() != null && !node.getSuggestedCourseIds().isEmpty())
                    .count();
            this.nodesWithModules = (int) nodes.stream()
                    .filter(node -> node.getSuggestedModuleIds() != null && !node.getSuggestedModuleIds().isEmpty())
                    .count();
        }
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
            List<RoadmapResponse.SkillDependency> skillDependencies,
            List<String> graphWarnings) {
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

    private SummaryProgressStats resolveSummaryProgressStats(RoadmapSession session) {
        List<RoadmapResponse.RoadmapNode> nodes = roadmapCompletionSyncService.extractNodes(session);
        int totalQuests = !nodes.isEmpty()
                ? nodes.size()
                : session.getTotalNodes() != null ? session.getTotalNodes() : 0;

        if (totalQuests <= 0) {
            return new SummaryProgressStats(0, 0, 0);
        }

        if (nodes.isEmpty()) {
            Long completedCount = progressRepository.countCompletedBySessionId(session.getId());
            int completed = completedCount != null ? completedCount.intValue() : 0;
            return new SummaryProgressStats(
                    totalQuests,
                    completed,
                    resolveFallbackSummaryProgressPercentage(totalQuests, completed));
        }

        Map<String, RoadmapResponse.QuestProgress> storedProgressMap =
                roadmapCompletionSyncService.loadStoredProgressMap(session.getId());
        Map<String, RoadmapResponse.QuestProgress> resolvedProgressMap =
                roadmapCompletionSyncService.overlayDerivedProgressSnapshot(
                        session,
                        nodes,
                        storedProgressMap);

        Set<String> nodeIds = nodes.stream()
                .map(RoadmapResponse.RoadmapNode::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        if (nodeIds.isEmpty()) {
            Long completedCount = progressRepository.countCompletedBySessionId(session.getId());
            int completed = completedCount != null ? completedCount.intValue() : 0;
            return new SummaryProgressStats(
                    totalQuests,
                    completed,
                    resolveFallbackSummaryProgressPercentage(totalQuests, completed));
        }

        int completedQuests = (int) nodeIds.stream()
                .map(resolvedProgressMap::get)
                .filter(Objects::nonNull)
                .filter(progress -> UserRoadmapProgress.ProgressStatus.COMPLETED.name()
                        .equals(progress.getStatus()))
                .count();

        RoadmapProgressCalculator.ProgressCalculation calc =
                RoadmapProgressCalculator.calculate(nodes, resolvedProgressMap);
        double rawPercentage = calc.totalWeight() > 0.0
                ? calc.completionPercentage()
                : resolveFallbackSummaryProgressPercentage(totalQuests, completedQuests);
        double progressPercentage = Math.round(Math.max(0.0, Math.min(100.0, rawPercentage)) * 10.0) / 10.0;
        int responseTotalQuests = calc.totalQuests() > 0 ? calc.totalQuests() : totalQuests;

        return new SummaryProgressStats(responseTotalQuests, completedQuests, progressPercentage);
    }

    private int resolveFallbackSummaryProgressPercentage(int totalQuests, int completedQuests) {
        if (totalQuests <= 0) {
            return 0;
        }
        return clampProgressPercentage((completedQuests * 100) / totalQuests);
    }

    private int clampProgressPercentage(int value) {
        return Math.max(0, Math.min(100, value));
    }

    /**
     * Get all roadmap sessions (Admin)
     */
    @Transactional(readOnly = true)
    public List<RoadmapSessionSummary> getAllRoadmaps() {
        List<RoadmapSession> sessions = roadmapSessionRepository.findAllByOrderByCreatedAtDesc();
        List<RoadmapSessionSummary> summaries = new ArrayList<>();

        for (RoadmapSession session : sessions) {
            SummaryProgressStats progressStats = resolveSummaryProgressStats(session);
            int totalQuests = progressStats.totalQuests();
            int completed = progressStats.completedQuests();
            double progressPercentage = progressStats.progressPercentage();

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
                    .status(session.getStatus() != null ? session.getStatus().name() : "ACTIVE")
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
    public List<RoadmapSessionSummary> getUserRoadmaps(Long userId, boolean includeDeleted) {
        List<RoadmapSession> sessions = includeDeleted
                ? roadmapSessionRepository.findByUserIdOrderByCreatedAtDesc(userId)
                : roadmapSessionRepository.findByUserIdAndStatusNotDeleted(userId);
        List<RoadmapSessionSummary> summaries = new ArrayList<>();

        for (RoadmapSession session : sessions) {
            SummaryProgressStats progressStats = resolveSummaryProgressStats(session);
            int totalQuests = progressStats.totalQuests();
            int completed = progressStats.completedQuests();
            double progressPercentage = progressStats.progressPercentage();

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
                    .status(session.getStatus() != null ? session.getStatus().name() : "ACTIVE")
                    .createdAt(session.getCreatedAt())
                    .build();

            summaries.add(summary);
        }

        return summaries;
    }

    @Transactional(readOnly = true)
    public List<RoadmapSessionSummary> getUserDeletedRoadmaps(Long userId) {
        List<RoadmapSession> sessions = roadmapSessionRepository.findByUserIdAndStatusDeleted(userId);
        List<RoadmapSessionSummary> summaries = new ArrayList<>();

        for (RoadmapSession session : sessions) {
            SummaryProgressStats progressStats = resolveSummaryProgressStats(session);
            int totalQuests = progressStats.totalQuests();
            int completed = progressStats.completedQuests();
            double progressPercentage = progressStats.progressPercentage();

            @SuppressWarnings("deprecation")
            RoadmapSessionSummary summary = RoadmapSessionSummary.builder()
                    .sessionId(session.getId())
                    .title(session.getTitle())
                    .roadmapMode(session.getRoadmapMode())
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
                    .status(session.getStatus() != null ? session.getStatus().name() : "ACTIVE")
                    .createdAt(session.getCreatedAt())
                    .build();

            summaries.add(summary);
        }

        return summaries;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getUserRoadmapStatusCounts(Long userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("active", 0L);
        counts.put("paused", 0L);
        counts.put("deleted", 0L);

        try {
            List<Object[]> rows = roadmapSessionRepository.countGroupedByStatusForUser(userId);
            for (Object[] row : rows) {
                if (row == null || row.length < 2) {
                    continue;
                }

                String status = row[0] == null
                        ? ""
                        : row[0].toString().trim().toUpperCase(Locale.ROOT);
                long count = row[1] instanceof Number ? ((Number) row[1]).longValue() : 0L;

                if ("ACTIVE".equals(status)) {
                    counts.put("active", count);
                } else if ("PAUSED".equals(status)) {
                    counts.put("paused", count);
                } else if ("DELETED".equals(status)) {
                    counts.put("deleted", count);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load roadmap status counts for user {}: {}", userId, e.getMessage());
        }

        long total = counts.getOrDefault("active", 0L)
                + counts.getOrDefault("paused", 0L)
                + counts.getOrDefault("deleted", 0L);
        counts.put("total", total);
        return counts;
    }

    /**
     * Get a specific roadmap session with full details (supports V1 and V2 schemas)
     */
    @Transactional
    public RoadmapResponse getRoadmapById(Long sessionId, Long userId) {
        RoadmapSession session = roadmapSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found"));
        if (session.getStatus() == RoadmapStatus.DELETED) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found");
        }

        // Block access to PAUSED roadmaps — user must reactivate first
        if (session.getStatus() == RoadmapStatus.PAUSED) {
            throw new ApiException(ErrorCode.FORBIDDEN,
                    "Roadmap này đang tạm dừng. Hãy kích hoạt lại roadmap để tiếp tục học.");
        }

        // Detect schema version and parse accordingly
        Integer schemaVersion = session.getSchemaVersion() != null ? session.getSchemaVersion() : 1;

        if (schemaVersion >= 2) {
            // V2: Parse full structure
            ParsedRoadmap parsed = validateAndParseRoadmapV2(session.getRoadmapJson());

            // Load progress data
            Map<String, RoadmapResponse.QuestProgress> progressMap = resolveProgressData(session, parsed.nodes());
            List<String> warnings = new ArrayList<>();
            if (parsed.graphWarnings() != null) {
                warnings.addAll(parsed.graphWarnings());
            }
            warnings.addAll(computeWarnings(parsed.metadata(), parsed.statistics()));

            return RoadmapResponse.builder()
                    .sessionId(session.getId())
                    .roadmapStatus(session.getStatus() != null ? session.getStatus().name() : "ACTIVE")
                    .metadata(parsed.metadata())
                    .roadmap(computeNodeStatuses(parsed.nodes(), progressMap))
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
            Map<String, RoadmapResponse.QuestProgress> progressMap = resolveProgressData(session, nodes);

            return RoadmapResponse.builder()
                    .sessionId(session.getId())
                    .roadmapStatus(session.getStatus() != null ? session.getStatus().name() : "ACTIVE")
                    .metadata(metadata)
                    .roadmap(computeNodeStatuses(nodes, progressMap))
                    .statistics(statistics)
                    .learningTips(List.of()) // Empty list for V1 data
                    .warnings(List.of())
                    .overview(null)
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

    private Map<String, RoadmapResponse.QuestProgress> resolveProgressData(
            RoadmapSession session,
            List<RoadmapResponse.RoadmapNode> nodes) {
        Map<String, RoadmapResponse.QuestProgress> storedProgressMap = loadProgressData(session.getId());
        return roadmapCompletionSyncService.overlayDerivedProgress(session, nodes, storedProgressMap);
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
        return updateProgressInternal(sessionId, userId, request, true);
        }

        private ProgressResponse updateProgressInternal(
            Long sessionId,
            Long userId,
            UpdateProgressRequest request,
            boolean enforceSequentialLockingCheck) {
        log.info("Updating progress for session {} - quest: {}, completed: {}",
                sessionId, request.getQuestId(), request.getCompleted());

        // Verify session belongs to user
        RoadmapSession session = roadmapSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found"));
        if (session.getStatus() == RoadmapStatus.DELETED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot update a deleted roadmap");
        }

        // Sequential Locking: Verify prerequisites are completed before allowing progress
        if (Boolean.TRUE.equals(request.getCompleted()) && enforceSequentialLockingCheck) {
            enforceSequentialLocking(session, request.getQuestId());
        }

        // Evidence gate + uncomplete guard — only for journey-linked sessions.
        Journey gateJourney = journeyRepository.findByRoadmapSessionId(sessionId).orElse(null);
        if (gateJourney != null) {
            if (Boolean.TRUE.equals(request.getCompleted())) {
                assertEvidenceGatePassed(gateJourney, request.getQuestId());
            } else {
                // Block un-completing a node that was already confirmed via evidence gate.
                // Only internal flows (mentor review, rework) should reset completion.
                UserRoadmapProgress existing = progressRepository
                        .findBySessionIdAndQuestId(sessionId, request.getQuestId())
                        .orElse(null);
                if (existing != null
                        && existing.getStatus() == UserRoadmapProgress.ProgressStatus.COMPLETED) {
                    throw new ApiException(ErrorCode.CONFLICT,
                            "Node này đã được xác nhận hoàn thành và không thể bỏ chọn qua API này.");
                }
            }
        }

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

        // Sync Journey.progressPercentage for journey-linked sessions so FE journey-complete
        // button reflects reality even when completing via this older progress endpoint.
        if (gateJourney != null) {
            syncJourneyProgressPercentage(gateJourney, session);
        }

        Integer schemaVersion = session.getSchemaVersion() != null ? session.getSchemaVersion() : 1;

        // Parse nodes once — reused for resolveProgressData and weighted calculator.
        List<RoadmapResponse.RoadmapNode> parsedNodes = null;
        int totalQuests;
        boolean weightedMode = true;
        try {
            if (schemaVersion >= 2) {
                parsedNodes = validateAndParseRoadmapV2(session.getRoadmapJson()).nodes();
            } else {
                parsedNodes = parseNodesFromV1Json(session.getRoadmapJson());
            }
            totalQuests = parsedNodes.size();
        } catch (Exception e) {
            log.warn("Failed to parse roadmap for session {}, falling back to count-based progress", session.getId());
            List<UserRoadmapProgress> allProgress = progressRepository.findBySessionId(sessionId);
            totalQuests = session.getTotalNodes() != null ? session.getTotalNodes() : allProgress.size();
            parsedNodes = List.of();
            weightedMode = false;
        }

        Map<String, RoadmapResponse.QuestProgress> resolvedProgressMap =
                resolveProgressData(session, parsedNodes);

        // Weighted progress via RoadmapProgressCalculator
        RoadmapProgressCalculator.ProgressCalculation calc =
                RoadmapProgressCalculator.calculate(parsedNodes, resolvedProgressMap);

        // Count-based fallback when nodes could not be parsed
        int completedQuests = calc.totalQuests() > 0
                ? calc.completedQuests()
                : (int) resolvedProgressMap.values().stream()
                        .filter(p -> UserRoadmapProgress.ProgressStatus.COMPLETED.name().equals(p.getStatus()))
                        .count();
        double completionPercentage = weightedMode
                ? calc.completionPercentage()
                : (totalQuests > 0 ? completedQuests * 100.0 / totalQuests : 0.0);
        String progressMode = weightedMode ? "WEIGHTED_IMPORTANCE" : "COUNT_FALLBACK";
        // Use calc.totalQuests() when weighted so totalQuests excludes blank-ID nodes,
        // keeping it consistent with completedQuests and completionPercentage denominators.
        int responseTotalQuests = weightedMode && calc.totalQuests() > 0
                ? calc.totalQuests()
                : totalQuests;

        log.info("Progress updated - {}/{} quests completed ({}%, mode={})",
                completedQuests, responseTotalQuests,
                String.format("%.1f", completionPercentage), progressMode);

        return ProgressResponse.builder()
                .sessionId(sessionId)
                .questId(request.getQuestId())
                .completed(request.getCompleted())
                .stats(ProgressResponse.ProgressStats.builder()
                        .totalQuests(responseTotalQuests)
                        .completedQuests(completedQuests)
                        .completionPercentage(completionPercentage)
                        .completedWeight(calc.completedWeight())
                        .totalWeight(calc.totalWeight())
                        .progressMode(progressMode)
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
    private ValidationResult validateGoalWithAI(String goal, Long userId) {
        log.info("🤖 AI Goal Validation Stage 1: Checking goal='{}'", goal);

        String validationPrompt = buildGoalValidationPrompt(goal);
        long startTime = System.currentTimeMillis();
        String aiResponse = null;

        try {
            // Use Gemini RestClient for validation
            aiResponse = callGeminiDirectly(validationPrompt, geminiModel);
            long latencyMs = System.currentTimeMillis() - startTime;

            // Parse AI response
            ValidationResult result = parseAIValidationResponse(aiResponse, goal);

            // Record token usage for successful Gemini validation
            recordValidationSuccess(AiProviderType.GEMINI, geminiModel, userId,
                    validationPrompt, aiResponse, latencyMs);

            return result;

        } catch (Exception e) {
            long geminiLatencyMs = System.currentTimeMillis() - startTime;
            log.warn("⚠️ AI validation (Gemini) failed, attempting fallback to Mistral: {}", e.getMessage());

            // Record Gemini failure
            recordValidationFailure(AiProviderType.GEMINI, geminiModel, userId,
                    validationPrompt, e.getMessage(), geminiLatencyMs);

            // Try Mistral fallback
            long mistralStartTime = System.currentTimeMillis();
            try {
                aiResponse = callMistralAPI(validationPrompt);
                long mistralLatencyMs = System.currentTimeMillis() - mistralStartTime;

                ValidationResult result = parseAIValidationResponse(aiResponse, goal);

                // Record token usage for successful Mistral validation
                recordValidationSuccess(AiProviderType.MISTRAL, "mistral-large-latest", userId,
                        validationPrompt, aiResponse, mistralLatencyMs);

                return result;

            } catch (Exception ex) {
                long mistralLatencyMs = System.currentTimeMillis() - mistralStartTime;
                log.warn("⚠️ AI validation (Mistral) failed, falling back to basic validation: {}", ex.getMessage());

                // Record Mistral failure
                recordValidationFailure(AiProviderType.MISTRAL, "mistral-large-latest", userId,
                        validationPrompt, ex.getMessage(), mistralLatencyMs);
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

        // Strip markdown code block markers (Mistral sometimes wraps output in ```...```)
        String cleaned = aiResponse.trim()
                .replaceAll("^```[a-z]*\\s*", "")
                .replaceAll("\\s*```$", "")
                .trim();

        String[] parts = cleaned.split("\\|");

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

    // =========================================================================
    // Phase 1: New Methods — Tree Node Status, Sequential Locking, Course Validation
    // =========================================================================

    /**
     * Match AI-generated roadmap nodes to real courses on the system using keyword matching.
     *
     * Strategy (2-layer):
     * - Layer 1 (primary): Extract keywords from node title + key_concepts + practical_exercises,
     *   then find PUBLIC courses whose title/description contains those keywords (case-insensitive).
     * - Layer 2 (supplementary): If CourseSkill table has data, also match via skill names.
     *
     * This ensures suggestedCourseIds points to real, enrolled-able courses rather than
     * hallucinated IDs. Already-populated IDs from AI are preserved; only nodes without IDs
     * get newly matched ones appended.
     */
    private void matchNodesToRealCourses(List<RoadmapResponse.RoadmapNode> nodes) {
        // Skip if no PUBLIC courses exist on the system
        long totalPublicCourses = courseRepository.countByStatus(CourseStatus.PUBLIC);
        if (totalPublicCourses == 0) {
            log.info("ℹ️ No PUBLIC courses on system — skipping course matching for {} nodes", nodes.size());
            return;
        }

        log.info("🔗 Matching {} roadmap nodes to real PUBLIC courses ({} total PUBLIC courses available)",
                nodes.size(), totalPublicCourses);

        int matchedCount = 0;
        int skippedNoKeywords = 0;
        int skippedNoCandidates = 0;
        int mergedWithExistingIds = 0;
        int newlyAssignedIds = 0;

        for (RoadmapResponse.RoadmapNode node : nodes) {
            List<String> existingIds = node.getSuggestedCourseIds();
            boolean hasAiProvidedIds = existingIds != null && !existingIds.isEmpty();

            // Extract keywords from node content
            Set<String> keywords = new HashSet<>();
            String title = node.getTitle() != null ? node.getTitle() : "";
            keywords.addAll(extractKeywords(title));

            if (node.getKeyConcepts() != null) {
                for (String concept : node.getKeyConcepts()) {
                    if (concept != null) keywords.addAll(extractKeywords(concept));
                }
            }
            if (node.getPracticalExercises() != null) {
                for (String exercise : node.getPracticalExercises()) {
                    if (exercise != null) keywords.addAll(extractKeywords(exercise));
                }
            }
            if (node.getLearningObjectives() != null) {
                for (String obj : node.getLearningObjectives()) {
                    if (obj != null) keywords.addAll(extractKeywords(obj));
                }
            }

            if (keywords.isEmpty()) {
                skippedNoKeywords++;
                log.debug("[RoadmapCourseMap] Node '{}' skipped: no extractable keywords", node.getId());
                continue;
            }

            // Build a combined topic string from extracted keywords for BM25 pre-selection
            String topicString = String.join(" ", keywords);

            // Use the unified BM25 catalog service instead of LIKE queries
            List<CourseCatalogEntry> candidates = aiCourseCatalogService.preSelectCourses(topicString, 5);
                if (log.isDebugEnabled() && !candidates.isEmpty()) {
                String topCandidateSummary = candidates.stream()
                    .limit(3)
                    .map(entry -> entry.getId() + ":" + previewHead(entry.getTitle(), 32) + "#" + entry.getScore())
                    .collect(Collectors.joining(" | "));
                log.debug("[RoadmapCourseMap] Node '{}' keywords={} candidates={} top3=[{}]",
                    node.getId(),
                    keywords.size(),
                    candidates.size(),
                    topCandidateSummary);
                }

            if (!candidates.isEmpty()) {
                matchedCount++;
                List<Long> matchedCourseIds = new ArrayList<>();
                for (CourseCatalogEntry entry : candidates) {
                    matchedCourseIds.add(entry.getId());
                }

                if (hasAiProvidedIds) {
                    // Merge: AI-provided IDs + newly matched IDs (deduped)
                    Set<String> merged = new HashSet<>(existingIds);
                    for (Long cid : matchedCourseIds) {
                        merged.add(String.valueOf(cid));
                    }
                    node.setSuggestedCourseIds(new ArrayList<>(merged));
                    mergedWithExistingIds++;
                } else {
                    // Set newly matched IDs as the only suggestion
                    List<String> idStrings = matchedCourseIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList());
                    node.setSuggestedCourseIds(idStrings);
                    newlyAssignedIds++;
                }
            } else {
                skippedNoCandidates++;
                log.debug("[RoadmapCourseMap] Node '{}' keywords={} but no catalog candidates", node.getId(), keywords.size());
            }
        }

        log.info(
                "✅ Course matching complete: matched={}/{}, mergedExisting={}, newlyAssigned={}, skippedNoKeywords={}, skippedNoCandidates={}",
                matchedCount,
                nodes.size(),
                mergedWithExistingIds,
                newlyAssignedIds,
                skippedNoKeywords,
                skippedNoCandidates);
    }

    /**
     * Extract meaningful keywords from a text string.
     * Removes common Vietnamese/English stopwords and returns cleaned lowercase tokens.
     */
    private Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();
        if (text == null || text.isBlank()) return keywords;

        String lower = text.toLowerCase(Locale.ROOT);
        // Remove punctuation and common delimiters
        String cleaned = lower.replaceAll("[\\[\\]{}()\"'.,;:!?\\-/\\\\|\\n\\r\\t]", " ");
        String[] tokens = cleaned.split("\\s+");

        Set<String> stopwords = new HashSet<>(List.of(
                "và", "của", "là", "có", "được", "trong", "cho", "với", "không", "để",
                "theo", "về", "từ", "ra", "vào", "hay", "vẫn", "còn", "sẽ", "này", "khi",
                "đã", "một", "các", "những", "bạn", "học", "hành", "tập", "lộ", "trình",
                "vien", "va", "de", "duoc", "trong", "cho", "voi", "khong", "để",
                "theo", "ve", "tu", "ra", "vao", "hay", "van", "con", "se", "nay", "khi",
                "da", "mot", "cac", "nhung", "ban", "hoc", "hanh", "tap", "lo", "trinh",
                "and", "or", "the", "a", "an", "to", "in", "for", "of", "is", "it", "on",
                "with", "as", "by", "at", "from", "this", "that", "be", "are", "was",
                "will", "can", "you", "your", "how", "what", "when", "where", "why"
        ));

        for (String token : tokens) {
            token = token.trim();
            if (token.length() >= 3 && !stopwords.contains(token) && !token.matches("\\d+")) {
                keywords.add(token);
            }
        }
        return keywords;
    }

    /**
     * Validate suggestedCourseIds against real DB and strip fake/hallucinated IDs.
     * AI may generate plausible-looking IDs — this ensures only real course IDs remain.
     */
    private void validateAndStripFakeCourseIds(List<RoadmapResponse.RoadmapNode> nodes) {
        // Collect all suggested course IDs from all nodes
        Set<Long> allSuggestedIds = new HashSet<>();
        for (RoadmapResponse.RoadmapNode node : nodes) {
            if (node.getSuggestedCourseIds() != null) {
                for (String idStr : node.getSuggestedCourseIds()) {
                    try {
                        allSuggestedIds.add(Long.parseLong(idStr));
                    } catch (NumberFormatException e) {
                        // Non-numeric ID = definitely hallucinated, will be stripped
                        log.warn("🚫 Hallucinated non-numeric course ID '{}' in node '{}'", idStr, node.getId());
                    }
                }
            }
        }

        if (allSuggestedIds.isEmpty()) {
            return; // No course IDs to validate
        }

        log.info("🔍 Validating {} suggested course ID(s) against DB", allSuggestedIds.size());

        // Batch query DB to find which IDs actually exist (finds PUBLIC courses)
        // NOTE: /courses/batch endpoint only returns PUBLIC courses. If a course was
        // created as DRAFT/INACTIVE, it will be stripped here even if it exists in DB.
        // User should publish courses to make them visible in roadmap.
        List<Course> foundCourses = courseRepository.findAllById(allSuggestedIds);
        Map<Long, CourseStatus> foundCourseStatuses = foundCourses.stream()
                .collect(Collectors.toMap(Course::getId, Course::getStatus, (a, b) -> a));

        Set<Long> validIds = foundCourses.stream()
                .map(Course::getId)
                .collect(Collectors.toSet());

        int totalStripped = 0;
        final int[] skippedNonPublic = {0};

        // Strip invalid IDs from each node
        for (RoadmapResponse.RoadmapNode node : nodes) {
            if (node.getSuggestedCourseIds() != null && !node.getSuggestedCourseIds().isEmpty()) {
                List<String> originalIds = node.getSuggestedCourseIds();
                List<String> validatedIds = originalIds.stream()
                        .filter(idStr -> {
                            try {
                                long id = Long.parseLong(idStr);
                                if (!validIds.contains(id)) {
                                    return false;
                                }
                                CourseStatus status = foundCourseStatuses.get(id);
                                if (status != CourseStatus.PUBLIC) {
                                    skippedNonPublic[0]++;
                                    return false;
                                }
                                return true;
                            } catch (NumberFormatException e) {
                                return false;
                            }
                        })
                        .collect(Collectors.toList());

                int stripped = originalIds.size() - validatedIds.size();
                if (stripped > 0) {
                    log.warn("🚫 Stripped {} course ID(s) from node '{}': {} → {}",
                            stripped, node.getId(), originalIds, validatedIds);
                    totalStripped += stripped;
                }

                node.setSuggestedCourseIds(validatedIds);
            }
        }

        if (totalStripped > 0) {
            log.info("🛡️ Anti-hallucination: Stripped {} fake course ID(s) total across all nodes ({} skipped: non-PUBLIC status)",
                    totalStripped, skippedNonPublic[0]);
        } else {
            log.info("✅ All {} suggested course ID(s) validated and retained", allSuggestedIds.size());
        }
    }

    /**
     * Compute nodeStatus for each node based on progress data and prerequisites graph.
     * Status flow: LOCKED → AVAILABLE → IN_PROGRESS → COMPLETED
     */
    private List<RoadmapResponse.RoadmapNode> computeNodeStatuses(
            List<RoadmapResponse.RoadmapNode> nodes,
            Map<String, RoadmapResponse.QuestProgress> progressMap) {

        Set<String> completedNodeIds = new HashSet<>();
        Set<String> inProgressNodeIds = new HashSet<>();
        if (progressMap != null) {
            for (Map.Entry<String, RoadmapResponse.QuestProgress> entry : progressMap.entrySet()) {
                if (entry.getValue() != null && "COMPLETED".equals(entry.getValue().getStatus())) {
                    completedNodeIds.add(entry.getKey());
                } else if (entry.getValue() != null && entry.getValue().getProgress() != null
                        && entry.getValue().getProgress() > 0) {
                    inProgressNodeIds.add(entry.getKey());
                }
            }
        }

        Map<String, RoadmapResponse.RoadmapNode> byId = nodes.stream()
                .filter(node -> node != null && node.getId() != null && !node.getId().isBlank())
                .collect(Collectors.toMap(
                        RoadmapResponse.RoadmapNode::getId,
                        node -> node,
                        (left, right) -> left,
                        LinkedHashMap::new));

        Set<String> unlockedMainNodeIds = new HashSet<>();
        boolean encounteredFirstPendingMain = false;
        for (RoadmapResponse.RoadmapNode node : nodes.stream()
                .filter(this::isMainRoadmapNode)
                .sorted(this::compareRoadmapLearningOrder)
                .toList()) {
            if (node == null || node.getId() == null || node.getId().isBlank()) {
                continue;
            }
            String nodeId = node.getId();

            if (completedNodeIds.contains(nodeId)) {
                node.setNodeStatus("COMPLETED");
                continue;
            }

            if (!encounteredFirstPendingMain) {
                encounteredFirstPendingMain = true;

                if (inProgressNodeIds.contains(nodeId)) {
                    node.setNodeStatus("IN_PROGRESS");
                    unlockedMainNodeIds.add(nodeId);
                } else if (arePrerequisitesCompleted(node, completedNodeIds)) {
                    node.setNodeStatus("AVAILABLE");
                    unlockedMainNodeIds.add(nodeId);
                } else {
                    node.setNodeStatus("LOCKED");
                }
            } else {
                node.setNodeStatus("LOCKED");
            }
        }

        for (RoadmapResponse.RoadmapNode node : nodes) {
            if (node == null || node.getId() == null || node.getId().isBlank() || isMainRoadmapNode(node)) {
                continue;
            }
            String nodeId = node.getId();

            if (completedNodeIds.contains(nodeId)) {
                node.setNodeStatus("COMPLETED");
                continue;
            }
            if (inProgressNodeIds.contains(nodeId)) {
                node.setNodeStatus("IN_PROGRESS");
                continue;
            }

            node.setNodeStatus(isSideNodeUnlocked(node, byId, completedNodeIds, unlockedMainNodeIds)
                    ? "AVAILABLE"
                    : "LOCKED");
        }

        return nodes;
    }

    private boolean arePrerequisitesCompleted(
            RoadmapResponse.RoadmapNode node,
            Set<String> completedNodeIds) {
        if (node == null || node.getPrerequisites() == null || node.getPrerequisites().isEmpty()) {
            return true;
        }

        for (String prereqId : node.getPrerequisites()) {
            if (!completedNodeIds.contains(prereqId)) {
                return false;
            }
        }
        return true;
    }

    private RoadmapResponse.RoadmapNode findFirstSequentialPlayableMainNode(
            List<RoadmapResponse.RoadmapNode> nodes,
            Map<String, RoadmapResponse.QuestProgress> progressMap) {
        Set<String> completedNodeIds = new HashSet<>();
        Set<String> inProgressNodeIds = new HashSet<>();
        if (progressMap != null) {
            for (Map.Entry<String, RoadmapResponse.QuestProgress> entry : progressMap.entrySet()) {
                if (entry.getValue() != null && "COMPLETED".equals(entry.getValue().getStatus())) {
                    completedNodeIds.add(entry.getKey());
                } else if (entry.getValue() != null && entry.getValue().getProgress() != null
                        && entry.getValue().getProgress() > 0) {
                    inProgressNodeIds.add(entry.getKey());
                }
            }
        }

        for (RoadmapResponse.RoadmapNode node : nodes.stream()
                .filter(this::isMainRoadmapNode)
                .sorted(this::compareRoadmapLearningOrder)
                .toList()) {
            if (node == null || node.getId() == null || node.getId().isBlank()) {
                continue;
            }

            String nodeId = node.getId();
            if (completedNodeIds.contains(nodeId)) {
                continue;
            }

            if (inProgressNodeIds.contains(nodeId) || arePrerequisitesCompleted(node, completedNodeIds)) {
                return node;
            }

            return null;
        }

        return null;
    }

    private boolean isSideNodePlayable(
            RoadmapResponse.RoadmapNode node,
            List<RoadmapResponse.RoadmapNode> nodes,
            Map<String, RoadmapResponse.QuestProgress> progressMap) {
        if (node == null || isMainRoadmapNode(node)) {
            return false;
        }

        Set<String> completedNodeIds = new HashSet<>();
        Set<String> unlockedMainNodeIds = new HashSet<>();
        if (progressMap != null) {
            for (Map.Entry<String, RoadmapResponse.QuestProgress> entry : progressMap.entrySet()) {
                RoadmapResponse.QuestProgress progress = entry.getValue();
                if (progress != null && "COMPLETED".equals(progress.getStatus())) {
                    completedNodeIds.add(entry.getKey());
                }
            }
        }

        RoadmapResponse.RoadmapNode firstPlayableMain = findFirstSequentialPlayableMainNode(nodes, progressMap);
        if (firstPlayableMain != null && firstPlayableMain.getId() != null) {
            unlockedMainNodeIds.add(firstPlayableMain.getId());
        }

        Map<String, RoadmapResponse.RoadmapNode> byId = nodes.stream()
                .filter(candidate -> candidate != null && candidate.getId() != null && !candidate.getId().isBlank())
                .collect(Collectors.toMap(
                        RoadmapResponse.RoadmapNode::getId,
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new));

        return isSideNodeUnlocked(node, byId, completedNodeIds, unlockedMainNodeIds);
    }

    private boolean isSideNodeUnlocked(
            RoadmapResponse.RoadmapNode sideNode,
            Map<String, RoadmapResponse.RoadmapNode> byId,
            Set<String> completedNodeIds,
            Set<String> unlockedMainNodeIds) {
        if (sideNode == null) {
            return false;
        }

        String parentId = sideNode.getParentId();
        RoadmapResponse.RoadmapNode parent = parentId != null ? byId.get(parentId) : null;
        if (parent != null && isMainRoadmapNode(parent)) {
            return completedNodeIds.contains(parentId) || unlockedMainNodeIds.contains(parentId);
        }

        return arePrerequisitesCompleted(sideNode, completedNodeIds);
    }

    private boolean isMainRoadmapNode(RoadmapResponse.RoadmapNode node) {
        return node != null
                && (node.getType() == RoadmapResponse.RoadmapNode.NodeType.MAIN || Boolean.TRUE.equals(node.getIsCore()));
    }

    private int compareRoadmapLearningOrder(
            RoadmapResponse.RoadmapNode left,
            RoadmapResponse.RoadmapNode right) {
        int leftMain = left != null && left.getMainPathIndex() != null ? left.getMainPathIndex() : Integer.MAX_VALUE;
        int rightMain = right != null && right.getMainPathIndex() != null ? right.getMainPathIndex() : Integer.MAX_VALUE;
        if (leftMain != rightMain) {
            return Integer.compare(leftMain, rightMain);
        }

        int leftOrder = left != null && left.getOrderIndex() != null ? left.getOrderIndex() : Integer.MAX_VALUE;
        int rightOrder = right != null && right.getOrderIndex() != null ? right.getOrderIndex() : Integer.MAX_VALUE;
        if (leftOrder != rightOrder) {
            return Integer.compare(leftOrder, rightOrder);
        }

        String leftId = left != null ? left.getId() : null;
        String rightId = right != null ? right.getId() : null;
        if (leftId == null && rightId == null) {
            return 0;
        }
        if (leftId == null) {
            return 1;
        }
        if (rightId == null) {
            return -1;
        }
        return leftId.compareTo(rightId);
    }

    /**
     * Enforce sequential locking: cannot complete a node unless it is the next playable node in roadmap order.
     * Throws FORBIDDEN (403) if the caller tries to skip ahead.
     */
    private void enforceSequentialLocking(RoadmapSession session, String questId) {
        try {
            Integer schemaVersion = session.getSchemaVersion() != null ? session.getSchemaVersion() : 1;
            List<RoadmapResponse.RoadmapNode> nodes;

            if (schemaVersion >= 2) {
                ParsedRoadmap parsed = validateAndParseRoadmapV2(session.getRoadmapJson());
                nodes = parsed.nodes();
            } else {
                nodes = parseNodesFromV1Json(session.getRoadmapJson());
            }

            Map<String, RoadmapResponse.QuestProgress> progressSnapshot = roadmapCompletionSyncService.overlayDerivedProgressSnapshot(
                    session,
                    nodes,
                    loadProgressData(session.getId()));
            RoadmapResponse.RoadmapNode requestedNode = nodes.stream()
                    .filter(node -> node != null && questId.equals(node.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "Roadmap node not found"));

            if (!isMainRoadmapNode(requestedNode)) {
                if (isSideNodePlayable(requestedNode, nodes, progressSnapshot)) {
                    return;
                }
                throw new ApiException(ErrorCode.FORBIDDEN,
                        "Bạn cần mở node chính liên quan trước khi hoàn thành node phụ.");
            }

            RoadmapResponse.RoadmapNode nextPlayableNode = findFirstSequentialPlayableMainNode(nodes, progressSnapshot);
            if (nextPlayableNode == null) {
                throw new ApiException(ErrorCode.FORBIDDEN,
                        "Bạn cần hoàn thành node trước đó trước khi mở node tiếp theo.");
            }

            if (!questId.equals(nextPlayableNode.getId())) {
                throw new ApiException(ErrorCode.FORBIDDEN,
                        String.format("Bạn cần hoàn thành node '%s' trước.", nextPlayableNode.getTitle()));
            }
        } catch (ApiException e) {
            throw e; // Re-throw ApiException (FORBIDDEN)
        } catch (Exception e) {
            log.warn("Sequential lock check failed for quest '{}' in session {} — allowing update: {}",
                    questId, session.getId(), e.getMessage());
            // Fail-open: if we can't parse the roadmap, allow the update
        }
    }

    // =========================================================================
    // Roadmap Lifecycle Management: Activate, Pause, Delete
    // =========================================================================

    private void ensureCanStartAnotherActiveRoadmap(Long userId, String message) {
        long activeRoadmapCount = roadmapSessionRepository.countActiveByUserId(userId);
        if (activeRoadmapCount >= MAX_CONCURRENT_ACTIVE_ROADMAPS) {
            throw new ApiException(ErrorCode.CONFLICT, message);
        }
    }

    /**
     * Activate a roadmap while allowing up to 5 active roadmaps per user.
     */
    @Override
    @Transactional
    public void activateRoadmap(Long sessionId, Long userId) {
        RoadmapSession session = roadmapSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found"));

        if (session.getStatus() == RoadmapStatus.DELETED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot activate a deleted roadmap");
        }

        if (session.getStatus() == RoadmapStatus.ACTIVE) {
            return; // Already active, no-op
        }

        ensureCanStartAnotherActiveRoadmap(userId,
                "Bạn đang học tối đa 5 lộ trình cùng lúc. Hãy tạm dừng hoặc xóa một lộ trình trước khi kích hoạt thêm.");

        // Activate the requested roadmap
        session.setStatus(RoadmapStatus.ACTIVE);
        // flush immediately so concurrent reads see the change
        roadmapSessionRepository.saveAndFlush(session);

        log.info("✅ Activated roadmap {} for user {}", sessionId, userId);
    }

    /**
     * Pause a roadmap (set to PAUSED status)
     */
    @Override
    @Transactional
    public void pauseRoadmap(Long sessionId, Long userId) {
        RoadmapSession session = roadmapSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found"));

        if (session.getStatus() == RoadmapStatus.DELETED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot pause a deleted roadmap");
        }

        // Cannot pause the last non-deleted roadmap — user needs at least one active roadmap
        long notDeletedCount = roadmapSessionRepository.countByUserIdAndStatusNotDeleted(userId);
        if (notDeletedCount <= 1) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Không thể tạm dừng roadmap cuối cùng. Mỗi tài khoản cần có ít nhất một roadmap để tiếp tục học.");
        }

        session.setStatus(RoadmapStatus.PAUSED);
        roadmapSessionRepository.saveAndFlush(session);

        // Archive roadmap-linked tasks so they no longer clutter the board.
        int archived = taskBoardService.archiveTasksByRoadmapSession(userId, sessionId);
        log.info("⏸️ Paused roadmap {} for user {} (archived {} tasks)", sessionId, userId, archived);
    }

    /**
     * Soft-delete a roadmap (set to DELETED status, data preserved)
     */
    @Override
    @Transactional
    public void deleteRoadmap(Long sessionId, Long userId) {
        RoadmapSession session = roadmapSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found"));

        if (session.getStatus() == RoadmapStatus.DELETED) {
            log.info("🗑️ Roadmap {} already deleted for user {}", sessionId, userId);
            return;
        }

        // Set status to DELETED and save to database
        session.setStatus(RoadmapStatus.DELETED);
        roadmapSessionRepository.saveAndFlush(session);

        // Archive tasks on soft-delete so board stays clean.
        int archived = taskBoardService.archiveTasksByRoadmapSession(userId, sessionId);
        log.info("🗑️ Soft-deleted roadmap {} for user {} (archived {} tasks)", sessionId, userId, archived);
    }

    @Override
    @Transactional
    public void permanentDeleteRoadmap(Long sessionId, Long userId) {
        RoadmapSession session = roadmapSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found"));

        if (session.getStatus() != RoadmapStatus.DELETED) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Please soft-delete the roadmap first before permanently deleting it");
        }

        // V3 Phase 3: Block permanent deletion if roadmap is still linked to a non-terminal journey.
        journeyRepository.findByRoadmapSessionId(sessionId).ifPresent(journey -> {
            var terminalStatuses = Set.of(
                    Journey.JourneyStatus.COMPLETED,
                    Journey.JourneyStatus.CANCELLED,
                    Journey.JourneyStatus.COMPLETED_UNVERIFIED,
                    Journey.JourneyStatus.COMPLETED_VERIFIED
            );
            if (!terminalStatuses.contains(journey.getStatus())) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Không thể xóa vĩnh viễn roadmap đang liên kết với hành trình chưa hoàn thành.");
            }
        });

        int clearedJourneys = journeyRepository.clearRoadmapSessionId(sessionId);
        progressRepository.deleteBySessionId(sessionId);
        int cleanedTasks = cleanupRoadmapLinksFromTasks(userId, sessionId);
        roadmapSessionRepository.delete(session);

        log.info("🔥 Permanently deleted roadmap {} for user {} (journeysCleared={}, cleanedTasks={})",
                sessionId, userId, clearedJourneys, cleanedTasks);
    }

    /**
     * Restore a soft-deleted roadmap back to PAUSED status.
     * User can then manually activate it if needed.
     */
    @Override
    @Transactional
    public void restoreRoadmap(Long sessionId, Long userId) {
        RoadmapSession session = roadmapSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found"));

        if (session.getStatus() != RoadmapStatus.DELETED) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Can only restore a deleted roadmap");
        }

        // Restore to PAUSED so user can review before activating
        session.setStatus(RoadmapStatus.PAUSED);
        roadmapSessionRepository.saveAndFlush(session);

        // Auto-unarchive tasks so they reappear on the board immediately after restore
        int unarchived = taskBoardService.unarchiveTasksByRoadmapSession(userId, sessionId);
        log.info("♻️ Restored roadmap {} for user {} (set to PAUSED, unarchived {} tasks)",
                sessionId, userId, unarchived);
    }

    @Override
    @Transactional
    public CompleteNodeResponse completeNode(Long sessionId, Long userId, String nodeId) {
        // Verify session ownership
        RoadmapSession session = roadmapSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found"));
        if (session.getStatus() == RoadmapStatus.DELETED) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Cannot complete a deleted roadmap");
        }

        // Validate nodeId exists and is the next sequential playable node before any side-effects.
        // This keeps the roadmap strictly step-by-step and prevents skipping sibling nodes.
        Integer schemaVersion = session.getSchemaVersion() != null ? session.getSchemaVersion() : 1;
        List<RoadmapResponse.RoadmapNode> nodes;
        if (schemaVersion >= 2) {
            ParsedRoadmap parsed = validateAndParseRoadmapV2(session.getRoadmapJson());
            nodes = parsed.nodes();
        } else {
            nodes = parseNodesFromV1Json(session.getRoadmapJson());
        }
        Map<String, RoadmapResponse.QuestProgress> progressSnapshot = roadmapCompletionSyncService.overlayDerivedProgressSnapshot(
                session,
                nodes,
                loadProgressData(session.getId()));
        RoadmapResponse.RoadmapNode requestedNode = nodes.stream()
                .filter(node -> node != null && nodeId.equals(node.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "Roadmap node not found"));

        if (!isMainRoadmapNode(requestedNode)) {
            if (!isSideNodePlayable(requestedNode, nodes, progressSnapshot)) {
                throw new ApiException(ErrorCode.FORBIDDEN,
                        "Bạn cần mở node chính liên quan trước khi hoàn thành node phụ.");
            }
        } else {
            RoadmapResponse.RoadmapNode nextPlayableNode = findFirstSequentialPlayableMainNode(nodes, progressSnapshot);
            if (nextPlayableNode == null) {
                throw new ApiException(ErrorCode.FORBIDDEN,
                        "Bạn cần hoàn thành node trước đó trước khi mở node tiếp theo.");
            }
            if (!nodeId.equals(nextPlayableNode.getId())) {
                throw new ApiException(ErrorCode.FORBIDDEN,
                        String.format("Bạn cần hoàn thành node '%s' trước.", nextPlayableNode.getTitle()));
            }
        }

        // Step 1: Mark all linked tasks done (single batch sync after)
        var taskResult = taskBoardService.completeAllTasksForNode(userId, sessionId, nodeId);

        // Step 2: Mark node complete — evidence gate + journey progress sync run inside
        // updateProgressInternal. If ApiException is thrown it propagates through the same
        // @Transactional boundary, rolling back task updates from Step 1 as well.
        int totalTasks = taskResult.getDoneCount();
        boolean nodeCompleted = true;
        String message;
        if (totalTasks == 0) {
            message = "Node marked as complete.";
        } else {
            message = String.format("%d task(s) marked done. Node marked as complete.", totalTasks);
        }

        // updateProgressInternal enforces the evidence gate and syncs journey progress.
        updateProgressInternal(sessionId, userId,
            UpdateProgressRequest.builder().questId(nodeId).completed(true).build(), false);

        return CompleteNodeResponse.builder()
                .doneCount(taskResult.getDoneCount())
                .failedCount(0)
                .nodeCompleted(nodeCompleted)
                .message(message)
                .build();
    }

    /**
     * Throws ApiException if the learner has not submitted evidence for this node.
     * Mentor coverage is intentionally NOT a blocker — it is an audit/display signal only.
     * No-op when {@code journey} is null (standalone roadmap session, not journey-linked).
     */
    private void assertEvidenceGatePassed(Journey journey, String nodeId) {
        if (journey == null) {
            return;
        }
        var submission = nodeSubmissionRepository
                .findByJourneyIdAndNodeId(journey.getId(), nodeId)
                .orElse(null);
        boolean hasEvidence = submission != null
                && (submission.getSubmissionStatus() == SubmissionStatus.SUBMITTED
                        || submission.getSubmissionStatus() == SubmissionStatus.RESUBMITTED);
        if (!hasEvidence) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Bạn cần nộp minh chứng trước khi hoàn thành node này.");
        }
    }

    private void syncJourneyProgressPercentage(Journey journey, RoadmapSession session) {
        try {
            List<RoadmapResponse.RoadmapNode> nodes;
            if (session.getSchemaVersion() != null && session.getSchemaVersion() >= 2) {
                nodes = validateAndParseRoadmapV2(session.getRoadmapJson()).nodes();
            } else {
                nodes = parseNodesFromV1Json(session.getRoadmapJson());
            }
            Map<String, RoadmapResponse.QuestProgress> progressMap = resolveProgressData(session, nodes);
            RoadmapProgressCalculator.ProgressCalculation calc =
                    RoadmapProgressCalculator.calculate(nodes, progressMap);
            int roadmapPct = (int) Math.round(calc.completionPercentage());
            // Map roadmap completion into lifecycle range [30, 90].
            // 0% => 30, 100% => 90. Final verification (100%) is set elsewhere.
            int mapped = Math.min(90, Math.max(30, (int) Math.round(30 + roadmapPct * 0.6)));
            int current = journey.getProgressPercentage() != null ? journey.getProgressPercentage() : 0;
            int next = Math.max(current, mapped);
            if (!Objects.equals(current, next)) {
                journey.setProgressPercentage(next);
                journeyRepository.save(journey);
            }
        } catch (Exception e) {
            log.warn("Failed to sync journey {} progressPercentage: {}", journey.getId(), e.getMessage());
        }
    }

    private int cleanupRoadmapLinksFromTasks(Long userId, Long roadmapSessionId) {
        List<Task> tasks = taskRepository.findByUserId(userId);
        List<Task> dirtyTasks = new ArrayList<>();

        for (Task task : tasks) {
            String existingNotes = task.getUserNotes();
            String cleanedNotes = removeRoadmapMarkers(existingNotes, roadmapSessionId);
            if (!stringEquals(existingNotes, cleanedNotes)) {
                task.setUserNotes(cleanedNotes);
                dirtyTasks.add(task);
            }
        }

        if (!dirtyTasks.isEmpty()) {
            taskRepository.saveAll(dirtyTasks);
        }

        return dirtyTasks.size();
    }

    private String removeRoadmapMarkers(String notes, Long roadmapSessionId) {
        if (notes == null || notes.isBlank()) {
            return notes;
        }

        Matcher matcher = ROADMAP_NODE_LINK_PATTERN.matcher(notes);
        StringBuffer buffer = new StringBuffer();
        boolean changed = false;

        while (matcher.find()) {
            Long matchedRoadmapId = safeParseLong(matcher.group(2));
            if (matchedRoadmapId != null && matchedRoadmapId.equals(roadmapSessionId)) {
                matcher.appendReplacement(buffer, "");
                changed = true;
            }
        }

        if (!changed) {
            return notes;
        }

        matcher.appendTail(buffer);
        String cleaned = buffer.toString()
                .replaceAll("(?m)^[ \\t]*\\r?\\n", "")
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private record SummaryProgressStats(int totalQuests, int completedQuests, double progressPercentage) {
    }

    private Long safeParseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean stringEquals(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    // ============== Token Usage Recording Methods ==============

    private void recordRoadmapSuccess(AiProviderType providerType, String modelName, Long userId,
                                      Long sessionId, String prompt, String response, long latencyMs) {
        if (tokenUsageRecorder == null) {
            return;
        }
        try {
            long promptTokens = TokenCounterUtil.estimateTokens(prompt);
            long completionTokens = TokenCounterUtil.estimateTokens(response);
            tokenUsageRecorder.recordSuccess(AiTokenUsageRecordCommand.builder()
                    .flowType(AiFlowType.ROADMAP_GENERATION)
                    .providerType(providerType)
                    .modelName(modelName)
                    .userId(userId)
                    .relatedEntityType("ROADMAP_SESSION")
                    .relatedEntityId(sessionId)
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .estimated(true)
                    .status(AiUsageStatus.SUCCESS)
                    .latencyMs(latencyMs)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record roadmap token usage: {}", e.getMessage());
        }
    }

    private void recordRoadmapFailure(AiProviderType providerType, String modelName, Long userId,
                                      Long sessionId, String prompt, String errorMessage, long latencyMs) {
        if (tokenUsageRecorder == null) {
            return;
        }
        try {
            long promptTokens = TokenCounterUtil.estimateTokens(prompt);
            tokenUsageRecorder.recordFailure(AiTokenUsageRecordCommand.builder()
                    .flowType(AiFlowType.ROADMAP_GENERATION)
                    .providerType(providerType)
                    .modelName(modelName)
                    .userId(userId)
                    .relatedEntityType("ROADMAP_SESSION")
                    .relatedEntityId(sessionId)
                    .promptTokens(promptTokens)
                    .completionTokens(0L)
                    .totalTokens(promptTokens)
                    .estimated(true)
                    .status(AiUsageStatus.FAILED)
                    .latencyMs(latencyMs)
                    .errorCode(errorMessage != null && errorMessage.length() > 50 ?
                            errorMessage.substring(0, 50) : errorMessage)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record roadmap failure token usage: {}", e.getMessage());
        }
    }

    private void recordValidationSuccess(AiProviderType providerType, String modelName, Long userId,
                                         String prompt, String response, long latencyMs) {
        if (tokenUsageRecorder == null) {
            return;
        }
        try {
            long promptTokens = TokenCounterUtil.estimateTokens(prompt);
            long completionTokens = TokenCounterUtil.estimateTokens(response);
            tokenUsageRecorder.recordSuccess(AiTokenUsageRecordCommand.builder()
                    .flowType(AiFlowType.ROADMAP_VALIDATION)
                    .providerType(providerType)
                    .modelName(modelName)
                    .userId(userId)
                    .relatedEntityType("VALIDATION")
                    .promptTokens(promptTokens)
                    .completionTokens(completionTokens)
                    .totalTokens(promptTokens + completionTokens)
                    .estimated(true)
                    .status(AiUsageStatus.SUCCESS)
                    .latencyMs(latencyMs)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record validation token usage: {}", e.getMessage());
        }
    }

    private void recordValidationFailure(AiProviderType providerType, String modelName, Long userId,
                                         String prompt, String errorMessage, long latencyMs) {
        if (tokenUsageRecorder == null) {
            return;
        }
        try {
            long promptTokens = TokenCounterUtil.estimateTokens(prompt);
            tokenUsageRecorder.recordFailure(AiTokenUsageRecordCommand.builder()
                    .flowType(AiFlowType.ROADMAP_VALIDATION)
                    .providerType(providerType)
                    .modelName(modelName)
                    .userId(userId)
                    .relatedEntityType("VALIDATION")
                    .promptTokens(promptTokens)
                    .completionTokens(0L)
                    .totalTokens(promptTokens)
                    .estimated(true)
                    .status(AiUsageStatus.FAILED)
                    .latencyMs(latencyMs)
                    .errorCode(errorMessage != null && errorMessage.length() > 50 ?
                            errorMessage.substring(0, 50) : errorMessage)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to record validation failure token usage: {}", e.getMessage());
        }
    }

}
