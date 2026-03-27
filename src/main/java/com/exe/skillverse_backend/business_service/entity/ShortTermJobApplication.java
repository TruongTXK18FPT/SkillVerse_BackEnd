package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.business_service.entity.enums.ShortTermApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Application cho Short-term Job
 */
@Entity
@Table(name = "short_term_job_applications", uniqueConstraints = {
    @UniqueConstraint(name = "uk_short_term_app_user_job", columnNames = {"user_id", "short_term_job_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortTermJobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "short_term_job_id", nullable = false)
    private ShortTermJob shortTermJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ==================== APPLICATION DETAILS ====================
    
    @Column(name = "cover_letter", columnDefinition = "TEXT")
    private String coverLetter;

    @Column(name = "proposed_price", precision = 15, scale = 2)
    private BigDecimal proposedPrice; // Giá đề xuất (nếu negotiable)

    @Column(name = "proposed_duration", length = 100)
    private String proposedDuration; // Thời gian đề xuất

    @Column(columnDefinition = "TEXT")
    private String portfolio; // JSON array of links to relevant work

    // ==================== STATUS ====================
    
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ShortTermApplicationStatus status = ShortTermApplicationStatus.PENDING;

    // ==================== TIMESTAMPS ====================
    
    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ==================== WORK SUBMISSION ====================
    
    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JobDeliverable> deliverables = new ArrayList<>();

    @Column(name = "work_note", columnDefinition = "TEXT")
    private String workNote; // Ghi chú khi bàn giao

    // ==================== REVISION ====================
    
    @Builder.Default
    @Column(name = "revision_count")
    private Integer revisionCount = 0;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RevisionNote> revisionNotes = new ArrayList<>();

    // ==================== SLA / CANCELLATION / DISPUTE FIELDS ====================

    @Column(name = "review_deadline_at")
    private LocalDateTime reviewDeadlineAt;

    @Column(name = "response_deadline_at")
    private LocalDateTime responseDeadlineAt;

    @Column(name = "cancellation_requested_at")
    private LocalDateTime cancellationRequestedAt;

    @Column(name = "cancellation_requested_by")
    private Long cancellationRequestedBy;

    @Column(name = "dispute_eligibility_unlocked", nullable = false)
    @Builder.Default
    private Boolean disputeEligibilityUnlocked = false;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @PrePersist
    protected void onCreate() {
        appliedAt = LocalDateTime.now();
        lastActivityAt = LocalDateTime.now();
    }

    // ==================== HELPER METHODS ====================
    
    public void addDeliverable(JobDeliverable deliverable) {
        deliverables.add(deliverable);
        deliverable.setApplication(this);
    }

    public void addRevisionNote(RevisionNote note) {
        revisionNotes.add(note);
        note.setApplication(this);
        revisionCount = (revisionCount == null ? 0 : revisionCount) + 1;
    }
}
