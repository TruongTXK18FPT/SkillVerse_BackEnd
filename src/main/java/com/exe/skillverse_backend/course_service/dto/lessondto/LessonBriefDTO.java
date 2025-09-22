package com.exe.skillverse_backend.course_service.dto.lessondto;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonBriefDTO {
    //Long id, String title, LessonType type, Integer orderIndex, Integer durationSec
    private Long id;
    private String title;
    private LessonType type;
    private Integer orderIndex;
    private Integer durationSec;
}
