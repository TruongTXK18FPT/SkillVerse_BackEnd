package com.exe.skillverse_backend.journey_service.node_mentoring.dto.request;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport.GateDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for mentor submitting an evidence report after a Jitsi meeting.
 * Evidence must be submitted BEFORE the mentor can issue a verdict (PASS/FAIL).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmitEvidenceReportRequest {

    /** Free-text summary of the verification meeting outcome. */
    @NotBlank(message = "summaryReport is required")
    private String summaryReport;

    /** Assignments/questions given during the meeting (optional). */
    private List<String> assignmentsGiven;

    /** Actual meeting duration in minutes. */
    private Integer meetingDurationMinutes;

    /** The verdict: PASS or FAIL. */
    @NotNull(message = "gateDecision is required")
    private GateDecision gateDecision;

    /**
     * Node IDs where learner was weak. Required when gateDecision = FAIL.
     * These nodes will be reset to IN_PROGRESS for re-learning.
     */
    private List<String> weakNodeIds;

    /** Reason for failure. Required when gateDecision = FAIL. */
    private String failReason;
}
