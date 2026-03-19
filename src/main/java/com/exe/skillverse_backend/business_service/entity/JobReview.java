package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Review/Rating hai chiều giữa Recruiter và Candidate
 */
@Entity
@Table(name = "job_reviews", uniqueConstraints = {
    @UniqueConstraint(name = "uk_job_review_app_reviewer", columnNames = {"application_id", "reviewer_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private ShortTermJobApplication application;

    // Reviewer - người đánh giá
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    // Reviewee - người được đánh giá
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewee_id", nullable = false)
    private User reviewee;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_type", nullable = false, length = 30)
    private ReviewType reviewType;

    // ==================== RATING ====================
    
    @Column(nullable = false)
    private Integer rating; // 1-5 sao

    @Column(columnDefinition = "TEXT")
    private String comment; // Nội dung đánh giá

    @Column(columnDefinition = "TEXT")
    private String strengths; // Điểm mạnh

    @Column(columnDefinition = "TEXT")
    private String improvements; // Điểm cần cải thiện

    @Column(columnDefinition = "TEXT")
    private String recommendations; // Khuyến nghị

    // ==================== SPECIFIC RATINGS ====================
    
    @Column(name = "communication_rating")
    private Integer communicationRating; // 1-5

    @Column(name = "quality_rating")
    private Integer qualityRating; // 1-5

    @Column(name = "timeliness_rating")
    private Integer timelinessRating; // 1-5

    @Column(name = "professionalism_rating")
    private Integer professionalismRating; // 1-5

    // ==================== VISIBILITY ====================
    
    @Builder.Default
    @Column(name = "is_public")
    private Boolean isPublic = true;

    // ==================== TIMESTAMPS ====================
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum ReviewType {
        RECRUITER_TO_CANDIDATE,  // Recruiter đánh giá Candidate
        CANDIDATE_TO_RECRUITER   // Candidate đánh giá Recruiter
    }
}
