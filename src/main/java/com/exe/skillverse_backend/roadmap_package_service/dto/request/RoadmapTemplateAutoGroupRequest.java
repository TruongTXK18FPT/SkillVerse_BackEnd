package com.exe.skillverse_backend.roadmap_package_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RoadmapTemplateAutoGroupRequest {
    @NotNull
    private Long jobPositionTrackId;

    @NotNull
    @Positive
    private Integer totalNodeCount;

    @Valid
    private List<RoadmapTemplateSkillBlockRequest> skillBlocks = new ArrayList<>();
}
