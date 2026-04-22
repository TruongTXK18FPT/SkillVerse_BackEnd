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
 * Mentor node verification decision. Only the mentor assigned to the node's
 * mentoring flow is authorized to create this record.
 */
@Entity
@Table(name = "roadmap_node_verifications", indexes = {
        @Index(columnList = "submission_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoadmapNodeVerification {

    public enum NodeVerificationStatus {
        VERIFIED,
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

    @Enumerated(EnumType.STRING)
    @Column(name = "node_verification_status", nullable = false, length = 30)
    private NodeVerificationStatus nodeVerificationStatus;

    @Column(name = "verification_note", columnDefinition = "TEXT")
    private String verificationNote;

    @CreationTimestamp
    @Column(name = "verified_at", nullable = false, updatable = false)
    private Instant verifiedAt;
}
