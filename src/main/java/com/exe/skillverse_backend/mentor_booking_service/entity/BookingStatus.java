package com.exe.skillverse_backend.mentor_booking_service.entity;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    ONGOING,
    MENTOR_COMPLETED,  // mentor done, waiting for learner confirm
    COMPLETED,
    CANCELLED,
    DISPUTED,
    REFUNDED
}

