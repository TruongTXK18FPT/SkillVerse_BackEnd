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
 * Final mentor confirmation and gate decision for a journey.
 * A journey cannot transition to COMPLETED_VERIFIED until a PASS record exists here.
 */
@Entity
@Table(name = "journey_completion_reports", indexes = {
        @Index(columnList = "journey_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourneyCompletionReport {

    public enum GateDecision {
        PASS,
        FAIL,
        PENDING
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journey_id", nullable = false)
    private Long journeyId;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    @Column(name = "booking_id")
    private Long bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "gate_decision", nullable = false, length = 30)
    private GateDecision gateDecision;

    @Column(name = "completion_note", columnDefinition = "TEXT")
    private String completionNote;

    @CreationTimestamp
    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private Instant confirmedAt;
}
