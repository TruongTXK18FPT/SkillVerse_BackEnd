package com.exe.skillverse_backend.business_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "job_escrow")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEscrow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    @JsonIgnore
    private ShortTermJob job;

    @Column(name = "recruiter_id", nullable = false)
    private Long recruiterId;

    @Column(name = "worker_id")
    private Long workerId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal platformFee;

    @Column(nullable = false, precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal feeRate = new BigDecimal("0.10");

    @Column(name = "escrow_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal escrowBalance = BigDecimal.ZERO;

    @Column(name = "pending_payout_balance", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal pendingPayoutBalance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private EscrowStatus status = EscrowStatus.PENDING;

    private LocalDateTime fundedAt;
    private LocalDateTime releasedAt;
    private LocalDateTime refundedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public enum EscrowStatus {
        PENDING,
        FUNDED,
        PARTIALLY_RELEASED,
        FULLY_RELEASED,
        REFUNDED,
        DISPUTED
    }
}
