package com.exe.skillverse_backend.course_service.policy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.course.quiz-attempt-session")
public class CourseQuizAttemptSessionProperties {

    /**
     * Enable/disable session tracking for in-progress quiz attempts.
     */
    private boolean enabled = true;

    /**
     * Session inactivity TTL in minutes.
     */
    private int ttlMinutes = 30;
}
