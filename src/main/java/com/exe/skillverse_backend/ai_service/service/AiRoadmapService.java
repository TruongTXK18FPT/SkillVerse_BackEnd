package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.request.UpdateProgressRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ProgressResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
import com.exe.skillverse_backend.ai_service.dto.response.ValidationResult;
import com.exe.skillverse_backend.ai_service.entity.RoadmapSession;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
import com.exe.skillverse_backend.ai_service.repository.RoadmapSessionRepository;
import com.exe.skillverse_backend.ai_service.repository.UserRoadmapProgressRepository;
import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.exception.ApiException;
import com.exe.skillverse_backend.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for AI-powered roadmap generation using Spring AI with Gemini
 * Using Spring AI OpenAI client with Gemini's OpenAI-compatible API
 */
@Service
@Slf4j
public class AiRoadmapService {

    private final ChatModel geminiChatModel;
    private final RoadmapSessionRepository roadmapSessionRepository;
    private final UserRoadmapProgressRepository progressRepository;
    private final ObjectMapper objectMapper;
    private final InputValidationService inputValidationService;

    @Value("${spring.ai.openai.fallback-models:gemini-2.0-flash,gemini-1.5-flash}")
    private String fallbackModels;

    public AiRoadmapService(
            @Qualifier("geminiChatModel") ChatModel geminiChatModel,
            RoadmapSessionRepository roadmapSessionRepository,
            UserRoadmapProgressRepository progressRepository,
            ObjectMapper objectMapper,
            InputValidationService inputValidationService) {
        this.geminiChatModel = geminiChatModel;
        this.roadmapSessionRepository = roadmapSessionRepository;
        this.progressRepository = progressRepository;
        this.objectMapper = objectMapper;
        this.inputValidationService = inputValidationService;
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

        List<ValidationResult> results = new java.util.ArrayList<>();

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
        log.info("🚀 Generating roadmap V2 for user {} with goal: {}", user.getId(), request.getGoal());

        try {
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
            String roadmapJson = callGeminiAPI(request);

            // Step 4: Parse and validate JSON (Schema V2)
            ParsedRoadmap parsed = validateAndParseRoadmapV2(roadmapJson);

            // Step 5: Extract statistics for database
            Integer totalNodes = parsed.statistics() != null ? parsed.statistics().getTotalNodes()
                    : parsed.nodes().size();
            Double totalHours = parsed.statistics() != null ? parsed.statistics().getTotalEstimatedHours()
                    : calculateTotalHours(parsed.nodes());

            // Step 6: Save to database with V2 schema
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
                    // Statistics (for premium quota)
                    .totalNodes(totalNodes)
                    .totalEstimatedHours(totalHours)
                    .difficultyLevel(parsed.metadata().getDifficultyLevel())
                    // Premium tracking
                    .isPremiumGenerated(false) // TODO: Check user premium status
                    // Full JSON
                    .roadmapJson(roadmapJson)
                    .build();

            session = roadmapSessionRepository.save(session);

            log.info("✅ Roadmap V2 session {} created: {} nodes, {}h, difficulty: {}",
                    session.getId(), totalNodes, String.format("%.1f", totalHours),
                    parsed.metadata().getDifficultyLevel());

            // Step 7: Return response (new format)
            return RoadmapResponse.builder()
                    .sessionId(session.getId())
                    .metadata(parsed.metadata())
                    .roadmap(parsed.nodes())
                    .statistics(parsed.statistics())
                    .learningTips(parsed.learningTips())
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
     * Calculate total hours from nodes (fallback if statistics missing)
     */
    private Double calculateTotalHours(List<RoadmapResponse.RoadmapNode> nodes) {
        int totalMinutes = nodes.stream()
                .mapToInt(RoadmapResponse.RoadmapNode::getEstimatedTimeMinutes)
                .sum();
        return totalMinutes / 60.0;
    }

    /**
     * Call Gemini API using Spring AI ChatClient
     * Automatic retry is handled by Spring AI retry configuration
     */
    private String callGeminiAPI(GenerateRoadmapRequest request) {
        String prompt = buildPrompt(request);

        try {
            log.info("Calling Gemini API via Spring AI ChatClient");

            // Use Spring AI ChatClient to call Gemini
            String response = ChatClient.builder(geminiChatModel)
                    .build()
                    .prompt()
                    .user(prompt
                            + "\n\nCRITICAL: Trả lời bằng TIẾNG VIỆT. Nếu phát hiện mục tiêu/đầu vào vô lý (ví dụ: IELTS 10.0, nội dung thô tục), hãy từ chối lịch sự bằng tiếng Việt và gợi ý cách nhập lại hợp lệ. Chỉ trả về JSON hợp lệ như yêu cầu.")
                    .call()
                    .content();

            log.debug("Raw AI response length: {} chars", response.length());
            log.debug("Raw AI response preview: {}", response.substring(0, Math.min(500, response.length())));

            // Extract JSON from markdown code blocks if present
            String cleanedResponse = extractJsonFromResponse(response);

            log.info("✅ Successfully generated roadmap with Gemini");
            return cleanedResponse;

        } catch (Exception e) {
            log.error("Failed to call Gemini API: {}", e.getMessage());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE,
                    "AI service unavailable. Please try again later. Error: " + e.getMessage());
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
                Goal: %s
                Duration: %s
                Experience Level: %s
                Learning Style: %s

                === YOUR TASK ===
                Analyze the goal above using Pattern Detection Engine (99%% accuracy).
                Validate using Validation Framework (check scores, deprecated tech, time feasibility).
                Generate roadmap adapted to experience level and learning style.
                Return ONLY valid JSON following the exact format specified above.

                CRITICAL: Response must be pure JSON starting with { and ending with }.
                NO markdown, NO explanations, ONLY JSON.
                """,
                request.getGoal(),
                request.getDuration(),
                request.getExperience(),
                request.getStyle());

        return systemPrompt + userContext;
    }

    /**
     * Build System Prompt V2 - Comprehensive AI Roadmap Architect Instructions
     */
    private String buildSystemPromptV2(GenerateRoadmapRequest request) {
        return """
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
                    "title": "Tên lộ trình (Tiếng Việt có dấu)",
                    "original_goal": "Mục tiêu gốc từ user (không thay đổi)",
                    "validated_goal": "Mục tiêu đã làm rõ/điều chỉnh",
                    "duration": "3 tháng | 6 tuần | 2 ngày (Tiếng Việt)",
                    "experience_level": "Mới bắt đầu | Trung cấp | Nâng cao | Chuyên gia",
                    "learning_style": "Theo dự án - Học bằng cách làm | Lý thuyết trước - Hiểu rồi làm | Thực hành trực tiếp",
                    "detected_intention": "Người dùng muốn học [X] để [Y]",
                    "validation_notes": "null hoặc giải thích điều chỉnh",
                    "estimated_completion": "Thời gian thực tế nếu khác duration (Tiếng Việt)",
                    "difficulty_level": "beginner | intermediate | advanced | expert",
                    "prerequisites": ["Kiến thức cần có trước"],
                    "career_relevance": "Liên quan nghề nghiệp"
                  },
                  "roadmap": [
                    {
                      "id": "quest-[mô-tả-ngắn]",
                      "title": "Tiêu đề (Tiếng Việt có dấu, 40-80 ký tự)",
                      "description": "Mô tả 2-4 câu: Học gì, đạt gì, tại sao quan trọng",
                      "estimated_time_minutes": 180,
                      "type": "MAIN",
                      "difficulty": "easy | medium | hard",
                      "learning_objectives": [
                        "Mục tiêu cụ thể đo lường được",
                        "Tạo được [X], Hiểu và giải thích được [Y]"
                      ],
                      "key_concepts": ["Khái niệm 1", "Khái niệm 2"],
                      "practical_exercises": [
                        "Bài tập cụ thể 1",
                        "Dự án mini 2"
                      ],
                      "suggested_resources": [
                        "FreeCodeCamp - Responsive Web Design",
                        "MDN Web Docs - HTML Basics",
                        "YouTube - Traversy Media - React Course"
                      ],
                      "success_criteria": [
                        "Tiêu chí đánh giá hoàn thành 1",
                        "Tiêu chí 2"
                      ],
                      "prerequisites": ["quest-id-1"],
                      "children": ["quest-id-2"],
                      "estimated_completion_rate": "90%%"
                    }
                  ],
                  "roadmap_statistics": {
                    "total_nodes": 12,
                    "main_nodes": 8,
                    "side_nodes": 4,
                    "total_estimated_hours": 48.5,
                    "difficulty_distribution": {
                      "easy": 4,
                      "medium": 6,
                      "hard": 2
                    }
                  },
                  "learning_tips": [
                    "Tip 1: Lời khuyên học tập phù hợp với learning style",
                    "Tip 2: Best practice cho goal này"
                  ]
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

                """;
    }

    /**
     * Validate and parse enhanced roadmap JSON (Schema V2)
     * Parses: metadata, roadmap nodes, statistics, learning tips
     */
    private ParsedRoadmap validateAndParseRoadmapV2(String roadmapJson) {
        try {
            JsonNode root = objectMapper.readTree(roadmapJson);

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

            log.info("✅ Validated roadmap V2: {} nodes, difficulty: {}",
                    nodes.size(), metadata.getDifficultyLevel());

            return new ParsedRoadmap(metadata, nodes, statistics, learningTips);

        } catch (JsonProcessingException e) {
            log.error("❌ Failed to parse roadmap JSON V2", e);
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "AI generation failed: invalid JSON format. Please retry.");
        }
    }

    /**
     * Parse roadmap metadata
     */
    private RoadmapResponse.RoadmapMetadata parseMetadata(JsonNode node) {
        return RoadmapResponse.RoadmapMetadata.builder()
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
                .build();
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
            List<String> learningTips) {
    }

    /**
     * Generate a readable title from goal and duration
     */
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
            progress.setCompletedAt(java.time.Instant.now());
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
            // Use Spring AI ChatModel for validation
            ChatResponse response = geminiChatModel.call(new Prompt(validationPrompt));
            String aiResponse = response.getResult().getOutput().getContent().trim();

            log.debug("AI Validation Response: {}", aiResponse);

            // Parse AI response
            return parseAIValidationResponse(aiResponse, goal);

        } catch (Exception e) {
            log.warn("⚠️ AI validation failed, falling back to basic validation: {}", e.getMessage());

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