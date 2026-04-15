package com.exe.skillverse_backend.assignment_ai_service.entity;

import com.exe.skillverse_backend.course_service.entity.Assignment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "assignment_prompt_audit_log")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AssignmentPromptAuditLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "admin_name", nullable = false, length = 200)
    private String adminName;

    @Column(nullable = false, length = 50)
    private String action;

    @Lob
    @Column(name = "before_value", columnDefinition = "TEXT")
    private String beforeValue;

    @Lob
    @Column(name = "after_value", columnDefinition = "TEXT")
    private String afterValue;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}