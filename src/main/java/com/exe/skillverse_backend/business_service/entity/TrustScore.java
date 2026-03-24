package com.exe.skillverse_backend.business_service.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
@Table(name = "trust_scores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @JsonIgnore
    private com.exe.skillverse_backend.auth_service.entity.User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal totalScore = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "trust_tier", length = 20)
    @Builder.Default
    private TrustTier trustTier = TrustTier.NEWCOMER;

    @Column(name = "completion_rate", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal completionRate = BigDecimal.ZERO;

    @Column(name = "avg_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal avgRating = BigDecimal.ZERO;

    @Column(name = "dispute_rate", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal disputeRate = BigDecimal.ZERO;

    @Column(name = "total_jobs", nullable = false)
    @Builder.Default
    private Integer totalJobs = 0;

    @Column(name = "completed_jobs", nullable = false)
    @Builder.Default
    private Integer completedJobs = 0;

    @Column(name = "disputed_jobs", nullable = false)
    @Builder.Default
    private Integer disputedJobs = 0;

    @Column(name = "total_reviews", nullable = false)
    @Builder.Default
    private Integer totalReviews = 0;

    @Column(name = "account_age_days")
    @Builder.Default
    private Integer accountAgeDays = 0;

    @Column(name = "response_time_hours", precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal responseTimeHours = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum TrustTier {
        NEWCOMER(0, 30),
        BASIC(31, 60),
        TRUSTED(61, 85),
        ELITE(86, 100);

        private final int min;
        private final int max;

        TrustTier(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public static TrustTier fromScore(BigDecimal score) {
            double s = score.doubleValue();
            if (s <= 30) return NEWCOMER;
            if (s <= 60) return BASIC;
            if (s <= 85) return TRUSTED;
            return ELITE;
        }
    }

    public void recalculate(BigDecimal completionRate, BigDecimal avgRating, BigDecimal disputeRate,
                            BigDecimal responseTimeHours, Integer accountAgeDays) {
        this.completionRate = completionRate;
        this.avgRating = avgRating;
        this.disputeRate = disputeRate;
        this.responseTimeHours = responseTimeHours;
        this.accountAgeDays = accountAgeDays;

        // Score formula:
        // completionRate * 30% + avgRating (normalized to 100) * 25% + (1 - disputeRate) * 20%
        // + (1 - normalizedResponseTime) * 15% + (normalizedAccountAge) * 10%

        BigDecimal score = BigDecimal.ZERO;

        // Completion rate: 30%
        score = score.add(completionRate.multiply(new BigDecimal("30")));

        // Average rating: normalize 1-5 to 0-100, then 25%
        BigDecimal ratingScore = avgRating.multiply(new BigDecimal("20")); // 1-5 -> 20-100, weighted 25%
        score = score.add(ratingScore);

        // Dispute rate: inverse, 20%
        BigDecimal disputeScore = BigDecimal.ONE.subtract(disputeRate).multiply(new BigDecimal("20"));
        score = score.add(disputeScore);

        // Response time: normalize hours (0-24h -> 0-100), inverse, 15%
        BigDecimal responseScore = BigDecimal.ZERO;
        if (responseTimeHours != null && responseTimeHours.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal normalized = BigDecimal.ONE.divide(
                    BigDecimal.ONE.add(responseTimeHours.divide(new BigDecimal("24"), 4, RoundingMode.HALF_UP)),
                    4, RoundingMode.HALF_UP);
            responseScore = normalized.multiply(new BigDecimal("15"));
        } else {
            responseScore = new BigDecimal("15"); // perfect score if no data
        }
        score = score.add(responseScore);

        // Account age: normalize (0-365 days -> 0-100), 10%
        BigDecimal ageScore = BigDecimal.ZERO;
        if (accountAgeDays != null && accountAgeDays > 0) {
            ageScore = BigDecimal.valueOf(Math.min(accountAgeDays, 365))
                    .divide(new BigDecimal("365"), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("10"));
        }
        score = score.add(ageScore);

        this.totalScore = score.setScale(2, RoundingMode.HALF_UP);
        this.trustTier = TrustTier.fromScore(this.totalScore);
    }
}
