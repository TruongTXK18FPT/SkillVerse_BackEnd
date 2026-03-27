package com.exe.skillverse_backend.premium_service.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Premium plan entity defining subscription tiers and pricing
 */
@Entity
@Table(name = "premium_plans", indexes = {
        @Index(columnList = "name"),
        @Index(columnList = "is_active"),
        @Index(columnList = "price")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PremiumPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", nullable = false, length = 10)
    @Builder.Default
    private String currency = "VND";

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 20)
    private PlanType planType;

    /**
     * Target role for this plan - used for filtering in UI
     * LEARNER: For regular users/students
     * RECRUITER: For recruiter users
     * PARENT: For parent accounts
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_role", length = 20)
    @Builder.Default
    private TargetRole targetRole = TargetRole.LEARNER;

    /**
     * Legacy storage for role-based discount percentage (0-100).
     * The column name is kept for backward compatibility while pricing logic
     * transitions away from student-specific semantics.
     */
    @Column(name = "student_discount_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal studentDiscountPercent = BigDecimal.ZERO;

    @Column(name = "discount_percent", precision = 5, scale = 2)
    private BigDecimal discountPercent;

    /**
     * Feature list stored as JSON
     */
    @Column(name = "features", columnDefinition = "TEXT")
    private String features;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * Maximum number of concurrent subscriptions (null = unlimited)
     */
    @Column(name = "max_subscribers")
    private Integer maxSubscribers;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<UserSubscription> subscriptions = new HashSet<>();

    /**
     * Feature limits configured for this plan
     */
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private Set<PlanFeatureLimits> featureLimits = new HashSet<>();

    /**
     * Plan types corresponding to the UI
     */
    public enum PlanType {
        FREE_TIER,
        PREMIUM_BASIC,
        PREMIUM_PLUS,
        STUDENT_PACK,
        RECRUITER_PRO
    }

    /**
     * Target roles for filtering plans in UI
     */
    public enum TargetRole {
        LEARNER,    // Regular learners/students
        RECRUITER,  // Recruiter users
        PARENT      // Parent accounts
    }

    /**
     * Generic configured discount percentage for this plan's target role.
     */
    public BigDecimal getDiscountPercent() {
        if (discountPercent != null) {
            return discountPercent;
        }
        return studentDiscountPercent != null ? studentDiscountPercent : BigDecimal.ZERO;
    }

    /**
     * Calculate discounted price for the plan's configured target role.
     */
    public BigDecimal getDiscountedPrice() {
        BigDecimal effectiveDiscountPercent = getDiscountPercent();
        if (effectiveDiscountPercent.compareTo(BigDecimal.ZERO) == 0) {
            return price;
        }
        BigDecimal discount = price.multiply(effectiveDiscountPercent).divide(BigDecimal.valueOf(100));
        return price.subtract(discount);
    }

    /**
     * Backward-compatible alias for older student-specific consumers.
     */
    public BigDecimal getStudentPrice() {
        return getDiscountedPrice();
    }

    /**
     * Check if this plan is available for new subscriptions
     */
    public boolean isAvailableForSubscription() {
        return isActive && (maxSubscribers == null ||
                subscriptions.size() < maxSubscribers);
    }
}
