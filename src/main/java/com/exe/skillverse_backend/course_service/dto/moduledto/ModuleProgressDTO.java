package com.exe.skillverse_backend.course_service.dto.moduledto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModuleProgressDTO {
    private long completedLessons;
    private long totalLessons;
    private int percent; // 0..100
}
