package com.exe.skillverse_backend.admin_service.dto.request;

import com.exe.skillverse_backend.mentor_booking_service.entity.BookingDispute.DisputeResolution;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveBookingDisputeRequest {
    private DisputeResolution resolution;
    private BigDecimal partialAmount;
    private String notes;
}
