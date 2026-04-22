package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport.GateDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourneyCompletionReportResponse {

    private Long id;
    private Long journeyId;
    private Long mentorId;
    private Long bookingId;
    private GateDecision gateDecision;
    private String completionNote;
    private Instant confirmedAt;

    public static JourneyCompletionReportResponse from(JourneyCompletionReport r) {
        return JourneyCompletionReportResponse.builder()
                .id(r.getId())
                .journeyId(r.getJourneyId())
                .mentorId(r.getMentorId())
                .bookingId(r.getBookingId())
                .gateDecision(r.getGateDecision())
                .completionNote(r.getCompletionNote())
                .confirmedAt(r.getConfirmedAt())
                .build();
    }
}
