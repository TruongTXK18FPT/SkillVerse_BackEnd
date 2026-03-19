package com.exe.skillverse_backend.course_service.policy;

import com.exe.skillverse_backend.course_service.entity.enums.CourseStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumSet;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.course")
public class CourseDeletionPolicyProperties {

    /**
     * Production-safe default: archive by default.
     * Hard delete can be enabled per environment when needed.
     */
    private boolean hardDeleteEnabled = false;

    /**
     * Only these statuses are eligible for hard delete when hardDeleteEnabled=true
     * and no business dependencies exist.
     */
    private Set<CourseStatus> hardDeleteEligibleStatuses = EnumSet.of(
            CourseStatus.DRAFT,
            CourseStatus.REJECTED
    );
}
