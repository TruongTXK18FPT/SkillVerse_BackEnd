package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Note cho revision request
 */
@Entity
@Table(name = "revision_notes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisionNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private ShortTermJobApplication application;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String note;

    @Column(name = "specific_issues", columnDefinition = "TEXT")
    private String specificIssues; // JSON array

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
    }
}
