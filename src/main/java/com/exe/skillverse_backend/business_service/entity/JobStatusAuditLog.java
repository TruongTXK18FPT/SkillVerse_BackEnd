package com.exe.skillverse_backend.business_service.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Audit log cho mọi thay đổi status
 */
@Entity
@Table(name = "job_status_audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatusAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "short_term_job_id")
    private Long shortTermJobId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "previous_status", nullable = false, length = 50)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 50)
    private String newStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "changed_by_role", nullable = false, length = 20)
    private AuditRole changedByRole;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON for additional info

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public enum AuditRole {
        RECRUITER,
        CANDIDATE,
        ADMIN,
        SYSTEM
    }
}
