package com.exe.skillverse_backend.skin_service.entity;

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
@Table(name = "user_skins")
public class UserSkin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skin_id", nullable = false)
    private MeowlSkin skin;

    @Builder.Default
    @Column(name = "purchased_at", nullable = false)
    private LocalDateTime purchasedAt = LocalDateTime.now();
    
    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
