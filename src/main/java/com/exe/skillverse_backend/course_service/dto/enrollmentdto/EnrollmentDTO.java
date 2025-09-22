package com.exe.skillverse_backend.course_service.dto.enrollmentdto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentDTO {
    //Long courseId, Long userId, String status, Integer progressPercent, String entitlementSource, String entitlementRef
    private Long courseId;
    private Long userId;
    private String status;
    private Integer progressPercent;
    private String entitlementSource;
    private String entitlementRef;
}
