package com.exe.skillverse_backend.ai_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for AI-generated roadmap (Schema V2)
 * Enhanced with metadata, statistics, and learning tips
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapResponse {

    private Long sessionId;
    private RoadmapMetadata metadata;
    private List<RoadmapNode> roadmap;
    private RoadmapStatistics statistics;
    private List<String> learningTips;
    private Instant createdAt;

    /**
     * Metadata about the roadmap generation
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoadmapMetadata {
        private String title;
        private String originalGoal;
        private String validatedGoal;
        private String duration;
        private String experienceLevel;
        private String learningStyle;
        private String detectedIntention;
        private String validationNotes;
        private String estimatedCompletion;
        private String difficultyLevel; // easy, medium, hard, expert
        private List<String> prerequisites;
        private String careerRelevance;
    }

    /**
     * Statistics about the roadmap
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoadmapStatistics {
        private Integer totalNodes;
        private Integer mainNodes;
        private Integer sideNodes;
        private Double totalEstimatedHours;
        private Map<String, Integer> difficultyDistribution; // easy: 4, medium: 6, hard: 2
    }

    /**
     * Enhanced node/quest in the roadmap tree with learning details
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoadmapNode {
        private String id;
        private String title;
        private String description;
        private Integer estimatedTimeMinutes;
        private NodeType type;
        private String difficulty; // easy, medium, hard

        // Learning content
        private List<String> learningObjectives;
        private List<String> keyConcepts;
        private List<String> practicalExercises;
        private List<String> suggestedResources;
        private List<String> successCriteria;

        // Graph structure
        private List<String> prerequisites; // Must complete these before this node
        private List<String> children; // Unlocks these nodes

        // Metadata
        private String estimatedCompletionRate; // e.g., "90%", "70%", "50%"

        public enum NodeType {
            MAIN, // Main path quest - required for core learning
            SIDE // Optional side quest - supplementary/advanced
        }
    }
}
