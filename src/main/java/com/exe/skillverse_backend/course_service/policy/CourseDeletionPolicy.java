package com.exe.skillverse_backend.course_service.policy;

import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CourseDeletionPolicy {

    private final CourseDeletionPolicyProperties properties;

    public boolean canHardDelete(CourseStatus status, long enrollmentCount, long purchaseCount) {
        if (!properties.isHardDeleteEnabled()) {
            return false;
        }
        if (enrollmentCount > 0 || purchaseCount > 0) {
            return false;
        }
        return properties.getHardDeleteEligibleStatuses() != null
                && properties.getHardDeleteEligibleStatuses().contains(status);
    }
}
