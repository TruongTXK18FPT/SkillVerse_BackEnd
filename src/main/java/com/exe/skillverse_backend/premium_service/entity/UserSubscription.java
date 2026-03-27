package com.exe.skillverse_backend.premium_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.payment_service.entity.PaymentTransaction;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

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
     * Cancellation records associated with this subscription
     */
    @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private Set<SubscriptionCancellation> cancellations = new HashSet<>();

    /**
     * Whether this is a student subscription with discount
     */
    @Column(name = "is_student_subscription", nullable = false)
    @Builder.Default
    private Boolean isStudentSubscription = false;

    @Column(name = "is_discounted_pricing")
    private Boolean discountedPricing;

    /**
     * Auto-renewal flag
     */
    @Column(name = "auto_renew", nullable = false)
    @Builder.Default
    private Boolean autoRenew = false;

    /**
     * Locked renewal amount for the upcoming billing cycle.
     * Falls back to the live plan price when null for legacy subscriptions.
     */
    @Column(name = "renewal_price_snapshot", precision = 12, scale = 2)
    private BigDecimal renewalPriceSnapshot;

    /**
     * When the current renewal snapshot was locked in.
     */
    @Column(name = "renewal_price_locked_at")
    private LocalDateTime renewalPriceLockedAt;

    /**
     * Actual amount charged for the current subscription cycle.
     * Used for refund and upgrade-credit calculations instead of live plan price.
     */
    @Column(name = "current_cycle_paid_amount_snapshot", precision = 12, scale = 2)
    private BigDecimal currentCyclePaidAmountSnapshot;

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
        return Duration.between(now, endDate).toDays();
    }

    public boolean isDiscountedPricingApplied() {
        if (discountedPricing != null) {
            return discountedPricing;
        }
        return Boolean.TRUE.equals(isStudentSubscription);
    }

    /**
     * Cancel the subscription (permanent - user requested)
     */
    public void cancel(String reason) {
        this.isActive = false;
        this.status = SubscriptionStatus.CANCELLED;
        this.cancellationReason = reason;
        this.cancelledAt = LocalDateTime.now();
        this.autoRenew = false;
    }

    /**
     * Suspend the subscription (temporary - can be reactivated)
     * Used when user upgrades from Free Tier to Premium
     */
    public void suspend(String reason) {
        this.isActive = false;
        this.status = SubscriptionStatus.SUSPENDED;
        this.cancellationReason = reason;
        // Don't set cancelledAt - this is suspension, not cancellation
    }

    /**
     * Reactivate a suspended subscription
     */
    public void reactivate() {
        this.isActive = true;
        this.status = SubscriptionStatus.ACTIVE;
        this.cancellationReason = null;
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
