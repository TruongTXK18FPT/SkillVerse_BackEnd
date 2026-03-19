package com.exe.skillverse_backend.mentor_booking_service.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    private LocalDateTime startTime;

    @NotNull
    @Min(60)
    private Integer durationMinutes;

    @NotNull
    @DecimalMin("1000")
    private BigDecimal priceVnd;

    @NotNull
    @Pattern(regexp = "^(PAYOS|WALLET)$")
    private String paymentMethod;

    private String successUrl;
    private String cancelUrl;
}

