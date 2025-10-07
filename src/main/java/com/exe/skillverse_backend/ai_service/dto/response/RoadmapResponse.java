package com.exe.skillverse_backend.ai_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for roadmap generation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapResponse {

    private Long sessionId;
    private String title;
    private String goal;
    private String duration;
    private String experience;
    private String style;
    private List<RoadmapNode> roadmap;
    private Instant createdAt;

    /**
     * Represents a node/quest in the roadmap tree
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
        private List<String> children;

        public enum NodeType {
            MAIN, // Main path quest
            SIDE // Optional side quest
        }
    }
}
