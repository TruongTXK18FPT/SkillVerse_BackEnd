package com.exe.skillverse_backend.career_taxonomy_service.dto;

import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPositionTrackSkillDto {
    private Long id;
    private Long trackId;
    private Long skillId;
    private String skillName;
    private String canonicalKey;
    private RequirementType requirementType;
    private Integer sortOrder;
    private Integer weight;
}
