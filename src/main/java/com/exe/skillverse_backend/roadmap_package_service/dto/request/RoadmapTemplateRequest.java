package com.exe.skillverse_backend.roadmap_package_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateGenerationMode;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateKnowledgePolicy;

@Data
public class RoadmapTemplateRequest {
    @NotNull
    private Long domainId;

    @NotNull
    private Long jobPositionId;

    @NotNull
    private Long jobPositionTrackId;

    @NotBlank
    private String title;

    private String description;
    private String targetRole;
    private String targetLevel;
    private String targetRoleSnapshot;
    private String targetLevelSnapshot;
    private Integer totalNodeCount;
    private RoadmapTemplateGenerationMode generationMode;
    private RoadmapTemplateKnowledgePolicy knowledgePolicy;
    private String globalLearningGoal;
    private String audienceLevel;
    private String outputStandard;
    private String assessmentPolicy;
    private String templateInstructions;
    private String constraintsJson;

    @Valid
    private List<RoadmapTemplateNodeRequest> nodes = new ArrayList<>();

    @Valid
    private List<RoadmapTemplateCourseRequest> courses = new ArrayList<>();

    @Valid
    private List<RoadmapTemplateSkillBlockRequest> skillBlocks = new ArrayList<>();

    @Valid
    private List<RoadmapTemplateNodeGroupRequest> nodeGroups = new ArrayList<>();
}
