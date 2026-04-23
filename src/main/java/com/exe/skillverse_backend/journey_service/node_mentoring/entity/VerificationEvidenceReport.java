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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Evidence report submitted by the mentor AFTER a Jitsi verification meeting.
 * This is mandatory before the mentor can issue a PASS/FAIL verdict.
 *
 * Each attempt has exactly one evidence report. Multiple attempts accumulate
 * (attempt 1, 2, 3) until the learner either passes or hits the 3-fail limit.
 */
@Entity
@Table(name = "verification_evidence_reports", indexes = {
        @Index(columnList = "journey_id"),
        @Index(columnList = "booking_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationEvidenceReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "journey_id", nullable = false)
    private Long journeyId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "mentor_id", nullable = false)
    private Long mentorId;

    /** Jitsi meeting link used for this verification attempt. */
    @Column(name = "meeting_jitsi_link", length = 500)
    private String meetingJitsiLink;

    /** Actual meeting duration in minutes. */
    @Column(name = "meeting_duration_minutes")
    private Integer meetingDurationMinutes;

    /** Free-text summary of the verification meeting outcome. */
    @Column(name = "summary_report", columnDefinition = "TEXT", nullable = false)
    private String summaryReport;

    /** JSON array of assignments/questions given during the meeting. */
    @Column(name = "assignments_given", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String assignmentsGiven;

    /**
     * JSON array of nodeIds that the learner was weak on (FAIL case only).
     * These nodes will be reset to IN_PROGRESS for re-learning.
     */
    @Column(name = "weak_node_ids", columnDefinition = "TEXT")
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    private String weakNodeIds;

    /** Reason for failure. Required when gateDecision = FAIL. */
    @Column(name = "fail_reason", columnDefinition = "TEXT")
    private String failReason;

    /** The verdict: PASS or FAIL. */
    @Enumerated(EnumType.STRING)
    @Column(name = "gate_decision", nullable = false, length = 10)
    private JourneyCompletionReport.GateDecision gateDecision;

    /** Which attempt this report belongs to (1, 2, or 3). */
    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;
}
