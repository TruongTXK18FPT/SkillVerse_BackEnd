package com.exe.skillverse_backend.course_service.dto.coursedto;

import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;

import jakarta.validation.constraints.NotBlank;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseUpdateDTO {
    //String title, String description, String level, Long thumbnailMediaId, CourseStatus status
    @NotBlank
    private String title;
    private String description;
    private String level;
    private Long thumbnailMediaId;
    private CourseStatus status;
}
