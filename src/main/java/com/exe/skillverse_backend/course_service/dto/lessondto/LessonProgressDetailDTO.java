package com.exe.skillverse_backend.course_service.dto.lessondto;

import java.time.Instant;

import com.exe.skillverse_backend.course_service.entity.enums.ProgressStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonProgressDetailDTO {
    private Long lessonId;
    private ProgressStatus status;
    private Integer timeSpenSec;
    private Integer lastPositionSec;
    private Instant updatedAt;
}
