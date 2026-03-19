package com.exe.skillverse_backend.course_service.policy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.course.revision.learning-revision-backfill")
public class CourseLearningRevisionBackfillProperties {

    /**
     * Safe-by-default: OFF in production until canary is ready.
     */
    private boolean enabled = false;

    /**
     * Number of enrollments processed per SQL batch.
     */
    private int batchSize = 1000;

    /**
     * Number of batches executed in one scheduler run.
     */
    private int maxBatchesPerRun = 3;

    /**
     * Scheduler fixed delay in milliseconds.
     */
    private long fixedDelayMs = 60000L;
}

