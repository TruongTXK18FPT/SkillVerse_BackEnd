package com.exe.skillverse_backend.user_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import com.exe.skillverse_backend.shared.entity.Media;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "avatar_media_id")
    private Long avatarMediaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avatar_media_id", insertable = false, updatable = false)
    private Media avatarMedia;

    @Column(name = "avatar_position")
    private String avatarPosition; // center, top, bottom, left, right, etc.

    @Column(columnDefinition = "TEXT")
    private String bio;

    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    private String region;

    @Column(name = "company_id")
    private Long companyId; // FK to business entity (nullable)

    @Column(name = "social_links", columnDefinition = "TEXT")
    private String socialLinks; // JSON string for social media links

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
