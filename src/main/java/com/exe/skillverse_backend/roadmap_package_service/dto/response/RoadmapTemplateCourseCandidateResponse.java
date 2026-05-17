package com.exe.skillverse_backend.roadmap_package_service.dto.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RoadmapTemplateCourseCandidateResponse {
    private Long courseId;
    private String title;
    private String level;
    private String category;
    private Instant createdAt;
    private Long enrollmentCount;
    private String thumbnailUrl;
}
