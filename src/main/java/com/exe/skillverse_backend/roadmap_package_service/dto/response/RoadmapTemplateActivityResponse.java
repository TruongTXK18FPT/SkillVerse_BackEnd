package com.exe.skillverse_backend.roadmap_package_service.dto.response;

import com.exe.skillverse_backend.journey_service.entity.Journey;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoadmapTemplateActivityResponse {
    private Long id;
    private Long templateId;
    private Long skillBlockId;
    private String title;
    private String description;
    private String exerciseType;
    private String expectedOutput;
    private String rubric;
    private String difficulty;
    private Journey.SkillLevel minLevel;
    private Journey.SkillLevel maxLevel;
    private Double estimatedHours;
    private String prerequisiteHint;
    private String aiPromptHint;
    private Integer orderIndex;
}
