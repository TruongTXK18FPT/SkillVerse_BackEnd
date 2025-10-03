package com.exe.skillverse_backend.premium_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * User subscription entity tracking premium subscriptions
 * One active subscription per user at any time
 */
@Entity
@Table(name = "user_subscriptions", indexes = {
        @Index(columnList = "user_id, is_active"),
        @Index(columnList = "plan_id"),
        @Index(columnList = "start_date, end_date"),
        @Index(columnList = "is_active")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private PremiumPlan plan;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Reference to the payment transaction that created this subscription
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_txn_id")
    private PaymentTransaction paymentTransaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    /**
     * Whether this is a student subscription with discount
     */
    @Column(name = "is_student_subscription", nullable = false)
    @Builder.Default
    private Boolean isStudentSubscription = false;

    /**
     * Auto-renewal flag
     */
    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private Boolean autoRenew = false;

    /**
     * Cancellation reason if cancelled
     */
    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    /**
     * When the subscription was cancelled
     */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Subscription status lifecycle
     */
    public enum SubscriptionStatus {
        PENDING, // Waiting for payment
        ACTIVE, // Currently active
        EXPIRED, // Past end date
        CANCELLED, // Manually cancelled
        SUSPENDED // Temporarily suspended
    }

    /**
     * Check if subscription is currently valid and active
     */
    public boolean isCurrentlyActive() {
        LocalDateTime now = LocalDateTime.now();
        return isActive &&
                status == SubscriptionStatus.ACTIVE &&
                startDate.isBefore(now) &&
                endDate.isAfter(now);
    }

    /**
     * Check if subscription is expired
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(endDate);
    }

    /**
     * Get days remaining in subscription
     */
    public long getDaysRemaining() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(endDate)) {
            return 0;
        }
        return java.time.Duration.between(now, endDate).toDays();
    }

    /**
     * Cancel the subscription
     */
    public void cancel(String reason) {
        this.isActive = false;
        this.status = SubscriptionStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = LocalDateTime.now();
        this.autoRenew = false;
    }

    /**
     * Mark subscription as expired
     */
    public void expire() {
        this.isActive = false;
        this.status = SubscriptionStatus.EXPIRED;
        this.autoRenew = false;
    }

    @PreUpdate
    private void onUpdate() {
        // Auto-update status based on dates
        if (isExpired() && status == SubscriptionStatus.ACTIVE) {
            expire();
        }
    }
}