package com.exe.skillverse_backend.roadmap_package_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadmapRubricDto {
    private String name;
    private String description;
    private Integer maxPoints;
}
