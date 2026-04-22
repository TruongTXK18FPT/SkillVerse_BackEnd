package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission.SubmissionStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Aggregated view of the current node evidence: the single submission row
 * plus the latest review and verification snapshots if they exist.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeEvidenceRecordResponse {

    private Long id;
    private Long journeyId;
    private Long roadmapSessionId;
    private String nodeId;
    private Long assignmentId;
    private Long learnerId;

    private String submissionText;
    private String evidenceUrl;
    private String attachmentUrl;

    private SubmissionStatus submissionStatus;
    private VerificationStatus verificationStatus;
    private String mentorFeedback;

    private Instant submittedAt;
    private Instant updatedAt;

    /** Most recent review, if any. */
    private NodeReviewResponse latestReview;

    /** Most recent verification decision, if any. */
    private NodeVerificationResponse latestVerification;

    public static NodeEvidenceRecordResponse from(
            RoadmapNodeSubmission s,
            NodeReviewResponse latestReview,
            NodeVerificationResponse latestVerification) {
        return NodeEvidenceRecordResponse.builder()
                .id(s.getId())
                .journeyId(s.getJourneyId())
                .roadmapSessionId(s.getRoadmapSessionId())
                .nodeId(s.getNodeId())
                .assignmentId(s.getAssignmentId())
                .learnerId(s.getLearnerId())
                .submissionText(s.getSubmissionText())
                .evidenceUrl(s.getEvidenceUrl())
                .attachmentUrl(s.getAttachmentUrl())
                .submissionStatus(s.getSubmissionStatus())
                .verificationStatus(s.getVerificationStatus())
                .mentorFeedback(s.getMentorFeedback())
                .submittedAt(s.getSubmittedAt())
                .updatedAt(s.getUpdatedAt())
                .latestReview(latestReview)
                .latestVerification(latestVerification)
                .build();
    }
}
