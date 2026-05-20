package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission.SubmissionStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeSubmission.VerificationStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.AiReviewStatus;
import com.exe.skillverse_backend.ai_service.entity.UserRoadmapProgress;
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
    private String evidencePublicId;
    private String evidenceResourceType;
    private String attachmentUrl;
    private String attachmentPublicId;
    private String attachmentResourceType;

    private SubmissionStatus submissionStatus;
    private VerificationStatus verificationStatus;
    private String mentorFeedback;

    private Instant submittedAt;
    private Instant updatedAt;
    private Boolean learnerMarkedComplete;
    private String roadmapProgressStatus;

    private Long latestAiReviewId;
    private AiReviewStatus latestAiReviewStatus;

    /**
     * True when an active mentor booking (NODE_MENTORING or ROADMAP_MENTORING) covers this node.
     * FE uses this to choose between "self-confirm" button and "awaiting mentor review" badge.
     * Populated by the service layer — not stored in the submission row.
     */
    private Boolean hasMentorCoverage;

    /** Most recent review, if any. */
    private NodeReviewResponse latestReview;

    /** Most recent verification decision, if any. */
    private NodeVerificationResponse latestVerification;

    public static NodeEvidenceRecordResponse from(
            RoadmapNodeSubmission s,
            NodeReviewResponse latestReview,
            NodeVerificationResponse latestVerification,
            UserRoadmapProgress roadmapProgress) {
        return NodeEvidenceRecordResponse.builder()
                .id(s.getId())
                .journeyId(s.getJourneyId())
                .roadmapSessionId(s.getRoadmapSessionId())
                .nodeId(s.getNodeId())
                .assignmentId(s.getAssignmentId())
                .learnerId(s.getLearnerId())
                .submissionText(s.getSubmissionText())
                .evidenceUrl(s.getEvidenceUrl())
                .evidencePublicId(s.getEvidencePublicId())
                .evidenceResourceType(s.getEvidenceResourceType())
                .attachmentUrl(s.getAttachmentUrl())
                .attachmentPublicId(s.getAttachmentPublicId())
                .attachmentResourceType(s.getAttachmentResourceType())
                .submissionStatus(s.getSubmissionStatus())
                .verificationStatus(s.getVerificationStatus())
                .mentorFeedback(s.getMentorFeedback())
                .submittedAt(s.getSubmittedAt())
                .updatedAt(s.getUpdatedAt())
                .learnerMarkedComplete(
                        Boolean.TRUE.equals(s.getLearnerMarkedComplete()))
                .roadmapProgressStatus(
                        roadmapProgress != null && roadmapProgress.getStatus() != null
                                ? roadmapProgress.getStatus().name()
                                : null)
                .latestReview(latestReview)
                .latestVerification(latestVerification)
                .latestAiReviewId(s.getLatestAiReviewId())
                .latestAiReviewStatus(s.getLatestAiReviewStatus())
                .build();
    }
}
