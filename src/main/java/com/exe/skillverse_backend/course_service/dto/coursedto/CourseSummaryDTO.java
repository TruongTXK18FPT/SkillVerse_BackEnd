package com.exe.skillverse_backend.course_service.dto.coursedto;

import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseSummaryDTO {
    private Long id;
    private String title;
    private String level;
    private CourseStatus status;
    private UserDto author;
    private String authorName; // Keep for backward compatibility
    private Long thumbnailMediaId;
    private String thumbnailUrl;
    private Integer enrollmentCount;
    private Integer moduleCount;
    private java.math.BigDecimal price;
    private String currency;
}
