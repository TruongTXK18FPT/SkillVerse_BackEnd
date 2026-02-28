package com.exe.skillverse_backend.course_service.dto.coursedto;

import com.exe.skillverse_backend.auth_service.dto.response.UserDto;
import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseSummaryDTO {
    private Long id;
    private String title;
    private String shortDescription;
    private String level;
    private String category;
    private Integer estimatedDurationHours;
    private String language;
    private CourseStatus status;
    private UserDto author;
    private String authorName; // Keep for backward compatibility
    private Long thumbnailMediaId;
    private String thumbnailUrl;
    private Integer enrollmentCount;
    private Integer moduleCount;
    private Integer lessonCount; // Total number of lessons across all modules
    private BigDecimal price;
    private String currency;
    // Additional timestamps for admin and list views
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime submittedDate;
    private LocalDateTime publishedDate;
    // Rejection info shown in mentor dashboard
    private String rejectionReason;
}
