package com.exe.skillverse_backend.shared.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "certificates")
@Data
public class Certificate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String issuer;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "icon_media_id")
    private Long iconMediaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icon_media_id", insertable = false, updatable = false)
    private Media iconMedia;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}