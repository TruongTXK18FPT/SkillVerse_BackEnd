package com.exe.skillverse_backend.course_service.dto.lessondto;

import com.exe.skillverse_backend.course_service.entity.enums.LessonType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonBriefDTO {
    private Long id;
    private String title;
    private LessonType type;
    private Integer orderIndex;
    private Integer durationSec;

    // ✅ NEW: Add contentText and videoUrl for edit modal
    private String contentText; // For READING lessons
    private String videoUrl; // For VIDEO lessons
    private Long videoMediaId; // For VIDEO lessons with uploaded files
}
