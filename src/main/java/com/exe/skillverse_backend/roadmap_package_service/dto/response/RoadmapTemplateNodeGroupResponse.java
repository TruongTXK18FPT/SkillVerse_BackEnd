package com.exe.skillverse_backend.roadmap_package_service.dto.response;

import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapTemplateNodeGroupResponse {
    private Long id;
    private Long templateId;
    private String nodeKey;
    private String title;
    private String description;
    private String learningObjectives;
    private String lessonsJson;
    private String exercisesJson;
    private String completionCriteria;
    private String difficulty;
    private Double estimatedHours;
    private String expectedOutput;
    private String rubric;
    private String aiPromptHint;
    private String nodeType;
    private String parentNodeKey;
    private Integer orderIndex;
    private List<SkillItem> skills;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillItem {
        private Long skillId;
        private String skillName;
        private String canonicalKey;
        private RequirementType requirementType;
    }
}
