package com.exe.skillverse_backend.mentor_verification_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity representing a batch verification request from a mentor.
 * A batch groups multiple {@link MentorSkillVerificationRequest} items and a set of shared evidences.
 */
@Entity
@Table(name = "mentor_batch_verification_requests", indexes = {
        @Index(name = "idx_mbvr_mentor_status", columnList = "mentor_id, status"),
        @Index(name = "idx_mbvr_status_submitted", columnList = "status, submitted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MentorBatchVerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mentor who created the batch */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_id", nullable = false)
    private User mentor;

    /** Batch status */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private VerificationStatus status = VerificationStatus.PENDING;

    /** Optional external links for the whole batch */
    @Column(name = "github_url", length = 500)
    private String githubUrl;

    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    /** General notes from the mentor */
    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;

    /** General review note from admin */
    @Column(name = "general_review_note", columnDefinition = "TEXT")
    private String generalReviewNote;

    /** Timestamp when the batch was created */
    @Column(name = "submitted_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();

    /** Admin who reviewed the batch */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Skills that belong to this batch */
    @OneToMany(mappedBy = "batchRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MentorSkillVerificationRequest> skillRequests = new ArrayList<>();

    /** Evidences that apply to the entire batch */
    @OneToMany(mappedBy = "batchRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MentorVerificationEvidence> evidences = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
