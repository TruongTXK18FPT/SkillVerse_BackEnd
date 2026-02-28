package com.exe.skillverse_backend.course_service.entity.enums;

public enum CourseStatus {
    PUBLIC,     // Course is live and visible to all users
    DRAFT,      // Course is being created/edited by author
    PENDING,    // Course submitted for admin approval
    REJECTED,   // Course rejected by admin — mentor can edit and resubmit
    ARCHIVED,   // Course archived (soft-deleted) by mentor
    SUSPENDED   // Course suspended by admin due to violations — mentor cannot reactivate
}
