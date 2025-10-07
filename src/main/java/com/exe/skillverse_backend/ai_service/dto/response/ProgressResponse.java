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
         * Completion percentage (0-100)
         */
        private Double completionPercentage;
    }
}
