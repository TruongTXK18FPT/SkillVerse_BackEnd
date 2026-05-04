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
 * Optional final-output assessment submitted by learner at the end of a journey
 * when {@code journey_output_verification_required} is enabled.
 */
@Entity
@Table(name = "journey_output_assessments", indexes = {
        @Index(columnList = "journey_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourneyOutputAssessment {

    public enum AssessmentStatus {
        PENDING,
        APPROVED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journey_id", nullable = false)
    private Long journeyId;

    @Column(name = "learner_id", nullable = false)
    private Long learnerId;

    @Column(name = "mentor_id")
    private Long mentorId;

    @Column(name = "submission_text", columnDefinition = "TEXT")
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

    @Column(name = "score")
    private Integer score;

    @Column(name = "feedback", columnDefinition = "TEXT")
    private String feedback;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_status", nullable = false, length = 30)
    private AssessmentStatus assessmentStatus = AssessmentStatus.PENDING;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "assessed_at")
    private Instant assessedAt;
}
