package com.exe.skillverse_backend.ai_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "taxonomy_entries", indexes = {
        @Index(columnList = "domain"),
        @Index(columnList = "role"),
        @Index(columnList = "active")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxonomyEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 100)
    private String domain;

    @Column(length = 150)
    private String role;

    @Column(name = "industry", length = 150)
    private String industry;

    @Column(name = "keywords", columnDefinition = "TEXT")
    private String keywords; // comma-separated keywords

    @Builder.Default
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
