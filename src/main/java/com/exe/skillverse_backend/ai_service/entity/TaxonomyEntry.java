package com.exe.skillverse_backend.ai_service.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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

    @Builder.Default
    @OneToMany(mappedBy = "taxonomyEntry")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ChatSession> chatSessions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
