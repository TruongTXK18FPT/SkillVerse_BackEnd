package com.exe.skillverse_backend.ai_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Simplified DTO for listing user's roadmap sessions
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapSessionSummary {

    private Long sessionId;
    private String title;
    private String goal;
    private String duration;
    private String experience;
    private Integer totalQuests;
    private Integer completedQuests;
    private Integer progressPercentage;
    private Instant createdAt;
}
