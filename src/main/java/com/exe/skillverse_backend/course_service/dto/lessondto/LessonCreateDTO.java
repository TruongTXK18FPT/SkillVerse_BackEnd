package com.exe.skillverse_backend.course_service.dto.lessondto;

import com.exe.skillverse_backend.course_service.entity.enums.LessonType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor

public class LessonCreateDTO {
    //NotBlank title, @NotNull LessonType type, Integer orderIndex, String contentText, String videoUrl, Long videoMediaId, Integer durationSec
    @NotBlank
    private String title;
    @NotNull
    private LessonType type;
    private Integer orderIndex;
    private String contentText;
    private String videoUrl;
    private Long videoMediaId;
    private Integer durationSec;
}
