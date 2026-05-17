package com.exe.skillverse_backend.roadmap_package_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RoadmapTemplateCourseRequest {
    private Long templateNodeId;

    @NotNull
    private Long courseId;

    private Long skillId;

    private Integer displayOrder;
    private Boolean required;
}
