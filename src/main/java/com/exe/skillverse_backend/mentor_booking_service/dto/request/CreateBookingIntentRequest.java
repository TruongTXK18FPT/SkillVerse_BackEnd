package com.exe.skillverse_backend.mentor_booking_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingIntentRequest {
    @NotNull
    private Long mentorId;

    @NotNull
    private ZonedDateTime startTime;

    /**
     * Duration in minutes. For ROADMAP_MENTORING this should be 0 (unlimited).
     * For other types, minimum is 60 (validated in service layer).
     */
    @NotNull
    private Integer durationMinutes;

    @NotNull
    @DecimalMin("1000")
    private BigDecimal priceVnd;

    @NotNull
    @Pattern(regexp = "^WALLET$")
    private String paymentMethod;

    // V3 Phase 1: optional node/journey context — null for legacy bookings
    private Long journeyId;
    private String nodeId;
    private Long nodeSkillId;
    private String bookingType; // "GENERAL" | "NODE_MENTORING" | "JOURNEY_MENTORING" | "ROADMAP_MENTORING"
}

