package com.exe.skillverse_backend.business_service.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "job_disputes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "initiator_id", nullable = false)
    private Long initiatorId;

    @Column(name = "respondent_id", nullable = false)
    private Long respondentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_type", nullable = false, length = 30)
    private DisputeType disputeType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DisputeStatus status = DisputeStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DisputeResolution resolution;

    @Column(name = "partial_refund_pct", precision = 5, scale = 2)
    private BigDecimal partialRefundPct;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "dispute", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("dispute-evidence")
    @Builder.Default
    private List<DisputeEvidence> evidence = new ArrayList<>();

    public enum DisputeType {
        NO_SUBMISSION,
        POOR_QUALITY,
        MISSING_DELIVERABLE,
        DEADLINE_VIOLATION,
        PAYMENT_ISSUE,
        COMMUNICATION_FAILURE,
        SCOPE_CHANGE,
        SCAM_REPORT,
        OTHER
    }

    public enum DisputeStatus {
        OPEN,
        UNDER_INVESTIGATION,
        AWAITING_RESPONSE,
        RESOLVED,
        DISMISSED,
        ESCALATED
    }

    public enum DisputeResolution {
        FULL_REFUND,
        FULL_RELEASE,
        PARTIAL_REFUND,
        PARTIAL_RELEASE,
        RESUBMIT_REQUIRED,
        NO_ACTION
    }
}
