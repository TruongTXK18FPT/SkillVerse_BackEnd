package com.exe.skillverse_backend.mentor_booking_service.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalRequest {
    @NotNull
    private Long bookingId;
}

