package com.exe.skillverse_backend.roadmap_package_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RoadmapTemplateNodeGroupRequest {
    private Long id;
    private String nodeKey;

    @NotBlank
    private String title;

    private String description;
    private String learningObjectives;
    private String lessonsJson;
    private String exercisesJson;
    private String completionCriteria;
    private String expectedOutput;
    private String rubric;
    private String difficulty;
    private Double estimatedHours;
    private String aiPromptHint;
    private String nodeType;
    private String parentNodeKey;

    @NotNull
    private Integer orderIndex;

    @Valid
    private List<RoadmapTemplateNodeGroupSkillRequest> skills = new ArrayList<>();
}
