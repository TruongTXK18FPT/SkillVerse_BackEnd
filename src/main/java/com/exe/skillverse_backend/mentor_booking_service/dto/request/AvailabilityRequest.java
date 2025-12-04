package com.exe.skillverse_backend.mentor_booking_service.dto.request;

import com.exe.skillverse_backend.mentor_booking_service.entity.MentorAvailability;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class AvailabilityRequest {
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    
    @JsonProperty("isRecurring")
    private boolean isRecurring;
    
    private MentorAvailability.RecurrenceType recurrenceType;
    private ZonedDateTime recurrenceEndDate;
}
