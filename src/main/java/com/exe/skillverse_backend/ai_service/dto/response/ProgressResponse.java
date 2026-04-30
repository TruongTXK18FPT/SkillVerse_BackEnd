package com.exe.skillverse_backend.ai_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for progress update operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressResponse {

    /**
     * Session ID
     */
    private Long sessionId;

    /**
     * Quest ID that was updated
     */
    private String questId;

    /**
     * New completion status
     */
    private Boolean completed;

    /**
     * Updated progress statistics
     */
    private ProgressStats stats;

    /**
     * Progress statistics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgressStats {
        /**
         * Total number of quests/milestones
         */
        private Integer totalQuests;

        /**
         * Number of completed quests
         */
        private Integer completedQuests;

        /**
         * Completion percentage (0-100) — weighted by node importance
         */
        private Double completionPercentage;

        /**
         * Sum of weights of completed nodes
         */
        private Double completedWeight;

        /**
         * Sum of weights of all valid nodes
         */
        private Double totalWeight;

        /**
         * Scoring mode: WEIGHTED_IMPORTANCE or COUNT_FALLBACK
         */
        private String progressMode;
    }
}
