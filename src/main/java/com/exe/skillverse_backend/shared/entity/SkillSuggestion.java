package com.exe.skillverse_backend.shared.entity;

import com.exe.skillverse_backend.shared.enums.SkillSuggestionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "skill_suggestions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suggested_name", nullable = false)
    private String suggestedName;

    @Column(name = "suggested_canonical_key", nullable = false)
    private String suggestedCanonicalKey;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "source_user_id")
    private Long sourceUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SkillSuggestionStatus status = SkillSuggestionStatus.PENDING;

    @Column(name = "matched_skill_id")
    private Long matchedSkillId;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
