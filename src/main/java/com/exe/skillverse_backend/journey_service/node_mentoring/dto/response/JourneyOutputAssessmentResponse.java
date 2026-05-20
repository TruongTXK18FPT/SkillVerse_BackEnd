package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment.AssessmentStatus;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.AiReviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourneyOutputAssessmentResponse {

    private Long id;
    private Long journeyId;
    private Long learnerId;
    private Long mentorId;
    private String submissionText;
    private String evidenceUrl;
    private String evidencePublicId;
    private String evidenceResourceType;
    private String attachmentUrl;
    private String attachmentPublicId;
    private String attachmentResourceType;
    private Integer score;
    private String feedback;
    private AssessmentStatus assessmentStatus;
    private Long latestAiReviewId;
    private AiReviewStatus latestAiReviewStatus;
    private Instant submittedAt;
    private Instant assessedAt;

    public static JourneyOutputAssessmentResponse from(JourneyOutputAssessment a) {
        return JourneyOutputAssessmentResponse.builder()
                .id(a.getId())
                .journeyId(a.getJourneyId())
                .learnerId(a.getLearnerId())
                .mentorId(a.getMentorId())
                .submissionText(a.getSubmissionText())
                .evidenceUrl(a.getEvidenceUrl())
                .evidencePublicId(a.getEvidencePublicId())
                .evidenceResourceType(a.getEvidenceResourceType())
                .attachmentUrl(a.getAttachmentUrl())
                .attachmentPublicId(a.getAttachmentPublicId())
                .attachmentResourceType(a.getAttachmentResourceType())
                .score(a.getScore())
                .feedback(a.getFeedback())
                .assessmentStatus(a.getAssessmentStatus())
                .latestAiReviewId(a.getLatestAiReviewId())
                .latestAiReviewStatus(a.getLatestAiReviewStatus())
                .submittedAt(a.getSubmittedAt())
                .assessedAt(a.getAssessedAt())
                .build();
    }
}
