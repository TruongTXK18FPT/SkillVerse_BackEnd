package com.exe.skillverse_backend.assignment_ai_service.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Domain event published when a student submits an assignment.
 * Contains only IDs (not entities) to avoid lazy-loading issues in async context.
 * Consumed by SubmissionCreatedEventListener via @TransactionalEventListener(AFTER_COMMIT).
 */
@Getter
public class SubmissionCreatedEvent extends ApplicationEvent {
    private final Long submissionId;
    private final Long assignmentId;
    private final Long studentId;

    public SubmissionCreatedEvent(Object source, Long submissionId, Long assignmentId, Long studentId) {
        super(source);
        this.submissionId = submissionId;
        this.assignmentId = assignmentId;
        this.studentId = studentId;
    }
}
