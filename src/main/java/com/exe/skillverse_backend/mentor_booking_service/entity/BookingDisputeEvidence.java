package com.exe.skillverse_backend.mentor_booking_service.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "booking_dispute_evidence", indexes = {
        @Index(columnList = "dispute_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDisputeEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id", nullable = false)
    @JsonBackReference("booking-dispute-evidence")
    private BookingDispute dispute;

    @Column(name = "submitted_by", nullable = false)
    private Long submittedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false, length = 20)
    private EvidenceType evidenceType;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Column(name = "is_official")
    private Boolean isOfficial = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "evidence", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("createdAt ASC")
    @JsonManagedReference("booking-dispute-response")
    private List<BookingDisputeResponse> responses = new ArrayList<>();

    public Long getDisputeId() {
        return dispute != null ? dispute.getId() : null;
    }

    public enum EvidenceType {
        TEXT, FILE, LINK, SCREENSHOT, CHAT_LOG, IMAGE
    }
}
