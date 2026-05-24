package com.exe.skillverse_backend.journey_service.node_mentoring.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Mentor review record against a node submission.
 * Must exist before a RoadmapNodeVerification can transition to VERIFIED.
 */
@Entity
@Table(name = "roadmap_node_reviews", indexes = {
        @Index(columnList = "submission_id"),
        @Index(columnList = "mentor_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapNodeReview {

    public enum ReviewResult {
        APPROVED,
        REWORK_REQUESTED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "submission_id", nullable = false)
    private Long submissionId;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Column(name = "score")
    private Integer score;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "criteria_scores_json", columnDefinition = "TEXT")
    private String criteriaScoresJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_result", nullable = false, length = 30)
    private ReviewResult reviewResult;

    @CreationTimestamp
    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;
}
