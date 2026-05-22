package com.exe.skillverse_backend.roadmap_package_service.dto.response;

import com.exe.skillverse_backend.career_taxonomy_service.enums.RequirementType;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoadmapTemplateAllocationPreviewResponse {
    private Integer totalNodeCount;
    private Integer allocatedNodeCount;
    private Boolean valid;
    private List<String> errors;
    private List<Item> items;

    @Data
    @Builder
    public static class Item {
        private Long skillId;
        private String skillName;
        private Double weightPercent;
        private RequirementType requirementType;
        private Integer trackWeight;
        private Double requirementMultiplier;
        private Double effectiveWeight;
        private Double normalizedWeightPercent;
        private Integer minNodes;
        private Integer maxNodes;
        private Integer nodeCountOverride;
        private Integer allocatedNodes;
        private Double rawShare;
    }
}
