package com.exe.skillverse_backend.mentor_booking_service.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "booking_dispute_responses", indexes = {
        @Index(columnList = "evidence_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDisputeResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evidence_id", nullable = false)
    @JsonBackReference("booking-dispute-response")
    private BookingDisputeEvidence evidence;

    @Column(name = "responded_by", nullable = false)
    private Long respondedBy;

    @Column(name = "responded_by_name", length = 200)
    private String respondedByName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Builder.Default
    @Column(name = "is_admin_response")
    private Boolean isAdminResponse = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getEvidenceId() {
        return evidence != null ? evidence.getId() : null;
    }
}
