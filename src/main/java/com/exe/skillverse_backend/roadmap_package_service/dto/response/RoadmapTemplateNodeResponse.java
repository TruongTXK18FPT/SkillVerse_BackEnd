package com.exe.skillverse_backend.roadmap_package_service.dto.response;

import com.exe.skillverse_backend.career_taxonomy_service.enums.ImportanceLevel;
import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoadmapTemplateNodeResponse {
    private Long id;
    private Long templateId;
    private Long parentNodeId;
    private String nodeKey;
    private String title;
    private String description;
    private Integer orderIndex;
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
