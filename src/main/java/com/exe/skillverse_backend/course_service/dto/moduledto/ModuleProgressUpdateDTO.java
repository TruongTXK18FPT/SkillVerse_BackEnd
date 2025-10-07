package com.exe.skillverse_backend.course_service.dto.moduledto;

import com.exe.skillverse_backend.course_service.entity.enums.ProgressStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ModuleProgressUpdateDTO {
    @NotNull
    private Long moduleId;
    @NotNull
    private ProgressStatus status;
    private Integer timeSpentSec;
    private Integer lastPositionSec;
}
