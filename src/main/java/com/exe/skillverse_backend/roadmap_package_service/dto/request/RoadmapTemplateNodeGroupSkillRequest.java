package com.exe.skillverse_backend.roadmap_package_service.dto.request;

import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoadmapTemplateNodeGroupSkillRequest {
    private Long id;

    @NotNull
    private Long skillId;

    private String skillNameSnapshot;
    private String skillCanonicalKeySnapshot;
    private RequirementType requirementType;
    private Double weightInNode;
    private Integer orderIndex;
}
