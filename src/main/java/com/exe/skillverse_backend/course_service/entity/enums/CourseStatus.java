package com.exe.skillverse_backend.course_service.entity.enums;

public enum CourseStatus {
    PUBLIC,     // Course is live and visible to all users
    DRAFT,      // Course is being created/edited by author
    PENDING,    // Course submitted for admin approval
    ARCHIVED    // Course is archived and not visible
}
