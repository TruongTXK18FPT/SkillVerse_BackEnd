package com.exe.skillverse_backend.premium_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Entity to track subscription cancellations
 * Used to prevent abuse of refund policy
 */
@Entity
@Table(name = "subscription_cancellations", indexes = {
        @Index(columnList = "user_id, cancellation_month"),
        @Index(columnList = "user_id, created_at"),
        @Index(columnList = "cancellation_month")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionCancellation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private UserSubscription subscription;

    /**
     * Month when cancellation occurred (format: YYYY-MM)
     * Used to count cancellations per month
     */
    @Column(name = "cancellation_month", nullable = false, length = 7)
    private String cancellationMonth;

    /**
     * Refund percentage applied (0, 50, or 100)
     */
    @Column(name = "refund_percentage", nullable = false)
    private Integer refundPercentage;

    /**
     * Refund amount in VND
     */
    @Column(name = "refund_amount", precision = 12, scale = 2)
    private BigDecimal refundAmount;

    /**
     * Days since subscription started
     */
    @Column(name = "days_since_purchase")
    private Long daysSincePurchase;

    /**
     * Reason for cancellation
     */
    @Column(name = "reason", length = 500)
    private String reason;

    /**
     * Type of cancellation
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_type", nullable = false, length = 30)
    private CancellationType cancellationType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Cancellation types
     */
    public enum CancellationType {
        CANCEL_WITH_REFUND,      // Cancelled with refund
        CANCEL_AUTO_RENEWAL,     // Only cancelled auto-renewal
        ADMIN_CANCELLED          // Cancelled by admin
    }

    /**
     * Get current month in YYYY-MM format
     */
    public static String getCurrentMonth() {
        YearMonth currentMonth = YearMonth.now();
        return currentMonth.toString();
    }

    /**
     * Check if this cancellation is in the current month
     */
    public boolean isCurrentMonth() {
        return cancellationMonth.equals(getCurrentMonth());
    }
}
