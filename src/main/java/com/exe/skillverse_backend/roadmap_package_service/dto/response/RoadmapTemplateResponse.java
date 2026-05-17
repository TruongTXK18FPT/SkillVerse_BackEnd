package com.exe.skillverse_backend.roadmap_package_service.dto.response;

import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateStatus;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateGenerationMode;
import com.exe.skillverse_backend.roadmap_package_service.entity.RoadmapTemplateKnowledgePolicy;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoadmapTemplateResponse {
    private Long id;
    private Long createdByAdminId;
    private Long updatedByAdminId;
    private Long domainId;
    private Long jobPositionId;
    private Long jobPositionTrackId;
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
    private RoadmapTemplateStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<RoadmapTemplateNodeResponse> nodes;
    private List<RoadmapTemplateCourseResponse> courses;
    private List<RoadmapTemplateSkillBlockResponse> skillBlocks;
    private RoadmapTemplateAllocationPreviewResponse allocationPreview;
}
