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

    @NotNull
    @Min(60)
    private Integer durationMinutes;

    @NotNull
    @DecimalMin("1000")
    private BigDecimal priceVnd;

    @NotNull
    @Pattern(regexp = "^WALLET$")
    private String paymentMethod;
}

