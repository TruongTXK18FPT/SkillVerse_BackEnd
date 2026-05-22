package com.exe.skillverse_backend.roadmap_package_service.dto.request;

import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateCourseLinkPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RoadmapTemplateSkillBlockRequest {
    private Long id;

    @NotNull
    private Long skillId;

    private String skillNameSnapshot;
    private String skillCanonicalKeySnapshot;

    @PositiveOrZero
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

    @Valid
    private List<RoadmapTemplateActivityRequest> activities = new ArrayList<>();
}
