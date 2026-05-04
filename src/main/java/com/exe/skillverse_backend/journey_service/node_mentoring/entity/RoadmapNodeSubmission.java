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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Current evidence record for a learner's roadmap node.
 * Only ONE current record per (journey_id, node_id) — rework updates same row.
 */
@Entity
@Table(name = "roadmap_node_submissions",
        indexes = {
                @Index(columnList = "learner_id, verification_status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_rns_current", columnNames = {"journey_id", "node_id"})
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapNodeSubmission {

    public enum SubmissionStatus {
        DRAFT,
        SUBMITTED,
        REWORK_REQUESTED,
        RESUBMITTED,
        WITHDRAWN
    }

    public enum VerificationStatus {
        PENDING,
        UNDER_REVIEW,
        APPROVED,
        REJECTED,
        VERIFIED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journey_id", nullable = false)
    private Long journeyId;

    @Column(name = "roadmap_session_id")
    private Long roadmapSessionId;

    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    @Column(name = "submission_text", nullable = false, columnDefinition = "TEXT")
    private String submissionText;

    @Column(name = "evidence_url", length = 1000)
    private String evidenceUrl;

    @Column(name = "evidence_public_id", length = 255)
    private String evidencePublicId;

    @Column(name = "evidence_resource_type", length = 50)
    private String evidenceResourceType;

    @Column(name = "attachment_url", length = 1000)
    private String attachmentUrl;

    @Column(name = "attachment_public_id", length = 255)
    private String attachmentPublicId;

    @Column(name = "attachment_resource_type", length = 50)
    private String attachmentResourceType;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "submission_status", nullable = false, length = 30)
    private SubmissionStatus submissionStatus = SubmissionStatus.SUBMITTED;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 30)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "mentor_feedback", columnDefinition = "TEXT")
    private String mentorFeedback;

    @Builder.Default
    @Column(name = "learner_marked_complete", nullable = false)
    private Boolean learnerMarkedComplete = false;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
