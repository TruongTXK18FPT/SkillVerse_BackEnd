package com.exe.skillverse_backend.mentor_booking_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "booking_disputes", indexes = {
        @Index(columnList = "booking_id", unique = true),
        @Index(columnList = "initiator_id"),
        @Index(columnList = "respondent_id"),
        @Index(columnList = "status")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDispute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    @JsonIgnore
    private Booking booking;

    @Column(name = "initiator_id", nullable = false)
    private Long initiatorId;

    @Column(name = "respondent_id", nullable = false)
    private Long respondentId;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DisputeStatus status = DisputeStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DisputeResolution resolution;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "released_amount", precision = 12, scale = 2)
    private BigDecimal releasedAmount;

    @Column(name = "mentor_payout_amount", precision = 12, scale = 2)
    private BigDecimal mentorPayoutAmount;

    @Column(name = "admin_commission_amount", precision = 12, scale = 2)
    private BigDecimal adminCommissionAmount;

    @Column(name = "resolved_by")
    private Long resolvedBy;

    private LocalDateTime resolvedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "dispute", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonManagedReference("booking-dispute-evidence")
    private List<BookingDisputeEvidence> evidence = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getBookingId() {
        return booking != null ? booking.getId() : null;
    }

    public enum DisputeStatus {
        OPEN, UNDER_INVESTIGATION, AWAITING_RESPONSE, RESOLVED, DISMISSED, ESCALATED
    }

    public enum DisputeResolution {
        FULL_REFUND, FULL_RELEASE, PARTIAL_REFUND, PARTIAL_RELEASE
    }
}
