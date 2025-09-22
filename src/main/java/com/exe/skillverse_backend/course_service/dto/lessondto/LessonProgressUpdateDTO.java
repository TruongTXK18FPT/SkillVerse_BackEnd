package com.exe.skillverse_backend.course_service.dto.lessondto;

import com.exe.skillverse_backend.course_service.entity.enums.ProgressStatus;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LessonProgressUpdateDTO {
    @NotNull
    private Long lessonId;
    @NotNull
    private ProgressStatus status;
    private Integer timeSpenSec;
    private Integer lastPositionSec;
}
