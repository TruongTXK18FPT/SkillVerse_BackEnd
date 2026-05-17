package com.exe.skillverse_backend.roadmap_package_service.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoadmapTemplateCourseResponse {
    private Long id;
    private Long templateId;
    private Long templateNodeId;
    private Long courseId;
    private Long skillId;
    private Integer displayOrder;
    private Boolean required;
}
