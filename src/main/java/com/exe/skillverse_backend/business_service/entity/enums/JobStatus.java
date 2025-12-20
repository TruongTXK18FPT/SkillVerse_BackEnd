package com.exe.skillverse_backend.business_service.entity.enums;

public enum JobStatus {
    IN_PROGRESS, // Job is being created/edited (private draft, not visible to users)
    PENDING_APPROVAL, // Job submitted, waiting for admin approval
    OPEN, // Job is published and accepting applications (public)
    REJECTED, // Job rejected by admin
    CLOSED // Job is closed and no longer accepting applications
}
