package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.JourneyOutputAssessment.AssessmentStatus;
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
    private String attachmentUrl;
    private Integer score;
    private String feedback;
    private AssessmentStatus assessmentStatus;
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
                .attachmentUrl(a.getAttachmentUrl())
                .score(a.getScore())
                .feedback(a.getFeedback())
                .assessmentStatus(a.getAssessmentStatus())
                .submittedAt(a.getSubmittedAt())
                .assessedAt(a.getAssessedAt())
                .build();
    }
}
