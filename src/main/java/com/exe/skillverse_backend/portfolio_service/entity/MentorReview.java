package com.exe.skillverse_backend.portfolio_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mentor_reviews")
public class MentorReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // Student/mentee receiving the review

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentor_id", nullable = false)
    private User mentor; // Mentor giving the review

    @Column(name = "feedback", columnDefinition = "TEXT", nullable = false)
    private String feedback;

    @Column(name = "skill_endorsed", length = 255)
    private String skillEndorsed; // Specific skill the mentor is endorsing

    @Column(name = "rating")
    private Integer rating; // 1-5 stars

    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false; // Admin verified

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = true; // Show on portfolio

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
