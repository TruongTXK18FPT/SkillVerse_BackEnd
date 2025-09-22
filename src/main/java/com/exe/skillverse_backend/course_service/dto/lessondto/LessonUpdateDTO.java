package com.exe.skillverse_backend.course_service.dto.lessondto;

import com.exe.skillverse_backend.course_service.entity.Lesson;
import com.exe.skillverse_backend.course_service.entity.enums.LessonType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonUpdateDTO {
    //String title, LessonType type, Integer orderIndex, String contentText, String videoUrl, Long videoMediaId, Integer durationSec
    private String title;
    private LessonType type;
    private Integer orderIndex;
    private String contentText;
    private String videoUrl;
    private Long videoMediaId;
    private Integer durationSec;
}
