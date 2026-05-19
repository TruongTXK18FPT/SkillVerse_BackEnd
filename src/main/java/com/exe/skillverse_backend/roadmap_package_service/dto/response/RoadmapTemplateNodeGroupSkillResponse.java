package com.exe.skillverse_backend.roadmap_package_service.dto.response;

import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoadmapTemplateNodeGroupSkillResponse {
    private Long id;
    private Long nodeGroupId;
    private Long skillId;
    private String skillNameSnapshot;
    private String skillCanonicalKeySnapshot;
    private RequirementType requirementType;
    private Double weightInNode;
    private Integer orderIndex;
}
