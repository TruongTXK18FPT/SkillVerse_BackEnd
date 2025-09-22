package com.exe.skillverse_backend.course_service.dto.enrollmentdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentDetailDTO {
    private Long id;
    private Long courseId;
    private String courseTitle;
    private String courseSlug;
    private Long userId;
    private String status;
    private Integer progressPercent;
    private String entitlementSource;
    private String entitlementRef;
    private LocalDateTime enrolledAt;
    private LocalDateTime completedAt;
    private boolean completed;
}