package com.exe.skillverse_backend.roadmap_package_service.dto.request;

import com.exe.skillverse_backend.career_taxonomy_service.enums.ImportanceLevel;
import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoadmapTemplateNodeRequest {
    private Long parentNodeId;
    private String nodeKey;

    @NotBlank
    private String title;

    private String description;

    @NotNull
    private Integer orderIndex;

    @NotNull
    private Long skillId;
    private String skillNameSnapshot;
    private String skillCanonicalKeySnapshot;
    private RequirementType requirementType;
    private ImportanceLevel importanceLevel;
    private String difficulty;
    private Double estimatedHours;
    private String expectedOutput;
    private String rubric;
}
