package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity storing match scores between jobs and candidates.
 * Used for fast retrieval and ranking in candidate search.
 */
@Entity
@Table(name = "candidate_match_scores")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandidateMatchScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private User candidate;

    // Scoring components (0-1 scale)
    @Column(name = "skill_match_score", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal skillMatchScore = BigDecimal.ZERO;

    @Column(name = "experience_match_score", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal experienceMatchScore = BigDecimal.ZERO;

    @Column(name = "budget_match_score", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal budgetMatchScore = BigDecimal.ZERO;

    @Column(name = "premium_bonus_score", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal premiumBonusScore = BigDecimal.ZERO;

    // Weighted total score
    @Column(name = "total_score", precision = 5, scale = 4)
    @Builder.Default
    private BigDecimal totalScore = BigDecimal.ZERO;

    // AI enhanced fields
    @Column(name = "ai_fit_summary", columnDefinition = "TEXT")
    private String aiFitSummary;

    @Column(name = "ai_skill_signals", columnDefinition = "jsonb")
    private String aiSkillSignals; // JSON string

    @Column(name = "ai_reasoning", columnDefinition = "TEXT")
    private String aiReasoning;

    // Metadata
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "is_recalculated", nullable = false)
    @Builder.Default
    private Boolean isRecalculated = false;

    @PrePersist
    protected void onCreate() {
        if (calculatedAt == null) {
            calculatedAt = LocalDateTime.now();
        }
    }

    /**
     * Calculate total score with default weights
     * Formula: 0.4 * skill + 0.3 * experience + 0.2 * budget + 0.1 * premium
     */
    public void calculateTotalScore() {
        BigDecimal skillWeight = new BigDecimal("0.40");
        BigDecimal expWeight = new BigDecimal("0.30");
        BigDecimal budgetWeight = new BigDecimal("0.20");
        BigDecimal premiumWeight = new BigDecimal("0.10");

        this.totalScore = skillMatchScore.multiply(skillWeight)
                .add(experienceMatchScore.multiply(expWeight))
                .add(budgetMatchScore.multiply(budgetWeight))
                .add(premiumBonusScore.multiply(premiumWeight));
    }

    /**
     * Calculate total score with custom weights
     */
    public void calculateTotalScore(BigDecimal skillWeight, BigDecimal expWeight,
                                   BigDecimal budgetWeight, BigDecimal premiumWeight) {
        this.totalScore = skillMatchScore.multiply(skillWeight)
                .add(experienceMatchScore.multiply(expWeight))
                .add(budgetMatchScore.multiply(budgetWeight))
                .add(premiumBonusScore.multiply(premiumWeight));
    }
}
