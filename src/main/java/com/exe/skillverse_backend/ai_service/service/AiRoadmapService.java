package com.exe.skillverse_backend.ai_service.service;

import com.exe.skillverse_backend.ai_service.dto.request.GenerateRoadmapRequest;
import com.exe.skillverse_backend.ai_service.dto.request.UpdateProgressRequest;
import com.exe.skillverse_backend.ai_service.dto.response.ProgressResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapResponse;
import com.exe.skillverse_backend.ai_service.dto.response.RoadmapSessionSummary;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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

    @Value("${gemini.api.fallback-models:gemini-2.0-flash,gemini-1.5-flash}")
    private String fallbackModels;

    public AiRoadmapService(
            @Qualifier("geminiChatModel") ChatModel geminiChatModel,
            RoadmapSessionRepository roadmapSessionRepository,
            UserRoadmapProgressRepository progressRepository,
            ObjectMapper objectMapper) {
        this.geminiChatModel = geminiChatModel;
        this.roadmapSessionRepository = roadmapSessionRepository;
        this.progressRepository = progressRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate a personalized learning roadmap using Gemini AI
     */
    @Transactional
    public RoadmapResponse generateRoadmap(GenerateRoadmapRequest request, User user) {
        log.info("Generating roadmap for user {} with goal: {}", user.getId(), request.getGoal());

        try {
            // Step 1: Call Gemini API
            String roadmapJson = callGeminiAPI(request);

            // Step 2: Validate JSON schema
            List<RoadmapResponse.RoadmapNode> nodes = validateAndParseRoadmap(roadmapJson);

            // Step 3: Generate title
            String title = generateTitle(request.getGoal(), request.getDuration());

            // Step 4: Save to database
            RoadmapSession session = RoadmapSession.builder()
                    .user(user)
                    .title(title)
                    .goal(request.getGoal())
                    .duration(request.getDuration())
                    .experience(request.getExperience())
                    .style(request.getStyle())
                    .roadmapJson(roadmapJson)
                    .build();

            session = roadmapSessionRepository.save(session);

            log.info("Roadmap session {} created successfully", session.getId());

            // Step 5: Return response
            return RoadmapResponse.builder()
                    .sessionId(session.getId())
                    .title(title)
                    .goal(request.getGoal())
                    .duration(request.getDuration())
                    .experience(request.getExperience())
                    .style(request.getStyle())
                    .roadmap(nodes)
                    .createdAt(session.getCreatedAt())
                    .build();

        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate roadmap", e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to generate roadmap: " + e.getMessage());
        }
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
                    .user(prompt)
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
     * Build structured prompt for Gemini
     */
    private String buildPrompt(GenerateRoadmapRequest request) {
        return String.format(
                """
                        You are an expert learning path designer. Create a personalized, interactive learning roadmap as a tree structure.

                        User Goal: %s
                        Duration: %s
                        Experience Level: %s
                        Learning Style: %s

                        CRITICAL: You MUST respond with ONLY valid JSON in this EXACT format (no extra text, no markdown blocks):
                        {
                          "roadmap": [
                            {
                              "id": "quest-1",
                              "title": "Quest Title Here",
                              "description": "Detailed description of what to learn and practice",
                              "estimated_time_minutes": 180,
                              "type": "MAIN",
                              "children": ["quest-2", "quest-3"]
                            },
                            {
                              "id": "quest-2",
                              "title": "Second Quest Title",
                              "description": "Another learning objective",
                              "estimated_time_minutes": 240,
                              "type": "SIDE",
                              "children": []
                            }
                          ]
                        }

                        MANDATORY RULES:
                        1. Create exactly 10-15 nodes in tree structure (not linear)
                        2. "type" MUST be exactly "MAIN" or "SIDE" (uppercase, nothing else)
                        3. "children" is an array of node IDs (can be empty [])
                        4. "estimated_time_minutes" must be a number (not string)
                        5. Use descriptive IDs like "quest-csharp-basics", "node-oop-concepts"
                        6. First 2-3 nodes should be foundational (root nodes)
                        7. %s learning style: %s
                        8. Tailor content difficulty to "%s" experience level

                        RESPONSE FORMAT: Start with { and end with }. No markdown blocks. No explanations. Just the JSON object.
                        """,
                request.getGoal(),
                request.getDuration(),
                request.getExperience(),
                request.getStyle(),
                request.getStyle(),
                getStyleGuidance(request.getStyle()),
                request.getExperience());
    }

    private String getStyleGuidance(String style) {
        return switch (style.toLowerCase()) {
            case "project-based" -> "Focus on hands-on projects and practical applications";
            case "theoretical" -> "Focus on concepts, theory, and deep understanding";
            case "video-based" -> "Prioritize video resources and visual learning";
            case "hands-on" -> "Emphasize practice exercises and interactive challenges";
            default -> "Balance theory and practice";
        };
    }

    /**
     * Validate and parse roadmap JSON
     */
    private List<RoadmapResponse.RoadmapNode> validateAndParseRoadmap(String roadmapJson) {
        try {
            JsonNode root = objectMapper.readTree(roadmapJson);
            JsonNode roadmapArray = root.path("roadmap");

            if (!roadmapArray.isArray() || roadmapArray.isEmpty()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid roadmap structure: missing 'roadmap' array");
            }

            List<RoadmapResponse.RoadmapNode> nodes = new ArrayList<>();

            for (JsonNode nodeJson : roadmapArray) {
                // Validate required fields
                if (!nodeJson.has("id") || !nodeJson.has("title") || !nodeJson.has("type")) {
                    throw new ApiException(ErrorCode.BAD_REQUEST,
                            "Invalid node: missing required fields (id, title, type)");
                }

                // Parse children array
                List<String> children = new ArrayList<>();
                JsonNode childrenNode = nodeJson.path("children");
                if (childrenNode.isArray()) {
                    for (JsonNode child : childrenNode) {
                        children.add(child.asText());
                    }
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
                        .children(children)
                        .build();

                nodes.add(node);
            }

            log.info("Validated roadmap with {} nodes", nodes.size());
            return nodes;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse roadmap JSON", e);
            throw new ApiException(ErrorCode.BAD_REQUEST, "AI generation failed: invalid JSON format. Please retry.");
        }
    }

    /**
     * Generate a readable title from goal and duration
     */
    private String generateTitle(String goal, String duration) {
        return String.format("%s - %s Learning Path", capitalize(goal), duration);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty())
            return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
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
                JsonNode root = objectMapper.readTree(session.getRoadmapJson());
                JsonNode roadmapArray = root.path("roadmap");
                totalQuests = roadmapArray.size();
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse roadmap JSON for session {}", session.getId());
            }

            // Count completed quests
            Long completedCount = progressRepository.countCompletedBySessionId(session.getId());
            int completed = completedCount != null ? completedCount.intValue() : 0;

            // Calculate progress percentage
            int progressPercentage = totalQuests > 0 ? (completed * 100) / totalQuests : 0;

            summaries.add(RoadmapSessionSummary.builder()
                    .sessionId(session.getId())
                    .title(session.getTitle())
                    .goal(session.getGoal())
                    .duration(session.getDuration())
                    .experience(session.getExperience())
                    .totalQuests(totalQuests)
                    .completedQuests(completed)
                    .progressPercentage(progressPercentage)
                    .createdAt(session.getCreatedAt())
                    .build());
        }

        return summaries;
    }

    /**
     * Get a specific roadmap session with full details
     */
    @Transactional(readOnly = true)
    public RoadmapResponse getRoadmapById(Long sessionId, Long userId) {
        RoadmapSession session = roadmapSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Roadmap not found"));

        List<RoadmapResponse.RoadmapNode> nodes = validateAndParseRoadmap(session.getRoadmapJson());

        return RoadmapResponse.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .goal(session.getGoal())
                .duration(session.getDuration())
                .experience(session.getExperience())
                .style(session.getStyle())
                .roadmap(nodes)
                .createdAt(session.getCreatedAt())
                .build();
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

        // Calculate progress statistics
        List<RoadmapResponse.RoadmapNode> nodes = validateAndParseRoadmap(session.getRoadmapJson());
        int totalQuests = nodes.size();

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

}