package com.exe.skillverse_backend.student_skill_verification.entity;

import com.exe.skillverse_backend.auth_service.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * [Nghiệp vụ] Yêu cầu xác thực skill của student.
 * Student muốn được công nhận 1 skill → tạo request kèm
 * chứng chỉ/bằng cấp/kinh nghiệm/project → admin review → approve/reject.
 */
@Entity
@Table(name = "student_skill_verification_requests", indexes = {
        @Index(name = "idx_ssvr_user_status", columnList = "user_id, status"),
        @Index(name = "idx_ssvr_status_requested", columnList = "status, requested_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSkillVerificationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Tên skill cần xác thực (normalized uppercase, e.g. "REACT", "JAVA_SPRING_BOOT") */
    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StudentVerificationStatus status = StudentVerificationStatus.PENDING;

    /** Link GitHub repo/profile liên quan skill */
    @Column(name = "github_url", length = 500)
    private String githubUrl;

    /** Link portfolio cá nhân */
    @Column(name = "portfolio_url", length = 500)
    private String portfolioUrl;

    /** Mô tả kinh nghiệm, lý do xin xác thực */
    @Column(name = "additional_notes", columnDefinition = "TEXT")
    private String additionalNotes;

    /** Ghi chú của admin khi duyệt/reject */
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    /** Admin đã review */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** Danh sách evidence kèm theo request */
    @OneToMany(mappedBy = "verificationRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StudentVerificationEvidence> evidences = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
