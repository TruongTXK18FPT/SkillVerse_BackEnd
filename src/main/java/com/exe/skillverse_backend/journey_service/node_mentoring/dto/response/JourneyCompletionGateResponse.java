package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aggregated final-verification gate state for a journey.
 * Used by both learner and mentor UIs to decide which CTA to render.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourneyCompletionGateResponse {

    public enum FinalGateStatus {
        /** Gate does not apply — finalVerificationRequired is false. */
        NOT_REQUIRED,
        /** Gate applies and at least one requirement is still unmet. */
        BLOCKED,
        /** All requirements met — completeJourney will succeed. */
        PASSED
    }

    private Long journeyId;
    private FinalGateStatus finalGateStatus;

    private Boolean finalVerificationRequired;
    private Boolean journeyOutputVerificationRequired;

    private Boolean hasPassCompletionReport;
    private Boolean outputAssessmentApproved;

    /** Human-readable reasons explaining why the gate is BLOCKED, if any. */
    private List<String> blockingReasons;
}
