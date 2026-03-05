package com.exe.skillverse_backend.business_service.entity.enums;

/**
 * Status workflow cho Long-term Job (mở rộng)
 */
public enum LongTermJobStatus {
    DRAFT,
    PENDING_APPROVAL,
    PUBLISHED,
    APPLIED,
    INTERVIEW_SCHEDULED,
    INTERVIEWED,
    OFFER_SENT,
    OFFER_ACCEPTED,
    OFFER_REJECTED,
    CONTRACT_SIGNED,
    PROBATION,
    EMPLOYED,
    EXTENDED,
    TERMINATED,
    RESIGNED,
    CLOSED
}
