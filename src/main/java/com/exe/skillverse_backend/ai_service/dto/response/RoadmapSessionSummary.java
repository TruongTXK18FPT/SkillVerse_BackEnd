package com.exe.skillverse_backend.ai_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Simplified DTO for listing user's roadmap sessions (Schema V2)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapSessionSummary {

    private Long sessionId;
    private String title;
    private String roadmapMode;

    // V2 field names (matching RoadmapSession entity)
    private String originalGoal;
    private String validatedGoal; // May be null for old V1 data
    private String duration;
    private String experienceLevel;
    private String learningStyle;

    // Progress tracking
    private Integer totalQuests;
    private Integer completedQuests;
    private Integer progressPercentage;

    // Metadata
    private String difficultyLevel;
    private Integer schemaVersion;

    private Instant createdAt;
}
