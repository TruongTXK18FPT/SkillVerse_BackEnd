package com.exe.skillverse_backend.mentor_booking_service.entity;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    ONGOING,
    MENTORING_ACTIVE,    // long-running roadmap mentoring — no fixed end time
    PENDING_COMPLETION,  // one party clicked, waiting for the other to confirm
    COMPLETED,
    CANCELLED,
    DISPUTED,
    REFUNDED
}

