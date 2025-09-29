package com.exe.skillverse_backend.premium_service.entity;

import jakarta.persistence.*;
import lombok.*;
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
     * Discount percentage for students (0-100)
     */
    @Column(name = "student_discount_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal studentDiscountPercent = BigDecimal.ZERO;

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
     * Plan types corresponding to the UI
     */
    public enum PlanType {
        PREMIUM_BASIC,
        PREMIUM_PLUS,
        STUDENT_PACK
    }

    /**
     * Calculate discounted price for students
     */
    public BigDecimal getStudentPrice() {
        if (studentDiscountPercent.compareTo(BigDecimal.ZERO) == 0) {
            return price;
        }
        BigDecimal discount = price.multiply(studentDiscountPercent).divide(BigDecimal.valueOf(100));
        return price.subtract(discount);
    }

    /**
     * Check if this plan is available for new subscriptions
     */
    public boolean isAvailableForSubscription() {
        return isActive && (maxSubscribers == null ||
                subscriptions.size() < maxSubscribers);
    }
}