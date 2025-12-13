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
    private List<String> warnings;
    private Instant createdAt;
    private Map<String, QuestProgress> progress; // Quest progress tracking
    private Overview overview;
    private List<StructurePhase> structure;
    private List<String> thinkingProgression;
    private List<ProjectEvidence> projectsEvidence;
    private NextSteps nextSteps;
    private List<SkillDependency> skillDependencies;

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
        private String roadmapType;
        private String target;
        private String finalObjective;
        private String currentLevel;
        private String desiredDuration;
        private String background;
        private String dailyTime;
        private String targetEnvironment;
        private String location;
        private String priority;
        private List<String> toolPreferences;
        private String difficultyConcern;
        private Boolean incomeGoal;
        private String roadmapMode;
        private SkillModeMeta skillMode;
        private CareerModeMeta careerMode;
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

    /**
     * Quest progress tracking
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestProgress {
        private String questId;
        private String status; // NOT_STARTED, IN_PROGRESS, COMPLETED, SKIPPED
        private Integer progress; // 0-100
        private Instant completedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Overview {
        private String purpose;
        private String audience;
        private String postRoadmapState;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StructurePhase {
        private String phaseId;
        private String title;
        private String timeframe;
        private String goal;
        private List<String> skillFocus;
        private String mindsetGoal;
        private String expectedOutput;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ProjectEvidence {
        private String phaseId;
        private String project;
        private String objective;
        private List<String> skillsProven;
        private List<String> kpi;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NextSteps {
        private List<String> jobs;
        private List<String> nextSkills;
        private List<String> mentorsMicroJobs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkillDependency {
        private String from;
        private String to;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkillModeMeta {
        private String skillName;
        private String skillCategory;
        private String desiredDepth;
        private String learnerType;
        private String currentSkillLevel;
        private String learningGoal;
        private String dailyLearningTime;
        private String assessmentPreference;
        private String difficultyTolerance;
        private List<String> toolPreference;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CareerModeMeta {
        private String targetRole;
        private String careerTrack;
        private String targetSeniority;
        private String workMode;
        private String targetMarket;
        private String companyType;
        private String timelineToWork;
        private Boolean incomeExpectation;
        private String workExperience;
        private Boolean transferableSkills;
        private String confidenceLevel;
    }
}
