package com.exe.skillverse_backend.business_service.entity.enums;

public enum JobApplicationStatus {
    PENDING, // Application submitted, waiting for recruiter review
    REVIEWED, // Application has been reviewed by recruiter
    ACCEPTED, // Application accepted by recruiter (with message)
    REJECTED // Application rejected by recruiter (with reason)
}
