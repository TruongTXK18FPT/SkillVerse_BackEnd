package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyCompletionReport.GateDecision;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.VerificationEvidenceReport;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationEvidenceReportResponse {
    private Long id;
    private Long journeyId;
    private Long bookingId;
    private Long mentorId;
    private String meetingJitsiLink;
    private Integer meetingDurationMinutes;
    private String summaryReport;
    private List<String> assignmentsGiven;
    private List<String> weakNodeIds;
    private String failReason;
    private GateDecision gateDecision;
    private Integer attemptNumber;
    private Instant submittedAt;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static VerificationEvidenceReportResponse from(VerificationEvidenceReport entity) {
        return VerificationEvidenceReportResponse.builder()
                .id(entity.getId())
                .journeyId(entity.getJourneyId())
                .bookingId(entity.getBookingId())
                .mentorId(entity.getMentorId())
                .meetingJitsiLink(entity.getMeetingJitsiLink())
                .meetingDurationMinutes(entity.getMeetingDurationMinutes())
                .summaryReport(entity.getSummaryReport())
                .assignmentsGiven(parseJsonList(entity.getAssignmentsGiven()))
                .weakNodeIds(parseJsonList(entity.getWeakNodeIds()))
                .failReason(entity.getFailReason())
                .gateDecision(entity.getGateDecision())
                .attemptNumber(entity.getAttemptNumber())
                .submittedAt(entity.getSubmittedAt())
                .build();
    }

    private static List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
