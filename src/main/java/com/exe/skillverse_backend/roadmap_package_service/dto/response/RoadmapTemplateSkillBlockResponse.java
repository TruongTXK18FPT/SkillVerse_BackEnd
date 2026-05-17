package com.exe.skillverse_backend.roadmap_package_service.dto.response;

import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateCourseLinkPolicy;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoadmapTemplateSkillBlockResponse {
    private Long id;
    private Long templateId;
    private Long skillId;
    private String skillNameSnapshot;
    private String skillCanonicalKeySnapshot;
    private Double weightPercent;
    private Integer minNodes;
    private Integer maxNodes;
    private Integer nodeCountOverride;
    private String learningGoals;
    private String requiredTopics;
    private String activityInstructions;
    private String exerciseTypes;
    private String successCriteria;
    private String ragQueryHint;
    private RoadmapTemplateCourseLinkPolicy courseLinkPolicy;
    private Integer autoCourseLimit;
    private Boolean ragEnabled;
    private Integer allocatedNodes;
    private List<RoadmapTemplateActivityResponse> activities;
}
