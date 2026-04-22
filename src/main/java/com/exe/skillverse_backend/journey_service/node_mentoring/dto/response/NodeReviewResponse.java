package com.exe.skillverse_backend.journey_service.node_mentoring.dto.response;

import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeReview;
import com.exe.skillverse_backend.journey_service.node_mentoring.entity.RoadmapNodeReview.ReviewResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NodeReviewResponse {

    private Long id;
    private Long submissionId;
    private Long mentorId;
    private Long bookingId;
    private Integer score;
    private String feedback;
    private ReviewResult reviewResult;
    private Instant reviewedAt;

    public static NodeReviewResponse from(RoadmapNodeReview r) {
        return NodeReviewResponse.builder()
                .id(r.getId())
                .submissionId(r.getSubmissionId())
                .mentorId(r.getMentorId())
                .bookingId(r.getBookingId())
                .score(r.getScore())
                .feedback(r.getFeedback())
                .reviewResult(r.getReviewResult())
                .reviewedAt(r.getReviewedAt())
                .build();
    }
}
