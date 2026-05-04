package com.exe.skillverse_backend.journey_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourneyVerificationDetailResponse {
    private Long journeyId;
    private String skillName;
    private Instant completedAt;

    private Long mentorId;
    private String mentorName;
    private String mentorAvatarUrl;
    private String mentorTitle;
    private String mentorProfileSlug;
    private String gateCompletionNote;

    private List<NodeSubmissionDetail> nodes;
    private OutputAssessmentDetail finalAssessment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeSubmissionDetail {
        private String nodeId;
        private String nodeTitle;
        private String submissionText;
        private String evidenceUrl;
        private String mentorFeedback;
        private Instant submittedAt;
        private Instant verifiedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputAssessmentDetail {
        private String submissionText;
        private String evidenceUrl;
        private String feedback;
        private Integer score;
        private String assessmentStatus;
        private Instant assessedAt;
    }
}
