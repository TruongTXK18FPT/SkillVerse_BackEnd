package com.exe.skillverse_backend.business_service.entity.enums;

public enum JobApplicationStatus {
    PENDING, // Application submitted, waiting for recruiter review
    REVIEWED, // Application has been reviewed by recruiter
    ACCEPTED, // Application accepted by recruiter (with message)
    INTERVIEW_SCHEDULED, // Interview has been scheduled (REMOTE job only)
    INTERVIEWED, // Interview completed, recruiter reviewing result
    OFFER_SENT, // Offer letter sent to candidate (REMOTE job only)
    OFFER_ACCEPTED, // Candidate accepted the offer (REMOTE job only)
    OFFER_REJECTED, // Candidate rejected the offer or recruiter rejected after interview
    REJECTED, // Application rejected by recruiter (with reason)
    CONTRACT_SIGNED  // Contract has been signed by both parties
}
