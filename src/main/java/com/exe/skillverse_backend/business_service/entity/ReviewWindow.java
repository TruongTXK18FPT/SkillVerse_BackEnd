package com.exe.skillverse_backend.business_service.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "review_windows")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.ACTIVE;

    @Column(name = "auto_action_at")
    private LocalDateTime autoActionAt;

    @Column(name = "reminder_sent")
    @Builder.Default
    private Boolean reminderSent = false;

    private LocalDateTime approvedAt;
    private LocalDateTime autoApprovedAt;

    public enum ReviewStatus {
        ACTIVE,
        AUTO_APPROVED,
        MANUAL_APPROVED,
        EXPIRED
    }
}
