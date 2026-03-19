package com.exe.skillverse_backend.course_service.policy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.course.revision")
public class CourseRevisionFeatureProperties {

    public enum SubmitChangeCheckMode {
        OFF,
        WARN,
        ENFORCE
    }

    /**
     * Read path via active_revision for learner/public.
     * Default false to avoid behavior change during platform bootstrap.
     */
    private boolean readEnabled = false;

    /**
     * Mentor revision write APIs (create/edit/submit).
     */
    private boolean writeEnabled = false;

    /**
     * Admin approve/reject revision APIs.
     */
    private boolean approvalEnabled = false;

    /**
     * Controls no-change submit behavior:
     * - OFF: no check
     * - WARN: log warning but allow submit
     * - ENFORCE: throw 409 when no meaningful changes
     */
    private SubmitChangeCheckMode submitChangeCheckMode = SubmitChangeCheckMode.ENFORCE;

    /**
     * If true, revisions with missing source baseline (legacy inconsistency) are blocked on submit.
     */
    private boolean requireSourceBaseline = false;

    /**
     * Canary rollout percentage for requireSourceBaseline (0..100), deterministic by courseId bucket.
     * - 0: disabled even if requireSourceBaseline=true
     * - 100: enforce for all eligible revisions
     */
    private int requireSourceBaselineRolloutPercent = 100;

    /**
     * Maximum canonicalized contentSnapshot payload size (UTF-8 bytes) accepted on revision update.
     * Set <= 0 to disable.
     */
    private int maxContentSnapshotBytes = 1_048_576; // 1MB

    /**
     * Warning threshold for snapshot hash compute latency.
     * Set <= 0 to disable warning.
     */
    private long snapshotHashLatencyWarnMs = 200;
}
