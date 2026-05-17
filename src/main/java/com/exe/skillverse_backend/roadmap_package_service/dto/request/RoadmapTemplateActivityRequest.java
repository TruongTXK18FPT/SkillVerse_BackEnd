package com.exe.skillverse_backend.roadmap_package_service.dto.request;

import com.exe.skillverse_backend.journey_service.entity.Journey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoadmapTemplateActivityRequest {
    private Long id;

    @NotBlank
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

    @NotNull
    private Integer orderIndex;
}
