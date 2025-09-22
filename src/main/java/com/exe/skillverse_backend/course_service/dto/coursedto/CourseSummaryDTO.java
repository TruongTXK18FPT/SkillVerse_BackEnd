package com.exe.skillverse_backend.course_service.dto.coursedto;

import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseSummaryDTO {
    //Long id, String title, String level, CourseStatus status, String authorName, Long thumbnailMediaId, Integer enrollmentCount
    private Long id;
    private String title;
    private String level;
    private CourseStatus status;
    private String authorName;
    private Long thumbnailMediaId;
    private Integer enrollmentCount;
}
