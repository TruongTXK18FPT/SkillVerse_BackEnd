  package com.exe.skillverse_backend.mentor_booking_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "mentor_bookings", indexes = {
        @Index(columnList = "mentor_id, status, start_time"),
        @Index(columnList = "learner_id, status, start_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_id", nullable = false)
    private User mentor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "learner_id", nullable = false)
    private User learner;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BookingStatus status = BookingStatus.PENDING;

    @Column(name = "price_vnd", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceVnd;

    @Column(name = "meeting_link", length = 255)
    private String meetingLink;

    @Column(name = "payment_reference", length = 50)
    private String paymentReference;

    @Column(name = "confirmed_by_learner")
    @Builder.Default
    private Boolean confirmedByLearner = false;

    @Column(name = "mentor_completed_at")
    private LocalDateTime mentorCompletedAt;

    @Column(name = "learner_confirmed_at")
    private LocalDateTime learnerConfirmedAt;

    @Column(name = "learner_completed_at")
    private LocalDateTime learnerCompletedAt;

    /**
     * 24h deadline for mutual confirmation.
     * Set when either mentor or learner first requests completion.
     * Used for auto-complete if neither party confirms within 24h.
     */
    @Column(name = "completion_deadline")
    private LocalDateTime completionDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
}

