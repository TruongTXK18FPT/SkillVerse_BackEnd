package com.exe.skillverse_backend.journey_service.node_mentoring.dto.request;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport.GateDecision;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mentor final completion confirmation.
 * {@code gateDecision = PASS} is required to unblock completeJourney; FAIL/PENDING
 * are tracked for audit but do not pass the gate.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmJourneyCompletionRequest {

    @NotNull
    private GateDecision gateDecision;

    private String completionNote;

    private Long bookingId;
}
