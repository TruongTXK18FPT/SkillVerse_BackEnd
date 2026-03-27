package com.exe.skillverse_backend.mentor_booking_service.entity;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    ONGOING,
    PENDING_COMPLETION,  // one party clicked, waiting for the other to confirm
    COMPLETED,
    CANCELLED,
    DISPUTED,
    REFUNDED
}

