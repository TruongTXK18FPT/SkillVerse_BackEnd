package com.exe.skillverse_backend.meowl_chat_service.entity;

import com.exe.skillverse_backend.meowl_chat_service.model.MeowlRoleMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persist Meowl onboarding/role preferences per user.
 */
@Entity
@Table(name = "meowl_user_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeowlUserPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_role_mode", length = 20)
    private MeowlRoleMode preferredRoleMode;

    @Builder.Default
    @Column(name = "onboarding_seen", nullable = false)
    private boolean onboardingSeen = false;

    @Column(name = "onboarding_seen_at")
    private LocalDateTime onboardingSeenAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
