package com.exe.skillverse_backend.course_service.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Published when a course revision is approved by admin.
 * Triggers BM25 catalog index refresh for the affected course.
 */
@Getter
public class CourseRevisionApprovedEvent extends ApplicationEvent {

    private final Long courseId;
    private final Long revisionId;

    public CourseRevisionApprovedEvent(Object source, Long courseId, Long revisionId) {
        super(source);
        this.courseId = courseId;
        this.revisionId = revisionId;
    }
}