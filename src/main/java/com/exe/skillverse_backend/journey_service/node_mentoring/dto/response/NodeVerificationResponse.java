package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeVerification;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeVerification.NodeVerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeVerificationResponse {

    private Long id;
    private Long submissionId;
    private Long mentorId;
    private Long bookingId;
    private NodeVerificationStatus nodeVerificationStatus;
    private String verificationNote;
    private Instant verifiedAt;

    public static NodeVerificationResponse from(RoadmapNodeVerification v) {
        return NodeVerificationResponse.builder()
                .id(v.getId())
                .submissionId(v.getSubmissionId())
                .mentorId(v.getMentorId())
                .bookingId(v.getBookingId())
                .nodeVerificationStatus(v.getNodeVerificationStatus())
                .verificationNote(v.getVerificationNote())
                .verifiedAt(v.getVerifiedAt())
                .build();
    }
}
