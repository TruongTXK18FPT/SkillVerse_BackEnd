package com.exe.skillverse_backend.course_service.dto.moduledto;

import java.time.Instant;

import com.exe.skillverse_backend.course_service.entity.enums.ProgressStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModuleProgressDetailDTO {
    private Long moduleId;
    private ProgressStatus status;
    private Integer timeSpentSec;
    private Integer lastPositionSec;
    private Instant updatedAt;
}
