package com.exe.skillverse_backend.course_service.dto.enrollmentdto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollmentStatsDTO {
    private Long courseId;
    private String courseTitle;
    private Long totalEnrollments;
    private Long activeEnrollments;
    private Long completedEnrollments;
    private Double completionRate;
    private Double averageProgress;
    private Long enrollmentsThisMonth;
    private Long completionsThisMonth;
}