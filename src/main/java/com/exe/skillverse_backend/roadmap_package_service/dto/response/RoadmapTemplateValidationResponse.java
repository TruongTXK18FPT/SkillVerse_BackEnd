package com.exe.skillverse_backend.roadmap_package_service.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoadmapTemplateValidationResponse {
    private Boolean valid;
    private List<String> errors;
    private List<String> warnings;
    private RoadmapTemplateAllocationPreviewResponse allocation;
}
