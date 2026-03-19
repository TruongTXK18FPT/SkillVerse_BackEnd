package com.exe.skillverse_backend.course_service.dto.enrollmentdto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Long learningRevisionId;
    private String upgradePolicySnapshot;
    private LocalDateTime enrolledAt;
    private LocalDateTime lastUpgradedAt;
    private LocalDateTime completedAt;
    private boolean completed;
}
