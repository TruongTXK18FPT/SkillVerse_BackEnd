package com.exe.skillverse_backend.payment_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment transaction entity to track all payment operations
 * Supports multiple payment methods: PayOS, MoMo, VNPay, etc.
 */
@Entity
@Table(name = "payment_transactions", indexes = {
        @Index(columnList = "user_id, status"),
        @Index(columnList = "payment_method, status"),
        @Index(columnList = "reference_id"),
        @Index(columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    /**
     * External payment gateway reference ID
     * Used for tracking payments with PayOS, MoMo, VNPay, etc.
     */
    @Column(name = "reference_id", length = 100)
    private String referenceId;

    /**
     * Internal transaction reference for our system
     */
    @Column(name = "internal_reference", length = 50, unique = true)
    private String internalReference;

    /**
     * Description of the payment (e.g., "Premium Basic Subscription", "Student
     * Pack")
     */
    @Column(name = "description", length = 255)
    private String description;

    /**
     * Additional metadata in JSON format
     * Can store gateway-specific response data
     */
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    /**
     * Failure reason if payment failed
     */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Payment types supported in the system
     */
    public enum PaymentType {
        PREMIUM_SUBSCRIPTION,
        COURSE_PURCHASE,
        WALLET_TOPUP,
        COIN_PURCHASE,
        REFUND
    }

    /**
     * Payment status lifecycle
     */
    public enum PaymentStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED,
        REFUNDED
    }

    /**
     * Supported payment methods
     */
    public enum PaymentMethod {
        PAYOS,
        MOMO,
        VNPAY,
        BANK_TRANSFER,
        CREDIT_CARD
    }

    @PrePersist
    private void generateInternalReference() {
        if (internalReference == null) {
            internalReference = "TXN_" + System.currentTimeMillis() + "_" +
                    (int) (Math.random() * 1000);
        }
    }
}