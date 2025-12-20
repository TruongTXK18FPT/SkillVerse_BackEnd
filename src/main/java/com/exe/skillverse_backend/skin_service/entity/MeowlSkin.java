package com.exe.skillverse_backend.skin_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "meowl_skins")
public class MeowlSkin {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String skinCode; // e.g., "default", "santa", "satan"

    @Column(nullable = false)
    private String name;

    @Column(name = "name_vi", nullable = false)
    private String nameVi;

    @Column(nullable = false)
    private String imageUrl;

    @Column(name = "is_premium", nullable = false)
    private boolean isPremium = false;

    @Column(nullable = false)
    private BigDecimal price; // 0 for free

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
