package com.exe.skillverse_backend.notification_service.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_fcm_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserFcmToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_token", nullable = false, length = 500)
    private String deviceToken;

    @Column(name = "device_type", nullable = false, length = 20)
    @Builder.Default
    private String deviceType = "ANDROID"; // ANDROID, IOS, WEB

    @Column(name = "device_name", length = 100)
    private String deviceName;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = java.time.LocalDateTime.now();
    }
}
